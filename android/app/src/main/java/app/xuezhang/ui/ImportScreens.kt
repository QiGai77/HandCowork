package app.xuezhang.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.xuezhang.data.Course
import app.xuezhang.data.Store
import app.xuezhang.engine.Bili
import app.xuezhang.engine.CoverMatch
import app.xuezhang.engine.Prompts
import app.xuezhang.engine.Schedule
import app.xuezhang.net.Api
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

// =================== 工具 ===================
fun decodeScaled(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1; while (maxOf(o.outWidth, o.outHeight) / s > maxSide * 2) s *= 2
    val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return null
    scaleDown(b, maxSide)
}.getOrNull()

fun scaleDown(b: Bitmap, maxSide: Int): Bitmap {
    val m = maxOf(b.width, b.height); if (m <= maxSide) return b
    val k = maxSide.toFloat() / m
    return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
}

/** 中心裁剪到封面比例 16:9。 */
fun cropCover(b: Bitmap): Bitmap {
    val target = 16f / 9f
    val r = b.width.toFloat() / b.height
    return if (r > target) { val w = (b.height * target).toInt(); Bitmap.createBitmap(b, (b.width - w) / 2, 0, w, b.height) }
    else { val h = (b.width / target).toInt(); Bitmap.createBitmap(b, 0, (b.height - h) / 2, b.width, h) }
}

fun saveCover(ctx: Context, b: Bitmap, id: String): String {
    val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
    val f = File(dir, "${id}_${System.currentTimeMillis()}.jpg")
    f.outputStream().use { scaleDown(cropCover(b), 1280).compress(Bitmap.CompressFormat.JPEG, 88, it) }
    return f.absolutePath
}

fun Bitmap.jpeg(q: Int = 85): ByteArray = ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()

@Composable
fun PageHeader(title: String, sub: String? = null, beta: Boolean = false) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = T.Large.copy(fontSize = 30.sp))
            if (beta) { Spacer(Modifier.width(8.dp)); BetaPill() }
        }
        if (sub != null) BasicText(sub, style = T.Sub)
    }
}

@Composable
fun CheckDot(on: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(22.dp).clip(Capsule()).background(if (on) C.Blue else Color.Transparent)
        .border(if (on) 0.dp else 1.5.dp, C.Sec.copy(alpha = 0.5f), Capsule()), contentAlignment = Alignment.Center) {
        if (on) Icon("check", Color.White, 13.dp, 3f)
    }
}

@Composable
fun KeyMissing(text: String) {
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        BasicText(text, style = T.Body.copy(fontSize = 14.sp, color = C.HlOrange))
        Spacer(Modifier.height(8.dp))
        BasicText("去「我的」填写 API Key", Modifier.clickable { Nav.home(); Nav.tab.intValue = 3 }, style = T.Btn.copy(color = C.Blue))
    }
}

// =================== 导入课程表 ===================
@Composable
fun ImportTimetableScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var img by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<Schedule.Group>>(emptyList()) }
    val picked = remember { mutableStateListOf<String>() }
    val camFile = remember { File(ctx.cacheDir, "timetable.jpg") }

    fun recognize(b: Bitmap) {
        img = b; err = null; groups = emptyList(); picked.clear()
        if (!app.xuezhang.net.Api.llmReady()) { err = "nokey"; return }
        busy = true
        scope.launch {
            val vm = Api.visionModelFor(Api.mode(), Store.settings.value.visionModel, Store.settings.value.llmModel)
            val job = app.xuezhang.engine.AiProgress.start("timetable", "识别课程表")
            try {
                job.phase("压缩图片", 0.08f, 800)
                val jpg = withContext(Dispatchers.IO) { scaleDown(b, 2048).jpeg(85) }
                job.phase("「看图」模型 $vm 读课表中", 0.88f, 35_000, "${jpg.size / 1024} KB")
                val text = withContext(Dispatchers.IO) { Api.vision(Prompts.get("timetable"), "请识别这张课程表，严格按要求只输出 JSON。", jpg) }
                job.phase("整理课程与上课时段", 0.98f, 800)
                val entries = runCatching { Schedule.parseTimetable(text) }.getOrElse { throw IllegalStateException("「看图」模型（$vm）的回答里没有课程列表 JSON。\n模型回答：" + text.trim().ifBlank { "（空）" }.take(300)) }
                if (entries.isEmpty()) throw IllegalStateException("模型回答里的课程都缺少课程名。\n模型回答：" + text.trim().take(300))
                app.xuezhang.engine.Log.ok("导入课表", "识别 ${entries.size} 条 · $vm")
                groups = Schedule.group(entries, Store.courses)
                picked.addAll(groups.filter { !it.exists }.map { it.name })
                app.xuezhang.engine.AiProgress.finish(job, true, "识别到 ${groups.size} 门课")
            } catch (e: Exception) { err = (e.message ?: e.javaClass.simpleName); app.xuezhang.engine.AiProgress.finish(job, false, "课表没认出来"); app.xuezhang.engine.Log.err("导入课表", e) } finally { busy = false }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { u -> decodeScaled(ctx, u, 2400)?.let { recognize(it) } } }
    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) BitmapFactory.decodeFile(camFile.absolutePath)?.let { recognize(scaleDown(it, 2000)) }
    }

    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item { TopInset(); PageHeader("导入课程表", "拍照或截图都行，学长用视觉大模型读出每门课") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 26.dp, padding = PaddingValues(14.dp)) {
                    if (img != null) {
                        Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(10.dp))) {
                            Image(img!!.asImageBitmap(), "课程表", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                            if (busy) ScanLine()
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon("sparkle", C.Blue, 15.dp, fill = true); Spacer(Modifier.width(6.dp))
                            val weeks = groups.flatMap { it.entries }.map { it.weeks }.firstOrNull { it.isNotBlank() }
                            BasicText(when {
                                busy -> "学长正在读课表…（${aiJob("timetable")?.step?.value ?: ""}）"
                                groups.isNotEmpty() -> "识别到 ${groups.size} 门课" + (weeks?.let { " · 第 $it 周" } ?: "")
                                else -> "没有识别结果"
                            }, style = T.Btn.copy(fontSize = 12.5.sp, color = C.Blue))
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon("cal", C.Blue, 30.dp)
                            Spacer(Modifier.height(8.dp))
                            BasicText("教务系统截图、App 课表、纸质课表照片都可以", style = T.Sub)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn("拍照", {
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", camFile)
                            runCatching { shoot.launch(uri) }.onFailure { Island.show("没有可用的相机", Island.Kind.Warn) }
                        }, Modifier.weight(1f), icon = "camera")
                        Btn("从相册选", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), icon = "image", tint = C.Ink)
                    }
                }
            }
            if (busy) item { AiProgressCard(aiJob("timetable")) }
            when {
                err == "nokey" -> item { KeyMissing("识别课程表要用「看图」模型（当前 ${Store.settings.value.visionModel}），先在「我的 › 大模型」填好 API Key 或登录账号。") }
                err != null -> item {
                    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                        BasicText("没认出来", style = T.Item)
                        BasicText(err!!, style = T.Sub)
                        Spacer(Modifier.height(8.dp))
                        BasicText("重试", Modifier.clickable { img?.let { recognize(it) } }, style = T.Btn.copy(color = C.Blue))
                    }
                }
            }
            if (groups.isNotEmpty()) item {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 22.dp, padding = PaddingValues(0.dp)) {
                    groups.forEachIndexed { i, g ->
                        val on = g.name in picked
                        Row(Modifier.fillMaxWidth().clickable { if (on) picked.remove(g.name) else picked.add(g.name) }.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CoverView(g.name, "", Modifier.size(50.dp, 34.dp), radius = 9.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(g.name, style = T.Btn.copy(fontSize = 14.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(g.entries.joinToString("、") { e -> Schedule.slotLabel(app.xuezhang.data.Slot(e.weekday, e.startSection, e.endSection)) }
                                    + (g.entries.firstOrNull { it.room.isNotBlank() }?.let { " · ${it.room}" } ?: ""), style = T.Cap.copy(fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val extra = listOfNotNull(g.entries.firstOrNull { it.teacher.isNotBlank() }?.teacher, g.entries.firstOrNull { it.weeks.isNotBlank() }?.weeks?.let { "第 $it 周" }).joinToString(" · ")
                                if (extra.isNotBlank()) BasicText(extra, style = T.Cap.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (g.exists) { BasicText("已存在", Modifier.clip(Capsule()).background(C.Orange.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp),
                                style = T.Cap.copy(fontSize = 10.5.sp, color = C.Orange, fontWeight = FontWeight.Bold)); Spacer(Modifier.width(10.dp)) }
                            CheckDot(on)
                        }
                        if (i < groups.lastIndex) Divider(76.dp)
                    }
                }
            }
        }
    }, overlay = { b ->
        BackBar(b)
        if (groups.isNotEmpty()) {
            val n = picked.size
            GlassPill(b, if (n > 0) "一键导入 $n 门课" else "勾选要导入的课", {
                if (n == 0) return@GlassPill
                var k = Store.courses.size
                groups.filter { it.name in picked }.forEach { g ->
                    val existing = Store.courses.firstOrNull { Schedule.norm(it.name) == Schedule.norm(g.name) }
                    val nc = Schedule.toCourse(g, k++ % C.course.size)
                    // 已存在的课：只补上课时间，不覆盖其他信息
                    if (existing != null) Store.upsertCourse(existing.copy(slots = (existing.slots + nc.slots).distinct(), teacher = existing.teacher.ifBlank { nc.teacher }, room = existing.room.ifBlank { nc.room }))
                    else Store.upsertCourse(nc)
                }
                Island.show("已导入 $n 门课", Island.Kind.Done)
                Nav.pop()
            }, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp).fillMaxWidth(),
                tint = C.Blue.copy(alpha = 0.9f), color = Color.White, height = 54.dp)
        }
    })
}

@Composable
private fun ScanLine() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "scan")
    val y by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Reverse), label = "y")
    Canvas(Modifier.fillMaxSize()) {
        val yy = size.height * y
        drawRect(C.Blue.copy(alpha = 0.08f), topLeft = androidx.compose.ui.geometry.Offset(0f, (yy - 24.dp.toPx()).coerceAtLeast(0f)), size = androidx.compose.ui.geometry.Size(size.width, 27.dp.toPx()))
        drawRect(C.Blue.copy(alpha = 0.8f), topLeft = androidx.compose.ui.geometry.Offset(0f, yy), size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()))
    }
}

// =================== 选择封面 ===================
@Composable
fun CoverPickerScreen(target: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val course: Course? = if (target == "draft") Nav.draft.value else Store.course(target)
    if (course == null) { Nav.pop(); return }
    fun apply(cover: String) {
        if (target == "draft") Nav.draft.value = Nav.draft.value?.copy(cover = cover)
        else Store.course(target)?.let { Store.upsertCourse(it.copy(cover = cover)) }
    }
    val name = course.name.ifBlank { "新课程" }
    val current = course.cover
    val auto = CoverMatch.match(name)
    var aiOpen by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("$name 课程封面，扁平插画，${auto?.let { colorWord(it.color) } ?: "蓝色调"}，简洁，无文字") }
    var gen by remember { mutableStateOf(false) }
    var genErr by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateListOf<Bitmap>() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val b = decodeScaled(ctx, uri, 2000) ?: run { Island.show("读不了这张图", Island.Kind.Warn); return@rememberLauncherForActivityResult }
        val p = saveCover(ctx, b, course.id)
        apply("file:$p"); Island.show("已换成相册图片（已裁成 16:9）", Island.Kind.Done); Nav.pop()
    }
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
            item { TopInset(); PageHeader("选择封面", "已为常见课程预制，按课程名自动匹配") }
            val tiles = CoverMatch.PRESETS.map { it.key to "preset:${it.key}" } + listOf("通用" to "generic")
            tiles.chunked(3).forEach { row ->
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, cv) ->
                            val sel = current == cv || (current.isBlank() && ((auto != null && cv == "preset:${auto.key}") || (auto == null && cv == "generic")))
                            Column(Modifier.weight(1f).clickable { apply(cv); Nav.pop() }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().aspectRatio(114f / 92f).then(if (sel) Modifier.border(2.4.dp, C.Blue, RoundedCornerShape(18.dp)).padding(3.dp) else Modifier)) {
                                    CoverView(if (cv == "generic") name else label, cv, Modifier.fillMaxSize(), radius = 16.dp)
                                    if (sel) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(Capsule()).background(C.Blue), contentAlignment = Alignment.Center) { Icon("check", Color.White, 12.dp, 3f) }
                                }
                                Spacer(Modifier.height(6.dp))
                                BasicText(if (cv == "generic") "通用 · ${CoverMatch.glyph(name)}" else label, style = T.Btn.copy(fontSize = 12.5.sp, color = if (sel) C.Blue else C.Ink), maxLines = 1)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (current.startsWith("file:")) item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverView(name, current, Modifier.size(96.dp, 54.dp), radius = 12.dp)
                    Spacer(Modifier.width(12.dp)); BasicText("当前：自定义图片", Modifier.weight(1f), style = T.Btn)
                    BasicText("恢复自动", Modifier.clickable { apply(""); Nav.pop() }, style = T.Btn.copy(color = C.Blue))
                }
            }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(), radius = 24.dp) {
                    Row(Modifier.fillMaxWidth().clickable { aiOpen = !aiOpen }, verticalAlignment = Alignment.CenterVertically) {
                        Icon("wand", C.Purple, 19.dp); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText("AI 生成封面", style = T.Btn)
                            BasicText("用 ${Store.settings.value.imageModel} 生成，复用你的 API Key", style = T.Cap)
                        }
                        Icon(if (aiOpen) "chevu" else "chevd", C.Sec, 14.dp)
                    }
                    if (aiOpen) {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0D000000)).padding(12.dp)) {
                            Field(prompt, { prompt = it }, "描述你想要的封面", Modifier.fillMaxWidth(), style = T.Label.copy(fontSize = 14.sp), singleLine = false)
                        }
                        Spacer(Modifier.height(10.dp))
                        Btn(if (gen) "生成中…" else if (results.isEmpty()) "生成 2 张" else "再生成 2 张", g@{
                            if (gen) return@g
                            val s = Store.settings.value
                            if (!app.xuezhang.net.Api.llmReady() && s.asrKey.isBlank()) { genErr = "先在「我的」里填写 API Key"; return@g }
                            gen = true; genErr = null
                            scope.launch {
                                try {
                                    val bmps = app.xuezhang.engine.AiProgress.run("cover", "AI 生成封面", doneMsg = "封面画好了") { job ->
                                        job.phase("${Store.settings.value.imageModel} 绘制中", 0.85f, 30_000, "2 张 · 16:9")
                                        val urls = withContext(Dispatchers.IO) { Api.images(prompt, 2) }
                                        job.phase("下载图片", 0.98f, 4000)
                                        withContext(Dispatchers.IO) { urls.take(4).mapNotNull { u -> runCatching { (if (u.startsWith("b64:")) android.util.Base64.decode(u.removePrefix("b64:"), android.util.Base64.DEFAULT) else Api.download(u)).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull() } }
                                            .also { if (it.isEmpty()) throw IllegalStateException("图片下载失败") }
                                    }
                                    results.clear(); results.addAll(bmps)
                                } catch (e: Exception) { genErr = e.message ?: "生成失败" } finally { gen = false }
                            }
                        }, Modifier.fillMaxWidth(), icon = "sparkle", tint = C.Purple)
                        if (gen) aiJob("cover")?.let { j -> Spacer(Modifier.height(10.dp)); AiProgressBar(j) }
                        genErr?.let { BasicText(it, Modifier.padding(top = 8.dp), style = T.Cap.copy(color = C.Red)) }
                        if (results.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            BasicText("点一张用作封面（会裁成 16:9）", style = T.Cap)
                            Spacer(Modifier.height(6.dp))
                            results.chunked(2).forEach { row ->
                                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { bm ->
                                        Image(cropCover(bm).asImageBitmap(), "AI 封面", Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).clickable {
                                            val p = saveCover(ctx, bm, course.id); apply("file:$p"); Island.show("已换成 AI 封面", Island.Kind.Done); Nav.pop()
                                        }, contentScale = ContentScale.Crop)
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }, overlay = { b ->
        BackBar(b)
        GlassPill(b, "从相册选择", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp).fillMaxWidth(), icon = "image", height = 52.dp)
    })
}

private fun colorWord(c: Long): String {
    val r = (c shr 16 and 0xff).toInt(); val g = (c shr 8 and 0xff).toInt(); val b = (c and 0xff).toInt()
    return when {
        r > 180 && g < 120 -> "红色调"; r > 180 && g >= 100 -> "橙色调"; g > r && g > b -> "绿色调"
        b > r && r > 100 -> "紫色调"; g > 130 && b > 130 -> "青色调"; else -> "蓝色调"
    }
}

// =================== 导入 B 站录播课 ===================
@Composable
fun ImportBiliScreen(presetCourse: String? = null, mode: Int = 0) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<Bili.Info?>(null) }
    var page by remember { mutableStateOf(1) }
    var pic by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var slideUri by remember { mutableStateOf<Uri?>(null) }
    var slideName by remember { mutableStateOf<String?>(null) }
    var courseId by remember { mutableStateOf<String?>(presetCourse) }
    var localAudio by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    // 本地录音 / 音频文件（m4a mp3 aac amr wav ogg opus），按录播课流程分段识别
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { cur -> val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME); cur.moveToFirst(); if (i >= 0) cur.getString(i) else null } ?: "录音"
        val dur = runCatching { android.media.MediaMetadataRetriever().run { setDataSource(ctx, uri); val d = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L; release(); (d / 1000).toInt() } }.getOrDefault(0)
        val title = name.substringBeforeLast('.').take(40)
        isVideo = ctx.contentResolver.getType(uri)?.startsWith("video") == true || name.substringAfterLast('.').lowercase() in setOf("mp4", "mkv", "webm", "mov", "3gp", "flv")
        localAudio = uri; err = null; page = 1
        // 本地视频：取一帧当封面
        pic = if (isVideo) runCatching { android.media.MediaMetadataRetriever().run { setDataSource(ctx, uri); val b = getFrameAtTime(3_000_000); release(); b?.asImageBitmap() } }.getOrNull() else null
        info = Bili.Info("local", title, if (isVideo) "本地视频" else "本地音频", dur, "", listOf(Bili.Page(-1, title, dur)))
        if (presetCourse == null) courseId = Store.courses.firstOrNull { c -> title.contains(c.name) }?.id
    }

    fun lookup(text: String) {
        localAudio = null
        link = text; err = null
        scope.launch {
            val t = withContext(Dispatchers.IO) { Bili.resolveShort(text) }
            val bv = Bili.parseBv(t) ?: run { if (text.isNotBlank()) err = "没找到 BV 号，粘贴完整的视频链接试试"; return@launch }
            loading = true
            try {
                val i = withContext(Dispatchers.IO) { Bili.info(bv) }
                info = i; page = Bili.parsePage(t).coerceIn(1, maxOf(1, i.pages.size))
                if (presetCourse == null) courseId = Store.courses.firstOrNull { c -> i.title.contains(c.name) || CoverMatch.match(i.title)?.let { p -> CoverMatch.match(c.name) == p } == true }?.id
                pic = withContext(Dispatchers.IO) { runCatching { Api.download(i.pic.replace("http://", "https://") + "@480w_270h_1c.jpg").let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull() }
            } catch (e: Exception) { err = e.message ?: "获取视频信息失败" } finally { loading = false }
        }
    }
    val doc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        slideUri = uri
        slideName = ctx.contentResolver.query(uri, null, null, null, null)?.use { cur -> val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME); cur.moveToFirst(); if (i >= 0) cur.getString(i) else null } ?: "课件.pdf"
    }
    val running = Bili.step.intValue in 0..4 && Bili.error.value == null
    val started = Bili.step.intValue >= 0

    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item { TopInset(); PageHeader(if (presetCourse != null) "课前资料 · 录播课" else "导入录播课", (presetCourse?.let { Store.course(it)?.name?.let { n -> "放进「$n」· " } } ?: "") + "B 站视频 / 本地音视频 + 对应 PPT", beta = true) }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(48.dp)
                    .glass(LocalBg.current!!, Capsule(), surface = Color.White.copy(alpha = 0.6f)).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("tv", Color(0xFFFB7299), 18.dp, 2.1f); Spacer(Modifier.width(10.dp))
                    Field(link, { link = it; if (Bili.parseBv(it) != null && Bili.parseBv(it) != info?.bvid) lookup(it) }, "bilibili.com/video/BV1…", Modifier.weight(1f), style = T.Label.copy(fontSize = 14.5.sp))
                    BasicText("粘贴", Modifier.clip(Capsule()).clickable {
                        val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                        if (clip.isBlank()) Island.show("剪贴板是空的", Island.Kind.Info) else lookup(clip)
                    }.padding(horizontal = 12.dp, vertical = 8.dp), style = T.Btn.copy(color = C.Blue, fontSize = 14.sp))
                }
            }
            if (info == null) item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (mode == 1) C.Blue.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable { pickAudio.launch(arrayOf("audio/*", "video/*")) }.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("mic", C.Blue, 16.dp); Spacer(Modifier.width(6.dp))
                    BasicText(if (mode == 1) "选择本地录音 / 音频 / 视频文件" else "或者导入手机录音 / 本地音频 / 视频文件", style = T.Btn.copy(color = C.Blue, fontSize = 14.sp))
                }
            }
            if (loading) item { BasicText("正在获取视频信息…", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = T.Sub) }
            err?.let { e -> item { BasicText(e, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = T.Sub.copy(color = C.Red)) } }
            info?.let { i ->
                val pg = i.pages.getOrNull(page - 1)
                item {
                    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 24.dp, padding = PaddingValues(12.dp)) {
                        Row {
                            Box(Modifier.size(132.dp, 92.dp).clip(RoundedCornerShape(14.dp))) {
                                pic?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: CoverView(i.title, "", Modifier.fillMaxSize(), radius = 14.dp)
                                Box(Modifier.align(Alignment.Center).size(32.dp).clip(Capsule()).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) { Icon("play", Color.White, 14.dp) }
                                BasicText(Bili.fmtDur(pg?.duration ?: i.duration), Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp), style = T.Cap.copy(fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(i.title, style = T.Btn, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (i.pages.size > 1 && pg != null) BasicText("P$page ${pg.part}", style = T.Label.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText("UP 主 · ${i.owner}", style = T.Cap.copy(fontSize = 11.5.sp), maxLines = 1)
                                if (i.bvid == "local") BasicText(if (isVideo) "本地视频 · 识别音轨，笔记配原画面截图" else "本地音频 · 分段语音识别", style = T.Cap.copy(fontSize = 11.5.sp)) else BasicText("将只下载音轨，约 ${maxOf(1, ((pg?.duration ?: i.duration) * 7.6 / 1024).toInt())} MB", style = T.Cap.copy(fontSize = 11.5.sp))
                            }
                        }
                        if (i.pages.size > 1) {
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                i.pages.forEachIndexed { k, p ->
                                    BasicText("P${k + 1} ${p.part.take(14)}", Modifier.clip(Capsule()).background(if (page == k + 1) C.Blue else C.Fill).clickable(enabled = !running) { page = k + 1 }
                                        .padding(horizontal = 10.dp, vertical = 5.dp), style = T.Cap.copy(color = if (page == k + 1) Color.White else C.Ink, fontSize = 11.5.sp))
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(60.dp)
                        .glass(LocalBg.current!!, com.kyant.shapes.RoundedRectangle(24.dp), surface = Color.White.copy(alpha = 0.6f)).clip(RoundedCornerShape(24.dp))
                        .clickable(enabled = !running) { doc.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.presentationml.presentation")) }
                        .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon("pres", C.Orange, 19.dp, 2.1f); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(slideName ?: "添加对应的 PPT / PDF（可选）", style = T.Btn.copy(fontSize = 14.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(if (slideName != null) (Bili.detail.value.takeIf { Bili.step.intValue > 1 } ?: "导入时提取热词和公式，并对齐页码") else "有课件的话，笔记会配上对应页面", style = T.Cap.copy(fontSize = 11.5.sp), maxLines = 1)
                        }
                        if (slideName != null) Icon("check", C.Green, 18.dp, 2.6f)
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        BasicText("放进哪门课", Modifier.padding(start = 4.dp, bottom = 6.dp), style = T.Sub)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val newName = CoverMatch.match(i.title)?.key ?: i.title.take(8)
                            (listOf<Pair<String?, String>>(null to "新建「$newName」") + Store.courses.map { it.id to it.name }).forEach { (cid, n) ->
                                BasicText(n, Modifier.clip(Capsule()).background(if (courseId == cid) C.Blue else C.Fill).clickable(enabled = !running) { courseId = cid }
                                    .padding(horizontal = 12.dp, vertical = 6.dp), style = T.Cap.copy(color = if (courseId == cid) Color.White else C.Ink, fontSize = 12.5.sp))
                            }
                        }
                    }
                }
                if (started) item { AiProgressCard(aiJob("bili")) }
                if (started) item { BiliSteps() }
                if (Store.settings.value.asrKey.isBlank() || !app.xuezhang.net.Api.llmReady()) item {
                    KeyMissing("识别和整理要用你自己的语音识别 / 大模型 Key（音轨下载不需要）。")
                }
            }
            if (info == null && !loading) item {
                val demo = Store.lectures.firstOrNull { l -> Store.course(l.courseId)?.source == "demo" }
                if (demo != null) PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(), onClick = { Nav.push(Route.Note(demo.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Store.course(demo.courseId)?.let { CourseCover(it, Modifier.size(72.dp, 48.dp), radius = 10.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText("先看看示例课", style = T.Btn)
                            BasicText("电机学 · ${demo.title} · 已生成好的图文笔记", style = T.Cap, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon("chev", C.Ter, 14.dp)
                    }
                }
            }
        }
    }, overlay = { b ->
        BackBar(b)
        val i = info
        val bottom = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp).fillMaxWidth()
        when {
            i == null -> {}
            Bili.step.intValue == 5 && Bili.lectureId.value != null -> GlassPill(b, "查看笔记", { Bili.step.intValue = -1; Nav.replace(Route.Note(Bili.lectureId.value!!)) }, bottom, tint = C.Blue.copy(alpha = 0.9f), color = Color.White, height = 52.dp)
            running -> GlassPill(b, "后台处理中，完成后灵动岛提醒你", {}, bottom, color = C.Sec, height = 50.dp)
            else -> GlassPill(b, if (Bili.error.value != null) "重试" else "开始导入", go@{
                val pg = i.pages.getOrNull(page - 1) ?: Bili.Page(0, i.title, i.duration)
                if (pg.cid == 0L && localAudio == null) { Island.show("这个视频没有分 P 信息", Island.Kind.Warn); return@go }
                val c = courseId?.let { Store.course(it) } ?: run {
                    val p = CoverMatch.match(i.title)
                    Course(name = p?.key ?: i.title.take(12), cover = "", colorIdx = Store.courses.size % C.course.size, source = "bilibili:${i.bvid}").also { Store.upsertCourse(it); courseId = it.id }
                }
                Bili.start(ctx, i, pg, c, slideUri, slideName, localAudio, isVideo)
            }, bottom, tint = C.Blue.copy(alpha = 0.9f), color = Color.White, height = 52.dp)
        }
    })
}

@Composable
private fun BiliSteps() {
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 24.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Bili.STEPS.forEachIndexed { i, n ->
            val st = Bili.step.intValue
            val failed = Bili.error.value != null && i == st
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    when {
                        i < st -> Box(Modifier.size(22.dp).clip(Capsule()).background(C.Green), contentAlignment = Alignment.Center) { Icon("check", Color.White, 13.dp, 3f) }
                        failed -> Box(Modifier.size(22.dp).clip(Capsule()).background(C.Red), contentAlignment = Alignment.Center) { Icon("x", Color.White, 12.dp, 3f) }
                        i == st -> Canvas(Modifier.size(22.dp)) {
                            drawCircle(C.Blue.copy(alpha = 0.25f), style = Stroke(2.5.dp.toPx()))
                            drawArc(C.Blue, -90f, 360f * Bili.progress.floatValue.coerceIn(0.05f, 1f), false, style = Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                        else -> Box(Modifier.size(20.dp).clip(Capsule()).border(1.6.dp, C.Sec.copy(alpha = 0.4f), Capsule()))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        BasicText(n, Modifier.weight(1f), style = if (i <= st) T.Btn.copy(fontSize = 14.5.sp) else T.Label.copy(fontSize = 14.5.sp, color = C.Sec))
                        BasicText(when { i < st -> "完成"; failed -> "失败"; i == st -> "${(Bili.progress.floatValue * 100).toInt()}%"; else -> "等待" },
                            style = T.Btn.copy(fontSize = 12.5.sp, color = when { i < st -> C.Green; failed -> C.Red; i == st -> C.Blue; else -> C.Sec }))
                    }
                    if (i == st && !failed) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(Capsule()).background(C.Fill)) {
                            Box(Modifier.fillMaxWidth(Bili.progress.floatValue.coerceIn(0.02f, 1f)).height(5.dp).clip(Capsule()).background(C.Blue))
                        }
                        if (Bili.detail.value.isNotBlank()) BasicText(Bili.detail.value, Modifier.padding(top = 6.dp), style = T.Cap.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (failed) BasicText(Bili.error.value ?: "", Modifier.padding(top = 4.dp), style = T.Cap.copy(color = C.Red))
                }
            }
        }
    }
}

// =================== 开屏公告 ===================
object Splash {
    var shownThisLaunch = false
    val visible = mutableStateOf(false)
    val ITEMS = listOf(
        "新" to "Google Drive 连接器：我的 › 云同步里授权 Google 账号，查看 Drive 文件夹和每个文件的上传进度",
        "新" to "所有 AI 生成（笔记、课表、课件、导图、录播课、问答、封面）都有进度条 + 灵动岛进度 + 小彩蛋",
        "新" to "笔记库变成书架：每门课一层，每节笔记一本书，点开翻页阅读",
        "新" to "上课页改成课程卡组，左右滑选课；下面是「资料上传」和红色「开始上课」",
        "新" to "录播视频（B 站 / 本地视频）的笔记每节配视频原画面截图，B 站截图可以换一张",
        "新" to "OpenAI / ChatGPT 用户笔记生成后自动画 AI 导图",
        "修" to "录音不足 30 秒视为误触，自动作废，不再生成空笔记",
        "修" to "课件页面加载时显示微光占位，不再空白",
    )
    fun versionName(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0.0.7"
    fun maybeShow(ctx: Context) {
        if (shownThisLaunch) return
        shownThisLaunch = true
        if (Store.settings.value.splashHidden != versionName(ctx)) visible.value = true
    }
}

@Composable
fun SplashHost() {
    val ctx = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { Splash.maybeShow(ctx) }
    var dontShow by remember { mutableStateOf(false) }
    val ver = remember { Splash.versionName(ctx) }
    AnimatedVisibility(Splash.visible.value, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(C.Bg.copy(alpha = 0.72f)).background(Color.Black.copy(alpha = 0.18f)).clickable(interactionSource = null, indication = null) {}) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(0.6f))
                Box(Modifier.size(80.dp).shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000)).clip(RoundedCornerShape(24.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                    BasicText("学", style = T.Large.copy(fontSize = 38.sp))
                }
                Spacer(Modifier.height(14.dp))
                BasicText("课堂学长", style = T.Large.copy(fontSize = 30.sp))
                Row(verticalAlignment = Alignment.CenterVertically) { BetaPill(); Spacer(Modifier.width(10.dp)); BasicText("v$ver", style = T.Btn.copy(fontSize = 13.sp, color = C.Sec)) }
                Spacer(Modifier.height(22.dp))
                AnimatedVisibility(Splash.visible.value, enter = scaleIn(initialScale = 0.92f) + fadeIn()) {
                    Column(Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(30.dp), ambientColor = Color(0x2A000000), spotColor = Color(0x2A000000))
                        .clip(RoundedCornerShape(30.dp)).background(Color.White).padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon("mega", C.Blue, 20.dp, 2.1f); Spacer(Modifier.width(8.dp))
                            BasicText("公告", Modifier.weight(1f), style = T.Head)
                            BasicText(java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA).format(java.util.Date()), style = T.Cap)
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) { Splash.ITEMS.forEach { (tag, s) ->
                            val col = if (tag == "新") C.Blue else C.Orange
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                BasicText(tag, Modifier.clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = T.Cap.copy(color = col, fontWeight = FontWeight.Bold, fontSize = 11.5.sp))
                                Spacer(Modifier.width(10.dp))
                                BasicText(s, style = T.Label.copy(fontSize = 13.5.sp))
                            }
                        } }
                        Spacer(Modifier.height(10.dp))
                        BasicText("测试版提示：录音与笔记保存在本机，API Key 只发给你填写的接口。遇到问题点「我的 › 反馈」。",
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1AFF9500)).padding(12.dp),
                            style = T.Cap.copy(fontSize = 12.5.sp, color = C.HlOrange, lineHeight = 19.sp))
                        Spacer(Modifier.height(14.dp))
                        BasicText("知道了", Modifier.fillMaxWidth().height(52.dp).clip(Capsule()).background(C.Blue).clickable {
                            if (dontShow) Store.updateSettings(Store.settings.value.copy(splashHidden = ver))
                            Splash.visible.value = false
                        }.padding(top = 14.dp), style = T.Btn.copy(color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.clip(Capsule()).clickable { dontShow = !dontShow }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(if (dontShow) C.Blue else Color.Transparent)
                        .border(if (dontShow) 0.dp else 1.5.dp, C.Sec, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                        if (dontShow) Icon("check", Color.White, 11.dp, 3f)
                    }
                    Spacer(Modifier.width(8.dp)); BasicText("本版本不再显示", style = T.Label.copy(fontSize = 13.sp, color = C.Sec))
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
