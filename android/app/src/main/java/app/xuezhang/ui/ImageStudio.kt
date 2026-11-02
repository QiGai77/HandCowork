package app.xuezhang.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Store
import app.xuezhang.engine.ImageGen
import com.kyant.shapes.Capsule

/** AI 生图：最多 10 个任务并行；点结果可再次对话修改或框选局部重绘。target：cover:<笔记id> / course:<课程id> / ""。 */
@Composable
fun ImageStudioScreen(target: String) {
    val ctx = LocalContext.current
    val defPrompt = remember(target) {
        when {
            target.startsWith("cover:") -> Store.lectures.firstOrNull { it.id == target.removePrefix("cover:") }?.let { l ->
                val c = Store.course(l.courseId)
                "为大学课程笔记《${l.title}》（课程：${c?.name ?: ""}）设计一张竖版书籍封面插画，简洁、现代、质感高级，留出上方空白，不要出现任何文字"
            } ?: ""
            target.startsWith("course:") -> Store.course(target.removePrefix("course:"))?.let { "为大学课程「${it.name}」设计一张横版封面插画，柔和渐变、富有学科意象，不要出现任何文字" } ?: ""
            else -> ""
        }
    }
    var prompt by remember { mutableStateOf(defPrompt) }
    var count by remember { mutableStateOf(1) }
    var openId by remember { mutableStateOf<String?>(null) }
    val size = if (target.startsWith("course:")) "1024x576" else "768x1024"
    val list = ImageGen.tasks.filter { target.isBlank() || it.target == target }
    val running = ImageGen.running()

    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
            item { TopInset(); PageHeader("AI 生图", "最多 ${ImageGen.MAX} 张同时生成 · 进行中 $running") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), radius = 24.dp) {
                    Field(prompt, { prompt = it }, "描述你想要的画面…", Modifier.fillMaxWidth().height(96.dp), singleLine = false)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText("数量", style = T.Sub); Spacer(Modifier.width(8.dp))
                        listOf(1, 2, 4, 10).forEach { n ->
                            BasicText("$n", Modifier.padding(end = 6.dp).clip(Capsule()).background(if (count == n) C.Blue else C.Fill).clickable { count = n }
                                .padding(horizontal = 12.dp, vertical = 6.dp), style = T.Btn.copy(fontSize = 13.sp, color = if (count == n) Color.White else C.Ink))
                        }
                        Spacer(Modifier.weight(1f))
                        BasicText("生成", Modifier.clip(Capsule()).background(if (prompt.isNotBlank()) C.Blue else C.Ter).clickable(enabled = prompt.isNotBlank()) {
                            repeat(count) { ImageGen.submit(ctx, prompt, target, size) }
                            Island.show("已加入 $count 个生图任务", Island.Kind.Info)
                        }.padding(horizontal = 20.dp, vertical = 9.dp), style = T.Btn.copy(color = Color.White))
                    }
                }
            }
            if (list.isEmpty()) item { EmptyHint("还没有图片", "写一句描述，点生成") }
            list.chunked(2).forEach { row ->
                item(key = row.first().id) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t -> TaskTile(t, Modifier.weight(1f)) { openId = t.id } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        openId?.let { id -> ImageGen.tasks.firstOrNull { it.id == id }?.let { t -> EditOverlay(t, target, onClose = { openId = null }, onNew = { openId = it }) } }
    }, overlay = { b -> if (openId == null) BackBar(b) })
}

@Composable
private fun TaskTile(t: ImageGen.Task, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(if (t.size.startsWith("1024x576")) 16f / 9f else 3f / 4f).clip(RoundedCornerShape(18.dp)).background(C.Fill).clickable(enabled = t.status == 2 || t.status == 3, onClick = onClick)) {
        when (t.status) {
            2 -> CoverImages.load(t.file)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            3 -> BasicText("失败\n${t.error.take(40)}", Modifier.align(Alignment.Center).padding(10.dp), style = T.Cap.copy(color = C.Red, textAlign = TextAlign.Center))
            else -> { Shimmer(Modifier.fillMaxSize()); BasicText(if (t.status == 0) "排队中" else "生成中…", Modifier.align(Alignment.Center), style = T.Sub) }
        }
        if (t.history.isNotEmpty()) BasicText(if (t.region != null) "局部 v${t.history.size + 1}" else "v${t.history.size + 1}", Modifier.align(Alignment.TopStart).padding(8.dp)
            .clip(Capsule()).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 2.dp), style = T.Cap.copy(color = Color.White, fontSize = 11.sp))
    }
}

/** 全屏编辑：再次对话（整图修改）或拖动框选区域局部重绘。 */
@Composable
private fun EditOverlay(t: ImageGen.Task, target: String, onClose: () -> Unit, onNew: (String) -> Unit) {
    val ctx = LocalContext.current
    var ins by remember(t.id) { mutableStateOf("") }
    var selMode by remember(t.id) { mutableStateOf(false) }
    var a by remember(t.id) { mutableStateOf<Offset?>(null) }
    var b by remember(t.id) { mutableStateOf<Offset?>(null) }
    var box by remember { mutableStateOf(Size.Zero) }
    Box(Modifier.fillMaxSize().background(Color(0xF0101114)).clickable(interactionSource = null, indication = null) {}) {
        Column(Modifier.fillMaxSize().padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("编辑图片", Modifier.weight(1f), style = T.Title.copy(color = Color.White, fontSize = 22.sp))
                BasicText("关闭", Modifier.clip(Capsule()).background(Color(0x33FFFFFF)).clickable(onClick = onClose).padding(horizontal = 16.dp, vertical = 8.dp), style = T.Btn.copy(color = Color.White))
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (t.status == 3) BasicText(t.error, style = T.Sub.copy(color = Color.White))
                else CoverImages.load(t.file)?.let { img ->
                    Box(Modifier.aspectRatio(img.width.toFloat() / img.height).clip(RoundedCornerShape(14.dp))) {
                        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        Canvas(Modifier.fillMaxSize().then(if (selMode) Modifier.pointerInput(t.id) {
                            detectDragGestures(onDragStart = { p -> a = p; b = p; box = Size(size.width.toFloat(), size.height.toFloat()) }) { ch, d -> ch.consume(); b = (b ?: Offset.Zero) + d }
                        } else Modifier)) {
                            val p = a; val q = b
                            if (p != null && q != null) {
                                val tl = Offset(minOf(p.x, q.x), minOf(p.y, q.y)); val sz = Size(kotlin.math.abs(q.x - p.x), kotlin.math.abs(q.y - p.y))
                                drawRect(Color.Black.copy(alpha = 0.35f))
                                drawRect(Color.White.copy(alpha = 0.18f), tl, sz)
                                drawRect(Color.White, tl, sz, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (t.history.isNotEmpty()) BasicText("已修改：" + t.history.joinToString(" → "), style = T.Cap.copy(color = Color(0xFFB8BCC6)), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (selMode) "框选中 · 拖动画框" else "框选局部", "select", selMode) { selMode = !selMode; if (!selMode) { a = null; b = null } }
                if (target.isNotBlank() && t.status == 2) Pill("设为封面", "check", false) {
                    if (target.startsWith("cover:")) Store.lectures.firstOrNull { it.id == target.removePrefix("cover:") }?.let { Store.upsertLecture(it.copy(coverImg = t.file)) }
                    if (target.startsWith("course:")) Store.course(target.removePrefix("course:"))?.let { Store.upsertCourse(it.copy(cover = "file:" + t.file)) }
                    Island.show("已设为封面", Island.Kind.Done)
                }
                if (t.status == 3) Pill("重试", "zap", false) { ImageGen.retry(ctx, t.id) }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x22FFFFFF)).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(ins, { ins = it }, if (selMode) "框选区域要改成什么…" else "继续对话：比如「背景换成夜空」", Modifier.weight(1f), style = T.Label.copy(color = Color.White))
                Box(Modifier.size(42.dp).clip(Capsule()).background(if (ins.isNotBlank() && t.status == 2) C.Blue else Color(0x33FFFFFF)).clickable(enabled = ins.isNotBlank() && t.status == 2) {
                    val p = a; val q = b
                    val id = if (selMode && p != null && q != null && box.width > 0) {
                        val r = ImageGen.Region((minOf(p.x, q.x) / box.width).coerceIn(0f, 1f), (minOf(p.y, q.y) / box.height).coerceIn(0f, 1f),
                            (maxOf(p.x, q.x) / box.width).coerceIn(0f, 1f), (maxOf(p.y, q.y) / box.height).coerceIn(0f, 1f))
                        ImageGen.inpaint(ctx, t, r, ins)
                    } else ImageGen.refine(ctx, t, ins)
                    ins = ""; a = null; b = null; selMode = false
                    Island.show("已提交修改，在后台生成", Island.Kind.Info)
                    onNew(id); onClose()
                }, contentAlignment = Alignment.Center) { Icon("send", Color.White, 20.dp) }
            }
        }
    }
}

@Composable
private fun Pill(text: String, icon: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(Capsule()).background(if (on) C.Blue else Color(0x33FFFFFF)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, Color.White, 16.dp); Spacer(Modifier.width(6.dp)); BasicText(text, style = T.Btn.copy(color = Color.White, fontSize = 13.5.sp))
    }
}

// ============ 书本长按菜单 ============
@Composable
fun BookMenuContent(lectureId: String) {
    val l = Store.lectures.firstOrNull { it.id == lectureId } ?: run { Nav.sheet.value = null; return }
    Column(Modifier.fillMaxWidth()) {
        BasicText(l.title, style = T.Title.copy(fontSize = 21.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(false to "按书架默认", true to "翻转朝向").forEach { (v, label) ->
                BasicText(label, Modifier.clip(Capsule()).background(if (l.lying == v) C.Blue else C.Fill).clickable { Store.upsertLecture(l.copy(lying = v)) }
                    .padding(horizontal = 16.dp, vertical = 9.dp), style = T.Btn.copy(fontSize = 14.sp, color = if (l.lying == v) Color.White else C.Ink))
            }
        }
        BasicText("长按书本：封面朝外 ⇄ 侧放书脊，再选一次即可恢复", Modifier.padding(top = 6.dp), style = T.Cap)
        Spacer(Modifier.height(16.dp))
        BasicText("精装封面", style = T.Btn)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(48.dp, 64.dp).clip(RoundedCornerShape(6.dp)).background(C.Fill).border(if (l.coverStyle < 0 && l.coverImg.isBlank()) 2.dp else 0.dp, C.Blue, RoundedCornerShape(6.dp))
                .clickable { Store.upsertLecture(l.copy(coverStyle = -1, coverImg = "")) }, contentAlignment = Alignment.Center) { BasicText("跟随\n课程", style = T.Cap.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)) }
            app.xuezhang.engine.HardCovers.ALL.forEach { hc ->
                val i = hc.index
                HcSwatch(hc, Modifier.size(48.dp, 64.dp).clip(RoundedCornerShape(6.dp)).border(if (l.coverStyle == i && l.coverImg.isBlank()) 2.dp else 0.dp, C.Blue, RoundedCornerShape(6.dp))
                    .clickable { Store.upsertLecture(l.copy(coverStyle = i, coverImg = "")) })
            }
        }
        BasicText("默认跟随课程的封面；单本笔记也可以换一款。AI 封面优先显示。", Modifier.padding(top = 6.dp), style = T.Cap)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().clip(Capsule()).background(Brush2.ai).clickable { Nav.sheet.value = null; Nav.push(Route.ImageStudio("cover:${l.id}")) }.padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon("sparkle", Color.White, 18.dp); Spacer(Modifier.width(8.dp))
            BasicText(if (l.coverImg.isNotBlank()) "AI 重新生成 / 修改封面" else "AI 生成封面", style = T.Btn.copy(color = Color.White))
        }
        Spacer(Modifier.height(8.dp))
    }
}

object Brush2 { val ai = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF4F8BFF), Color(0xFF9A6BFF))) }

// ============ 切换课程 ============
@Composable
fun CoursePickContent() {
    val courses = Store.courses
    Column(Modifier.fillMaxWidth()) {
        BasicText("切换课程", style = T.Title.copy(fontSize = 22.sp))
        Spacer(Modifier.height(10.dp))
        if (courses.isEmpty()) BasicText("还没有课程", style = T.Sub)
        LazyColumn(Modifier.fillMaxWidth().height(minOf(420, 74 * courses.size + 8).dp)) {
            items(courses.size) { i ->
                val c = courses[i]; val on = i == ClassPick.index.intValue
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (on) C.Blue.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { ClassPick.index.intValue = i; Nav.sheet.value = null }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CourseCover(c, Modifier.size(64.dp, 44.dp), radius = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(c.name, style = T.Btn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(listOf(c.teacher, app.xuezhang.engine.Schedule.courseSlotLabel(c), c.room).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未排课" }, style = T.Cap, maxLines = 1)
                    }
                    if (on) Icon("checkc", C.Blue, 20.dp)
                }
            }
        }
    }
}
