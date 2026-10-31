package app.xuezhang.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Store
import app.xuezhang.engine.Log
import app.xuezhang.engine.Prompts
import com.kyant.shapes.Capsule

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) =
    BasicText(text, Modifier.clip(Capsule()).background(if (on) C.Blue else C.Fill).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        style = T.Btn.copy(fontSize = 13.sp, color = if (on) Color.White else C.Ink))

private fun copy(ctx: Context, label: String, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    Island.show("已复制", Island.Kind.Done)
}

/** 我的 › 日志：每一次操作的记录，可筛选错误、复制、分享导出。 */
@Composable
fun LogScreen() {
    val ctx = LocalContext.current
    val ver = Log.version.value.let { it }
    var filter by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<Long?>(null) }
    val list = remember(ver, filter) { Log.all().filter { filter == 0 || !it.ok }.asReversed() }
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item { TopInset(); PageHeader("日志", "最近 ${Log.MAX} 条操作记录 · 不含任何密钥") }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("全部", filter == 0) { filter = 0 }
                    Chip("错误", filter == 1) { filter = 1 }
                    Spacer(Modifier.weight(1f))
                    Chip("复制", false) { copy(ctx, "课堂学长日志", Log.export(filter == 1)) }
                    Chip("分享", false) {
                        val f = java.io.File(ctx.cacheDir, "xuezhang-log.txt").apply { writeText(Log.export(filter == 1)) }
                        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "导出日志")) }
                    }
                }
            }
            if (list.isEmpty()) item { EmptyHint(if (filter == 1) "没有错误" else "还没有记录", "导入、调用模型、生成笔记、同步都会记在这里") }
            items(list, key = { it.t.toString() + it.tag + it.msg.hashCode() }) { e ->
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth(), radius = 16.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    onClick = { open = if (open == e.t) null else e.t }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (e.ok) "check" else "x", if (e.ok) C.Green else C.Red, 14.dp, 2.4f)
                        Spacer(Modifier.width(6.dp))
                        BasicText(e.tag, style = T.Btn.copy(fontSize = 13.sp))
                        Spacer(Modifier.weight(1f))
                        BasicText(Log.line(e).substring(0, 14), style = T.Cap.copy(fontFamily = FontFamily.Monospace))
                    }
                    BasicText(e.msg, Modifier.padding(top = 3.dp), style = T.Sub.copy(fontSize = 13.sp, color = if (e.ok) C.Ink else C.Red), maxLines = if (open == e.t) 30 else 2)
                    if (open == e.t && e.detail.isNotBlank()) BasicText(e.detail, Modifier.padding(top = 6.dp).clickable { copy(ctx, "日志", Log.line(e)) },
                        style = T.Cap.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp))
                }
            }
            if (list.isNotEmpty()) item { BasicText("清空日志", Modifier.padding(20.dp).clickable { Log.clear() }, style = T.Btn.copy(color = C.Red)) }
        }
    }, overlay = { b -> BackBar(b) })
}

/** 我的 › 模型提示词：查看每项任务用的系统提示词，可修改、恢复默认。 */
@Composable
fun PromptsScreen() {
    var openKey by remember { mutableStateOf<String?>(null) }
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item { TopInset(); PageHeader("模型提示词", "学长在每项任务里对模型说的话。改坏了随时恢复默认") }
            items(Prompts.catalog, key = { it.first }) { (k, name) ->
                val custom = Store.settings.value.promptOverrides[k]
                var text by remember(k, custom) { mutableStateOf(custom ?: Prompts.default(k)) }
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), radius = 18.dp, padding = PaddingValues(14.dp),
                    onClick = if (openKey == k) null else ({ openKey = k })) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(name, Modifier.weight(1f), style = T.Item)
                        if (custom != null) BasicText("已修改", style = T.Cap.copy(color = C.HlOrange))
                    }
                    if (openKey != k) BasicText(text.trim().take(60) + "…", Modifier.padding(top = 4.dp), style = T.Cap, maxLines = 1)
                    else {
                        Spacer(Modifier.height(8.dp))
                        BasicTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp).clip(RoundedCornerShape(10.dp)).background(C.Fill).padding(10.dp),
                            textStyle = T.Cap.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = C.Ink))
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("保存", true) {
                                val m = Store.settings.value.promptOverrides.toMutableMap()
                                if (text.isBlank() || text == Prompts.default(k)) m.remove(k) else m[k] = text
                                Store.updateSettings(Store.settings.value.copy(promptOverrides = m)); openKey = null
                                Island.show("已保存", Island.Kind.Done)
                            }
                            Chip("恢复默认", false) {
                                Store.updateSettings(Store.settings.value.copy(promptOverrides = Store.settings.value.promptOverrides - k)); text = Prompts.default(k)
                            }
                            Chip("收起", false) { openKey = null }
                        }
                    }
                }
            }
            item { BasicText("整理笔记时还会自动附上完整性要求和分段说明；看图识别课表等任务会附上输出格式要求。", Modifier.padding(20.dp), style = T.Cap) }
        }
    }, overlay = { b -> BackBar(b) })
}
