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
    data class Review(val lectureId: String) : Route
    data class Materials(val courseId: String) : Route
    data object Advanced : Route
    data object Accounts : Route
    data class ImageStudio(val target: String = "") : Route
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
}

sealed interface Sheet {
    data class Formula(val tex: String, val context: String = "") : Sheet
    data class Fill(val word: String, val raw: String) : Sheet
    data class Answer(val question: String, val lectureId: String) : Sheet
    data class Model(val kind: Int) : Sheet // 0 语音 1 大模型
    data object Sync : Sheet
    data class Frames(val lectureId: String, val secIdx: Int) : Sheet
    data class BookMenu(val lectureId: String) : Sheet
    data object CoursePick : Sheet
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
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    ThemeState.dark = when (app.xuezhang.data.Store.settings.value.theme) { 1 -> false; 2 -> true; else -> sysDark }
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
                is Route.Review -> ReviewScreen(r.lectureId)
                is Route.Materials -> MaterialsScreen(r.courseId)
                Route.Advanced -> AdvancedScreen()
                Route.Accounts -> AccountsScreen()
                is Route.ImageStudio -> ImageStudioScreen(r.target)
            }
        }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalBg provides appB) { SheetHost() }
        SlideViewerHost()
        androidx.compose.runtime.CompositionLocalProvider(LocalBg provides appB) { SplashHost() }
        androidx.compose.runtime.CompositionLocalProvider(LocalBg provides appB) { OnboardingHost() }
        IslandHost()
    }
}

private val TABS = listOf("课程" to "book", "上课" to "mic", "笔记" to "note", "我的" to "person")

@Composable
fun TabsHost() {
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
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidBottomTabs(
                    selectedTabIndex = { Nav.tab.intValue },
                    onTabSelected = { Nav.tab.intValue = it },
                    backdrop = backdrop,
                    tabsCount = TABS.size,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TABS.forEachIndexed { i, (label, ic) ->
                        LiquidBottomTab({ Nav.tab.intValue = i }) {
                            Icon(ic, androidx.compose.ui.graphics.Color(0xFF1D1D1F), 24.dp)
                            BasicText(label, style = TextStyle(androidx.compose.ui.graphics.Color(0xFF1D1D1F), 10.5.sp, FontWeight.SemiBold))
                        }
                    }
                }
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
