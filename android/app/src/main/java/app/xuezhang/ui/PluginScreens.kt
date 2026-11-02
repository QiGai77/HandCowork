package app.xuezhang.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Store
import app.xuezhang.engine.plugin.Compatibility
import app.xuezhang.engine.plugin.Market
import app.xuezhang.engine.plugin.MarketEntry
import app.xuezhang.engine.plugin.PluginManifest
import app.xuezhang.engine.plugin.Plugins
import app.xuezhang.engine.plugin.ProviderConfig
import app.xuezhang.engine.plugin.ProviderRegistry
import app.xuezhang.net.Api
import app.xuezhang.net.Auth
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// =================== v0.0.7.8 插件：已安装 / 插件市场 / 从文件安装 / 详情 / 自定义服务商 ===================

private val TYPE_LABEL = mapOf("theme" to "主题", "covers" to "封面", "prompts" to "提示词", "skills" to "技能", "provider" to "服务商",
    "ui-block" to "组件", "importer" to "导入", "exporter" to "导出", "splash" to "开屏")

object PluginUi {
    val msg = mutableStateOf<String?>(null)
    val busy = mutableStateOf<String?>(null)
}

@Composable
private fun TypeChips(types: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        types.forEach { t ->
            BasicText(TYPE_LABEL[t] ?: t, Modifier.clip(RoundedCornerShape(6.dp)).background(C.Blue.copy(alpha = 0.10f)).padding(horizontal = 6.dp, vertical = 1.dp),
                style = T.Cap.copy(fontSize = 10.5.sp, color = C.Blue, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun PluginIcon(bmp: ImageBitmap?, name: String, size: androidx.compose.ui.unit.Dp = 46.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(C.Fill), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else BasicText(name.take(1), style = T.Head.copy(fontSize = 20.sp, color = C.Sec))
    }
}

private fun localIcon(p: Plugins.Installed): ImageBitmap? = p.file(p.m.icon)?.let { ThemeArt.bitmap(it, 256) }

@Composable
private fun marketBitmap(path: String, max: Int = 512): ImageBitmap? {
    val v by produceState<ImageBitmap?>(null, path) {
        if (path.isBlank()) return@produceState
        value = withContext(Dispatchers.IO) { Market.image(path)?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() } }
    }
    return v
}

@Composable
private fun MsgBox() {
    PluginUi.msg.value?.let { m ->
        val bad = m.startsWith("失败") || m.startsWith("不兼容")
        BasicText(m, Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (bad) Color(0x14FF3B30) else C.Blue.copy(alpha = 0.07f)).clickable { PluginUi.msg.value = null }.padding(12.dp),
            style = T.Cap.copy(fontSize = 12.5.sp, color = if (bad) C.Red else C.Ink))
    }
}

@Composable
fun PluginsScreen(startTab: Int = 0) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(startTab) }
    @Suppress("UNUSED_VARIABLE") val epoch = Plugins.epoch.value
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            PluginUi.busy.value = "安装中…"
            PluginUi.msg.value = try {
                val m = withContext(Dispatchers.IO) {
                    val b = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    Plugins.installZip(b, null, enable = false)
                }
                "已安装「${m.name}」${m.version}，打开开关即可启用"
            } catch (e: Exception) { "失败：" + (e.message ?: "无法安装") } finally { PluginUi.busy.value = null }
        }
    }
    fun loadMarket() { scope.launch { runCatching { withContext(Dispatchers.IO) { Market.refresh() } } } }
    LaunchedEffect(tab) { if (tab == 1 && Market.entries.value == null && !Market.loading.value) loadMarket() }

    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item { TopInset(); PageHeader("插件", "主题、封面、识别技能、服务商 · 声明式插件，不运行下载来的代码") }
            item {
                LiquidSegmented(items = listOf("已安装 · ${Plugins.installed.size}", "插件市场"), selected = tab, onSelect = { tab = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), height = 40.dp)
            }
            item { MsgBox() }
            if (tab == 0) {
                if (Plugins.installed.isEmpty()) item { EmptyHint("还没有插件", "去「插件市场」看看，或从文件安装 .xzplugin") }
                Plugins.installed.forEach { p -> item(key = "i-" + p.m.id) { InstalledCard(p) } }
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn(PluginUi.busy.value ?: "从文件安装", { pick.launch("*/*") }, Modifier.weight(1f), icon = "folder")
                        Btn("插件市场", { tab = 1 }, Modifier.weight(1f), icon = "download", tint = C.Blue)
                    }
                }
                item { BasicText("插件包是 .xzplugin（zip）：plugin.json + 主题 / 字体 / 纹理 / 封面 / 提示词 / 规则 / 服务商配置。安装前校验 sha256 与兼容性，失败自动回滚；停用即完全还原。",
                    Modifier.padding(horizontal = 24.dp, vertical = 6.dp), style = T.Cap.copy(fontSize = 11.5.sp)) }
            } else {
                item { MarketHeader { loadMarket() } }
                val list = Market.entries.value
                when {
                    Market.loading.value && list == null -> item { Shimmer(Modifier.padding(16.dp).fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp))) }
                    list == null -> item { EmptyHint("读不到插件市场", Market.error.value ?: "检查网络后下拉重试") }
                    list.isEmpty() -> item { EmptyHint("市场里还没有插件", "") }
                    else -> list.forEach { e -> item(key = "m-" + e.id) { MarketCard(e) } }
                }
            }
        }
    }, overlay = { b -> BackBar(b) })
}

@Composable
private fun MarketHeader(onRefresh: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(Plugins.state.value.marketUrl) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("来源：" + (Plugins.state.value.marketUrl.ifBlank { "GitHub · ${Market.OWNER}/${Market.REPO} › ${Market.BRANCH}" }), Modifier.weight(1f),
                style = T.Cap.copy(fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(if (open) "收起" else "换源", Modifier.clip(Capsule()).clickable { open = !open }.padding(horizontal = 8.dp, vertical = 4.dp), style = T.Btn.copy(fontSize = 12.5.sp, color = C.Blue))
            BasicText(if (Market.loading.value) "刷新中…" else "刷新", Modifier.clip(Capsule()).clickable { onRefresh() }.padding(horizontal = 8.dp, vertical = 4.dp), style = T.Btn.copy(fontSize = 12.5.sp, color = C.Blue))
        }
        if (open) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Fill).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(url, { url = it }, Market.DEFAULT_URL, Modifier.weight(1f), style = T.Label.copy(fontSize = 13.sp))
                BasicText("保存", Modifier.clip(Capsule()).clickable { Plugins.setMarketUrl(url); onRefresh(); open = false }.padding(horizontal = 8.dp), style = T.Btn.copy(fontSize = 13.sp, color = C.Blue))
            }
            BasicText("可以填 Gitee 镜像等 index.json 的地址；留空用默认 GitHub。默认地址读不到时会用你连接的 GitHub 令牌走 API。", Modifier.padding(top = 4.dp), style = T.Cap.copy(fontSize = 11.sp))
        }
    }
}

@Composable
private fun InstalledCard(p: Plugins.Installed) {
    val on = Plugins.isEnabled(p.m.id)
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), onClick = { Nav.push(Route.PluginDetail(p.m.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (p.compat.ok) 1f else 0.55f)) {
            PluginIcon(remember(p.m.id, p.m.version) { localIcon(p) }, p.m.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(p.m.name, style = T.Item.copy(fontSize = 16.sp)); Spacer(Modifier.width(6.dp))
                    BasicText("v${p.m.version}", style = T.Cap.copy(fontSize = 11.sp))
                }
                Spacer(Modifier.height(3.dp)); TypeChips(p.m.types)
                Spacer(Modifier.height(3.dp))
                BasicText(if (p.compat.ok) p.m.description else "不兼容：" + p.compat.reason, style = T.Cap.copy(fontSize = 11.5.sp, color = if (p.compat.ok) C.Sec else C.Red), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            if (p.compat.ok) Toggle(on) { v -> Plugins.setEnabled(p.m.id, v); PluginUi.msg.value = if (v) "已启用「${p.m.name}」" else "已停用「${p.m.name}」，相关界面已还原" }
        }
    }
}

@Composable
private fun MarketCard(e: MarketEntry) {
    val scope = rememberCoroutineScope()
    val compat = remember(e) { Compatibility.check(e.asManifest(), Plugins.appVersion) }
    val inst = Plugins.installed.firstOrNull { it.m.id == e.id }
    val upd = Market.hasUpdate(e)
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), onClick = { Nav.push(Route.PluginDetail(e.id, market = true)) }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (compat.ok) 1f else 0.55f)) {
            PluginIcon(marketBitmap(e.icon, 256), e.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(e.name, style = T.Item.copy(fontSize = 16.sp)); Spacer(Modifier.width(6.dp))
                    BasicText("v${e.version} · ${"%.1f".format(e.size / 1024.0 / 1024.0)}MB", style = T.Cap.copy(fontSize = 11.sp))
                }
                Spacer(Modifier.height(3.dp)); TypeChips(e.types)
                Spacer(Modifier.height(3.dp))
                BasicText(if (compat.ok) e.summary else "不兼容：" + compat.reason, style = T.Cap.copy(fontSize = 11.5.sp, color = if (compat.ok) C.Sec else C.Red), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            val label = when { !compat.ok -> "不兼容"; inst == null -> "安装"; upd -> "更新"; else -> "已安装" }
            BasicText(if (PluginUi.busy.value == e.id) "…" else label, Modifier.clip(Capsule()).background(if (compat.ok && (inst == null || upd)) C.Blue else C.Fill)
                .clickable(enabled = compat.ok && (inst == null || upd) && PluginUi.busy.value == null) { installFromMarket(e, scope) }
                .padding(horizontal = 14.dp, vertical = 7.dp), style = T.Btn.copy(fontSize = 13.sp, color = if (compat.ok && (inst == null || upd)) Color.White else C.Sec))
        }
    }
}

private fun installFromMarket(e: MarketEntry, scope: kotlinx.coroutines.CoroutineScope) {
    PluginUi.busy.value = e.id
    scope.launch {
        PluginUi.msg.value = try {
            val m = withContext(Dispatchers.IO) { Market.install(e, enable = true) }
            "已安装并启用「${m.name}」${m.version}（sha256 校验通过）"
        } catch (ex: Exception) { "失败：" + (ex.message ?: "安装失败") } finally { PluginUi.busy.value = null }
    }
}

@Composable
fun PluginDetailScreen(id: String, market: Boolean) {
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val epoch = Plugins.epoch.value
    val inst = Plugins.installed.firstOrNull { it.m.id == id }
    val me = Market.entries.value?.firstOrNull { it.id == id }
    val m: PluginManifest = inst?.m ?: me?.asManifest() ?: run { Nav.pop(); return }
    val compat = inst?.compat ?: Compatibility.check(m, Plugins.appVersion)
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item { TopInset(); Spacer(Modifier.height(44.dp)) }
            item {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    PluginIcon(if (inst != null) remember(inst.m.version) { localIcon(inst) } else marketBitmap(me?.icon.orEmpty(), 256), m.name, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(m.name, style = T.Title)
                        BasicText("v${m.version}" + (if (m.author.isNotBlank()) " · ${m.author}" else "") + (if (inst != null && me != null && Market.hasUpdate(me)) " · 可更新到 ${me.version}" else ""), style = T.Cap)
                        Spacer(Modifier.height(4.dp)); TypeChips(m.types)
                    }
                }
            }
            item { MsgBox() }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        !compat.ok -> BasicText("不兼容：${compat.reason}", Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0x14FF3B30)).padding(12.dp), style = T.Cap.copy(color = C.Red))
                        inst == null -> me?.let { e -> Btn(if (PluginUi.busy.value == e.id) "安装中…" else "安装并启用", { installFromMarket(e, scope) }, Modifier.weight(1f), icon = "download", tint = C.Blue) }
                        else -> {
                            val on = Plugins.isEnabled(id)
                            Btn(if (on) "停用" else "启用", { Plugins.setEnabled(id, !on) }, Modifier.weight(1f), icon = if (on) "x" else "checkc", tint = if (on) Color.Unspecified else C.Blue)
                            if (me != null && Market.hasUpdate(me)) Btn("更新", { installFromMarket(me, scope) }, Modifier.weight(1f), icon = "download")
                            Btn("卸载", { Plugins.uninstall(id); PluginUi.msg.value = "已卸载「${m.name}」"; Nav.pop() }, Modifier.weight(1f), icon = "x")
                        }
                    }
                }
            }
            if (m.description.isNotBlank()) item { BasicText(m.description, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = T.Body) }
            val shots = m.screenshots.ifEmpty { me?.screenshots.orEmpty() }
            if (shots.isNotEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    shots.forEach { sp ->
                        val bmp = if (inst != null) remember(sp) { inst.file(sp)?.let { ThemeArt.bitmap(it, 900) } } else marketBitmap(if (me != null) "${me.path.substringBeforeLast('/', "")}/$sp".trimStart('/') else sp, 900)
                        Box(Modifier.size(150.dp, 320.dp).clip(RoundedCornerShape(16.dp)).background(C.Fill).border(0.5.dp, C.Line, RoundedCornerShape(16.dp))) {
                            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Shimmer(Modifier.fillMaxSize())
                        }
                    }
                }
            }
            if (inst != null && m.settings.isNotEmpty()) {
                item { SectionLabel("设置") }
                m.settings.forEach { st -> item {
                    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                        BasicText(st.label, style = T.Label)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val cur = Plugins.setting(id, st.key)
                            st.options.forEach { o ->
                                val sel = o == cur
                                BasicText(o, Modifier.clip(Capsule()).background(if (sel) C.Blue else C.Fill).clickable { Plugins.setSetting(id, st.key, o) }
                                    .padding(horizontal = 16.dp, vertical = 7.dp), style = T.Btn.copy(fontSize = 13.5.sp, color = if (sel) Color.White else C.Ink))
                            }
                        }
                    }
                } }
            }
            if (inst != null && "provider" in m.types) item {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), onClick = { Nav.push(Route.Providers) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon("server", C.Blue, 20.dp); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText("管理服务商", style = T.Label.copy(fontWeight = FontWeight.SemiBold))
                            BasicText("导入 / 新建 / 选用 · ${ProviderRegistry.presets.size} 个预设 · ${ProviderRegistry.custom.size} 个自定义", style = T.Cap)
                        }
                        Icon("chevr", C.Sec, 16.dp)
                    }
                }
            }
            item { SectionLabel("信息") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    Row2("插件 ID", m.id); Divider(16.dp)
                    Row2("插件 API", "v${m.apiVersion}" + (if (m.minApp.isNotBlank()) " · App ≥ ${m.minApp}" else "")); Divider(16.dp)
                    Row2("网络权限", m.networkHosts.joinToString("、").ifBlank { "无" }); Divider(16.dp)
                    Row2("许可", m.license.ifBlank { "—" })
                }
            }
        }
    }, overlay = { b -> BackBar(b) })
}

// ---------------- 自定义服务商（供应商兼容插件）----------------

@Composable
fun ProvidersScreen() {
    val ctx = LocalContext.current
    val s = Store.settings.value
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    var importText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item { TopInset(); PageHeader("服务商", "任意 OpenAI 兼容 / Claude / Gemini 接口与中转 · Key 只存在本机加密区") }
            item { MsgBox() }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("新建", { editing = ProviderConfig("custom-" + (ProviderRegistry.custom.size + 1), "自定义服务商", baseUrl = "https://") }, Modifier.weight(1f), icon = "plusc", tint = C.Blue)
                    Btn("导入 JSON", { showImport = !showImport }, Modifier.weight(1f), icon = "download")
                    Btn("导出", {
                        val json = ProviderConfig.exportMany(ProviderRegistry.custom)
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("providers", json))
                        PluginUi.msg.value = "已复制 ${ProviderRegistry.custom.size} 个服务商配置（不含 Key）"
                    }, Modifier.weight(1f), icon = "cloudup")
                }
            }
            if (showImport) item {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                    BasicText("粘贴服务商配置 JSON（单个、数组或 {providers:[…]}）；里面的 key/token 字段会被丢弃", style = T.Cap)
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(importText, { importText = it }, Modifier.fillMaxWidth().heightIn(min = 110.dp).clip(RoundedCornerShape(10.dp)).background(C.Fill).padding(10.dp),
                        textStyle = T.Cap.copy(fontSize = 12.sp, color = C.Ink, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), cursorBrush = SolidColor(C.Blue))
                    Spacer(Modifier.height(8.dp))
                    Btn("导入", {
                        val list = runCatching { ProviderConfig.parseMany(importText) }.getOrDefault(emptyList())
                        list.forEach { ProviderRegistry.upsert(it) }
                        PluginUi.msg.value = if (list.isEmpty()) "失败：没有识别到服务商配置" else "已导入 ${list.size} 个：" + list.joinToString("、") { it.name }
                        if (list.isNotEmpty()) { importText = ""; showImport = false }
                    }, Modifier.fillMaxWidth(), tint = C.Blue)
                }
            }
            editing?.let { e -> item { ProviderEditor(e, onDone = { editing = null }) } }
            item { SectionLabel("我的服务商") }
            if (ProviderRegistry.custom.isEmpty()) item { BasicText("还没有：从下面的预设一键添加，或新建 / 导入", Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = T.Cap) }
            ProviderRegistry.custom.forEach { c -> item(key = "c-" + c.id) { ProviderRow(c, s.customProvider == c.id, onEdit = { editing = c }) } }
            item { SectionLabel("预设（来自插件）") }
            ProviderRegistry.presets.forEach { c -> item(key = "p-" + c.id) {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProviderLogo(c.baseUrl, 30.dp); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(c.name, style = T.Label.copy(fontWeight = FontWeight.SemiBold))
                            BasicText("${c.style} · ${c.baseUrl}", style = T.Cap.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        BasicText("添加", Modifier.clip(Capsule()).background(C.Blue).clickable { ProviderRegistry.upsert(c); editing = c; PluginUi.msg.value = "已添加「${c.name}」，填上 Key 后选用" }
                            .padding(horizontal = 14.dp, vertical = 6.dp), style = T.Btn.copy(fontSize = 13.sp, color = Color.White))
                    }
                }
            } }
            item {
                BasicText("选用后，大模型调用（笔记、复习卡、导图、问答）都走这个服务商；声明了「语音识别」能力的 OpenAI 兼容服务商也会接管转写。" +
                    "Key 用系统 Keystore 加密保存在本机，不会写进插件、导出 JSON 或任何备份。停用供应商兼容插件即恢复原来的服务商设置。",
                    Modifier.padding(horizontal = 24.dp, vertical = 10.dp), style = T.Cap.copy(fontSize = 11.5.sp))
            }
        }
    }, overlay = { b -> BackBar(b) })
}

@Composable
private fun ProviderRow(c: ProviderConfig, active: Boolean, onEdit: () -> Unit) {
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().then(if (active) Modifier.border(1.5.dp, C.Blue, RoundedCornerShape(22.dp)) else Modifier), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderLogo(c.baseUrl, 30.dp); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                BasicText(c.name + if (active) " · 使用中" else "", style = T.Label.copy(fontWeight = FontWeight.SemiBold, color = if (active) C.Blue else C.Ink))
                BasicText("${c.style} · ${c.defaultModel.ifBlank { c.models.firstOrNull() ?: "未选模型" }} · " + c.caps.joinToString("/") { CAP_LABEL[it] ?: it } +
                    (if (Auth.providerKeys[c.id].isNullOrBlank()) " · 未填 Key" else " · Key ✓"), style = T.Cap.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicText(if (active) "停用" else "选用", Modifier.clip(Capsule()).background(if (active) C.Fill else C.Blue).clickable {
                val cur = Store.settings.value
                Store.updateSettings(if (active) cur.copy(customProvider = "") else cur.copy(customProvider = c.id, llmModel = c.defaultModel.ifBlank { c.models.firstOrNull() ?: cur.llmModel }))
            }.padding(horizontal = 14.dp, vertical = 6.dp), style = T.Btn.copy(fontSize = 13.sp, color = if (active) C.Ink else Color.White))
        }
    }
}

private val CAP_LABEL = mapOf("chat" to "对话", "vision" to "看图", "image" to "画图", "asr" to "语音识别", "embeddings" to "向量")

@Composable
private fun ProviderEditor(c0: ProviderConfig, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember(c0.id) { mutableStateOf(c0.name) }
    var base by remember(c0.id) { mutableStateOf(c0.baseUrl) }
    var style by remember(c0.id) { mutableStateOf(c0.style) }
    var authHeader by remember(c0.id) { mutableStateOf(c0.authHeader) }
    var prefix by remember(c0.id) { mutableStateOf(c0.authPrefix) }
    var headers by remember(c0.id) { mutableStateOf(c0.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }) }
    var models by remember(c0.id) { mutableStateOf(c0.models.joinToString(", ")) }
    var model by remember(c0.id) { mutableStateOf(c0.defaultModel) }
    var caps by remember(c0.id) { mutableStateOf(c0.caps) }
    var proxy by remember(c0.id) { mutableStateOf(c0.proxy) }
    var upstream by remember(c0.id) { mutableStateOf(c0.upstream) }
    var asrModel by remember(c0.id) { mutableStateOf(c0.asrModel) }
    var key by remember(c0.id) { mutableStateOf(Auth.providerKeys[c0.id].orEmpty()) }
    var err by remember(c0.id) { mutableStateOf<String?>(null) }
    var loading by remember(c0.id) { mutableStateOf(false) }
    fun build() = ProviderConfig(c0.id, name.trim(), style, base.trim(), authHeader.trim(), prefix,
        headers.lines().mapNotNull { l -> l.split(':', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0].trim() to it[1].trim() } }
            .filter { it.first.lowercase() !in setOf("authorization", "x-api-key", "api-key") }.toMap(),
        models.split(',', '，', '\n').map { it.trim() }.filter { it.isNotBlank() }, model.trim(), caps, proxy.trim(), upstream.trim(), c0.chatPath, c0.modelsPath, c0.asrPath, asrModel.trim(), c0.note)
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        BasicText("编辑服务商", style = T.Item)
        Spacer(Modifier.height(10.dp))
        @Composable fun line(label: String, v: String, ph: String, mask: Boolean = false, on: (String) -> Unit) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(C.Fill).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(label, Modifier.width(76.dp), style = T.Cap.copy(fontSize = 12.sp))
                Field(v, on, ph, Modifier.weight(1f), style = T.Label.copy(fontSize = 13.5.sp), mask = mask)
            }
        }
        line("名称", name, "如 我的中转") { name = it }
        line("Base URL", base, "https://api.example.com/v1") { base = it; if (style == "openai") style = ProviderConfig.guessStyle(it) }
        Spacer(Modifier.height(6.dp))
        LiquidSegmented(items = listOf("OpenAI 兼容", "Anthropic", "Gemini"), selected = listOf("openai", "anthropic", "gemini").indexOf(style).coerceAtLeast(0),
            onSelect = { i -> style = listOf("openai", "anthropic", "gemini")[i]; authHeader = when (style) { "anthropic" -> "x-api-key"; "gemini" -> "x-goog-api-key"; else -> "Authorization" }; prefix = if (style == "openai") "Bearer " else "" },
            modifier = Modifier.fillMaxWidth(), height = 38.dp)
        Spacer(Modifier.height(6.dp))
        line("认证头", authHeader, "Authorization / x-api-key / query:key") { authHeader = it }
        line("前缀", prefix, "Bearer （留空则无）") { prefix = it }
        line("API Key", key, "只存本机加密区", mask = true) { key = it }
        line("模型", model, "默认模型") { model = it }
        line("模型列表", models, "逗号分隔，或点「获取模型」") { models = it }
        line("代理", proxy, "可选 host:port（HTTP 代理）") { proxy = it }
        line("上游备注", upstream, "可选：one-api / new-api 渠道说明") { upstream = it }
        if ("asr" in caps) line("识别模型", asrModel, "如 whisper-1 / SenseVoiceSmall") { asrModel = it }
        BasicText("自定义请求头（每行 名称: 值，不要放 Key）", Modifier.padding(top = 6.dp, bottom = 4.dp), style = T.Cap.copy(fontSize = 11.5.sp))
        BasicTextField(headers, { headers = it }, Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp)).background(C.Fill).padding(10.dp),
            textStyle = T.Cap.copy(fontSize = 12.sp, color = C.Ink, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), cursorBrush = SolidColor(C.Blue))
        BasicText("能力", Modifier.padding(top = 8.dp, bottom = 4.dp), style = T.Cap.copy(fontSize = 11.5.sp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProviderConfig.CAPS.forEach { k ->
                val on = k in caps
                BasicText(CAP_LABEL[k] ?: k, Modifier.clip(Capsule()).background(if (on) C.Blue else C.Fill).clickable { caps = if (on) caps - k else caps + k }
                    .padding(horizontal = 12.dp, vertical = 6.dp), style = T.Btn.copy(fontSize = 12.5.sp, color = if (on) Color.White else C.Ink))
            }
        }
        err?.let { BasicText(it, Modifier.padding(top = 8.dp), style = T.Cap.copy(color = if (it.startsWith("获取到")) C.Green else C.Red)) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(if (loading) "获取中…" else "获取模型", {
                val c = runCatching { build() }.getOrNull() ?: return@Btn
                loading = true; err = null
                scope.launch {
                    try {
                        val list = withContext(Dispatchers.IO) { Api.customModels(c, key) }
                        models = list.joinToString(", "); if (model.isBlank()) model = list.firstOrNull().orEmpty()
                        err = "获取到 ${list.size} 个模型"
                    } catch (e: Exception) { err = e.message } finally { loading = false }
                }
            }, Modifier.weight(1f), icon = "repeat")
            Btn("保存", {
                val c = runCatching { build() }.getOrElse { err = it.message; return@Btn }
                if (!(c.baseUrl.startsWith("https://") || c.baseUrl.startsWith("http://"))) { err = "Base URL 需要 http(s):// 开头"; return@Btn }
                ProviderRegistry.upsert(c); Auth.setProviderKey(c.id, key); PluginUi.msg.value = "已保存「${c.name}」"; onDone()
            }, Modifier.weight(1f), icon = "checkc", tint = C.Blue)
            Btn("删除", { ProviderRegistry.remove(c0.id); Auth.setProviderKey(c0.id, ""); if (Store.settings.value.customProvider == c0.id) Store.updateSettings(Store.settings.value.copy(customProvider = "")); onDone() },
                Modifier.weight(0.8f), icon = "x")
        }
    }
}

@Suppress("unused")
private fun fileExists(f: File?) = f?.isFile == true
