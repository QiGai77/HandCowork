package app.xuezhang.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Store
import app.xuezhang.engine.Sync
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 云同步：系统文件选择器（Google 云端硬盘 / 手机云盘等）+ WebDAV。 */
@Composable
fun SyncSheetContent() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Store.settings.value
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var dav by remember { mutableStateOf(s.davUrl) }
    var user by remember { mutableStateOf(s.davUser) }
    var pass by remember { mutableStateOf(s.davPass) }

    fun job(label: String, block: suspend () -> String) {
        if (busy != null) return
        busy = label; msg = null
        app.xuezhang.engine.AppJobs.launch {
            msg = try { block() } catch (e: Exception) { "失败：" + (e.message ?: "未知错误") } finally { busy = null }
        }
    }
    fun stamp() = Store.updateSettings(Store.settings.value.copy(lastSync = System.currentTimeMillis()))
    suspend fun restore(bytes: ByteArray): String {
        val u = withContext(Dispatchers.IO) { Sync.unpack(ctx, bytes) }
        val (c, l) = Sync.apply(u); stamp()
        return "已恢复 $c 门课、$l 节笔记（与本机合并）"
    }
    suspend fun upload(uri: Uri): String {
        val bytes = withContext(Dispatchers.IO) { Sync.pack() }
        withContext(Dispatchers.IO) { Sync.write(ctx, uri, bytes) }
        stamp()
        return "已备份 ${Store.courses.size} 门课、${Store.lectures.size} 节笔记 · ${"%.1f".format(bytes.size / 1048576.0)} MB"
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        Store.updateSettings(Store.settings.value.copy(syncUri = uri.toString()))
        job("备份中…") { upload(uri) }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        job("恢复中…") { restore(withContext(Dispatchers.IO) { Sync.read(ctx, uri) }) }
    }

    Column(Modifier.heightIn(max = 660.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon("cloud", C.Blue, 22.dp, 2.1f); Spacer(Modifier.width(8.dp))
            BasicText("云同步课程与笔记", Modifier.weight(1f), style = T.Title)
            Box(Modifier.size(36.dp).clip(Capsule()).background(C.Fill).clickable { Nav.sheet.value = null }, contentAlignment = Alignment.Center) { Icon("x", C.Ink, 16.dp, 2.2f) }
        }
        Spacer(Modifier.height(6.dp))
        BasicText("课程、笔记、封面和课件打包成一个备份文件。API Key 和账号登录信息不会被备份。", style = T.Cap.copy(fontSize = 12.sp, lineHeight = 17.sp))

        Spacer(Modifier.height(14.dp))
        // Google Drive 连接器：OAuth 授权后直接用 Drive API 上传，可查看文件夹内容和每个文件的上传状态
        val g = app.xuezhang.net.Auth.google.value
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(0.5.dp, Color(0x14000000), RoundedCornerShape(20.dp))
            .clickable { Nav.sheet.value = null; Nav.push(Route.Drive) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(Capsule()).background(Color(0xFF1A73E8).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon("cloud", Color(0xFF1A73E8), 18.dp, 2.1f) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(if (g == null) "连接 Google Drive" else "Google Drive", style = T.Btn)
                BasicText(if (g == null) "授权后自动上传备份，查看每个文件的上传状态" else (g.email.ifBlank { "已连接" } + " · 查看文件与上传状态"), style = T.Cap.copy(fontSize = 11.5.sp), maxLines = 1)
            }
            Icon("chev", C.Ter, 14.dp)
        }
        Spacer(Modifier.height(14.dp))
        BasicText("系统文件选择器（Google 云端硬盘 App、手机自带云盘等）", style = T.Sub)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(0.5.dp, Color(0x14000000), RoundedCornerShape(20.dp)).padding(14.dp)) {
            BasicText("在系统文件选择器左上角菜单里选 Google 云端硬盘或手机厂商的云盘（需已安装对应 App 并登录），选好文件夹保存即可。", style = T.Cap.copy(fontSize = 11.5.sp, lineHeight = 16.sp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("备份到…", { create.launch(Sync.FILE) }, Modifier.weight(1f), icon = "cloudup", tint = C.Blue, color = Color.White)
                Btn("从备份恢复", { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, Modifier.weight(1f), icon = "clouddown")
            }
            if (s.syncUri.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clip(Capsule()).background(C.Blue.copy(alpha = 0.08f)).clickable {
                    job("同步中…") { upload(Uri.parse(Store.settings.value.syncUri)) }
                }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("refresh", C.Blue, 15.dp, 2.2f); Spacer(Modifier.width(8.dp))
                    BasicText("覆盖上次的备份文件", Modifier.weight(1f), style = T.Btn.copy(fontSize = 13.5.sp, color = C.Blue))
                    BasicText(Uri.parse(s.syncUri).lastPathSegment?.substringAfterLast('/')?.take(18).orEmpty(), style = T.Cap.copy(fontSize = 11.sp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        BasicText("WebDAV（坚果云、Nextcloud、群晖等）", style = T.Sub)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(0.5.dp, Color(0x14000000), RoundedCornerShape(20.dp))) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon("server", C.Sec, 16.dp); Spacer(Modifier.width(10.dp))
                Field(dav, { dav = it.trim() }, "https://dav.jianguoyun.com/dav/", Modifier.weight(1f), style = T.Label.copy(fontSize = 14.sp))
            }
            Divider(14.dp)
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon("person", C.Sec, 16.dp); Spacer(Modifier.width(10.dp))
                Field(user, { user = it.trim() }, "账号", Modifier.weight(1f), style = T.Label.copy(fontSize = 14.sp))
            }
            Divider(14.dp)
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon("key", C.Sec, 16.dp); Spacer(Modifier.width(10.dp))
                Field(pass, { pass = it }, "密码 / 应用密码", Modifier.weight(1f), style = T.Label.copy(fontSize = 14.sp), mask = true)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fun saveDav() = Store.updateSettings(Store.settings.value.copy(davUrl = dav, davUser = user, davPass = pass))
            Btn("上传备份", {
                saveDav()
                job("上传中…") {
                    val bytes = withContext(Dispatchers.IO) { Sync.pack() }
                    withContext(Dispatchers.IO) { Sync.davUpload(dav, user, pass, bytes) }; stamp()
                    "已上传到 WebDAV：xuezhang/${Sync.FILE}"
                }
            }, Modifier.weight(1f), icon = "cloudup")
            Btn("下载恢复", {
                saveDav()
                job("下载中…") { restore(withContext(Dispatchers.IO) { Sync.davDownload(dav, user, pass) }) }
            }, Modifier.weight(1f), icon = "clouddown")
        }

        Spacer(Modifier.height(12.dp))
        (busy ?: msg)?.let {
            BasicText(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (msg?.startsWith("失败") == true) Color(0x14FF3B30) else C.Blue.copy(alpha = 0.08f)).padding(12.dp),
                style = T.Cap.copy(fontSize = 12.5.sp, color = if (msg?.startsWith("失败") == true) C.Red else C.Blue))
        }
        Spacer(Modifier.height(8.dp))
    }
}
