package app.xuezhang.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Store
import app.xuezhang.net.Api
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** 闪电测试（新旧设置页共用）。 */
fun meRunTest(target: Int, scope: CoroutineScope, ctx: Context) {
    if (MeState.testing.value >= 0) return
    MeState.testing.value = target; MeState.streamText.value = ""
    scope.launch {
        try {
            if (target == 1) {
                val b = withContext(Dispatchers.IO) { Api.benchChat("用一句话介绍变压器的工作原理，不超过 40 字。") { MeState.streamText.value = it } }
                MeState.result[1] = "%.0f tok/s".format(b.tokPerSec); MeState.ok[1] = true
                Island.show("大模型正常 · 首字 ${"%.2f".format(b.firstMs / 1000.0)}s", Island.Kind.Done)
            } else {
                val st = Store.settings.value
                if (st.asrKey.isBlank()) throw IllegalStateException("先填写语音识别 API Key")
                val f = File(ctx.cacheDir, "asr_test.mp3")
                withContext(Dispatchers.IO) { Api.speech("今天我们讲变压器的工作原理。", f) }
                val t0 = System.currentTimeMillis()
                val text = withContext(Dispatchers.IO) { Api.transcribe(f) }
                val dt = (System.currentTimeMillis() - t0) / 1000.0
                MeState.streamText.value = "识别结果：$text"
                MeState.result[0] = "%.2fs".format(dt); MeState.ok[0] = true
                Island.show("语音识别正常 · ${"%.2f".format(dt)}s", Island.Kind.Done)
            }
        } catch (e: Exception) {
            MeState.result[target] = "失败"; MeState.ok[target] = false
            MeState.streamText.value = e.message?.take(120) ?: "测试失败"
            Island.show("测试失败", Island.Kind.Warn)
        } finally { MeState.testing.value = -1 }
    }
}

private val MeBlue = Color(0xFF1F6FEB)

@Composable
private fun MeCard(content: @Composable ColumnScope.() -> Unit) =
    PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), radius = 28.dp, padding = PaddingValues(vertical = 4.dp), content = content)

@Composable
private fun MeGroup(t: String) = BasicText(t, Modifier.padding(start = 24.dp, top = 22.dp, bottom = 10.dp), style = T.Sub.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium))

@Composable
private fun MeLine() = Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(0.5.dp).background(C.Line))

/** 前置 Lucide 线性图标的一行；expand != null 时右侧箭头可旋转，表示二级折叠。 */
@Composable
fun MeRow(icon: String, title: String, value: String = "", expanded: Boolean? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val rot by animateFloatAsState(if (expanded == true) 90f else 0f, label = "chev")
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, MeBlue.copy(alpha = if (ThemeState.dark) 1f else 0.92f), 22.dp, stroke = 1.7f)
        Spacer(Modifier.width(16.dp))
        BasicText(title, Modifier.weight(1f), style = T.Label.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
        if (trailing != null) trailing() else {
            if (value.isNotBlank()) BasicText(value, Modifier.padding(start = 10.dp), style = T.Label.copy(color = C.Sec, fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onClick != null) { Spacer(Modifier.width(8.dp)); Icon("chevr", C.Sec, 16.dp, modifier = Modifier.rotate(rot)) }
        }
    }
}

@Composable
private fun Fold(open: Boolean, content: @Composable ColumnScope.() -> Unit) =
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(Modifier.fillMaxWidth().padding(start = 38.dp)) { content() }
    }

@Composable
private fun Seg(items: List<String>, sel: Int, w: Int = 170, onSel: (Int) -> Unit) =
    LiquidSegmented(items = items, selected = sel, onSelect = onSel, modifier = Modifier.width(w.dp), height = 38.dp)

@Composable
fun SettingsTab() {
    val s = Store.settings.value
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val lectures = Store.lectures
    var advOpen by remember { mutableStateOf(false) }
    var dispOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    LaunchedEffect(s.llmKey, s.llmBase) { MeState.balance.value = withContext(Dispatchers.IO) { runCatching { Api.balance() }.getOrNull() } }
    fun set(f: (app.xuezhang.data.Settings) -> app.xuezhang.data.Settings) = Store.updateSettings(f(Store.settings.value))

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            TopInset()
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 18.dp, top = 40.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("我的", Modifier.weight(1f), style = T.Large.copy(fontSize = 40.sp, lineHeight = 48.sp))
                GlassCircle(icon = "settings", onClick = { Nav.push(Route.Advanced) }, size = 50.dp)
            }
        }
        // 资料卡
        item {
            PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), radius = 30.dp, padding = PaddingValues(20.dp)) {
                val guest = s.account.isBlank() || s.account == "guest"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(Capsule()).background(Brush.linearGradient(listOf(Color(0xFFE2EDFF), Color(0xFFC8DBFF)))), contentAlignment = Alignment.Center) {
                        BasicText("学", style = T.Title.copy(color = MeBlue, fontSize = 28.sp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(if (guest) "本地用户" else s.account, style = T.Title.copy(fontSize = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon("phonesm", C.Sec, 14.dp); Spacer(Modifier.width(4.dp))
                            BasicText(if (guest) "仅保存在此设备" else "已登录", style = T.Sub)
                        }
                    }
                    BasicText(if (guest) "登录" else "账号", Modifier.clip(Capsule()).border(1.2.dp, MeBlue.copy(alpha = 0.35f), Capsule())
                        .background(Color.White.copy(alpha = if (ThemeState.dark) 0.08f else 0.7f)).clickable { Nav.push(if (guest) Route.Login else Route.Accounts) }
                        .padding(horizontal = 22.dp, vertical = 10.dp), style = T.Btn.copy(color = MeBlue, fontSize = 16.sp))
                }
                Spacer(Modifier.height(16.dp)); Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.Line)); Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf(Store.courses.size to "课程", lectures.size to "笔记", lectures.sumOf { it.cards.size } to "复习卡").forEachIndexed { i, (n, l) ->
                        if (i > 0) Box(Modifier.padding(top = 10.dp).width(0.5.dp).height(28.dp).background(C.Line))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText("$n", style = T.Title.copy(color = MeBlue, fontSize = 24.sp, fontFamily = null, fontWeight = FontWeight.SemiBold))
                            BasicText(l, style = T.Sub.copy(fontSize = 14.sp))
                        }
                    }
                }
            }
        }
        // 学习助手
        item { MeGroup("学习助手") }
        item {
            MeCard {
                val asrOk = s.asrKey.isNotBlank(); val llmOk = Api.llmReady()
                AssistRow("mic", "语音识别", s.asrModel.substringAfterLast('/'), asrOk) { Nav.sheet.value = Sheet.Model(0) }
                MeLine()
                AssistRow("box", "笔记整理", s.llmModel.substringAfterLast('/'), llmOk) { Nav.sheet.value = Sheet.Model(1) }
                MeLine()
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProviderLogo(s.llmBase, 22.dp); Spacer(Modifier.width(8.dp))
                    BasicText(Providers.nameOf(s.llmBase), style = T.Label.copy(color = C.Sec, fontSize = 16.sp))
                    MeState.balance.value?.let { BasicText("  余额 $it", style = T.Cap) }
                    Spacer(Modifier.weight(1f))
                    val busy = MeState.testing.value >= 0
                    Row(Modifier.clip(Capsule()).clickable { meRunTest(s.testTarget, scope, ctx) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(if (busy) "测试中…" else "测试连接", style = T.Btn.copy(color = MeBlue, fontSize = 15.sp))
                        Icon("chevr", MeBlue, 16.dp)
                    }
                }
                val st = MeState.streamText.value
                if (st.isNotBlank()) BasicText(st, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp), style = T.Cap.copy(lineHeight = 17.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        // 笔记偏好
        item { MeGroup("笔记偏好") }
        item {
            MeCard {
                MeRow("filetext", "笔记方式", trailing = { Seg(listOf("原文转写", "学长讲解"), s.defaultMode, 180) { m -> set { it.copy(defaultMode = m) } } })
                MeLine()
                MeRow("alignl", "笔记详略", trailing = { Seg(listOf("精简", "详细"), s.noteDepth, 150) { m -> set { it.copy(noteDepth = m) } } })
                MeLine()
                MeRow("hexset", "高级设置", "提示词 · 智能补全", expanded = advOpen, onClick = { advOpen = !advOpen })
                Fold(advOpen) {
                    Row2("模型提示词", "预览 / 修改", onClick = { Nav.push(Route.Prompts) })
                    Row2("上下文补全（需确认）", "", trailing = { Toggle(s.confirmFill) { v -> set { it.copy(confirmFill = v) } } })
                    Row2("公式渲染 LaTeX", "", trailing = { Toggle(s.latex) { v -> set { it.copy(latex = v) } } })
                    Row2("分段时长", "", trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(10, 15, 20).forEach { n ->
                                BasicText("${n}s", Modifier.clip(Capsule()).background(if (s.chunkSec == n) C.Blue else C.Fill).clickable { set { it.copy(chunkSec = n) } }
                                    .padding(horizontal = 12.dp, vertical = 6.dp), style = T.Btn.copy(fontSize = 13.sp, color = if (s.chunkSec == n) Color.White else C.Ink))
                            }
                        }
                    })
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        // 数据与帮助
        item { MeGroup("数据与帮助") }
        item {
            MeCard {
                MeRow("cloud", "云同步与备份", if (s.lastSync > 0) "上次 " + java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(s.lastSync)) else "未开启", onClick = { Nav.sheet.value = Sheet.Sync })
                MeLine()
                MeRow("users", "连接账户", listOfNotNull(app.xuezhang.net.Auth.google.value?.let { "Google" }, s.ghUser.takeIf { it.isNotBlank() }?.let { "GitHub" }).joinToString(" · ").ifBlank { "Google · GitHub" }, onClick = { Nav.push(Route.Accounts) })
                MeLine()
                MeRow("monitor", "显示与交互", expanded = dispOpen, onClick = { dispOpen = !dispOpen })
                Fold(dispOpen) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("深色模式", Modifier.weight(1f), style = T.Label.copy(fontSize = 15.5.sp))
                        Seg(listOf("跟随系统", "浅色", "深色"), s.theme, 196) { m -> set { it.copy(theme = m) } }
                    }
                    Row2("书架折叠成一行", "", trailing = { Toggle(s.shelfFold) { v -> set { it.copy(shelfFold = v) } } })
                    BasicText("复习卡默认样式 · ${Skins.card(s.cardStyle).name}", Modifier.padding(start = 18.dp, top = 8.dp, bottom = 8.dp), style = T.Label.copy(fontSize = 15.5.sp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Skins.cards.forEachIndexed { i, k ->
                            Box(Modifier.size(44.dp, 58.dp).clip(RoundedCornerShape(10.dp)).background(k.brush)
                                .border(if (i == s.cardStyle) 2.dp else 0.5.dp, if (i == s.cardStyle) MeBlue else C.Line, RoundedCornerShape(10.dp))
                                .clickable { set { it.copy(cardStyle = i) } }, contentAlignment = Alignment.Center) {
                                BasicText("Aa", style = T.Btn.copy(color = k.ink, fontSize = 13.sp))
                            }
                        }
                    }
                }
                MeLine()
                MeRow("help", "帮助与关于", expanded = helpOpen, onClick = { helpOpen = !helpOpen })
                Fold(helpOpen) {
                    Row2("日志", "${app.xuezhang.engine.Log.all().count { !it.ok }} 条错误", onClick = { Nav.push(Route.Logs) })
                    Row2("再看一次更新公告", "", onClick = { Splash.visible.value = true })
                    Row2("全部设置", "", onClick = { Nav.push(Route.Advanced) })
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        RichText("[b:蓝色] 概念、定义\n[r:浅红] 考点、必考\n[o:橙色] 助记、易错\n[u:下划线] 老师强调\n[f:灰色虚线] 根据上下文补全的字", style = T.Body.copy(fontSize = 14.sp))
                        Spacer(Modifier.height(8.dp))
                        BasicText("课堂学长 v${Splash.versionName(ctx)} · 录音只在本机切片后发送到你填写的接口", style = T.Cap)
                    }
                }
            }
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun AssistRow(icon: String, title: String, model: String, ok: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(Capsule()).background(MeBlue.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) { Icon(icon, MeBlue, 24.dp, stroke = 1.7f) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            BasicText(title, style = T.Label.copy(fontSize = 16.5.sp, fontWeight = FontWeight.Medium))
            BasicText(model.ifBlank { "未选择模型" }, style = T.Sub.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(8.dp).clip(Capsule()).background(if (ok) C.Green else C.Ter)); Spacer(Modifier.width(8.dp))
        BasicText(if (ok) "已连接" else "未配置", style = T.Label.copy(color = C.Sec, fontSize = 14.5.sp))
        Spacer(Modifier.width(8.dp)); Icon("chevr", C.Sec, 16.dp)
    }
}

@Composable
fun AdvancedScreen() {
    GlassScaffold(content = { SettingsAdvancedList() }, overlay = { b ->
        Box(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 44.dp)) { GlassCircle(b, "back", { Nav.pop() }) }
    })
}

/** 连接账户：Google（Drive / 登录）与 GitHub（个人访问令牌）。 */
@Composable
fun AccountsScreen() {
    val s = Store.settings.value
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val g = app.xuezhang.net.Auth.google.value
    fun verifyGh() {
        if (token.isBlank() || busy) return
        busy = true; msg = null
        scope.launch {
            try {
                val login = withContext(Dispatchers.IO) {
                    val req = Request.Builder().url("https://api.github.com/user").header("Authorization", "Bearer ${token.trim()}")
                        .header("Accept", "application/vnd.github+json").build()
                    Api.http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw IllegalStateException("GitHub 返回 ${r.code}，请检查令牌")
                        JSONObject(r.body!!.string()).optString("login")
                    }
                }
                Store.updateSettings(Store.settings.value.copy(ghToken = token.trim(), ghUser = login))
                token = ""; Island.show("已连接 GitHub · $login", Island.Kind.Done)
            } catch (e: Exception) { msg = e.message ?: "连接失败" } finally { busy = false }
        }
    }
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize()) {
            item { TopInset(); PageHeader("连接账户", "账号令牌只保存在本机") }
            item {
                MeCard {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(Capsule()).background(Color.White), contentAlignment = Alignment.Center) {
                            BasicText("G", style = T.Title.copy(fontSize = 22.sp, color = Color(0xFF4285F4), fontFamily = null))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText("Google", style = T.Label.copy(fontSize = 16.5.sp, fontWeight = FontWeight.Medium))
                            BasicText(g?.email ?: "未连接 · 用于 Google 云端硬盘同步", style = T.Sub)
                        }
                        BasicText(if (g != null) "管理" else "连接", Modifier.clip(Capsule()).background(MeBlue.copy(alpha = 0.1f)).clickable { Nav.push(Route.Drive) }
                            .padding(horizontal = 18.dp, vertical = 9.dp), style = T.Btn.copy(color = MeBlue))
                    }
                }
            }
            item { Spacer(Modifier.height(14.dp)) }
            item {
                MeCard {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(Capsule()).background(Color(0xFF1F2328)), contentAlignment = Alignment.Center) { Icon("github", Color.White, 24.dp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText("GitHub", style = T.Label.copy(fontSize = 16.5.sp, fontWeight = FontWeight.Medium))
                            BasicText(if (s.ghUser.isNotBlank()) "已连接 · @${s.ghUser}" else "未连接 · 用于把笔记备份到仓库", style = T.Sub)
                        }
                        if (s.ghUser.isNotBlank()) BasicText("断开", Modifier.clip(Capsule()).background(C.Red.copy(alpha = 0.1f))
                            .clickable { Store.updateSettings(Store.settings.value.copy(ghToken = "", ghUser = "")); Island.show("已断开 GitHub", Island.Kind.Info) }
                            .padding(horizontal = 18.dp, vertical = 9.dp), style = T.Btn.copy(color = C.Red))
                    }
                    if (s.ghUser.isBlank()) {
                        MeLine()
                        Column(Modifier.padding(20.dp)) {
                            BasicText("在 GitHub → Settings → Developer settings → Personal access tokens 生成令牌后粘贴到这里", style = T.Cap.copy(lineHeight = 17.sp))
                            Spacer(Modifier.height(10.dp))
                            Field(token, { token = it }, "ghp_… 或 github_pat_…", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Fill).padding(14.dp), mask = true)
                            Spacer(Modifier.height(10.dp))
                            BasicText(if (busy) "验证中…" else "连接 GitHub", Modifier.fillMaxWidth().clip(Capsule()).background(MeBlue).clickable { verifyGh() }.padding(vertical = 13.dp),
                                style = T.Btn.copy(color = Color.White, textAlign = TextAlign.Center))
                            msg?.let { Spacer(Modifier.height(8.dp)); BasicText(it, style = T.Cap.copy(color = C.Red)) }
                        }
                    }
                }
            }
            item { BottomSpacer() }
        }
    }, overlay = { b ->
        Box(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 44.dp)) { GlassCircle(b, "back", { Nav.pop() }) }
    })
}
