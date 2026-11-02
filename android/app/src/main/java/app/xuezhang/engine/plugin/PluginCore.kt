package app.xuezhang.engine.plugin

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * v0.0.7.8 插件系统核心（纯 Kotlin + org.json，便于单元测试）。
 *
 * 插件是**声明式**的：一个文件夹或 .xzplugin（zip），根目录有 plugin.json，外加资源文件（主题 token、字体、纹理、封面图、
 * 提示词 / 技能 markdown、供应商 json、规则 json）。App 不下载、不加载任何 DEX / so 原生代码；「脚本」用一个小的
 * 规则 DSL（见 [TextRules]）实现，只能做文本替换/删句/标记，碰不到网络和文件。
 */
object PluginApi {
    /** 本版 App 提供的插件 API 版本。插件 apiVersion 大于它就不兼容。 */
    const val VERSION = 1
    val TYPES = setOf("theme", "covers", "prompts", "skills", "provider", "ui-block", "importer", "exporter", "splash")
    /** App 已实现的扩展点；其余类型可以安装但只显示「本版暂不支持」。 */
    val SUPPORTED = setOf("theme", "covers", "prompts", "skills", "provider", "splash")
    val PERMISSIONS = setOf("network")
}

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String = "",
    val description: String = "",
    val types: List<String> = emptyList(),
    val apiVersion: Int = 1,
    val minApp: String = "",
    val maxApp: String = "",
    /** 需要访问的网络主机（默认无）。 */
    val networkHosts: List<String> = emptyList(),
    /** 扩展点 → 相对路径（如 theme → theme.json，skills → skills/refine.json）。 */
    val entries: Map<String, String> = emptyMap(),
    val icon: String = "",
    val screenshots: List<String> = emptyList(),
    val settings: List<PluginSetting> = emptyList(),
    val license: String = "",
    val homepage: String = "",
) {
    companion object {
        private val ID = Regex("^[a-z0-9][a-z0-9._-]{1,63}$")

        /** 解析 plugin.json；不合法抛 [IllegalArgumentException]（中文原因）。 */
        fun parse(json: String): PluginManifest {
            val o = runCatching { JSONObject(json) }.getOrElse { throw IllegalArgumentException("plugin.json 不是合法 JSON") }
            val id = o.optString("id").trim()
            require(ID.matches(id)) { "插件 id 只能用小写字母、数字、点、横线、下划线（2–64 位）：$id" }
            val name = o.optString("name").trim(); require(name.isNotEmpty()) { "缺少 name" }
            val ver = o.optString("version").trim(); require(Semver.valid(ver)) { "version 格式应为 1.2.3：$ver" }
            val types = (o.optJSONArray("types") ?: o.optString("type").takeIf { it.isNotBlank() }?.let { JSONArray().put(it) } ?: JSONArray())
                .strings().map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            require(types.isNotEmpty()) { "缺少 types（theme / covers / prompts / skills / provider / splash …）" }
            types.forEach { require(it in PluginApi.TYPES) { "未知插件类型：$it" } }
            val perms = o.optJSONObject("permissions")
            val hosts = perms?.optJSONArray("network")?.strings().orEmpty()
            hosts.forEach { require(Regex("^[A-Za-z0-9.*-]+(:\\d+)?$").matches(it)) { "network 权限只能写主机名：$it" } }
            val entries = o.optJSONObject("entries")?.let { e -> e.keys().asSequence().associateWith { k -> e.optString(k) } }.orEmpty()
            entries.values.forEach { require(PluginFiles.safePath(it)) { "entries 路径不安全：$it" } }
            return PluginManifest(
                id = id, name = name, version = ver, author = o.optString("author"), description = o.optString("description"),
                types = types, apiVersion = o.optInt("apiVersion", 1), minApp = o.optString("minApp"), maxApp = o.optString("maxApp"),
                networkHosts = hosts, entries = entries, icon = o.optString("icon"),
                screenshots = o.optJSONArray("screenshots")?.strings().orEmpty(),
                settings = o.optJSONArray("settings")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(PluginSetting::parse) } }.orEmpty(),
                license = o.optString("license"), homepage = o.optString("homepage"),
            )
        }
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("version", version).put("author", author)
        .put("description", description).put("types", JSONArray(types)).put("apiVersion", apiVersion).put("minApp", minApp).put("maxApp", maxApp)
        .put("permissions", JSONObject().put("network", JSONArray(networkHosts))).put("entries", JSONObject(entries as Map<*, *>))
        .put("icon", icon).put("screenshots", JSONArray(screenshots)).put("license", license).put("homepage", homepage)
        .put("settings", JSONArray(settings.map { it.toJson() }))
}

/** 插件设置项（App 渲染成选择/开关）：如识别优化的「精炼强度」。 */
data class PluginSetting(val key: String, val label: String, val type: String = "choice", val options: List<String> = emptyList(), val default: String = "") {
    fun toJson(): JSONObject = JSONObject().put("key", key).put("label", label).put("type", type).put("options", JSONArray(options)).put("default", default)
    companion object {
        fun parse(o: JSONObject) = o.optString("key").takeIf { it.isNotBlank() }?.let {
            PluginSetting(it, o.optString("label", it), o.optString("type", "choice"), o.optJSONArray("options")?.strings().orEmpty(), o.optString("default"))
        }
    }
}

internal fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it, null) }

/** 1.2.3 / 0.0.7.8 风格版本比较（数字段逐段比，缺的段当 0；忽略 -beta 等后缀）。 */
object Semver {
    fun valid(v: String) = Regex("^\\d+(\\.\\d+){0,3}([-+][0-9A-Za-z.-]+)?$").matches(v)
    private fun parts(v: String) = v.substringBefore('-').substringBefore('+').split('.').map { it.toIntOrNull() ?: 0 }
    fun compare(a: String, b: String): Int {
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) { val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 }); if (c != 0) return c }
        return 0
    }
}

/** 兼容性检查结果：ok=false 时 reason 是给用户看的中文原因。 */
data class Compat(val ok: Boolean, val reason: String = "")

object Compatibility {
    fun check(m: PluginManifest, appVersion: String, api: Int = PluginApi.VERSION): Compat = when {
        m.apiVersion > api -> Compat(false, "需要插件 API $api 以上（插件要求 ${m.apiVersion}），请先升级 App")
        m.apiVersion < 1 -> Compat(false, "插件 API 版本无效")
        m.minApp.isNotBlank() && Semver.compare(appVersion, m.minApp) < 0 -> Compat(false, "需要 App ${m.minApp} 以上，当前 $appVersion")
        m.maxApp.isNotBlank() && Semver.compare(appVersion, m.maxApp) > 0 -> Compat(false, "只支持 App ${m.maxApp} 及以下，当前 $appVersion")
        m.types.none { it in PluginApi.SUPPORTED } -> Compat(false, "本版 App 还不支持这种插件（${m.types.joinToString("/")}）")
        else -> Compat(true)
    }
}

/** 插件市场 index.json 里的一条。 */
data class MarketEntry(
    val id: String, val name: String, val version: String, val types: List<String>, val apiVersion: Int, val minApp: String,
    val sha256: String, val size: Long, val path: String, val icon: String = "", val summary: String = "", val author: String = "",
    val screenshots: List<String> = emptyList(), val maxApp: String = "",
) {
    fun asManifest() = PluginManifest(id, name, version, author, summary, types, apiVersion, minApp, maxApp)
}

object MarketIndex {
    /** 解析 index.json：{ "apiVersion":1, "plugins":[ {...} ] }；也接受直接一个数组。坏条目跳过。 */
    fun parse(json: String): List<MarketEntry> {
        val t = json.trim()
        val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray("plugins") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                val types = (o.optJSONArray("types") ?: JSONArray().put(o.optString("type"))).strings().filter { it.isNotBlank() }
                val sha = o.optString("sha256").lowercase()
                require(Regex("^[0-9a-f]{64}$").matches(sha))
                val path = o.optString("path").ifBlank { o.optString("url") }
                require(path.isNotBlank() && (path.startsWith("https://") || PluginFiles.safePath(path)))
                MarketEntry(o.getString("id"), o.optString("name", o.getString("id")), o.getString("version"), types, o.optInt("apiVersion", 1),
                    o.optString("minApp"), sha, o.optLong("size"), path, o.optString("icon"), o.optString("summary"), o.optString("author"),
                    o.optJSONArray("screenshots")?.strings().orEmpty(), o.optString("maxApp"))
            }.getOrNull()
        }
    }

    /** 相对路径 → 绝对 URL（以 index.json 所在目录为基准）。 */
    fun resolve(indexUrl: String, path: String): String = if (path.startsWith("https://") || path.startsWith("http://")) path
        else indexUrl.substringBeforeLast('/') + "/" + path.trimStart('/')
}

object PluginFiles {
    /** zip 条目 / entries 路径是否安全（防 zip-slip：不许绝对路径、..、反斜杠、盘符）。 */
    fun safePath(p: String): Boolean {
        if (p.isBlank() || p.startsWith("/") || p.contains('\\') || p.contains(':') || p.contains('\u0000')) return false
        return p.split('/').none { it == ".." }
    }

    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** 单个插件包最大 12MB，解压后最大 24MB（字体 + 纹理足够）。 */
    const val MAX_PACKAGE = 12L * 1024 * 1024
    const val MAX_UNPACKED = 24L * 1024 * 1024
    /** 允许的资源扩展名：不许 dex/so/apk/jar。 */
    private val ALLOWED = setOf("json", "md", "txt", "ttf", "otf", "png", "webp", "jpg", "jpeg", "svg", "xzr")
    fun allowedFile(p: String): Boolean = p.endsWith("/") || p.substringAfterLast('.', "").lowercase() in ALLOWED

    /** 只把 zip 里第一层「包了一层文件夹」的情况剥掉：foo/plugin.json → plugin.json。 */
    fun stripRoot(names: List<String>): String {
        if (names.any { it == "plugin.json" }) return ""
        val root = names.firstOrNull { it.endsWith("/plugin.json") && it.count { c -> c == '/' } == 1 }?.substringBefore('/') ?: return ""
        return "$root/"
    }
}

/** 已安装插件的启用状态与设置（存 plugins/state.json）。 */
data class PluginState(val enabled: Set<String> = emptySet(), val settings: Map<String, Map<String, String>> = emptyMap(), val marketUrl: String = "") {
    fun toJson(): String = JSONObject().put("enabled", JSONArray(enabled.toList()))
        .put("settings", JSONObject().also { o -> settings.forEach { (k, v) -> o.put(k, JSONObject(v as Map<*, *>)) } })
        .put("marketUrl", marketUrl).toString(1)
    fun setting(id: String, key: String, def: String) = settings[id]?.get(key) ?: def
    companion object {
        fun parse(s: String): PluginState = runCatching {
            val o = JSONObject(s)
            PluginState(o.optJSONArray("enabled")?.strings().orEmpty().toSet(),
                o.optJSONObject("settings")?.let { st -> st.keys().asSequence().associateWith { k -> st.getJSONObject(k).let { m -> m.keys().asSequence().associateWith { m.optString(it) } } } }.orEmpty(),
                o.optString("marketUrl"))
        }.getOrDefault(PluginState())
    }
}
