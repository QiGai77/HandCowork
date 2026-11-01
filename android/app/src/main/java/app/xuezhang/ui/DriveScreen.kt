package app.xuezhang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Store
import app.xuezhang.engine.AiProgress
import app.xuezhang.engine.Sync
import app.xuezhang.net.Auth
import app.xuezhang.net.GDrive
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun size(b: Long) = when { b <= 0 -> "—"; b < 1024 -> "$b B"; b < 1048576 -> "%.1f KB".format(b / 1024.0); else -> "%.1f MB".format(b / 1048576.0) }
private fun time(t: Long) = if (t <= 0) "" else SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(t))

/** 我的 › 云同步 › Google Drive：连接账号、手动同步、查看文件夹内容与每个文件的上传状态。 */
@Composable
fun DriveScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc = Auth.google.value
    var files by remember { mutableStateOf<List<GDrive.DFile>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var cid by remember { mutableStateOf(Store.settings.value.gClientId) }
    var sec by remember { mutableStateOf(Store.settings.value.gClientSecret) }
    var showSetup by remember { mutableStateOf(!GDrive.configured()) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { GDrive.loadStatus() } }

    fun refresh() {
        if (Auth.google.value == null || loading) return
        loading = true
        scope.launch {
            try { files = withContext(Dispatchers.IO) { GDrive.list() } } catch (e: Exception) { msg = "读取 Drive 失败：${e.message}" } finally { loading = false }
        }
    }
    LaunchedEffect(acc?.email) { if (acc != null) refresh() }

    fun connect() {
        Store.updateSettings(Store.settings.value.copy(gClientId = cid.trim(), gClientSecret = sec.trim()))
        if (!GDrive.configured()) { msg = "先填写 Google OAuth 客户端 ID（以 .apps.googleusercontent.com 结尾）"; showSetup = true; return }
        msg = null
        scope.launch {
            try { withContext(Dispatchers.IO) { GDrive.login(ctx) }; showSetup = false; Island.show("已连接 Google Drive", Island.Kind.Done); refresh() }
            catch (e: Exception) { msg = e.message ?: "连接失败" }
        }
    }
    fun sync() {
        if (GDrive.syncing.value) return
        msg = null
        val items = GDrive.locals()
        scope.launch {
            try {
                val (up, skip, fail) = AiProgress.run("gdrive", "同步到 Google Drive", doneMsg = "Google Drive 同步完成") { job ->
                    job.phase("连接 Drive 文件夹", 0.05f, 4000)
                    withContext(Dispatchers.IO) {
                        GDrive.syncAll(items) { f, name -> job.set(0.05f + 0.95f * f, name); if (name.isNotBlank()) job.step.value = "上传 $name" }
                    }
                }
                msg = "上传 $up 个 · 内容没变跳过 $skip 个" + if (fail > 0) " · 失败 $fail 个（可再点同步重试）" else ""
                refresh()
            } catch (e: Exception) { msg = "同步失败：${e.message}" }
        }
    }
    fun restore(f: GDrive.DFile) {
        scope.launch {
            try {
                val (c, l) = AiProgress.run("gdrive-restore", "从 Google Drive 恢复") { job ->
                    job.phase("下载 ${f.name}", 0.7f, 8000, size(f.size))
                    val bytes = withContext(Dispatchers.IO) { GDrive.download(f.id) }
                    job.phase("解包并合并", 0.98f, 2000)
                    val u = withContext(Dispatchers.IO) { Sync.unpack(ctx, bytes) }
                    Sync.apply(u)
                }
                msg = "已恢复 $c 门课、$l 节笔记（与本机合并）"
            } catch (e: Exception) { msg = "恢复失败：${e.message}" }
        }
    }

    val gj = aiJob("gdrive"); val rj = aiJob("gdrive-restore")
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item { TopInset(); PageHeader("Google Drive", "备份课程与笔记到你自己的 Google 云端硬盘") }
            // 账号
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 24.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(Capsule()).background(if (acc != null) Color(0xFF1A73E8) else C.Fill), contentAlignment = Alignment.Center) {
                            BasicText(acc?.plan?.take(1)?.ifBlank { null } ?: acc?.email?.take(1)?.uppercase() ?: "G", style = T.Head.copy(color = if (acc != null) Color.White else C.Sec, fontSize = 18.sp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(if (acc != null) (acc.plan.ifBlank { "Google 账号" }) else "未连接", style = T.Item)
                            BasicText(acc?.email?.ifBlank { "已授权" } ?: "连接后只会访问本应用创建的文件（drive.file）", style = T.Cap, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (acc != null) BasicText("断开", Modifier.clip(Capsule()).background(C.Fill).clickable { GDrive.logout(); files = null; msg = "已断开 Google Drive" }
                            .padding(horizontal = 14.dp, vertical = 7.dp), style = T.Btn.copy(fontSize = 13.sp, color = C.Red))
                    }
                    GDrive.loginState.value?.let {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(it, Modifier.weight(1f), style = T.Cap.copy(color = C.Blue))
                            BasicText("取消", Modifier.clickable { GDrive.cancel() }.padding(6.dp), style = T.Btn.copy(fontSize = 13.sp, color = C.Sec))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (acc == null) Btn(if (GDrive.loginState.value != null) "等待授权…" else "连接 Google Drive", { connect() }, Modifier.fillMaxWidth(), icon = "cloud", tint = C.Blue)
                    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn(if (GDrive.syncing.value) "同步中…" else "立即同步", { sync() }, Modifier.weight(1f), icon = "cloudup", tint = C.Blue)
                        Btn("刷新", { refresh() }, Modifier.weight(1f), icon = "refresh")
                    }
                    if (acc != null && Store.settings.value.gLastSync > 0) BasicText("上次同步 " + time(Store.settings.value.gLastSync), Modifier.padding(top = 8.dp), style = T.Cap)
                }
            }
            gj?.let { j -> item { AiProgressCard(j) } }
            rj?.let { j -> item { AiProgressCard(j) } }
            msg?.let { m -> item {
                BasicText(m, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (m.contains("失败")) Color(0x14FF3B30) else C.Blue.copy(alpha = 0.08f)).padding(12.dp),
                    style = T.Cap.copy(fontSize = 12.5.sp, color = if (m.contains("失败")) C.Red else C.Blue))
            } }
            // 每个文件的上传状态
            if (GDrive.uploads.isNotEmpty()) {
                item { SectionLabel("上传状态") }
                item {
                    PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        GDrive.uploads.forEachIndexed { i, u -> UploadRow(u); if (i < GDrive.uploads.lastIndex) Divider(16.dp) }
                    }
                }
            }
            if (acc != null) {
                item { SectionLabel("Drive 文件夹「${GDrive.FOLDER}」" + (files?.let { " · ${it.size} 个文件" } ?: "")) }
                item {
                    PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        when {
                            files == null -> Row2(if (loading) "正在读取…" else "点「刷新」读取文件列表", "")
                            files!!.isEmpty() -> Row2("文件夹是空的，点「立即同步」上传", "")
                            else -> files!!.forEachIndexed { i, f ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (f.name.endsWith(".zip")) "cloud" else "note", if (f.name.endsWith(".zip")) C.Blue else C.Sec, 18.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        BasicText(f.name, style = T.Label.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        BasicText(size(f.size) + " · " + time(f.modified), style = T.Cap.copy(fontSize = 11.5.sp))
                                    }
                                    if (f.name == Sync.FILE) BasicText("恢复", Modifier.clip(Capsule()).background(C.Blue.copy(alpha = 0.1f)).clickable { restore(f) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp), style = T.Btn.copy(fontSize = 12.5.sp, color = C.Blue))
                                }
                                if (i < files!!.lastIndex) Divider(44.dp)
                            }
                        }
                    }
                }
            }
            // 设置：客户端 ID
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp).clickable { showSetup = !showSetup }, verticalAlignment = Alignment.CenterVertically) {
                    BasicText("Google Drive 设置说明" + if (GDrive.configured()) " · 已配置客户端" else " · 需要先配置", Modifier.weight(1f), style = T.Sub.copy(fontWeight = FontWeight.SemiBold))
                    BasicText(if (showSetup) "收起" else "展开", style = T.Btn.copy(fontSize = 13.sp, color = C.Blue))
                }
            }
            if (showSetup) item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), radius = 22.dp) {
                    BasicText("一次性设置（开发者在 Google Cloud 控制台完成）", style = T.Item.copy(fontSize = 15.sp))
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        "1. 打开 console.cloud.google.com，新建或选择一个项目",
                        "2. 「API 和服务 › 库」搜索 Google Drive API 并启用",
                        "3. 「Google Auth Platform › 品牌塑造 / 受众群体」配置同意屏幕；测试阶段把自己的 Google 账号加入「测试用户」",
                        "4. 「数据访问」添加权限范围 …/auth/drive.file（非敏感，无需审核）",
                        "5. 「客户端 › 创建客户端」，应用类型选「桌面应用」（Google 已不允许安卓类型客户端在浏览器里回跳），得到客户端 ID 和密钥",
                        "6. 把客户端 ID（和密钥）填到下面，或编译时用 -PgoogleClientId=… -PgoogleClientSecret=… 写进 BuildConfig",
                    ).forEach { BasicText(it, Modifier.padding(vertical = 3.dp), style = T.Cap.copy(fontSize = 12.sp, lineHeight = 18.sp, color = C.Ink)) }
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Fill).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Field(cid, { cid = it }, "客户端 ID：xxxx.apps.googleusercontent.com", Modifier.fillMaxWidth().padding(vertical = 8.dp), style = T.Label.copy(fontSize = 13.sp))
                        Divider()
                        Field(sec, { sec = it }, "客户端密钥（桌面应用类型会给，可留空）", Modifier.fillMaxWidth().padding(vertical = 8.dp), style = T.Label.copy(fontSize = 13.sp), mask = true)
                    }
                    Spacer(Modifier.height(10.dp))
                    Btn("保存", { Store.updateSettings(Store.settings.value.copy(gClientId = cid.trim(), gClientSecret = sec.trim())); msg = if (GDrive.configured()) "已保存客户端配置" else "客户端 ID 格式不对" },
                        Modifier.fillMaxWidth(), icon = "check")
                    Spacer(Modifier.height(6.dp))
                    BasicText("授权在浏览器里完成，回调到本机 127.0.0.1 的临时端口，令牌只保存在手机上（Android Keystore 加密），不会上传或写进日志。", style = T.Cap.copy(fontSize = 11.sp, lineHeight = 16.sp))
                }
            }
        }
    }, overlay = { b -> BackBar(b) })
}

@Composable
private fun UploadRow(u: GDrive.Item) {
    val (label, col) = when (u.st) {
        GDrive.St.Waiting -> "等待" to C.Sec
        GDrive.St.Uploading -> "上传中" to C.Blue
        GDrive.St.Done -> "已上传" to C.Green
        GDrive.St.Skipped -> "未变化" to C.Sec
        GDrive.St.Failed -> "失败" to C.Red
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(u.name, Modifier.weight(1f), style = T.Label.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            BasicText(if (u.st == GDrive.St.Uploading && u.size > 0) "${(u.sent * 100 / u.size)}%" else label,
                Modifier.clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
                style = T.Cap.copy(fontSize = 11.sp, color = col, fontWeight = FontWeight.Bold))
        }
        if (u.st == GDrive.St.Uploading) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(Capsule()).background(C.Fill)) {
                Box(Modifier.fillMaxWidth(if (u.size > 0) (u.sent.toFloat() / u.size).coerceIn(0.02f, 1f) else 0.02f).height(5.dp).clip(Capsule()).background(C.Blue))
            }
        }
        Spacer(Modifier.height(3.dp))
        BasicText(listOfNotNull(if (u.size > 0) size(u.size) else null, if (u.st == GDrive.St.Done || u.st == GDrive.St.Skipped || u.st == GDrive.St.Failed) time(u.at) else null,
            u.err.ifBlank { null }).joinToString(" · "), style = T.Cap.copy(fontSize = 11.sp, color = if (u.st == GDrive.St.Failed) C.Red else C.Sec), maxLines = 2)
    }
}
