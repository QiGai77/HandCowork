package app.xuezhang.engine.plugin

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * v0.0.7.8 插件管理：安装（校验 sha256 → 安全解压到临时目录 → 校验 plugin.json 与兼容性 → 原子替换，失败回滚）、
 * 启用/停用（停用即完全还原：注册表清空后按剩余启用插件重建）、卸载、设置。
 * 目录：files/plugins/<id>/…，状态 files/plugins/state.json。
 */
object Plugins {
    data class Installed(val m: PluginManifest, val dir: File, val compat: Compat) {
        fun file(rel: String): File? = rel.takeIf { it.isNotBlank() && PluginFiles.safePath(it) }?.let { File(dir, it) }?.takeIf { it.isFile }
    }

    var root: File? = null
        private set
    var appVersion: String = "0.0.8.0"
    val installed = mutableStateListOf<Installed>()
    val state = mutableStateOf(PluginState())
    /** 每次重建注册表 +1，界面据此刷新（主题、封面、提示词、服务商）。 */
    val epoch = mutableStateOf(0)
    val lastError = mutableStateOf<String?>(null)

    fun init(dir: File, version: String) {
        root = dir.apply { mkdirs() }
        appVersion = version
        state.value = File(dir, "state.json").takeIf { it.isFile }?.let { PluginState.parse(it.readText()) } ?: PluginState()
        // 上次安装中途被杀：清理临时目录，把 .bak 还原
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith(".stage-")) f.deleteRecursively()
            if (f.name.startsWith(".bak-")) { val id = f.name.removePrefix(".bak-"); val live = File(dir, id); if (!live.exists()) f.renameTo(live) else f.deleteRecursively() }
        }
        scan(); applyAll()
    }

    fun scan() {
        val r = root ?: return
        val list = r.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.mapNotNull { d ->
            runCatching {
                val m = PluginManifest.parse(File(d, "plugin.json").readText())
                if (m.id != d.name) null else Installed(m, d, Compatibility.check(m, appVersion))
            }.getOrNull()
        }.orEmpty().sortedBy { it.m.name }
        installed.clear(); installed.addAll(list)
    }

    private fun saveState() { root?.let { File(it, "state.json").writeText(state.value.toJson()) } }

    fun isEnabled(id: String) = id in state.value.enabled
    fun enabled(): List<Installed> = installed.filter { it.m.id in state.value.enabled && it.compat.ok }

    fun setEnabled(id: String, on: Boolean) {
        val p = installed.firstOrNull { it.m.id == id } ?: return
        if (on && !p.compat.ok) { lastError.value = p.compat.reason; return }
        var en = state.value.enabled
        if (on && p.m.types.contains("theme")) {
            // 同时只启用一个主题
            en = en - installed.filter { it.m.types.contains("theme") }.map { it.m.id }.toSet()
        }
        state.value = state.value.copy(enabled = if (on) en + id else en - id)
        saveState(); applyAll()
    }

    fun setting(id: String, key: String): String {
        val def = installed.firstOrNull { it.m.id == id }?.m?.settings?.firstOrNull { it.key == key }?.default.orEmpty()
        return state.value.setting(id, key, def)
    }

    fun setSetting(id: String, key: String, value: String) {
        val cur = state.value.settings[id].orEmpty() + (key to value)
        state.value = state.value.copy(settings = state.value.settings + (id to cur)); saveState(); applyAll()
    }

    fun setMarketUrl(u: String) { state.value = state.value.copy(marketUrl = u.trim()); saveState() }

    fun uninstall(id: String) {
        val r = root ?: return
        File(r, id).deleteRecursively()
        state.value = state.value.copy(enabled = state.value.enabled - id, settings = state.value.settings - id); saveState()
        scan(); applyAll()
    }

    /**
     * 从 zip 字节安装。expectedSha 来自市场 index（从文件安装时为 null，只做结构校验）。
     * 成功返回清单；失败抛异常（中文原因），已安装的旧版本原样保留。
     */
    fun installZip(bytes: ByteArray, expectedSha: String? = null, enable: Boolean = false): PluginManifest {
        val r = root ?: throw IllegalStateException("插件目录未初始化")
        require(bytes.size <= PluginFiles.MAX_PACKAGE) { "插件包太大（>${PluginFiles.MAX_PACKAGE / 1024 / 1024}MB）" }
        if (expectedSha != null) {
            val got = PluginFiles.sha256(bytes)
            require(got.equals(expectedSha, true)) { "校验失败：sha256 不一致，可能下载不完整或被篡改" }
        }
        val stage = File(r, ".stage-" + System.nanoTime()).apply { mkdirs() }
        try {
            val m = unpack(bytes, stage)
            val compat = Compatibility.check(m, appVersion)
            require(compat.ok) { "不兼容：${compat.reason}" }
            m.entries.forEach { (k, v) -> require(File(stage, v).isFile) { "缺少 $k 入口文件：$v" } }
            // 原子替换 + 回滚
            val live = File(r, m.id); val bak = File(r, ".bak-" + m.id)
            bak.deleteRecursively()
            if (live.exists()) require(live.renameTo(bak)) { "无法备份旧版本" }
            if (!stage.renameTo(live)) { bak.renameTo(live); throw IllegalStateException("无法写入插件目录") }
            try {
                scan()
                if (enable) state.value = state.value.copy(enabled = state.value.enabled + m.id).also { saveState() }
                applyAll()
                if (RegistryErrors.last != null && m.id in state.value.enabled) throw IllegalStateException(RegistryErrors.last)
            } catch (e: Exception) {
                live.deleteRecursively(); if (bak.exists()) bak.renameTo(live)
                scan(); applyAll()
                throw IllegalStateException("插件加载失败，已回滚：${e.message}")
            }
            bak.deleteRecursively()
            return m
        } finally { stage.deleteRecursively() }
    }

    /** 安全解压：限制条目路径、扩展名、总大小；剥掉外层文件夹。 */
    fun unpack(bytes: ByteArray, dest: File): PluginManifest {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> generateSequence { z.nextEntry }.forEach { names.add(it.name) } }
        require(names.isNotEmpty()) { "不是有效的插件包（zip）" }
        val prefix = PluginFiles.stripRoot(names)
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.name.startsWith(prefix)) continue
                val rel = e.name.removePrefix(prefix)
                if (rel.isEmpty()) continue
                require(PluginFiles.safePath(rel)) { "插件包里有不安全的路径：${e.name}" }
                require(PluginFiles.allowedFile(rel)) { "插件包里有不允许的文件类型：$rel（不支持可执行代码）" }
                val out = File(dest, rel)
                require(out.canonicalPath.startsWith(dest.canonicalPath + File.separator)) { "路径越界：$rel" }
                if (e.isDirectory || rel.endsWith("/")) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                out.outputStream().use { o ->
                    val buf = ByteArray(16 * 1024)
                    while (true) { val n = z.read(buf); if (n < 0) break; total += n; require(total <= PluginFiles.MAX_UNPACKED) { "解压后太大" }; o.write(buf, 0, n) }
                }
            }
        }
        val mf = File(dest, "plugin.json"); require(mf.isFile) { "插件包缺少 plugin.json" }
        return PluginManifest.parse(mf.readText())
    }

    /** 按启用插件重建全部注册表；任何一个插件出错只记录，不影响其它插件。 */
    fun applyAll() {
        RegistryErrors.last = null
        val on = enabled()
        runCatching { ThemeRegistry.load(on) }.onFailure { RegistryErrors.last = "主题：${it.message}"; ThemeRegistry.clear() }
        runCatching { CoverPackRegistry.load(on) }.onFailure { RegistryErrors.last = "封面：${it.message}"; CoverPackRegistry.clear() }
        runCatching { SkillRegistry.load(on) }.onFailure { RegistryErrors.last = "技能：${it.message}"; SkillRegistry.clear() }
        runCatching { ProviderRegistry.load(on) }.onFailure { RegistryErrors.last = "服务商：${it.message}"; ProviderRegistry.clear() }
        epoch.value++
    }
}

object RegistryErrors { @Volatile var last: String? = null }

/** 当前主题（只取一个启用的 theme 插件）。字体/纹理由 UI 层按文件路径懒加载。 */
object ThemeRegistry {
    data class Active(val pluginId: String, val spec: ThemeSpec, val dir: File, val splash: SplashSpec?)
    val active = mutableStateOf<Active?>(null)
    fun clear() { active.value = null }
    fun load(on: List<Plugins.Installed>) {
        val p = on.firstOrNull { "theme" in it.m.types || "splash" in it.m.types }
        if (p == null) { clear(); return }
        val spec = p.m.entries["theme"]?.let { p.file(it) }?.let { ThemeSpec.parse(it.readText()) }
        val splashRel = p.m.entries["splash"] ?: spec?.splash
        val splash = splashRel?.takeIf { it.isNotBlank() }?.let { p.file(it) }?.let { SplashSpec.parse(it.readText()) }
        active.value = Active(p.m.id, spec ?: ThemeSpec(p.m.name), p.dir, splash)
    }
}

object CoverPackRegistry {
    data class Active(val pluginId: String, val pack: CoverPack, val dir: File)
    val active = mutableStateOf<Active?>(null)
    fun clear() { active.value = null }
    fun load(on: List<Plugins.Installed>) {
        val p = on.firstOrNull { "covers" in it.m.types } ?: run { clear(); return }
        val f = p.m.entries["covers"]?.let { p.file(it) } ?: run { clear(); return }
        val pack = CoverPack.parse(f.readText())
        active.value = if (pack.covers.isEmpty()) null else Active(p.m.id, pack, p.dir)
    }
    fun coverFile(index: Int): File? = active.value?.let { a -> a.pack.covers.getOrNull(Math.floorMod(index, a.pack.covers.size))?.let { File(a.dir, it.file) } }
    fun cover(index: Int): CoverPack.Cover? = active.value?.let { a -> a.pack.covers.getOrNull(Math.floorMod(index, a.pack.covers.size)) }
    fun spineFile(index: Int): File? = active.value?.let { a -> cover(index)?.spine?.takeIf { it.isNotBlank() }?.let { File(a.dir, it) } }
}

/** 技能 / 提示词：识别优化等文本管线 + 提示词覆盖（用户自定义 > 插件 > 内置）。 */
object SkillRegistry {
    /**
     * pinyin：插件带的汉字→拼音表（hotwords 步骤用）；lexicon：所有启用插件的词表合集；
     * hooks：v0.0.8.0 通用钩子配置（第一个声明的插件生效）。
     */
    data class Refine(val pluginId: String, val program: TextRules.Program, val level: Int, val prompts: Map<String, String>,
                      val pinyin: Hotwords.Pinyin? = null, val lexicon: List<Hotwords.Term> = emptyList(), val hooks: org.json.JSONObject = org.json.JSONObject())
    val refine = mutableStateOf<Refine?>(null)
    /** 插件提供的系统提示词覆盖：键同 Prompts.catalog（segment / notes0 / notes1 …）。 */
    val prompts = mutableStateOf<Map<String, String>>(emptyMap())
    /** v0.0.8.0 提示词追加：规则 llm 里写 "segment+": "prompts/x.md"，内容追加到该提示词末尾（多个插件依次追加）。 */
    val appends = mutableStateOf<Map<String, String>>(emptyMap())
    fun clear() { refine.value = null; prompts.value = emptyMap(); appends.value = emptyMap() }
    fun load(on: List<Plugins.Installed>) {
        clear()
        val pm = LinkedHashMap<String, String>()
        val ap = LinkedHashMap<String, String>()
        val lex = mutableListOf<Hotwords.Term>()
        data class P(val inst: Plugins.Installed, val prog: TextRules.Program, val prompts: Map<String, String>, val pinyin: Hotwords.Pinyin?)
        val progs = mutableListOf<P>()
        on.filter { "skills" in it.m.types || "prompts" in it.m.types }.forEach { p ->
            p.m.entries.filterKeys { it.startsWith("prompt.") }.forEach { (k, v) -> p.file(v)?.let { pm[k.removePrefix("prompt.")] = it.readText() } }
            val sk = p.m.entries["skills"]?.let { p.file(it) } ?: return@forEach
            val prog = TextRules.parse(sk.readText())
            val prompts = prog.llm.filterKeys { !it.endsWith("+") }.mapNotNull { (k, v) -> p.file(v)?.let { k to it.readText() } }.toMap()
            prog.llm.filterKeys { it.endsWith("+") }.forEach { (k, v) -> p.file(v)?.let { f -> val key = k.removeSuffix("+"); ap[key] = (ap[key]?.let { it + "\n\n" } ?: "") + f.readText() } }
            lex += prog.lexicon
            val py = prog.steps.filterIsInstance<TextRules.HotwordsStep>().firstOrNull()?.pinyin?.let { p.file(it) }?.let { runCatching { Hotwords.Pinyin.parse(it.readText()) }.getOrNull() }
            progs += P(p, prog, prompts, py)
        }
        // 文本管线：取第一个真正改文本的插件（只带词表的插件如「理工科学长」不再抢占识别优化，v0.0.8.0 修复）
        val primary = progs.firstOrNull { it.prog.isPipeline && it.prog.hooks.length() > 0 } ?: progs.firstOrNull { it.prog.isPipeline }
        if (primary != null) {
            val setting = primary.inst.m.settings.firstOrNull { it.key == "strength" || it.key == "level" }?.key ?: "strength"
            val lv = TextRules.level(Plugins.setting(primary.inst.m.id, setting))
            val hooks = progs.firstOrNull { it.prog.hooks.length() > 0 }?.prog?.hooks ?: org.json.JSONObject()
            refine.value = Refine(primary.inst.m.id, primary.prog, lv, primary.prompts, progs.firstNotNullOfOrNull { it.pinyin }, lex.distinctBy { it.term }, hooks)
        }
        prompts.value = pm
        appends.value = ap
    }

    fun append(key: String): String = appends.value[key]?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: ""

    /** 确定性清洗（无网络）。没有启用识别优化插件时原样返回。 */
    fun clean(raw: String, ctx: TextRules.Ctx = TextRules.Ctx()): TextRules.Result? = refine.value?.let { TextRules.run(it.program, raw, it.level, ctx) }
}

/** 服务商：插件预设 + 用户导入/自建的配置（存 plugins/providers.json，不含 Key）。 */
object ProviderRegistry {
    val presets = mutableStateListOf<ProviderConfig>()
    val custom = mutableStateListOf<ProviderConfig>()
    /** 供应商兼容插件是否启用（启用才显示「导入服务商」入口、才走自定义服务商调用）。 */
    val enabled = mutableStateOf(false)
    fun clear() { presets.clear(); enabled.value = false }
    fun load(on: List<Plugins.Installed>) {
        clear()
        on.filter { "provider" in it.m.types }.forEach { p ->
            enabled.value = true
            p.m.entries["providers"]?.let { p.file(it) }?.let { presets.addAll(ProviderConfig.parseMany(it.readText())) }
        }
        custom.clear()
        Plugins.root?.let { File(it, "providers.json") }?.takeIf { it.isFile }?.let { custom.addAll(ProviderConfig.parseMany(it.readText())) }
    }
    fun saveCustom() { Plugins.root?.let { File(it, "providers.json").writeText(ProviderConfig.exportMany(custom)) } }
    fun upsert(c: ProviderConfig) { val i = custom.indexOfFirst { it.id == c.id }; if (i >= 0) custom[i] = c else custom.add(c); saveCustom() }
    fun remove(id: String) { custom.removeAll { it.id == id }; saveCustom() }
    fun byId(id: String): ProviderConfig? = custom.firstOrNull { it.id == id } ?: presets.firstOrNull { it.id == id }
}
