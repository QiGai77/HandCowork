package app.xuezhang.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Store
import app.xuezhang.engine.AiProgress
import app.xuezhang.engine.GhBackup
import app.xuezhang.engine.GhBackupCore
import app.xuezhang.engine.GhException
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GhInk = Color(0xFF1F2328)
private fun ghTime(t: Long) = if (t <= 0) "从未备份" else SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(t))

/** 备份动作（连接账户卡片和 GitHub 备份页共用）。 */
object GhActions {
    val msg = mutableStateOf<String?>(null)
    fun backup(ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope) {
        if (GhBackup.running.value) return
        msg.value = null
        scope.launch {
            msg.value = try {
                val r = AiProgress.run("ghbackup", "备份到 GitHub", doneMsg = "GitHub 备份完成") { job ->
                    GhBackup.backup(ctx.applicationContext, "手动") { f, step -> job.set(f, step); job.step.value = step }
                }
                GhBackup.summary(r) + if (r.skipped.isNotEmpty()) "（跳过 ${r.skipped.size} 个，详见日志）" else ""
            } catch (e: Exception) { "失败：" + explain(e) }
        }
    }
    fun explain(e: Exception): String = when {
        e is GhException && e.code == 401 -> e.message + "。"
        e is GhException && e.code == 403 -> e.message.orEmpty()
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException -> "连不上 GitHub，检查网络后再试"
        else -> e.message ?: "未知错误"
    }
}

/** 连接账户 › GitHub 卡片里的备份区。 */
@Composable
fun GhBackupInline() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Store.settings.value
    val job = aiJob("ghbackup")
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon("cloudup", GhInk, 18.dp, 2.1f); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                BasicText("备份到私有仓库", style = T.Label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                BasicText("${s.ghUser}/${GhBackupCore.cleanRepo(s.ghRepo)} · 上次 ${ghTime(s.ghLastBackup)}", style = T.Cap.copy(fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(if (GhBackup.running.value) "备份中…" else "立即备份", Modifier.weight(1f).height(44.dp).clip(Capsule()).background(GhInk)
                .clickable { GhActions.backup(ctx, scope) }.padding(top = 11.dp), style = T.Btn.copy(color = Color.White, textAlign = TextAlign.Center))
            BasicText("备份与恢复", Modifier.weight(1f).height(44.dp).clip(Capsule()).background(C.Fill)
                .clickable { Nav.push(Route.GhBackup) }.padding(top = 11.dp), style = T.Btn.copy(color = GhInk, textAlign = TextAlign.Center))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("自动备份", style = T.Label.copy(fontSize = 14.5.sp))
                BasicText("每天连 Wi‑Fi 时，以及每次笔记生成后", style = T.Cap.copy(fontSize = 11.sp))
            }
            Toggle(s.ghAutoBackup) { on -> Store.updateSettings(Store.settings.value.copy(ghAutoBackup = on)); GhBackup.schedule(ctx) }
        }
        job?.let { Spacer(Modifier.height(10.dp)); AiProgressCard(it) }
        GhActions.msg.value?.let { m ->
            Spacer(Modifier.height(10.dp))
            BasicText(m, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (m.startsWith("失败")) Color(0x14FF3B30) else GhInk.copy(alpha = 0.06f)).padding(10.dp),
                style = T.Cap.copy(fontSize = 12.sp, color = if (m.startsWith("失败")) C.Red else GhInk))
        }
    }
}

/** 云同步 › GitHub 备份：仓库、立即备份（灵动岛进度）、自动备份、包含录音、从备份恢复、在 GitHub 查看。 */
@Composable
fun GhBackupScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Store.settings.value
    var repo by remember { mutableStateOf(GhBackupCore.cleanRepo(s.ghRepo)) }
    var list by remember { mutableStateOf<List<GhBackup.Snapshot>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<GhBackup.Snapshot?>(null) }
    val msg = GhActions.msg.value
    val connected = GhBackup.connected()
    val bj = aiJob("ghbackup"); val rj = aiJob("ghrestore")

    fun saveRepo() { val r = GhBackupCore.cleanRepo(repo); repo = r; if (r != Store.settings.value.ghRepo) { Store.updateSettings(Store.settings.value.copy(ghRepo = r)); list = null } }
    fun load() {
        if (loading || !connected) return
        saveRepo(); loading = true
        scope.launch {
            try { list = GhBackup.history() } catch (e: Exception) { GhActions.msg.value = "失败：读取备份记录 · " + GhActions.explain(e); app.xuezhang.engine.Log.err(GhBackup.TAG, e, "读取备份记录失败：${e.message}") }
            finally { loading = false }
        }
    }
    fun restore(snap: GhBackup.Snapshot) {
        GhActions.msg.value = null
        scope.launch {
            GhActions.msg.value = try {
                val (c, l) = AiProgress.run("ghrestore", "从 GitHub 恢复", doneMsg = "恢复完成") { job ->
                    val rs = GhBackup.download(ctx, snap.sha) { f, step -> job.set(f, step); job.step.value = step }
                    GhBackup.apply(rs)
                }
                "已恢复 $c 门课、$l 节笔记（与本机合并，Key 与账号不变）"
            } catch (e: Exception) { app.xuezhang.engine.Log.err(GhBackup.TAG, e, "恢复失败：${e.message}"); "失败：" + GhActions.explain(e) }
        }
    }

    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item { TopInset(); PageHeader("GitHub 备份", "课程、笔记、复习卡、导图和附件提交到你自己的私有仓库") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 24.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(Capsule()).background(GhInk), contentAlignment = Alignment.Center) { Icon("github", Color.White, 22.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(if (connected) "@${s.ghUser}" else "未连接 GitHub", style = T.Item)
                            BasicText(if (connected) "上次备份 ${ghTime(s.ghLastBackup)}" + (s.ghLastCommit.takeIf { it.isNotBlank() }?.let { " · ${it.take(7)}" } ?: "") else "先在「连接账户」粘贴个人访问令牌（需要 repo 权限）",
                                style = T.Cap, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (!connected) BasicText("去连接", Modifier.clip(Capsule()).background(GhInk.copy(alpha = 0.08f)).clickable { Nav.push(Route.Accounts) }
                            .padding(horizontal = 14.dp, vertical = 7.dp), style = T.Btn.copy(fontSize = 13.sp, color = GhInk))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Fill).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("仓库", style = T.Cap.copy(fontSize = 12.sp)); Spacer(Modifier.width(10.dp))
                        BasicText("${s.ghUser.ifBlank { "you" }}/", style = T.Label.copy(fontSize = 14.sp, color = C.Sec))
                        Field(repo, { repo = it }, GhBackupCore.DEFAULT_REPO, Modifier.weight(1f), style = T.Label.copy(fontSize = 14.sp))
                        BasicText("私有", Modifier.clip(RoundedCornerShape(6.dp)).background(GhInk.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 2.dp), style = T.Cap.copy(fontSize = 10.5.sp, color = GhInk))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn(if (GhBackup.running.value) "备份中…" else "立即备份", { saveRepo(); GhActions.backup(ctx, scope) }, Modifier.weight(1f), icon = "cloudup", tint = GhInk, color = Color.White)
                        Btn("在 GitHub 查看", { saveRepo(); runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GhBackup.webUrl()))) } }, Modifier.weight(1f), icon = "link")
                    }
                }
            }
            bj?.let { j -> item { AiProgressCard(j) } }
            rj?.let { j -> item { AiProgressCard(j) } }
            msg?.let { m -> item {
                BasicText(m, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (m.startsWith("失败")) Color(0x14FF3B30) else GhInk.copy(alpha = 0.06f)).padding(12.dp),
                    style = T.Cap.copy(fontSize = 12.5.sp, color = if (m.startsWith("失败")) C.Red else GhInk))
            } }
            item { SectionLabel("自动备份") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText("自动备份", style = T.Label)
                            BasicText("每天在 Wi‑Fi 下备份一次；每次笔记生成后也会备份（仅 Wi‑Fi）", style = T.Cap.copy(fontSize = 11.5.sp))
                        }
                        Toggle(s.ghAutoBackup) { on -> Store.updateSettings(Store.settings.value.copy(ghAutoBackup = on)); GhBackup.schedule(ctx) }
                    }
                    Divider(16.dp)
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText("包含录音", style = T.Label)
                            BasicText("录音文件很大，默认不备份；超过 50MB 的文件一律跳过", style = T.Cap.copy(fontSize = 11.5.sp))
                        }
                        Toggle(s.ghIncludeAudio) { on -> Store.updateSettings(Store.settings.value.copy(ghIncludeAudio = on)) }
                    }
                }
            }
            item { SectionLabel("从备份恢复" + (list?.let { " · ${it.size} 次备份" } ?: "")) }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    when {
                        list == null -> Row2(if (loading) "正在读取备份记录…" else "读取备份记录", "", onClick = { load() })
                        list!!.isEmpty() -> Row2("仓库里还没有备份，点「立即备份」", "")
                        else -> list!!.forEachIndexed { i, b ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon("clock", C.Sec, 18.dp); Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    BasicText(ghTime(b.at) + if (i == 0) " · 最新" else "", style = T.Label.copy(fontSize = 14.sp))
                                    BasicText(b.message.substringAfter(" · ", b.message).take(40) + " · " + b.sha.take(7), style = T.Cap.copy(fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                BasicText("恢复", Modifier.clip(Capsule()).background(GhInk.copy(alpha = 0.08f)).clickable { confirm = b }
                                    .padding(horizontal = 12.dp, vertical = 6.dp), style = T.Btn.copy(fontSize = 12.5.sp, color = GhInk))
                            }
                            if (i < list!!.lastIndex) Divider(44.dp)
                        }
                    }
                }
            }
            item {
                BasicText("备份内容：课程与课表、笔记、复习卡、概念图、自定义提示词和偏好，以及封面、课件、截图、AI 生成图。API Key、GitHub 令牌、账号密码永远不会写进备份。只上传有变化的文件；恢复时与本机数据合并（同一门课 / 同一节笔记以备份为准）。",
                    Modifier.padding(horizontal = 22.dp, vertical = 14.dp), style = T.Cap.copy(fontSize = 11.5.sp, lineHeight = 17.sp))
            }
        }
    }, overlay = { b ->
        BackBar(b)
        confirm?.let { snap ->
            Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(remember { MutableInteractionSource() }, null) { confirm = null }, contentAlignment = Alignment.Center) {
                Column(Modifier.padding(32.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color.White).clickable(remember { MutableInteractionSource() }, null) {}.padding(22.dp)) {
                    BasicText("从这次备份恢复？", style = T.Title)
                    Spacer(Modifier.height(8.dp))
                    BasicText("${ghTime(snap.at)} 的备份将与本机合并：同一门课、同一节笔记会被备份里的版本覆盖，其余保留。API Key 和账号不受影响。", style = T.Cap.copy(fontSize = 13.sp, lineHeight = 19.sp))
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn("取消", { confirm = null }, Modifier.weight(1f))
                        Btn("恢复", { confirm = null; restore(snap) }, Modifier.weight(1f), icon = "clouddown", tint = GhInk, color = Color.White)
                    }
                }
            }
        }
    })
}
