package app.xuezhang.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Course
import app.xuezhang.data.Store
import app.xuezhang.engine.Live
import app.xuezhang.engine.Schedule
import app.xuezhang.engine.SlideImport
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 上课 Tab 当前选中的课程（跨页面保持）。 */
object ClassPick {
    val index = mutableIntStateOf(-1)
    /** 左滑下一门、右滑上一门，循环。 */
    fun step(cur: Int, n: Int, dir: Int): Int = if (n <= 0) 0 else ((cur + dir) % n + n) % n
}

// =================== 上课 Tab（按参考版式）===================
private val ClsBlue = Color(0xFF1F6FEB)

@Composable
fun ClassTab() {
    val start = rememberStartClass()
    val courses = Store.courses
    val n = courses.size
    val ctx = LocalContext.current
    if (ClassPick.index.intValue !in 0 until n) {
        val cal = Schedule.clock()
        val next = Schedule.next(Schedule.today(courses, Schedule.isoWeekday(cal)), cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE))
        ClassPick.index.intValue = next?.let { t -> courses.indexOfFirst { it.id == t.course.id } }?.takeIf { it >= 0 } ?: 0
    }
    val sel = ClassPick.index.intValue.coerceIn(0, maxOf(0, n - 1))
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            TopInset()
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText("上课", style = T.Large.copy(fontSize = 28.sp, lineHeight = 34.sp))
                    BasicText(if (Live.running.value) "正在录音，点下方回到课堂" else "选择课程，开始记录这一堂课", style = T.Sub.copy(fontSize = 13.sp))
                }
            }
        }
        if (Live.running.value) item { LiveBanner() }
        if (n == 0) item {
            EmptyHint("还没有课程", "先新建一门课，或拍照导入课程表")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("新建课程", { Nav.draft.value = null; Nav.push(Route.EditCourse(null)) }, Modifier.weight(1f), icon = "plus", tint = C.Ink)
                Btn("导入课程表", { Nav.push(Route.ImportTimetable) }, Modifier.weight(1f), icon = "scan")
            }
        } else {
            val c = courses[sel]
            val notes = Store.lectures.count { it.courseId == c.id }
            // 课程卡片（精简紧凑版，不用下滑直接录课，左右滑也可切课）
            item {
                val scope = rememberCoroutineScope()
                val dx = remember(c.id) { Animatable(0f) }
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                    .graphicsLayer { translationX = dx.value; rotationZ = dx.value / 80f }
                    .then(if (n > 1) Modifier.pointerInput(sel) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { ch, d -> ch.consume(); scope.launch { dx.snapTo(dx.value + d) } },
                            onDragEnd = { if (abs(dx.value) > 200f) { ClassPick.index.intValue = ClassPick.step(sel, n, if (dx.value < 0) 1 else -1) } else scope.launch { dx.animateTo(0f, spring(0.6f, 380f)) } })
                    } else Modifier), radius = 24.dp, padding = PaddingValues(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(96.dp)) {
                        CourseCover(c, Modifier.fillMaxSize().clickable { Nav.push(Route.CourseDetail(c.id)) }, radius = 18.dp)
                        Row(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(Capsule()).background(Color.White.copy(alpha = 0.65f)).padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon("checkc", Color(0xFF1D1D1F), 13.dp); Spacer(Modifier.width(4.dp))
                            BasicText("已选课程", style = T.Cap.copy(color = Color(0xFF1D1D1F), fontWeight = FontWeight.SemiBold, fontSize = 11.sp))
                        }
                        if (n > 1) BasicText("${sel + 1} / $n", Modifier.align(Alignment.TopStart).padding(8.dp).clip(Capsule()).background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 8.dp, vertical = 2.dp), style = T.Cap.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                    }
                    Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(c.name, Modifier.weight(1f), style = T.Title.copy(fontSize = 20.sp, lineHeight = 26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.clip(Capsule()).clickable { Nav.push(Route.CourseDetail(c.id)) }.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                BasicText("$notes 篇笔记", style = T.Cap.copy(color = ClsBlue, fontSize = 12.sp)); Spacer(Modifier.width(2.dp)); Icon("chevr", ClsBlue, 12.dp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon("user", C.Sec, 14.dp); Spacer(Modifier.width(4.dp)); BasicText(c.teacher.ifBlank { "未填老师" }, style = T.Sub.copy(fontSize = 12.5.sp))
                            Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(12.dp).background(C.Line))
                            Icon("pin", C.Sec, 14.dp); Spacer(Modifier.width(4.dp)); BasicText(c.room.ifBlank { "未填教室" }, style = T.Sub.copy(fontSize = 12.5.sp), maxLines = 1)
                            Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(12.dp).background(C.Line))
                            Icon("clock", C.Sec, 14.dp); Spacer(Modifier.width(4.dp)); BasicText(Schedule.courseSlotLabel(c).ifBlank { "未排课" }, style = T.Sub.copy(fontSize = 12.5.sp), maxLines = 1)
                        }
                    }
                }
            }
            // 课前资料
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), radius = 20.dp, padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    val sp = c.slidePath
                    val has = sp.isNotBlank() && java.io.File(sp).exists()
                    val total = remember(sp) { if (has) SlideImages.pageCount(sp) else 0 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(72.dp)) {
                            if (has) SlideThumb(sp, 1, widthPx = 400) { SlideViewer.open.value = Triple(sp, 1, total) }
                            else Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp)).background(C.Fill), contentAlignment = Alignment.Center) { Icon("pres", C.Sec, 18.dp) }
                            if (has) BasicText(if (sp.endsWith(".pdf", true)) "PDF" else "PPT", Modifier.align(Alignment.BottomEnd).padding(2.dp).clip(RoundedCornerShape(4.dp)).background(C.Red)
                                .padding(horizontal = 4.dp, vertical = 0.5.dp), style = T.Cap.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(if (has) c.slideFile.substringBeforeLast('.') else "课前资料", style = T.Btn.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(if (has) "已关联 · $total 页" else "上传 PPT / PDF 辅助笔记", style = T.Cap.copy(fontSize = 11.5.sp))
                        }
                        BasicText(if (has) "查看" else "添加", Modifier.clip(Capsule()).background(ClsBlue.copy(alpha = 0.10f))
                            .clickable { if (has) SlideViewer.open.value = Triple(sp, 1, total) else Nav.push(Route.Materials(c.id)) }
                            .padding(horizontal = 12.dp, vertical = 5.dp), style = T.Btn.copy(color = ClsBlue, fontSize = 12.5.sp))
                    }
                }
            }
            // 上课准备
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), radius = 20.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    val micOk = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val mode = Store.settings.value.defaultMode
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon("mic", ClsBlue, 18.dp); Spacer(Modifier.width(10.dp))
                        BasicText(if (micOk) "麦克风已就绪" else "开始时请求麦克风", style = T.Label.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium))
                        Spacer(Modifier.width(6.dp)); Box(Modifier.size(7.dp).clip(Capsule()).background(if (micOk) C.Green else C.Orange))
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.clip(Capsule()).background(ClsBlue.copy(alpha = 0.08f)).clickable { Store.updateSettings(Store.settings.value.copy(defaultMode = 1 - mode)) }
                            .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(if (mode == 1) "学长讲解" else "原文转写", style = T.Btn.copy(color = ClsBlue, fontSize = 12.5.sp)); Icon("chevr", ClsBlue, 13.dp)
                        }
                    }
                }
            }
            // 开始上课
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth().height(56.dp).shadow(12.dp, Capsule(), ambientColor = ClsBlue.copy(alpha = 0.4f), spotColor = ClsBlue.copy(alpha = 0.5f))
                        .clip(Capsule()).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF39A0FF), Color(0xFF1F6FEB), Color(0xFF3B7BFF)))).clickable { start(c) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon("mic", Color.White, 22.dp, 2.3f); Spacer(Modifier.width(10.dp))
                        BasicText(if (Live.running.value) "回到课堂" else "开始上课", style = T.Btn.copy(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.height(6.dp))
                    BasicText("开始录音并实时整理笔记 · 不足 30 秒自动作废", Modifier.fillMaxWidth(), style = T.Cap.copy(textAlign = TextAlign.Center, fontSize = 11.5.sp))
                }
            }
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun CourseDeck(courses: List<Course>, sel: Int, onSelect: (Int) -> Unit) {
    val n = courses.size
    val scope = rememberCoroutineScope()
    Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().height(330.dp), contentAlignment = Alignment.TopCenter) {
        val depthMax = minOf(2, n - 1)
        (depthMax downTo 0).forEach { depth ->
            val i = (sel + depth) % n
            val c = courses[i]
            key(c.id) {
                val dx = remember { Animatable(0f) }
                val lift by animateFloatAsState(depth.toFloat(), spring(0.7f, 300f), label = "lift")
                fun fling(dir: Int) = scope.launch {
                    // dir = +1 左滑（下一门），-1 右滑（上一门）
                    dx.animateTo(-dir * 1300f, spring(1f, 260f))
                    onSelect(ClassPick.step(sel, n, dir))
                    dx.snapTo(0f)
                }
                Box(Modifier.fillMaxWidth().height(290.dp)
                    .graphicsLayer {
                        translationY = lift * 18.dp.toPx(); scaleX = 1f - lift * 0.06f; scaleY = 1f - lift * 0.06f
                        translationX = dx.value; rotationZ = dx.value / 45f
                        alpha = if (depth == 2) 0.85f else 1f
                    }
                    .shadow(if (depth == 0) 16.dp else 5.dp, RoundedCornerShape(30.dp), clip = false)
                    .clip(RoundedCornerShape(30.dp)).background(Color.White)
                    .then(if (depth == 0 && n > 1) Modifier.pointerInput(sel) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { ch, d -> ch.consume(); scope.launch { dx.snapTo(dx.value + d) } },
                            onDragEnd = { if (abs(dx.value) > 220f) fling(if (dx.value < 0) 1 else -1) else scope.launch { dx.animateTo(0f, spring(0.55f, 380f)) } })
                    } else Modifier)
                    .clickable(enabled = depth == 0) { Nav.push(Route.CourseDetail(c.id)) }
                ) {
                    if (depth <= 1) DeckCard(c, i, n)
                }
            }
        }
    }
}

@Composable
private fun DeckCard(c: Course, i: Int, n: Int) {
    val lectures = Store.lectures.count { it.courseId == c.id }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            CourseCover(c, Modifier.fillMaxWidth().height(150.dp), radius = 22.dp)
            BasicText("${i + 1} / $n", Modifier.align(Alignment.TopEnd).padding(10.dp).clip(Capsule()).background(Color.Black.copy(alpha = 0.3f))
                .padding(horizontal = 10.dp, vertical = 3.dp), style = T.Cap.copy(color = Color.White, fontWeight = FontWeight.Bold))
        }
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp)) {
            BasicText(c.name, style = T.Title.copy(fontSize = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            BasicText(listOf(c.teacher, Schedule.courseSlotLabel(c), c.room).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未排课" },
                style = T.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(if (c.slideFile.isNotBlank()) "课件 ${c.slides.size} 页" else "未传课件", if (c.slideFile.isNotBlank()) C.Blue else C.Sec)
                Tag("$lectures 节笔记", C.Green)
                if (c.hotwords.isNotEmpty()) Tag("${c.hotwords.size} 个热词", C.Orange)
            }
        }
    }
}

// =================== 课前资料（某门课） ===================
@Composable
fun MaterialsScreen(courseId: String) {
    val c = Store.course(courseId) ?: run { Nav.pop(); return }
    val ctx = LocalContext.current
    val scope = app.xuezhang.engine.Bg.scope
    var busy by app.xuezhang.engine.keep("materials.busy:$courseId") { false }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
            val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME); cur.moveToFirst(); if (i >= 0) cur.getString(i) else null
        } ?: "课件"
        busy = true
        scope.launch {
            try {
                val r = SlideImport.run(ctx, uri, name, courseId)
                Store.course(courseId)?.let { cur -> Store.upsertCourse(cur.copy(hotwords = (cur.hotwords + r.hot).distinct(), slides = r.slides, slideFile = r.name, slidePath = r.local)) }
                Island.show("课件解析完成 · ${r.pages} 页 · ${r.hot.size} 个热词", Island.Kind.Done)
            } catch (e: Exception) { Island.show("解析失败：${e.message?.take(30)}", Island.Kind.Warn) } finally { busy = false }
        }
    }
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
            item { TopInset(); PageHeader("课前资料", c.name) }
            item { SectionLabel("课件 PPT / PDF") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable(enabled = !busy) {
                    picker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                }, radius = 22.dp) {
                    if (busy) AiProgressBar(aiJob(SlideImport.key(courseId)))
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon("pres", C.Orange, 24.dp, 2.1f); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(if (c.slideFile.isBlank()) "上传 PPT / PDF" else c.slideFile, style = T.Btn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(if (c.slideFile.isBlank()) "学长提前读一遍：页码、热词、公式都会识别" else "${c.slides.size} 页有内容 · 点按更换", style = T.Cap)
                        }
                        if (c.slideFile.isNotBlank()) Icon("check", C.Green, 18.dp, 2.6f) else Icon("upload", C.Blue, 20.dp, 2.2f)
                    }
                }
            }
            item { SectionLabel("录播课 · 生成图文笔记（视频课配原画面截图）") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(0.dp), radius = 22.dp) {
                    Row(Modifier.fillMaxWidth().clickable { Nav.push(Route.ImportBili(courseId, 0)) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon("tv", Color(0xFFFB7299), 22.dp, 2.1f); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText("B 站视频（BV 号 / 链接）", style = T.Btn)
                            BasicText("只下载音轨识别；笔记每节配视频原画面", style = T.Cap)
                        }
                        Icon("chev", C.Ter, 14.dp)
                    }
                    Divider(50.dp)
                    Row(Modifier.fillMaxWidth().clickable { Nav.push(Route.ImportBili(courseId, 1)) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon("mic", C.Blue, 22.dp, 2.1f); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText("本地音频 / 视频", style = T.Btn)
                            BasicText("手机录音、课程录像都可以", style = T.Cap)
                        }
                        Icon("chev", C.Ter, 14.dp)
                    }
                }
            }
            if (c.hotwords.isNotEmpty()) {
                item { SectionLabel("识别热词 · 点按删除") }
                item { HotwordChips(c.hotwords) { w -> Store.course(courseId)?.let { Store.upsertCourse(it.copy(hotwords = it.hotwords - w)) } } }
            }
        }
    }, overlay = { b -> BackBar(b) })
}
