package app.xuezhang.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import app.xuezhang.engine.HardCovers
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

// =================== 上课 Tab v0.0.7.6（按概念图 02-class-picker 重做）===================
private val ClsRed0 = Color(0xFFF2554A)
private val ClsRed1 = Color(0xFFD7261E)
private val ClsJade = Color(0xFF2F7A5C)

@Composable
fun ClassTab() {
    val start = rememberStartClass()
    val courses = Store.courses
    val n = courses.size
    if (ClassPick.index.intValue !in 0 until n) {
        val cal = Schedule.clock()
        val next = Schedule.next(Schedule.today(courses, Schedule.isoWeekday(cal)), cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE))
        ClassPick.index.intValue = next?.let { t -> courses.indexOfFirst { it.id == t.course.id } }?.takeIf { it >= 0 } ?: 0
    }
    val sel = ClassPick.index.intValue.coerceIn(0, maxOf(0, n - 1))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        TopInset()
        if (Live.running.value) LiveBanner()
        Spacer(Modifier.height(if (Live.running.value) 6.dp else 34.dp))
        BasicText(if (n > 1) "左右滑动选一门课" else if (n == 1) "今天上这门课" else "先添加一门课", style = T.Sub.copy(fontSize = 15.sp, color = Color(0xFF8E8A84)))
        Spacer(Modifier.height(2.dp))
        BasicText("上课", style = T.Large.copy(fontSize = 54.sp, lineHeight = 62.sp, fontWeight = FontWeight.Black))
        Spacer(Modifier.height(14.dp))
        if (n == 0) {
            EmptyHint("还没有课程", "先新建一门课，或拍照导入课程表")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("新建课程", { Nav.draft.value = null; Nav.push(Route.EditCourse(null)) }, Modifier.weight(1f), icon = "plus", tint = C.Ink)
                Btn("导入课程表", { Nav.push(Route.ImportTimetable) }, Modifier.weight(1f), icon = "scan")
            }
        } else {
            val c = courses[sel]
            ClassCardStack(courses, sel) { ClassPick.index.intValue = it }
            Spacer(Modifier.height(18.dp))
            PagerDots(n, sel)
            Spacer(Modifier.height(26.dp))
            // 资料上传（玻璃胶囊）
            Row(Modifier.width(220.dp).height(54.dp)
                .shadow(10.dp, Capsule(), ambientColor = Color(0x22000000), spotColor = Color(0x22000000)).clip(Capsule())
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.96f), Color(0xFFF6F5F2).copy(alpha = 0.92f))))
                .border(1.dp, Color.White, Capsule())
                .clickable { Nav.push(Route.Materials(c.id)) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon("upload", C.Ink, 21.dp, 2.2f); Spacer(Modifier.width(12.dp))
                BasicText("资料上传", style = T.Btn.copy(fontSize = 19.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(Modifier.height(18.dp))
            // 开始上课（红色光泽胶囊 + 红色光晕）
            Box(Modifier.width(272.dp).height(72.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(262.dp).height(64.dp).clip(Capsule()).background(Brush.radialGradient(listOf(ClsRed0.copy(alpha = 0.30f), Color.Transparent))))
                Row(Modifier.width(250.dp).height(58.dp)
                    .shadow(18.dp, Capsule(), ambientColor = ClsRed1.copy(alpha = 0.55f), spotColor = ClsRed1.copy(alpha = 0.75f)).clip(Capsule())
                    .background(Brush.verticalGradient(listOf(ClsRed0, ClsRed1)))
                    .border(3.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color(0xFFFFC9C4).copy(alpha = 0.6f))), Capsule())
                    .drawWithContent {
                        drawContent()
                        // 顶部高光
                        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.Transparent), endY = size.height * 0.55f),
                            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.06f, size.height * 0.08f),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.88f, size.height * 0.42f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
                    }
                    .clickable { start(c) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon("mic", Color.White, 22.dp, 2.4f); Spacer(Modifier.width(10.dp))
                    BasicText(if (Live.running.value) "回到课堂" else "开始上课", style = T.Btn.copy(color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold))
                }
            }
            Spacer(Modifier.height(10.dp))
            BasicText("录音不足 30 秒会当作误触自动作废", style = T.Cap.copy(fontSize = 13.sp, color = Color(0xFF9A968F), textAlign = TextAlign.Center))
        }
        BottomSpacer()
    }
}

/** 页码点：当前页是一段拉长的胶囊。 */
@Composable
private fun PagerDots(n: Int, sel: Int) {
    val shown = minOf(n, 7)
    val first = (sel - shown / 2).coerceIn(0, n - shown)
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in first until first + shown) {
            val on = i == sel
            val w by androidx.compose.animation.core.animateDpAsState(if (on) 34.dp else 8.dp, spring(0.8f, 400f), label = "dot")
            val edge = (i == first && first > 0) || (i == first + shown - 1 && first + shown < n)
            Box(Modifier.width(w).height(if (on) 8.dp else if (edge) 5.dp else 8.dp).clip(Capsule())
                .background(if (on) Color(0xFF8C8C92) else Color(0xFFC9C8CC)))
        }
    }
}

/** 居中一张 + 左右各露出一张倾斜的课程卡；左右拖动换课。 */
@Composable
private fun ClassCardStack(courses: List<Course>, sel: Int, onSelect: (Int) -> Unit) {
    val n = courses.size
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(318.dp), contentAlignment = Alignment.TopCenter) {
        val cardW = minOf(maxWidth * 0.56f, 236.dp)
        val cardH = 306.dp
        val wPx = with(androidx.compose.ui.platform.LocalDensity.current) { cardW.toPx() }
        val p = (dx.value / wPx).coerceIn(-1f, 1f) // <0 向左拖（下一门）
        fun go(dir: Int) = scope.launch {
            dx.animateTo(-dir * wPx * 1.1f, spring(1f, 420f))
            onSelect(ClassPick.step(sel, n, dir)); dx.snapTo(0f)
        }
        val sides = if (n >= 3) listOf(-1, 1) else if (n == 2) listOf(1) else emptyList()
        sides.forEach { side ->
            val i = ClassPick.step(sel, n, side)
            // 向这一侧拖时，这张往中间挪
            val toward = if (side == 1) (-p).coerceAtLeast(0f) else p.coerceAtLeast(0f)
            key("side$side${courses[i].id}") {
                Box(Modifier.size(cardW, cardH).graphicsLayer {
                    val base = side * wPx * 0.70f
                    translationX = base * (1f - toward); translationY = 26.dp.toPx() * (1f - toward)
                    rotationZ = side * 7f * (1f - toward); val sc = 0.86f + 0.14f * toward; scaleX = sc; scaleY = sc
                    alpha = 0.9f
                }.clickable { go(side) }) { ClassCard(courses[i], i, n, dim = 1f - toward) }
            }
        }
        key("center${courses[sel].id}") {
            Box(Modifier.size(cardW, cardH).zIndex(2f).graphicsLayer {
                translationX = dx.value; rotationZ = dx.value / wPx * 9f
                val sc = 1f - 0.10f * kotlin.math.abs(p); scaleX = sc; scaleY = sc
            }.then(if (n > 1) Modifier.pointerInput(sel) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { ch, d -> ch.consume(); scope.launch { dx.snapTo(dx.value + d) } },
                    onDragEnd = { if (abs(dx.value) > wPx * 0.28f) go(if (dx.value < 0) 1 else -1) else scope.launch { dx.animateTo(0f, spring(0.6f, 380f)) } },
                    onDragCancel = { scope.launch { dx.animateTo(0f) } })
            } else Modifier).clickable { Nav.push(Route.CourseDetail(courses[sel].id)) }) {
                ClassCard(courses[sel], sel, n, dim = 0f)
            }
        }
    }
}

@Composable
private fun ClassCard(c: Course, i: Int, n: Int, dim: Float) {
    val notes = Store.lectures.count { it.courseId == c.id }
    val shape = RoundedCornerShape(22.dp)
    val hasSlides = c.slideFile.isNotBlank()
    Column(Modifier.fillMaxSize()
        .shadow(18.dp, shape, ambientColor = Color(0x332A2040), spotColor = Color(0x332A2040)).clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFBFAF8))))
        .border(1.2.dp, Brush.linearGradient(listOf(Color(0xFFCDEFE6), Color.White, Color(0xFFE7D9F5))), shape)
        .padding(9.dp)) {
        Box(Modifier.fillMaxWidth().weight(0.62f).clip(RoundedCornerShape(16.dp))) {
            if (c.cover.startsWith("file:")) CourseCover(c, Modifier.fillMaxSize(), radius = 16.dp)
            else HcCloth(courseHc(c), HardCovers.watermark(c.name), Modifier.fillMaxSize(), iconFrac = 0.5f, iconCenter = androidx.compose.ui.geometry.Offset(0.48f, 0.46f))
            if (n > 1) BasicText("${i + 1} / $n", Modifier.align(Alignment.TopEnd).padding(8.dp).clip(Capsule()).background(Color.White.copy(alpha = 0.88f))
                .padding(horizontal = 10.dp, vertical = 3.dp), style = T.Btn.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D1D1F)))
        }
        Column(Modifier.fillMaxWidth().weight(0.38f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BasicText(c.name, style = T.Title.copy(fontSize = 19.sp, lineHeight = 24.sp, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            BasicText(listOf(c.teacher, Schedule.courseSlotLabel(c), c.room).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未排课" },
                style = T.Sub.copy(fontSize = 13.sp, color = Color(0xFF55524D), textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(if (hasSlides) "课件 1 份" else "未传课件", Modifier.clip(Capsule()).border(1.3.dp, if (hasSlides) ClsJade else C.Line, Capsule())
                    .background(if (hasSlides) ClsJade.copy(alpha = 0.06f) else Color.Transparent).padding(horizontal = 12.dp, vertical = 4.dp),
                    style = T.Btn.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = if (hasSlides) ClsJade else C.Sec))
                BasicText("$notes 本笔记", Modifier.clip(Capsule()).background(Color(0xFFEDEDEF)).padding(horizontal = 12.dp, vertical = 4.dp),
                    style = T.Btn.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Color(0xFF3A3A3C)))
            }
        }
    }
    if (dim > 0f) Box(Modifier.fillMaxSize().clip(shape).background(Color.White.copy(alpha = 0.18f * dim)))
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
