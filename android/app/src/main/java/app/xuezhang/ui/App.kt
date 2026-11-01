package app.xuezhang.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Store
import app.xuezhang.glass.LiquidBottomTab
import app.xuezhang.glass.LiquidBottomTabs
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

sealed interface Route {
    data object Tabs : Route
    data class EditCourse(val id: String?) : Route
    data class CourseDetail(val id: String) : Route
    data object Live : Route
    data class Generating(val lectureId: String) : Route
    data class Note(val lectureId: String) : Route
    data object Search : Route
    data object Courses : Route
    data object ImportTimetable : Route
    data class ImportBili(val courseId: String? = null, val mode: Int = 0) : Route // mode 0 B 站链接 1 本地音频/视频
    /** target = 课程 id，或 "draft" 表示正在新建/编辑的草稿 */
    data class CoverPicker(val target: String) : Route
    data object Login : Route
    data object Logs : Route
    data object Prompts : Route
    data object Drive : Route
    data class Book(val lectureId: String) : Route
    data class Materials(val courseId: String) : Route
    // 重构版 v0.0.1Ex 新页面
    data class EndClass(val lectureId: String) : Route          // 11 下课
    data class MindMap(val lectureId: String) : Route           // 17 概念图
    data class Cards(val lectureId: String) : Route             // 18 复习卡
    data class Ask(val lectureId: String, val q: String = "") : Route // 19 问学长
    data class Share(val lectureId: String) : Route             // 20 分享导出
    data object Models : Route                                   // 23 模型与供应商
    data object GoogleConnect : Route                            // 26 Google 授权
}

object Nav {
    val stack = mutableStateListOf<Route>(Route.Tabs)
    val tab = mutableIntStateOf(0)
    val sheet = mutableStateOf<Sheet?>(null)
    /** 新建/编辑课程的草稿：跳到选择封面、导入等页面时不丢失 */
    val draft = mutableStateOf<app.xuezhang.data.Course?>(null)
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replace(r: Route) { if (stack.size > 1) stack[stack.lastIndex] = r else stack.add(r) }
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
    /** 打开某页：已经在栈里就退回到它，否则压栈。 */
    fun open(r: Route) { val i = stack.lastIndexOf(r); if (i >= 0) { while (stack.size > i + 1) stack.removeAt(stack.lastIndex) } else push(r) }
    /** 回到任务发起页（灵动岛点按）：Tabs 页还要切回当时的 tab。 */
    fun resume(r: Route, tab: Int = 0) { if (r == Route.Tabs) { home(); this.tab.intValue = tab } else open(r) }
    /** 切到某个底部 tab（id，不是位置）。 */
    fun tabTo(id: Int) { home(); tab.intValue = id }
    /** 笔记页：按课程筛选（课程详情 › 这门课的笔记）。 */
    val libFilter = mutableStateOf<String?>(null)
}

sealed interface Sheet {
    data class Formula(val tex: String, val context: String = "") : Sheet
    data class Fill(val word: String, val raw: String) : Sheet
    data class Answer(val question: String, val lectureId: String) : Sheet
    data class Model(val kind: Int) : Sheet // 0 语音 1 大模型
    data object Sync : Sheet
    data class Frames(val lectureId: String, val secIdx: Int) : Sheet
}

/** 内容层（参与玻璃折射） + 浮层（玻璃控件）。 */
@Composable
fun GlassScaffold(content: @Composable BoxScope.() -> Unit, overlay: @Composable BoxScope.(LayerBackdrop) -> Unit) {
    val bg = rememberLayerBackdrop()
    val front = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(front)) {
            Ambient(Modifier.fillMaxSize().layerBackdrop(bg))
            androidx.compose.runtime.CompositionLocalProvider(LocalBg provides bg) { content() }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalBg provides front) { overlay(front) }
    }
}

@Composable
fun App() {
    val top = Nav.stack.last()
    BackHandler(enabled = Nav.sheet.value != null || Nav.stack.size > 1 || SlideViewer.open.value != null) {
        when {
            SlideViewer.open.value != null -> SlideViewer.open.value = null
            Nav.sheet.value != null -> Nav.sheet.value = null
            else -> Nav.pop()
        }
    }
    val appB = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Ambient(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().layerBackdrop(appB)) {
        AnimatedContent(
            targetState = top,
            transitionSpec = {
                val forward = Nav.stack.contains(initialState)
                if (forward)
                    (slideInHorizontally(tween(320)) { it } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(250)))
                else
                    (slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(320)) { it } + fadeOut(tween(250)))
            },
            label = "nav"
        ) { r ->
            when (r) {
                Route.Tabs -> TabsHost()
                is Route.EditCourse -> EditCourseScreen(r.id)
                is Route.CourseDetail -> CourseDetailScreen(r.id)
                Route.Live -> LiveScreen()
                is Route.Generating -> GeneratingScreen(r.lectureId)
                is Route.Note -> NoteScreen(r.lectureId)
                Route.Search -> SearchScreen()
                Route.Courses -> CoursesScreen()
                Route.ImportTimetable -> ImportTimetableScreen()
                is Route.ImportBili -> ImportBiliScreen(r.courseId, r.mode)
                is Route.CoverPicker -> CoverPickerScreen(r.target)
                Route.Login -> LoginScreen()
                Route.Logs -> LogScreen()
                Route.Prompts -> PromptsScreen()
                Route.Drive -> DriveScreen()
                is Route.Book -> BookScreen(r.lectureId)
                is Route.Materials -> MaterialsScreen(r.courseId)
                is Route.EndClass -> EndClassScreen(r.lectureId)
                is Route.MindMap -> MindMapScreen(r.lectureId)
                is Route.Cards -> CardsScreen(r.lectureId)
                is Route.Ask -> AskScreen(r.lectureId, r.q)
                is Route.Share -> ShareScreen(r.lectureId)
                Route.Models -> ModelsScreen()
                Route.GoogleConnect -> GoogleConnectScreen()
            }
        }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalBg provides appB) { SheetHost() }
        SlideViewerHost()
        androidx.compose.runtime.CompositionLocalProvider(LocalBg provides appB) { SplashHost() }
        IslandHost()
    }
}

@Composable
fun TabsHost() {
    val order = app.xuezhang.engine.NavOrder.parse(Store.settings.value.navOrder)
    GlassScaffold(
        content = {
            when (Nav.tab.intValue) {
                0 -> HomeTab()
                1 -> ClassTab()
                2 -> LibraryTab()
                else -> SettingsTab()
            }
        },
        overlay = { backdrop ->
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 导航栏顺序可在「我的 › 底部导航栏」长按拖动调整；位置 ≠ tab id
                LiquidBottomTabs(
                    selectedTabIndex = { order.indexOf(Nav.tab.intValue).coerceAtLeast(0) },
                    onTabSelected = { Nav.tab.intValue = order[it] },
                    backdrop = backdrop,
                    tabsCount = order.size,
                    modifier = Modifier.weight(1f)
                ) {
                    order.forEach { id ->
                        val on = Nav.tab.intValue == id
                        LiquidBottomTab({ Nav.tab.intValue = id }) {
                            Icon(app.xuezhang.engine.NavOrder.ICONS[id], if (on) C.Jade else C.Ink, 24.dp)
                            BasicText(app.xuezhang.engine.NavOrder.LABELS[id], style = TextStyle(if (on) C.Jade else C.Ink, 10.5.sp, FontWeight.SemiBold))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                GlassCircle(backdrop, "search", { Nav.push(Route.Search) }, size = 64.dp)
            }
        }
    )
}

@Composable
fun BottomSpacer() = Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).size(1.dp, 110.dp))

@Composable
fun EmptyHint(title: String, sub: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(title, style = T.Head)
        BasicText(sub, style = T.Sub)
    }
}
