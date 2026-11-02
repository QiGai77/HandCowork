package app.xuezhang.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/** 检查 Gitee 仓库远程更新。 */
fun checkGiteeUpdate(scope: CoroutineScope, ctx: Context) {
    Island.show("正在检查 Gitee 远程更新…", Island.Kind.Info)
    scope.launch {
        try {
            val curVer = Splash.versionName(ctx)
            val info = withContext(Dispatchers.IO) {
                val req = Request.Builder().url("https://gitee.com/api/v5/repos/QiGai77/HandCowork/releases/latest")
                    .header("User-Agent", "HandCowork-Android").build()
                runCatching {
                    Api.http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) null
                        else {
                            val obj = JSONObject(r.body!!.string())
                            val tag = obj.optString("tag_name").removePrefix("v").trim()
                            val body = obj.optString("body")
                            tag to body
                        }
                    }
                }.getOrNull()
            }
            if (info != null && info.first.isNotBlank() && info.first != curVer) {
                Island.show("发现 Gitee 新版本 v${info.first}！", Island.Kind.Done)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gitee.com/QiGai77/HandCowork/releases"))
                ctx.startActivity(intent)
            } else {
                Island.show("已是最新版本 v$curVer", Island.Kind.Done)
            }
        } catch (e: Exception) {
            Island.show("无法连接 Gitee 仓库，稍后重试", Island.Kind.Warn)
        }
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
            if (value.isNotBlank()) BasicText(value, Modifier.padding(start = 10.dp), style = T.Label.copy(color = C.Sec, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun Seg(items: List<String>, sel: Int, w: Int = 180, onSel: (Int) -> Unit) =
    LiquidSegmented(items = items, selected = sel, onSelect = onSel, modifier = Modifier.width(w.dp), height = 38.dp)

@Composable
fun UserAvatarView(avatar: String, size: androidx.compose.ui.unit.Dp = 64.dp, modifier: Modifier = Modifier) {
    val isFile = remember(avatar) { avatar.isNotBlank() && File(avatar).exists() }
    val bmp = remember(avatar, isFile) {
        if (isFile) runCatching { BitmapFactory.decodeFile(avatar)?.asImageBitmap() }.getOrNull() else null
    }
    Box(
        modifier.size(size).clip(Capsule()).background(Brush.linearGradient(listOf(Color(0xFFE2EDFF), Color(0xFFC8DBFF)))),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp, "头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BasicText(avatar.ifBlank { "学" }, style = T.Title.copy(color = MeBlue, fontSize = (size.value * 0.44f).sp))
        }
    }
}

@Composable
fun SettingsTab() {
    val s = Store.settings.value
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val lectures = Store.lectures

    var showProfileDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var advOpen by remember { mutableStateOf(false) }
    var dispOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }

    val presetAvatars = listOf("🎓", "⚡", "🚀", "💡", "📖", "🎨", "🌟", "🐱", "🐻", "🐧")

    LaunchedEffect(s.llmKey, s.llmBase) { MeState.balance.value = withContext(Dispatchers.IO) { runCatching { Api.balance() }.getOrNull() } }
    fun set(f: (app.xuezhang.data.Settings) -> app.xuezhang.data.Settings) = Store.updateSettings(f(Store.settings.value))

    val linkedAccounts = listOfNotNull(
        if (s.giteeUser.isNotBlank()) "Gitee" else null,
        if (s.ghUser.isNotBlank()) "GitHub" else null,
        app.xuezhang.net.Auth.google.value?.let { "Google" }
    )

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            TopInset()
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 18.dp, top = 40.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("我的", Modifier.weight(1f), style = T.Large.copy(fontSize = 40.sp, lineHeight = 48.sp))
                GlassCircle(icon = "settings", onClick = { Nav.push(Route.Advanced) }, size = 50.dp)
            }
        }
        // 用户卡片（支持更换头像与名字）
        item {
            PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), radius = 30.dp, padding = PaddingValues(20.dp)) {
                val isLogged = s.userEmail.isNotBlank() || (s.account.isNotBlank() && s.account != "guest")
                val accountText = if (s.userEmail.isNotBlank()) s.userEmail else if (s.account.isNotBlank() && s.account != "guest") s.account else "本地用户"

                Row(Modifier.clickable { showProfileDialog = true }, verticalAlignment = Alignment.CenterVertically) {
                    UserAvatarView(s.userAvatar, 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(s.userName.ifBlank { "同学" }, style = T.Title.copy(fontSize = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(6.dp))
                            Icon("edit", C.Sec, 14.dp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon("mail", C.Sec, 13.dp); Spacer(Modifier.width(4.dp))
                            BasicText(if (isLogged) accountText else "未绑定邮箱 · 点击登录", style = T.Sub.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    BasicText(
                        if (isLogged) "切换" else "登录",
                        Modifier.clip(Capsule()).border(1.2.dp, MeBlue.copy(alpha = 0.35f), Capsule())
                            .background(Color.White.copy(alpha = if (ThemeState.dark) 0.08f else 0.7f)).clickable { Nav.push(Route.Login) }
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                        style = T.Btn.copy(color = MeBlue, fontSize = 15.sp)
                    )
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

        // 模型配置（学习助手升级为模型配置，预留原生模式/Agent模式切换，二级菜单配置具体参数）
        item { MeGroup("模型配置") }
        item {
            MeCard {
                // 模式切换
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("zap", MeBlue, 22.dp, stroke = 1.7f)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText("运行模式", style = T.Label.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
                        BasicText(if (s.modelMode == 1) "Agent 模式（待开发）" else "原生极速推理模式", style = T.Sub.copy(fontSize = 13.sp))
                    }
                    Seg(listOf("原生模式", "Agent"), s.modelMode, 160) { m ->
                        if (m == 1) {
                            Island.show("Agent 模式正在深度开发中，敬请期待！", Island.Kind.Info)
                        }
                        set { it.copy(modelMode = m) }
                    }
                }
                MeLine()
                // 模型状态概览与连通性测试
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProviderLogo(s.llmBase, 22.dp); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(Providers.nameOf(s.llmBase), style = T.Label.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium))
                        MeState.balance.value?.let { BasicText("余额 $it", style = T.Cap) }
                    }
                    val busy = MeState.testing.value >= 0
                    Row(Modifier.clip(Capsule()).clickable { meRunTest(s.testTarget, scope, ctx) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(if (busy) "测试中…" else "测试连接", style = T.Btn.copy(color = MeBlue, fontSize = 14.5.sp))
                        Icon("chevr", MeBlue, 15.dp)
                    }
                }
                val st = MeState.streamText.value
                if (st.isNotBlank()) BasicText(st, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp), style = T.Cap.copy(lineHeight = 17.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                MeLine()
                // 二级菜单：详细模型与参数配置
                MeRow("settings", "详细模型与参数配置", if (s.llmKey.isNotBlank()) "已配置" else "未配置", expanded = modelMenuOpen, onClick = { modelMenuOpen = !modelMenuOpen })
                Fold(modelMenuOpen) {
                    AssistRow("mic", "语音识别模型", s.asrModel.substringAfterLast('/'), s.asrKey.isNotBlank()) { Nav.sheet.value = Sheet.Model(0) }
                    AssistRow("box", "笔记整理大模型", s.llmModel.substringAfterLast('/'), Api.llmReady()) { Nav.sheet.value = Sheet.Model(1) }
                    Row2("提示词管理", "预览 / 修改系统提示词", onClick = { Nav.push(Route.Prompts) })
                }
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
                MeRow(
                    "hexset",
                    "高级设置",
                    value = if (s.promptOverrides.isEmpty() && s.chunkSec == 15 && s.confirmFill) "" else "已配置",
                    expanded = advOpen,
                    onClick = { advOpen = !advOpen }
                )
                Fold(advOpen) {
                    Row2("模型提示词", "预览 / 自定义", onClick = { Nav.push(Route.Prompts) })
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
                // 云备份：点击弹窗自动在手机创建 Documents/课堂学长备份 文件夹
                MeRow(
                    "cloud",
                    "云备份",
                    value = if (s.lastSync > 0) "上次 " + SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(s.lastSync)) else "未备份 · 点击保存到手机",
                    onClick = { showBackupDialog = true }
                )
                MeLine()
                MeRow(
                    "users",
                    "连接账户",
                    value = if (linkedAccounts.isEmpty()) "" else linkedAccounts.joinToString(" · "),
                    onClick = { Nav.push(Route.Accounts) }
                )
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
                // 帮助与关于：展示作者 GitHub、仓库地址、邮箱与 Gitee 更新
                MeRow("help", "帮助与关于", expanded = helpOpen, onClick = { helpOpen = !helpOpen })
                Fold(helpOpen) {
                    Row2("作者 GitHub", "@QiGai77", onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/QiGai77"))
                        ctx.startActivity(intent)
                    })
                    Row2("GitHub 仓库", "QiGai77/HandCowork", onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/QiGai77/HandCowork"))
                        ctx.startActivity(intent)
                    })
                    Row2("官方支持邮箱", "qigai730@gmail.com", onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:qigai730@gmail.com"))
                        ctx.startActivity(intent)
                    })
                    Row2("Gitee 仓库与远程更新", "点击检查最新版本", onClick = {
                        checkGiteeUpdate(scope, ctx)
                    })
                    Row2("日志", "${app.xuezhang.engine.Log.all().count { !it.ok }} 条错误", onClick = { Nav.push(Route.Logs) })
                    Row2("再看一次更新公告", "", onClick = { Splash.visible.value = true })
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

    // 1. 头像与昵称修改弹窗（支持相册自定义上传与预设选择）
    if (showProfileDialog) {
        var editName by remember { mutableStateOf(s.userName) }
        var selectedAvatar by remember { mutableStateOf(s.userAvatar) }
        val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                val dir = File(ctx.filesDir, "avatars").apply { mkdirs() }
                val f = File(dir, "avatar_${System.currentTimeMillis()}.png")
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    f.outputStream().use { ins.copyTo(it) }
                }
                if (f.exists() && f.length() > 0L) {
                    selectedAvatar = f.absolutePath
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { showProfileDialog = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth().shadow(20.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp)).background(if (ThemeState.dark) Color(0xFF1E2129) else Color.White)
                    .clickable(interactionSource = null, indication = null) {}
                    .padding(22.dp)
            ) {
                BasicText("更换头像与昵称", style = T.Head.copy(fontSize = 19.sp))
                Spacer(Modifier.height(16.dp))

                // 当前选定头像预览与相册上传按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatarView(selectedAvatar, 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            Modifier.clip(Capsule()).background(MeBlue.copy(alpha = 0.12f))
                                .clickable { photoPicker.launch("image/*") }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon("image", MeBlue, 18.dp); Spacer(Modifier.width(6.dp))
                            BasicText("相册选取照片", style = T.Btn.copy(color = MeBlue, fontSize = 13.5.sp))
                        }
                        Spacer(Modifier.height(4.dp))
                        BasicText("支持任意相册图片作为自定义头像", style = T.Cap.copy(fontSize = 11.5.sp))
                    }
                }

                Spacer(Modifier.height(16.dp))
                BasicText("或选择预设头像", style = T.Label.copy(fontSize = 13.sp, color = C.Sec))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    presetAvatars.forEach { av ->
                        val sel = selectedAvatar == av
                        Box(
                            Modifier.size(46.dp).clip(Capsule())
                                .background(if (sel) MeBlue.copy(alpha = 0.15f) else C.Fill)
                                .border(if (sel) 2.dp else 0.dp, MeBlue, Capsule())
                                .clickable { selectedAvatar = av },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(av, style = T.Title.copy(fontSize = 22.sp))
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                BasicText("昵称", style = T.Label.copy(fontSize = 13.5.sp, color = C.Sec))
                Spacer(Modifier.height(6.dp))
                Field(editName, { editName = it }, "输入您的昵称", Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Fill).padding(12.dp))
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Btn("取消", { showProfileDialog = false }, Modifier.weight(1f))
                    Btn("保存", {
                        set { it.copy(userName = editName.trim().ifBlank { "同学" }, userAvatar = selectedAvatar) }
                        showProfileDialog = false
                        Island.show("资料已更新", Island.Kind.Done)
                    }, Modifier.weight(1f), tint = MeBlue)
                }
            }
        }
    }

    // 2. 云备份确认弹窗
    if (showBackupDialog) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { showBackupDialog = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth().shadow(20.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp)).background(if (ThemeState.dark) Color(0xFF1E2129) else Color.White)
                    .clickable(interactionSource = null, indication = null) {}
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon("cloud", MeBlue, 24.dp)
                    Spacer(Modifier.width(10.dp))
                    BasicText("本地云备份", style = T.Head.copy(fontSize = 19.sp))
                }
                Spacer(Modifier.height(14.dp))
                BasicText(
                    "点击允许后，将在您的手机储存「Documents/课堂学长备份」文件夹下自动备份全部课程、笔记和复习卡数据。",
                    style = T.Label.copy(fontSize = 14.5.sp, lineHeight = 20.sp, color = C.Ink.copy(alpha = 0.85f))
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Btn("取消", { showBackupDialog = false }, Modifier.weight(1f))
                    Btn("允许并备份", {
                        showBackupDialog = false
                        runCatching {
                            val extDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                            val targetDir = if (extDocs != null && extDocs.canWrite()) File(extDocs, "课堂学长备份")
                                else File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "课堂学长备份")
                            targetDir.mkdirs()
                            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
                            val backupFile = File(targetDir, "课堂学长备份_$ts.json")
                            backupFile.writeText(Store.exportBundle().toString(2))
                            set { it.copy(lastSync = System.currentTimeMillis()) }
                            Island.show("已成功备份至 Documents/课堂学长备份", Island.Kind.Done)
                        }.getOrElse { e ->
                            Island.show("备份失败：${e.message}", Island.Kind.Warn)
                        }
                    }, Modifier.weight(1.2f), tint = MeBlue)
                }
            }
        }
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

/** 连接账户：Google（原生 4 色标志）、Gitee（私人令牌与远程更新）、GitHub（个人访问令牌）。 */
@Composable
fun AccountsScreen() {
    val s = Store.settings.value
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var ghToken by remember { mutableStateOf("") }
    var giteeToken by remember { mutableStateOf("") }
    var busyGh by remember { mutableStateOf(false) }
    var busyGitee by remember { mutableStateOf(false) }
    var msgGh by remember { mutableStateOf<String?>(null) }
    var msgGitee by remember { mutableStateOf<String?>(null) }
    val g = app.xuezhang.net.Auth.google.value

    fun verifyGh() {
        if (ghToken.isBlank() || busyGh) return
        busyGh = true; msgGh = null
        scope.launch {
            try {
                val login = withContext(Dispatchers.IO) {
                    val req = Request.Builder().url("https://api.github.com/user").header("Authorization", "Bearer ${ghToken.trim()}")
                        .header("Accept", "application/vnd.github+json").build()
                    Api.http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw IllegalStateException("GitHub 返回 ${r.code}，请检查令牌")
                        JSONObject(r.body!!.string()).optString("login")
                    }
                }
                Store.updateSettings(Store.settings.value.copy(ghToken = ghToken.trim(), ghUser = login))
                ghToken = ""; app.xuezhang.engine.GhBackup.schedule(ctx); Island.show("已连接 GitHub · $login，可以备份了", Island.Kind.Done)
            } catch (e: Exception) { msgGh = e.message ?: "连接失败" } finally { busyGh = false }
        }
    }

    fun verifyGitee() {
        if (giteeToken.isBlank() || busyGitee) return
        busyGitee = true; msgGitee = null
        scope.launch {
            try {
                val login = withContext(Dispatchers.IO) {
                    val req = Request.Builder().url("https://gitee.com/api/v5/user?access_token=${giteeToken.trim()}")
                        .header("User-Agent", "HandCowork-Android").build()
                    Api.http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw IllegalStateException("Gitee 返回 ${r.code}，请检查令牌")
                        JSONObject(r.body!!.string()).optString("login")
                    }
                }
                Store.updateSettings(Store.settings.value.copy(giteeToken = giteeToken.trim(), giteeUser = login))
                giteeToken = ""; Island.show("已连接 Gitee · $login", Island.Kind.Done)
            } catch (e: Exception) { msgGitee = e.message ?: "连接失败" } finally { busyGitee = false }
        }
    }

    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize()) {
            item { TopInset(); PageHeader("连接账户", "账号与访问令牌仅保存在本机") }

            // 1. Google (使用原生四色标志)
            item {
                MeCard {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(Capsule()).background(Color.White).shadow(2.dp, Capsule()), contentAlignment = Alignment.Center) {
                            GoogleNativeLogo(size = 30.dp)
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

            // 2. Gitee (官方品牌标志与私人令牌)
            item {
                MeCard {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(Capsule()).background(Color.White).shadow(2.dp, Capsule()), contentAlignment = Alignment.Center) {
                            GiteeLogo(size = 32.dp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText("Gitee", style = T.Label.copy(fontSize = 16.5.sp, fontWeight = FontWeight.Medium))
                            BasicText(if (s.giteeUser.isNotBlank()) "已连接 · @${s.giteeUser}" else "未连接 · 支持远程版本更新与同步", style = T.Sub)
                        }
                        if (s.giteeUser.isNotBlank()) {
                            BasicText("断开", Modifier.clip(Capsule()).background(C.Red.copy(alpha = 0.1f))
                                .clickable { Store.updateSettings(Store.settings.value.copy(giteeToken = "", giteeUser = "")); Island.show("已断开 Gitee", Island.Kind.Info) }
                                .padding(horizontal = 18.dp, vertical = 9.dp), style = T.Btn.copy(color = C.Red))
                        }
                    }
                    if (s.giteeUser.isBlank()) {
                        MeLine()
                        Column(Modifier.padding(20.dp)) {
                            BasicText("在 Gitee → 设置 → 私人令牌（Personal Access Tokens）生成令牌后粘贴到此处", style = T.Cap.copy(lineHeight = 17.sp))
                            Spacer(Modifier.height(10.dp))
                            Field(giteeToken, { giteeToken = it }, "输入 Gitee 私人令牌", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Fill).padding(14.dp), mask = true)
                            Spacer(Modifier.height(10.dp))
                            BasicText(if (busyGitee) "验证中…" else "连接 Gitee", Modifier.fillMaxWidth().height(48.dp).clip(Capsule()).background(Color(0xFFC71D23)).clickable { verifyGitee() }.padding(top = 13.dp),
                                style = T.Btn.copy(color = Color.White, textAlign = TextAlign.Center))
                            msgGitee?.let { Spacer(Modifier.height(8.dp)); BasicText(it, style = T.Cap.copy(color = C.Red)) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(14.dp)) }

            // 3. GitHub
            item {
                MeCard {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(Capsule()).background(Color(0xFF1F2328)), contentAlignment = Alignment.Center) { Icon("github", Color.White, 24.dp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText("GitHub", style = T.Label.copy(fontSize = 16.5.sp, fontWeight = FontWeight.Medium))
                            BasicText(if (s.ghUser.isNotBlank()) "已连接 · @${s.ghUser}" else "未连接 · 用于把笔记备份到私有仓库", style = T.Sub)
                        }
                        if (s.ghUser.isNotBlank()) BasicText("断开", Modifier.clip(Capsule()).background(C.Red.copy(alpha = 0.1f))
                            .clickable { Store.updateSettings(Store.settings.value.copy(ghToken = "", ghUser = "")); app.xuezhang.engine.GhBackup.schedule(ctx); Island.show("已断开 GitHub", Island.Kind.Info) }
                            .padding(horizontal = 18.dp, vertical = 9.dp), style = T.Btn.copy(color = C.Red))
                    }
                    if (s.ghUser.isNotBlank()) { MeLine(); GhBackupInline() }
                    if (s.ghUser.isBlank()) {
                        MeLine()
                        Column(Modifier.padding(20.dp)) {
                            BasicText("在 GitHub → Settings → Developer settings → Personal access tokens 生成令牌后粘贴到这里。备份需要 repo 权限（细粒度令牌：Contents 与 Administration 读写）", style = T.Cap.copy(lineHeight = 17.sp))
                            Spacer(Modifier.height(10.dp))
                            Field(ghToken, { ghToken = it }, "ghp_… 或 github_pat_…", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Fill).padding(14.dp), mask = true)
                            Spacer(Modifier.height(10.dp))
                            BasicText(if (busyGh) "验证中…" else "连接 GitHub", Modifier.fillMaxWidth().height(48.dp).clip(Capsule()).background(MeBlue).clickable { verifyGh() }.padding(top = 13.dp),
                                style = T.Btn.copy(color = Color.White, textAlign = TextAlign.Center))
                            msgGh?.let { Spacer(Modifier.height(8.dp)); BasicText(it, style = T.Cap.copy(color = C.Red)) }
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
