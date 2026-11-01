package app.xuezhang.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Settings
import app.xuezhang.data.Store
import app.xuezhang.net.Api
import app.xuezhang.net.Auth
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// =================== 模型服务商 ===================
/** 预置服务商（OpenAI 兼容接口）。logo 路径来自 lobehub/lobe-icons（MIT）。 */
object Providers {
    data class P(val id: String, val name: String, val base: String, val asr: String, val llm: String, val vision: String, val image: String,
                 val paths: List<Pair<String, Long>>, val asrOk: Boolean = true,
                 val oauth: String = "",            // 支持的账号登录：chatgpt / claude
                 val evenOdd: Boolean = true, val extra: List<String> = emptyList(), val extraImage: List<String> = emptyList())

    val ALL = listOf(
        P("siliconflow", "SiliconFlow", "https://api.siliconflow.cn/v1", "FunAudioLLM/SenseVoiceSmall", "deepseek-ai/DeepSeek-V3",
            "Qwen/Qwen2.5-VL-72B-Instruct", "Kwai-Kolors/Kolors",
            listOf("M22.956 6.521H12.522c-.577 0-1.044.468-1.044 1.044v3.13c0 .577-.466 1.044-1.043 1.044H1.044c-.577 0-1.044.467-1.044 1.044v4.174C0 17.533.467 18 1.044 18h10.434c.577 0 1.044-.467 1.044-1.043v-3.13c0-.578.466-1.044 1.043-1.044h9.391c.577 0 1.044-.467 1.044-1.044V7.565c0-.576-.467-1.044-1.044-1.044z" to 0xFF6E29F6)),
        P("openai", "OpenAI", "https://api.openai.com/v1", "whisper-1", "gpt-6.1-sol", "gpt-6.1-sol", "gpt-image-2.5-flare",
            listOf("M9.205 8.658v-2.26c0-.19.072-.333.238-.428l4.543-2.616c.619-.357 1.356-.523 2.117-.523 2.854 0 4.662 2.212 4.662 4.566 0 .167 0 .357-.024.547l-4.71-2.759a.797.797 0 00-.856 0l-5.97 3.473zm10.609 8.8V12.06c0-.333-.143-.57-.429-.737l-5.97-3.473 1.95-1.118a.433.433 0 01.476 0l4.543 2.617c1.309.76 2.189 2.378 2.189 3.948 0 1.808-1.07 3.473-2.76 4.163zM7.802 12.703l-1.95-1.142c-.167-.095-.239-.238-.239-.428V5.899c0-2.545 1.95-4.472 4.591-4.472 1 0 1.927.333 2.712.928L8.23 5.067c-.285.166-.428.404-.428.737v6.898zM12 15.128l-2.795-1.57v-3.33L12 8.658l2.795 1.57v3.33L12 15.128zm1.796 7.23c-1 0-1.927-.332-2.712-.927l4.686-2.712c.285-.166.428-.404.428-.737v-6.898l1.974 1.142c.167.095.238.238.238.428v5.233c0 2.545-1.974 4.472-4.614 4.472zm-5.637-5.303l-4.544-2.617c-1.308-.761-2.188-2.378-2.188-3.948A4.482 4.482 0 014.21 6.327v5.423c0 .333.143.571.428.738l5.947 3.449-1.95 1.118a.432.432 0 01-.476 0zm-.262 3.9c-2.688 0-4.662-2.021-4.662-4.519 0-.19.024-.38.047-.57l4.686 2.71c.286.167.571.167.856 0l5.97-3.448v2.26c0 .19-.07.333-.237.428l-4.543 2.616c-.619.357-1.356.523-2.117.523zm5.899 2.83a5.947 5.947 0 005.827-4.756C22.287 18.339 24 15.84 24 13.296c0-1.665-.713-3.282-1.998-4.448.119-.5.19-.999.19-1.498 0-3.401-2.759-5.947-5.946-5.947-.642 0-1.26.095-1.88.31A5.962 5.962 0 0010.205 0a5.947 5.947 0 00-5.827 4.757C1.713 5.447 0 7.945 0 10.49c0 1.666.713 3.283 1.998 4.448-.119.5-.19 1-.19 1.499 0 3.401 2.759 5.946 5.946 5.946.642 0 1.26-.095 1.88-.309a5.96 5.96 0 004.162 1.713z" to 0xFF1D1D1F), oauth = "chatgpt",
            extraImage = listOf("gpt-image-2.5-flare", "gpt-image-2.5-sunburst", "gpt-image-2")),
        P("aliyun", "阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", "", "qwen-plus", "qwen-vl-plus", "",
            listOf("M14.752 4.64h5.274C22.242 4.64 24 6.475 24 8.691V15.8a3.947 3.947 0 01-3.974 3.975h-5.274l1.299-1.835 3.822-1.222c.688-.23 1.146-.918 1.146-1.605v-5.81c0-.687-.458-1.375-1.146-1.605L16.05 6.475l-1.3-1.835zM2.98 15.111c0 .688.46 1.376 1.147 1.606l3.822 1.146 1.3 1.835H3.974A3.947 3.947 0 010 15.723V8.69c0-2.216 1.758-4.05 3.975-4.05h5.273L7.95 6.474 4.127 7.697c-.688.23-1.146.918-1.146 1.606v5.808z" to 0xFFFF6A00,
                "M16.051 11.213H8.025v1.835h8.026v-1.835z" to 0xFFFF6A00), asrOk = false),
        P("volcengine", "火山引擎", "https://ark.cn-beijing.volces.com/api/v3", "", "deepseek-v3-250324", "doubao-1-5-vision-pro-32k-250115", "",
            listOf("M19.44 10.153l-2.936 11.586a.215.215 0 00.214.261h5.87a.215.215 0 00.214-.261l-2.95-11.586a.214.214 0 00-.412 0zM3.28 12.778l-2.275 8.96A.214.214 0 001.22 22h4.532a.212.212 0 00.214-.165.214.214 0 000-.097l-2.276-8.96a.214.214 0 00-.41 0z" to 0xFF00E5E5,
                "M7.29 5.359L3.148 21.738a.215.215 0 00.203.261h8.29a.214.214 0 00.215-.261L7.7 5.358a.214.214 0 00-.41 0z" to 0xFF006EFF,
                "M14.44.15a.214.214 0 00-.41 0L8.366 21.739a.214.214 0 00.214.261H19.9a.216.216 0 00.171-.078.214.214 0 00.044-.183L14.439.15z" to 0xFF006EFF,
                "M10.278 7.741L6.685 21.736a.214.214 0 00.214.264h7.17a.215.215 0 00.214-.264L10.688 7.741a.214.214 0 00-.41 0z" to 0xFF00E5E5), asrOk = false),
        // DeepSeek 官方：deepseek-flash（V4.1-Flash，可看图）与 deepseek-v4-pro；没有语音识别接口
        P("deepseek", "DeepSeek", "https://api.deepseek.com", "", "deepseek-v4-pro", "deepseek-flash", "",
            listOf("M23.748 4.482c-.254-.124-.364.113-.512.234-.051.039-.094.09-.137.136-.372.397-.806.657-1.373.626-.829-.046-1.537.214-2.163.848-.133-.782-.575-1.248-1.247-1.548-.352-.156-.708-.311-.955-.65-.172-.241-.219-.51-.305-.774-.055-.16-.11-.323-.293-.35-.2-.031-.278.136-.356.276-.313.572-.434 1.202-.422 1.84.027 1.436.633 2.58 1.838 3.393.137.093.172.187.129.323-.082.28-.18.552-.266.833-.055.179-.137.217-.329.14a5.526 5.526 0 01-1.736-1.18c-.857-.828-1.631-1.742-2.597-2.458a11.365 11.365 0 00-.689-.471c-.985-.957.13-1.743.388-1.836.27-.098.093-.432-.779-.428-.872.004-1.67.295-2.687.684a3.055 3.055 0 01-.465.137 9.597 9.597 0 00-2.883-.102c-1.885.21-3.39 1.102-4.497 2.623C.082 8.606-.231 10.684.152 12.85c.403 2.284 1.569 4.175 3.36 5.653 1.858 1.533 3.997 2.284 6.438 2.14 1.482-.085 3.133-.284 4.994-1.86.47.234.962.327 1.78.397.63.059 1.236-.03 1.705-.128.735-.156.684-.837.419-.961-2.155-1.004-1.682-.595-2.113-.926 1.096-1.296 2.746-2.642 3.392-7.003.05-.347.007-.565 0-.845-.004-.17.035-.237.23-.256a4.173 4.173 0 001.545-.475c1.396-.763 1.96-2.015 2.093-3.517.02-.23-.004-.467-.247-.588zM11.581 18c-2.089-1.642-3.102-2.183-3.52-2.16-.392.024-.321.471-.235.763.09.288.207.486.371.739.114.167.192.416-.113.603-.673.416-1.842-.14-1.897-.167-1.361-.802-2.5-1.86-3.301-3.307-.774-1.393-1.224-2.887-1.298-4.482-.02-.386.093-.522.477-.592a4.696 4.696 0 011.529-.039c2.132.312 3.946 1.265 5.468 2.774.868.86 1.525 1.887 2.202 2.891.72 1.066 1.494 2.082 2.48 2.914.348.292.625.514.891.677-.802.09-2.14.11-3.054-.614zm1-6.44a.306.306 0 01.415-.287.302.302 0 01.2.288.306.306 0 01-.31.307.303.303 0 01-.304-.308zm3.11 1.596c-.2.081-.399.151-.59.16a1.245 1.245 0 01-.798-.254c-.274-.23-.47-.358-.552-.758a1.73 1.73 0 01.016-.588c.07-.327-.008-.537-.239-.727-.187-.156-.426-.199-.688-.199a.559.559 0 01-.254-.078c-.11-.054-.2-.19-.114-.358.028-.054.16-.186.192-.21.356-.202.767-.136 1.146.016.352.144.618.408 1.001.782.391.451.462.576.685.914.176.265.336.537.445.848.067.195-.019.354-.25.452z" to 0xFF4D6BFE), asrOk = false, evenOdd = false, extra = listOf("deepseek-flash", "deepseek-v4-pro")),
        P("claude", "Claude", "https://api.anthropic.com/v1", "", "claude-sonnet-4-5", "claude-sonnet-4-5", "",
            listOf("M4.709 15.955l4.72-2.647.08-.23-.08-.128H9.2l-.79-.048-2.698-.073-2.339-.097-2.266-.122-.571-.121L0 11.784l.055-.352.48-.321.686.06 1.52.103 2.278.158 1.652.097 2.449.255h.389l.055-.157-.134-.098-.103-.097-2.358-1.596-2.552-1.688-1.336-.972-.724-.491-.364-.462-.158-1.008.656-.722.881.06.225.061.893.686 1.908 1.476 2.491 1.833.365.304.145-.103.019-.073-.164-.274-1.355-2.446-1.446-2.49-.644-1.032-.17-.619a2.97 2.97 0 01-.104-.729L6.283.134 6.696 0l.996.134.42.364.62 1.414 1.002 2.229 1.555 3.03.456.898.243.832.091.255h.158V9.01l.128-1.706.237-2.095.23-2.695.08-.76.376-.91.747-.492.584.28.48.685-.067.444-.286 1.851-.559 2.903-.364 1.942h.212l.243-.242.985-1.306 1.652-2.064.73-.82.85-.904.547-.431h1.033l.76 1.129-.34 1.166-1.064 1.347-.881 1.142-1.264 1.7-.79 1.36.073.11.188-.02 2.856-.606 1.543-.28 1.841-.315.833.388.091.395-.328.807-1.969.486-2.309.462-3.439.813-.042.03.049.061 1.549.146.662.036h1.622l3.02.225.79.522.474.638-.079.485-1.215.62-1.64-.389-3.829-.91-1.312-.329h-.182v.11l1.093 1.068 2.006 1.81 2.509 2.33.127.578-.322.455-.34-.049-2.205-1.657-.851-.747-1.926-1.62h-.128v.17l.444.649 2.345 3.521.122 1.08-.17.353-.608.213-.668-.122-1.374-1.925-1.415-2.167-1.143-1.943-.14.08-.674 7.254-.316.37-.729.28-.607-.461-.322-.747.322-1.476.389-1.924.315-1.53.286-1.9.17-.632-.012-.042-.14.018-1.434 1.967-2.18 2.945-1.726 1.845-.414.164-.717-.37.067-.662.401-.589 2.388-3.036 1.44-1.882.93-1.086-.006-.158h-.055L4.132 18.56l-1.13.146-.487-.456.061-.746.231-.243 1.908-1.312-.006.006z" to 0xFFD97757), asrOk = false, oauth = "claude", evenOdd = false),
    )

    fun byBase(base: String): P? = ALL.firstOrNull { p -> base.contains(p.base.removePrefix("https://").substringBefore('/')) }
    fun nameOf(base: String): String = byBase(base)?.name ?: runCatching { java.net.URI(base).host?.removePrefix("api.") ?: "自定义" }.getOrDefault("自定义")

    private val cache = HashMap<String, List<Pair<Path, Color>>>()
    fun paths(p: P): List<Pair<Path, Color>> = cache.getOrPut(p.id) {
        p.paths.map { (d, c) -> PathParser().parsePathString(d).toPath().apply { fillType = if (p.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero } to Color(c) }
    }
}

@Composable
fun ProviderLogo(base: String, size: Dp, modifier: Modifier = Modifier) {
    val p = Providers.byBase(base)
    if (p == null || p.paths.isEmpty()) {
        Box(modifier.size(size).clip(RoundedCornerShape(size / 4)).background(C.Blue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            BasicText(Providers.nameOf(base).take(1).uppercase(), style = T.Head.copy(color = C.Blue, fontSize = (size.value * 0.42f).sp))
        }
        return
    }
    val ps = remember(p.id) { Providers.paths(p) }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        withTransform({ scale(s, s, Offset.Zero) }) { ps.forEach { (path, c) -> drawPath(path, c) } }
    }
}

// =================== 我的 ===================
object MeState {
    /** 最近一次测试结果：0 语音 1 大模型 */
    val result = mutableStateListOf("", "")
    val ok = mutableStateListOf(true, true)
    val testing = mutableStateOf(-1)
    val balance = mutableStateOf<String?>(null)
    val streamText = mutableStateOf("")
}

@Composable
fun SettingsAdvancedList() {
    val s = Store.settings.value
    fun set(n: Settings) { Store.updateSettings(n) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val lectures = Store.lectures
    LaunchedEffect(s.llmKey, s.llmBase) { MeState.balance.value = withContext(Dispatchers.IO) { Api.balance() } }

    fun runTest(target: Int) = meRunTest(target, scope, ctx)
    fun runTestOld(target: Int) {
        if (MeState.testing.value >= 0) return
        MeState.testing.value = target; MeState.streamText.value = ""
        scope.launch {
            try {
                if (target == 1) {
                    val b = withContext(Dispatchers.IO) { Api.benchChat("用一句话介绍变压器的工作原理，不超过 40 字。") { MeState.streamText.value = it } }
                    MeState.result[1] = "%.0f tok/s".format(b.tokPerSec); MeState.ok[1] = true
                    Island.show("大模型正常 · 首字 ${"%.2f".format(b.firstMs / 1000.0)}s", Island.Kind.Done)
                } else {
                    val st = Store.settings.value
                    if (st.asrKey.isBlank()) throw IllegalStateException("先填写语音识别 API Key")
                    val f = File(ctx.cacheDir, "asr_test.mp3")
                    withContext(Dispatchers.IO) { Api.speech("今天我们讲变压器的工作原理。", f) }
                    val t0 = System.currentTimeMillis()
                    val text = withContext(Dispatchers.IO) { Api.transcribe(f) }
                    val dt = (System.currentTimeMillis() - t0) / 1000.0
                    MeState.streamText.value = "识别结果：$text"
                    MeState.result[0] = "%.2fs".format(dt); MeState.ok[0] = true
                    Island.show("语音识别正常 · ${"%.2f".format(dt)}s", Island.Kind.Done)
                }
            } catch (e: Exception) {
                MeState.result[target] = "失败"; MeState.ok[target] = false
                MeState.streamText.value = e.message?.take(120) ?: "测试失败"
                Island.show("测试失败", Island.Kind.Warn)
            } finally { MeState.testing.value = -1 }
        }
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item { TopInset(); PageHeader("全部设置", "模型、转写、账号与数据") }
        item { AccountCard(s.account, lectures.size, lectures.sumOf { it.durationMs }) }
        item { SectionLabel("模型") }
        item {
            PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), radius = 30.dp, padding = PaddingValues(14.dp)) {
                Row {
                    // 左 1/4：服务商
                    Column(Modifier.weight(0.27f).padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(Providers.nameOf(s.llmBase), style = T.Btn.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.size(64.dp).shadow(6.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                            .clip(RoundedCornerShape(18.dp)).background(Color.White).clickable { Nav.sheet.value = Sheet.Model(1) }, contentAlignment = Alignment.Center) {
                            ProviderLogo(s.llmBase, 36.dp)
                        }
                        Spacer(Modifier.height(8.dp))
                        val connected = Api.llmReady()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(Capsule()).background(if (connected) C.Green else C.Ter)); Spacer(Modifier.width(4.dp))
                            BasicText(when { !connected -> "未配置"; s.llmAuth == "chatgpt" -> "ChatGPT 账号"; s.llmAuth == "claude" -> "Claude 账号"; else -> "已填 Key" }, style = T.Cap.copy(fontSize = 11.5.sp))
                        }
                        MeState.balance.value?.let { BasicText("余额 $it", style = T.Cap.copy(fontSize = 11.sp)) }
                    }
                    Spacer(Modifier.width(10.dp))
                    // 右：两个模型胶囊
                    Column(Modifier.weight(0.73f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModelChip("mic", "语音识别", s.asrModel.substringAfterLast('/'), MeState.result[0], MeState.ok[0], MeState.testing.value == 0) { Nav.sheet.value = Sheet.Model(0) }
                        ModelChip("box", "大模型", s.llmModel.substringAfterLast('/'), MeState.result[1], MeState.ok[1], MeState.testing.value == 1) { Nav.sheet.value = Sheet.Model(1) }
                    }
                }
                if ((s.llmAuth == "chatgpt" && app.xuezhang.net.Auth.chatgpt.value != null) || (s.llmAuth == "claude" && app.xuezhang.net.Auth.claude.value != null)) {
                    Spacer(Modifier.height(12.dp))
                    QuotaBars(s.llmAuth)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        val st = MeState.streamText.value
                        if (st.isNotBlank()) BasicText(st, style = T.Cap.copy(fontSize = 12.sp, color = C.Ink, lineHeight = 17.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        else BasicText("点闪电测试${if (s.testTarget == 0) "语音识别" else "大模型"}，长按切换测试对象\n长按麦克风或方块图标换模型", style = T.Cap.copy(fontSize = 12.sp, lineHeight = 17.sp))
                    }
                    Spacer(Modifier.width(8.dp))
                    ZapButton(s.testTarget, MeState.testing.value >= 0, onTap = { runTest(s.testTarget) }, onLong = {
                        val n = 1 - s.testTarget; set(s.copy(testTarget = n))
                        Island.show("闪电改为测试${if (n == 0) "语音识别" else "大模型"}", Island.Kind.Info)
                    })
                }
            }
        }
        item { SectionLabel("转写与理解") }
        item {
            PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
                Row2("默认模式", "", trailing = {
                    LiquidSegmented(items = listOf("原声", "学长"), selected = s.defaultMode, onSelect = { set(Store.settings.value.copy(defaultMode = it)) }, modifier = Modifier.width(132.dp), height = 36.dp)
                }); Divider(18.dp)
                Row2("笔记详略", "", trailing = {
                    LiquidSegmented(items = listOf("简单", "详细"), selected = s.noteDepth, onSelect = { set(Store.settings.value.copy(noteDepth = it)) }, modifier = Modifier.width(132.dp), height = 36.dp)
                }); Divider(18.dp)
                Row2("模型提示词", "预览 / 修改", onClick = { Nav.push(Route.Prompts) }); Divider(18.dp)
                Row2("上下文补全（需确认）", "", trailing = { Toggle(s.confirmFill) { set(Store.settings.value.copy(confirmFill = it)) } }); Divider(18.dp)
                Row2("分段时长", "", trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10, 15, 20).forEach { n ->
                            BasicText("${n}s", Modifier.clip(Capsule()).background(if (s.chunkSec == n) C.Blue else C.Fill).clickable { set(Store.settings.value.copy(chunkSec = n)) }
                                .padding(horizontal = 12.dp, vertical = 6.dp), style = T.Btn.copy(fontSize = 13.sp, color = if (s.chunkSec == n) Color.White else C.Ink))
                        }
                    }
                })
            }
        }
        item { SectionLabel("账号与数据") }
        item {
            PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
                Row2("云同步课程与笔记", if (s.lastSync > 0) "上次 " + java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(s.lastSync)) else "云盘 / WebDAV", onClick = { Nav.sheet.value = Sheet.Sync })
                Divider(18.dp)
                Row2("数据位置", "仅本机 · ${lectures.size} 节笔记")
                Divider(18.dp)
                Row2("日志", "${app.xuezhang.engine.Log.all().count { !it.ok }} 条错误", onClick = { Nav.push(Route.Logs) }); Divider(18.dp)
                Row2("再看一次更新公告", "", onClick = { Splash.visible.value = true })
                if (s.account.isNotBlank()) {
                    Divider(18.dp)
                    Row2(if (s.account == "guest") "登录账号" else "退出登录", "", onClick = {
                        if (s.account == "guest") Nav.push(Route.Login) else { set(Store.settings.value.copy(account = "guest")); Island.show("已退出，继续在本机使用", Island.Kind.Info) }
                    })
                }
            }
        }
        item { SectionLabel("标注说明") }
        item {
            PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                RichText("[b:蓝色] 概念、定义\n[r:浅红] 考点、必考\n[o:橙色] 助记、易错\n[u:下划线] 老师强调\n[f:灰色虚线] 根据上下文补全的字\n公式：\$\\frac{\\partial f}{\\partial l}=\\nabla f\\cdot\\vec e\$", style = T.Body.copy(fontSize = 14.5.sp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) { BasicText("课堂学长 v${Splash.versionName(ctx)}", style = T.Cap); Spacer(Modifier.width(6.dp)); BetaPill() }
                BasicText("录音只在本机切片后发送到你填写的接口", style = T.Cap.copy(textAlign = TextAlign.Center))
            }
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun AccountCard(account: String, n: Int, ms: Long) {
    val phone = account.takeIf { it.isNotBlank() && it != "guest" }
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 30.dp, onClick = { if (phone == null) Nav.push(Route.Login) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(Capsule()).background(C.Blue.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                BasicText(if (phone != null) phone.takeLast(2) else "学", style = T.Head.copy(color = C.Blue))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText(when { phone != null -> phone.replace(Regex("(\\d{3})\\d{4}(\\d{4})"), "$1****$2"); account == "guest" -> "本机用户"; else -> "登录 / 注册" }, style = T.Item.copy(fontSize = 19.sp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon("cloud", C.Sec, 14.dp); Spacer(Modifier.width(4.dp))
                    BasicText((if (phone != null) "本机登录 · " else "未登录 · 仅存本机 · ") + "$n 节课 · " + "%.1f 小时".format(ms / 3_600_000.0),
                        style = T.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon("chev", C.Ter, 14.dp)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ModelChip(icon: String, kind: String, model: String, result: String, ok: Boolean, testing: Boolean, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp).shadow(4.dp, Capsule(), ambientColor = Color(0x18000000), spotColor = Color(0x18000000))
        .clip(Capsule()).background(Color.White.copy(alpha = 0.92f)).clickable(onClick = onChange).padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(Capsule()).background(C.Blue.copy(alpha = 0.10f)).pointerInput(Unit) {
            detectTapGestures(onLongPress = { onChange() }, onTap = { onChange() })
        }, contentAlignment = Alignment.Center) { Icon(icon, C.Blue, 21.dp, 2.1f) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(kind, style = T.Cap.copy(fontSize = 11.5.sp))
            BasicText(model.ifBlank { "未选择" }, style = T.Btn.copy(fontSize = 14.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            testing -> BasicText("测试中…", style = T.Cap.copy(color = C.Blue, fontWeight = FontWeight.Bold))
            result.isNotBlank() -> BasicText(result, style = T.Btn.copy(fontSize = 13.sp, color = if (ok) C.Green else C.Red))
        }
    }
}

@Composable
private fun ZapButton(target: Int, busy: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    Box(Modifier.size(48.dp)) {
        Box(Modifier.align(Alignment.Center).size(40.dp).shadow(8.dp, Capsule(), ambientColor = C.Blue.copy(alpha = 0.4f), spotColor = C.Blue.copy(alpha = 0.4f))
            .clip(Capsule()).background(if (busy) C.Blue.copy(alpha = 0.6f) else C.Blue)
            .pointerInput(target, busy) { detectTapGestures(onTap = { if (!busy) onTap() }, onLongPress = { onLong() }) },
            contentAlignment = Alignment.Center) { Icon("zap", Color.White, 19.dp, 2.2f, fill = true) }
        Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(Capsule()).background(Color.White).border(0.5.dp, Color(0x22000000), Capsule()),
            contentAlignment = Alignment.Center) { Icon(if (target == 0) "mic" else "box", C.Blue, 11.dp, 2.4f) }
    }
}

// =================== 模型选择（底部弹层内容） ===================
@Composable
fun ModelSheetContent(kind: Int) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val s0 = Store.settings.value
    var base by remember(kind) { mutableStateOf(if (kind == 0) s0.asrBase else s0.llmBase) }
    var key by remember(kind) { mutableStateOf(if (kind == 0) s0.asrKey else s0.llmKey) }
    var model by remember(kind) { mutableStateOf(if (kind == 0) s0.asrModel else s0.llmModel) }
    var vision by remember(kind) { mutableStateOf(s0.visionModel) }
    var image by remember(kind) { mutableStateOf(s0.imageModel) }
    var auth by remember(kind) { mutableStateOf(if (kind == 0) "key" else s0.llmAuth) }
    var q by remember(kind) { mutableStateOf("") }
    val list = remember(kind) { mutableStateListOf<String>() }
    var loading by remember(kind) { mutableStateOf(false) }
    var err by remember(kind) { mutableStateOf<String?>(null) }
    var showKey by remember(kind) { mutableStateOf(false) }
    val prov = Providers.byBase(base)
    fun fetch() {
        val logged = (auth == "chatgpt" && Auth.chatgpt.value != null) || (auth == "claude" && Auth.claude.value != null)
        if (auth == "key" && key.isBlank()) { err = "先填写 API Key"; return }
        if (auth != "key" && !logged) { list.clear(); err = null; return }
        loading = true; err = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Api.models(base, key, if (kind == 0) "audio" else "text", auth) }
                list.clear(); list.addAll(r)
                if (r.isEmpty()) err = "这个接口没有返回模型列表，可以直接手填模型名"
            } catch (e: Exception) { err = e.message } finally { loading = false }
        }
    }
    LaunchedEffect(kind) { fetch() }
    Column(Modifier.heightIn(max = 660.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (kind == 0) "mic" else "box", C.Blue, 22.dp, 2.1f); Spacer(Modifier.width(8.dp))
            BasicText(if (kind == 0) "语音识别模型" else "大模型", Modifier.weight(1f), style = T.Title)
            Box(Modifier.size(36.dp).clip(Capsule()).background(C.Fill).clickable { Nav.sheet.value = null }, contentAlignment = Alignment.Center) { Icon("x", C.Ink, 16.dp, 2.2f) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Providers.ALL.filter { kind == 1 || (it.id != "deepseek" && it.id != "claude") }.forEach { p ->
                val sel = base.trimEnd('/') == p.base
                Column(Modifier.width(82.dp).clip(RoundedCornerShape(20.dp)).background(if (sel) C.Blue.copy(alpha = 0.08f) else Color.White)
                    .border(if (sel) 2.dp else 0.5.dp, if (sel) C.Blue else Color(0x14000000), RoundedCornerShape(20.dp))
                    .clickable {
                        base = p.base
                        val m = if (kind == 0) p.asr else p.llm
                        if (m.isNotBlank()) model = m
                        if (kind == 1) { if (p.vision.isNotBlank()) vision = p.vision; if (p.image.isNotBlank()) image = p.image }
                        if (p.oauth.isEmpty() || (auth != "key" && auth != p.oauth)) auth = "key"
                        list.clear(); fetch()
                    }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProviderLogo(p.base, 28.dp)
                    Spacer(Modifier.height(6.dp))
                    BasicText(p.name, style = T.Cap.copy(fontSize = 11.5.sp, color = if (sel) C.Blue else C.Ink, fontWeight = FontWeight.SemiBold), maxLines = 1)
                }
            }
        }
        if (kind == 0 && prov?.asrOk == false) BasicText("提示：这家的接口不一定兼容 /audio/transcriptions，语音识别建议用 SiliconFlow 或 OpenAI。",
            Modifier.padding(top = 8.dp), style = T.Cap.copy(color = C.HlOrange))
        // 账号登录 / API Key 切换（OpenAI ↔ ChatGPT 账号，Claude ↔ Claude 账号）
        if (kind == 1 && prov?.oauth?.isNotEmpty() == true) {
            Spacer(Modifier.height(12.dp))
            LiquidSegmented(items = listOf("API Key", if (prov.oauth == "chatgpt") "ChatGPT 账号" else "Claude 账号"),
                selected = if (auth == "key") 0 else 1,
                onSelect = { i ->
                    auth = if (i == 0) "key" else prov.oauth
                    if (auth == "chatgpt" && (model.isBlank() || model.startsWith("gpt-5") || model.startsWith("gpt-4"))) model = Api.CHATGPT_MODELS.first()
                    list.clear(); fetch()
                }, modifier = Modifier.fillMaxWidth(), height = 40.dp)
        }
        Spacer(Modifier.height(12.dp))
        if (auth != "key") {
            AccountBox(auth) { fetch() }
        } else {
            BasicText("接口", style = T.Sub)
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(0.5.dp, Color(0x14000000), RoundedCornerShape(20.dp))) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("key", C.Sec, 17.dp); Spacer(Modifier.width(10.dp))
                    Field(key, { key = it.trim() }, "sk-…", Modifier.weight(1f), style = T.Label.copy(fontSize = 14.5.sp), mask = !showKey)
                    Icon("eye", if (showKey) C.Blue else C.Sec, 18.dp, modifier = Modifier.clickable { showKey = !showKey })
                }
                Divider(14.dp)
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("link", C.Sec, 17.dp); Spacer(Modifier.width(10.dp))
                    Field(base, { base = it.trim() }, "https://…/v1", Modifier.weight(1f), style = T.Label.copy(fontSize = 14.sp, color = C.Sec))
                }
            }
            if (kind == 1) BasicText("复制语音识别的 Key", Modifier.padding(top = 6.dp).clickable { key = Store.settings.value.asrKey }, style = T.Cap.copy(color = C.Blue))
            if (kind == 0) BasicText("复制大模型的 Key", Modifier.padding(top = 6.dp).clickable { key = Store.settings.value.llmKey }, style = T.Cap.copy(color = C.Blue))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(if (list.isEmpty()) "模型" else "从上游获取 · ${list.size} 个可用", Modifier.weight(1f), style = T.Sub)
            BasicText(if (loading) "获取中…" else "刷新", Modifier.clickable(enabled = !loading) { fetch() }, style = T.Btn.copy(fontSize = 13.sp, color = C.Blue))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(44.dp).clip(Capsule()).background(Color.White).border(0.5.dp, Color(0x14000000), Capsule()).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon("search", C.Sec, 16.dp); Spacer(Modifier.width(8.dp))
            Field(q, { q = it }, "搜索或直接填写模型名", Modifier.weight(1f), style = T.Label.copy(fontSize = 14.sp))
            if (q.isNotBlank()) BasicText("使用", Modifier.clickable { model = q.trim() }, style = T.Btn.copy(fontSize = 13.sp, color = C.Blue))
        }
        err?.let { BasicText(it, Modifier.padding(top = 6.dp), style = T.Cap.copy(color = C.Red)) }
        Spacer(Modifier.height(8.dp))
        val all = (listOf(model) + (prov?.extra ?: emptyList()) + list).distinct()
        val shown = all.filter { q.isBlank() || it.contains(q, true) }.take(40)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(0.5.dp, Color(0x14000000), RoundedCornerShape(20.dp))) {
            shown.forEachIndexed { i, m ->
                Row(Modifier.fillMaxWidth().clickable { model = m }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProviderLogo(base, 20.dp); Spacer(Modifier.width(10.dp))
                    BasicText(m, Modifier.weight(1f), style = T.Label.copy(fontSize = 14.sp, color = if (m == model) C.Blue else C.Ink, fontWeight = if (m == model) FontWeight.SemiBold else FontWeight.Normal),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (m == model) Icon("check", C.Blue, 16.dp, 2.6f)
                }
                if (i < shown.lastIndex) Divider(44.dp)
            }
        }
        if (kind == 1) {
            Spacer(Modifier.height(14.dp))
            BasicText("其他用途（同一个接口和登录）", style = T.Sub)
            Spacer(Modifier.height(8.dp))
            val visionOpts = (listOf(vision) + listOfNotNull(prov?.vision?.takeIf { it.isNotBlank() }) + (prov?.extra ?: emptyList()) + list).distinct()
            val imageOpts = (listOf(image) + listOfNotNull(prov?.image?.takeIf { it.isNotBlank() }) + (prov?.extraImage ?: emptyList()) +
                    list.filter { m -> listOf("image", "kolors", "flux", "dall", "sd", "diffusion", "wanx", "seedream", "cogview").any { m.contains(it, true) } }).distinct()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModelSelector("看图", "image", vision, visionOpts, base, Modifier.weight(1f)) { vision = it }
                ModelSelector("画图", "wand", image, imageOpts, base, Modifier.weight(1f)) { image = it }
            }
            if (auth == "claude" || prov?.id == "claude") BasicText("Claude 不能画图，AI 封面请换用其他服务商。", Modifier.padding(top = 6.dp), style = T.Cap.copy(color = C.HlOrange))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(52.dp).clip(Capsule()).background(C.Blue).clickable {
            val cur = Store.settings.value
            val n = if (kind == 0) cur.copy(asrBase = base, asrKey = key, asrModel = model)
            else cur.copy(llmBase = base, llmKey = key, llmModel = model, visionModel = vision.ifBlank { cur.visionModel }, imageModel = image.ifBlank { cur.imageModel },
                provider = Providers.byBase(base)?.id ?: "custom", llmAuth = auth)
            Store.updateSettings(n.copy(testTarget = kind))
            Nav.sheet.value = null
            Island.show("已保存，点闪电测试", Island.Kind.Done)
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon("zap", Color.White, 18.dp, 2.2f, fill = true); Spacer(Modifier.width(6.dp))
            BasicText("保存", style = T.Btn.copy(color = Color.White, fontSize = 16.sp))
        }
    }
}

/** 液态玻璃选择器：只显示当前模型，点开才弹出模型列表。 */
@Composable
private fun ModelSelector(label: String, icon: String, value: String, options: List<String>, base: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        val bg = LocalBg.current
        val inner: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            Box(Modifier.size(30.dp).clip(Capsule()).background(C.Blue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, C.Blue, 16.dp, 2.1f) }
            Column(Modifier.weight(1f)) {
                BasicText(label, style = T.Cap.copy(fontSize = 11.sp))
                BasicText(value.substringAfterLast('/').ifBlank { "未选择" }, style = T.Btn.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (open) "chevu" else "chevd", C.Sec, 14.dp, 2.2f)
        }
        if (bg != null) app.xuezhang.glass.LiquidButton({ open = !open }, bg, Modifier.fillMaxWidth().height(56.dp), surfaceColor = Color.White.copy(alpha = 0.62f), content = inner)
        else Row(Modifier.fillMaxWidth().height(56.dp).clip(Capsule()).background(Color.White).clickable { open = !open }.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = inner)
        if (open) {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.ui.window.Popup(alignment = Alignment.TopCenter, offset = with(d) { androidx.compose.ui.unit.IntOffset(0, 62.dp.roundToPx()) },
                onDismissRequest = { open = false }, properties = androidx.compose.ui.window.PopupProperties(focusable = true)) {
                var q by remember { mutableStateOf("") }
                Column(Modifier.width(280.dp).shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x30000000), spotColor = Color(0x30000000))
                    .clip(RoundedCornerShape(22.dp)).background(Color.White).padding(vertical = 8.dp)) {
                    BasicText("选择「$label」模型", Modifier.padding(horizontal = 14.dp, vertical = 4.dp), style = T.Cap)
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().height(38.dp).clip(Capsule()).background(C.Fill).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon("search", C.Sec, 14.dp); Spacer(Modifier.width(6.dp))
                        Field(q, { q = it }, "搜索或填写模型名", Modifier.weight(1f), style = T.Label.copy(fontSize = 13.5.sp))
                        if (q.isNotBlank()) BasicText("使用", Modifier.clickable { onPick(q.trim()); open = false }, style = T.Btn.copy(fontSize = 13.sp, color = C.Blue))
                    }
                    val shown = options.filter { it.isNotBlank() && (q.isBlank() || it.contains(q, true)) }.take(60)
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        if (shown.isEmpty()) BasicText("没有匹配的模型，可直接填写后点「使用」", Modifier.padding(14.dp), style = T.Cap)
                        shown.forEach { m ->
                            Row(Modifier.fillMaxWidth().clickable { onPick(m); open = false }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProviderLogo(base, 18.dp); Spacer(Modifier.width(10.dp))
                                BasicText(m, Modifier.weight(1f), style = T.Label.copy(fontSize = 13.5.sp, color = if (m == value) C.Blue else C.Ink,
                                    fontWeight = if (m == value) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (m == value) Icon("check", C.Blue, 14.dp, 2.6f)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** ChatGPT / Claude 账号登录区。 */
@Composable
private fun AccountBox(auth: String, onLogged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tok = if (auth == "chatgpt") Auth.chatgpt.value else Auth.claude.value
    var err by remember(auth) { mutableStateOf<String?>(null) }
    var code by remember(auth) { mutableStateOf("") }
    var waitingCode by remember(auth) { mutableStateOf(false) }
    var busy by remember(auth) { mutableStateOf(false) }
    val name = if (auth == "chatgpt") "ChatGPT" else "Claude"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(0.5.dp, Color(0x14000000), RoundedCornerShape(20.dp)).padding(14.dp)) {
        if (tok != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(Capsule()).background(C.Green.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Icon("check", C.Green, 18.dp, 2.6f) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    BasicText("已登录 $name 账号", style = T.Btn.copy(fontSize = 14.5.sp))
                    BasicText(listOf(tok.email, tok.plan.takeIf { it.isNotBlank() }?.let { "$it 套餐" }).filter { !it.isNullOrBlank() }.joinToString(" · ").ifBlank { "令牌已加密保存在本机" },
                        style = T.Cap.copy(fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicText("退出", Modifier.clip(Capsule()).clickable { if (auth == "chatgpt") Auth.logoutChatGPT() else Auth.logoutClaude() }.padding(8.dp),
                    style = T.Btn.copy(fontSize = 13.sp, color = C.Red))
            }
            Spacer(Modifier.height(12.dp))
            QuotaBars(auth)
        } else if (auth == "chatgpt") {
            val state = Auth.gptLoginState.value
            BasicText("和 Codex CLI 一样用 ChatGPT 账号登录，按你的 Plus / Pro 套餐额度使用，不需要 API Key。", style = T.Cap.copy(fontSize = 12.sp, lineHeight = 17.sp))
            Spacer(Modifier.height(10.dp))
            if (state != null) {
                BasicText(state, style = T.Btn.copy(fontSize = 13.5.sp, color = C.Blue))
                BasicText("取消", Modifier.padding(top = 6.dp).clickable { Auth.cancelChatGPT() }, style = T.Btn.copy(fontSize = 13.sp, color = C.Sec))
            } else Btn("用 ChatGPT 账号登录", {
                err = null
                scope.launch {
                    try { withContext(Dispatchers.IO) { Auth.loginChatGPT(ctx) }; Island.show("ChatGPT 账号已登录", Island.Kind.Done); onLogged() }
                    catch (e: Exception) { err = e.message }
                }
            }, Modifier.fillMaxWidth(), icon = "login")
            Spacer(Modifier.height(6.dp))
            BasicText("会在浏览器里打开 OpenAI 登录页，登录后浏览器会跳回 localhost:1455，由课堂学长在本机接收。登录完成前请不要关闭课堂学长。", style = T.Cap.copy(fontSize = 11.sp, lineHeight = 15.sp))
        } else {
            BasicText("用 Claude Pro / Max 账号授权（与 Claude Code 相同），按订阅额度使用。", style = T.Cap.copy(fontSize = 12.sp, lineHeight = 17.sp))
            Spacer(Modifier.height(10.dp))
            Btn(if (waitingCode) "重新打开授权页" else "去浏览器授权", { err = null; runCatching { Auth.startClaude(ctx); waitingCode = true }.onFailure { err = "没有可用的浏览器" } },
                Modifier.fillMaxWidth(), icon = "login")
            if (waitingCode) {
                Spacer(Modifier.height(10.dp))
                BasicText("授权后页面会显示一串代码（形如 xxxx#yyyy），复制粘贴到这里：", style = T.Cap.copy(fontSize = 11.5.sp))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(44.dp).clip(Capsule()).background(C.Fill).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(code, { code = it.trim() }, "粘贴授权码", Modifier.weight(1f), style = T.Label.copy(fontSize = 13.5.sp))
                    BasicText(if (busy) "登录中…" else "完成登录", Modifier.clickable(enabled = !busy && code.isNotBlank()) {
                        busy = true; err = null
                        scope.launch {
                            try { withContext(Dispatchers.IO) { Auth.finishClaude(code) }; Island.show("Claude 账号已登录", Island.Kind.Done); waitingCode = false; onLogged() }
                            catch (e: Exception) { err = e.message } finally { busy = false }
                        }
                    }, style = T.Btn.copy(fontSize = 13.sp, color = C.Blue))
                }
            }
        }
        err?.let { BasicText(it, Modifier.padding(top = 8.dp), style = T.Cap.copy(color = C.Red)) }
    }
}

private fun resetText(t: Long?): String {
    t ?: return ""
    val d = t - System.currentTimeMillis()
    return when {
        d <= 0 -> "已重置"
        d < 3_600_000 -> "${d / 60_000 + 1} 分钟后重置"
        d < 86_400_000 -> "${d / 3_600_000} 小时 ${(d % 3_600_000) / 60_000} 分后重置"
        else -> java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(t)) + " 重置"
    }
}

/** 5 小时 / 每周额度：两条小进度条 + 刷新按钮。 */
@Composable
fun QuotaBars(auth: String) {
    val scope = rememberCoroutineScope()
    val qs = if (auth == "chatgpt") Auth.gptQuota else Auth.claudeQuota
    val q = qs.value
    var busy by remember(auth) { mutableStateOf(false) }
    var err by remember(auth) { mutableStateOf<String?>(null) }
    fun load() {
        if (busy) return
        busy = true; err = null
        scope.launch {
            try { withContext(Dispatchers.IO) { if (auth == "chatgpt") Api.quotaChatGPT() else Api.quotaClaude() } }
            catch (e: Exception) { err = e.message } finally { busy = false }
        }
    }
    LaunchedEffect(auth) { if (q == null || System.currentTimeMillis() - q.at > 5 * 60_000) load() }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon("gauge", C.Sec, 14.dp); Spacer(Modifier.width(6.dp))
            BasicText("${if (auth == "chatgpt") "ChatGPT" else "Claude"} 额度", Modifier.weight(1f), style = T.Cap.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            Box(Modifier.size(28.dp).clip(Capsule()).background(C.Fill).clickable(enabled = !busy) { load() }, contentAlignment = Alignment.Center) {
                Icon("refresh", if (busy) C.Ter else C.Blue, 14.dp, 2.2f)
            }
        }
        Spacer(Modifier.height(6.dp))
        listOf(Triple("5 小时", q?.fivePct, q?.fiveReset), Triple("每周", q?.weekPct, q?.weekReset)).forEach { (label, pct, reset) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(label, Modifier.width(44.dp), style = T.Cap.copy(fontSize = 11.5.sp))
                val f = ((pct ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                val col = when { (pct ?: 0.0) >= 90 -> C.Red; (pct ?: 0.0) >= 70 -> C.Orange; else -> C.Blue }
                Box(Modifier.weight(1f).height(6.dp).clip(Capsule()).background(C.Fill)) {
                    if (pct != null) Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.02f)).height(6.dp).clip(Capsule()).background(col))
                }
                Spacer(Modifier.width(8.dp))
                BasicText(if (pct == null) "—" else "已用 ${"%.0f".format(pct)}%", Modifier.width(64.dp), style = T.Cap.copy(fontSize = 11.sp, color = if (pct == null) C.Ter else C.Ink, textAlign = TextAlign.End))
            }
            if (reset != null) BasicText(resetText(reset), Modifier.padding(start = 44.dp), style = T.Cap.copy(fontSize = 10.5.sp))
        }
        err?.let { BasicText(it, Modifier.padding(top = 4.dp), style = T.Cap.copy(fontSize = 11.sp, color = C.Red)) }
    }
}

// =================== 登录（仅界面：登录后端未接入，测试版只在本机） ===================
@Composable
fun LoginScreen() {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }
    GlassScaffold(content = {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(96.dp))
            Box(Modifier.size(84.dp).shadow(10.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                BasicText("学", style = T.Large.copy(fontSize = 40.sp))
            }
            Spacer(Modifier.height(16.dp))
            BasicText("课堂学长", style = T.Large.copy(fontSize = 34.sp))
            BasicText("把老师讲的，变成你记得住的", style = T.Sub.copy(fontSize = 15.sp))
            Spacer(Modifier.height(28.dp))
            PlainCard(Modifier.fillMaxWidth(), radius = 32.dp, padding = PaddingValues(18.dp)) {
                Row(Modifier.fillMaxWidth().height(54.dp).clip(Capsule()).background(Color.White).border(0.5.dp, Color(0x14000000), Capsule()).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon("phone", C.Sec, 18.dp); Spacer(Modifier.width(8.dp))
                    BasicText("+86", style = T.Btn); Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(1.dp, 22.dp).background(C.Line)); Spacer(Modifier.width(10.dp))
                    Field(phone, { phone = it.filter { c -> c.isDigit() }.take(11) }, "手机号", Modifier.weight(1f), style = T.Label.copy(fontSize = 17.sp))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).height(54.dp).clip(Capsule()).background(Color.White).border(0.5.dp, Color(0x14000000), Capsule()).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon("shield", C.Sec, 18.dp); Spacer(Modifier.width(8.dp))
                        Field(code, { code = it.filter { c -> c.isDigit() }.take(6) }, "6 位验证码", Modifier.weight(1f), style = T.Label.copy(fontSize = 16.sp))
                    }
                    Spacer(Modifier.width(10.dp))
                    BasicText("获取验证码", Modifier.height(54.dp).clip(Capsule()).background(C.Blue.copy(alpha = 0.10f)).clickable {
                        Island.show("测试版还没接入短信服务", Island.Kind.Info)
                    }.padding(horizontal = 18.dp, vertical = 16.dp), style = T.Btn.copy(color = C.Blue))
                }
                Spacer(Modifier.height(18.dp))
                BasicText("登录 / 注册", Modifier.fillMaxWidth().height(54.dp).clip(Capsule()).background(C.Ink.copy(alpha = 0.9f)).clickable {
                    when {
                        !agree -> Island.show("先勾选同意用户协议", Island.Kind.Warn)
                        phone.length != 11 -> Island.show("手机号是 11 位", Island.Kind.Warn)
                        else -> {
                            Store.updateSettings(Store.settings.value.copy(account = phone))
                            Island.show("测试版：账号只记在本机，未上传", Island.Kind.Done); Nav.pop()
                        }
                    }
                }.padding(top = 15.dp), style = T.Btn.copy(color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.clickable { agree = !agree }, verticalAlignment = Alignment.CenterVertically) {
                    CheckDot(agree, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    BasicText("已阅读并同意《用户协议》《隐私政策》", style = T.Cap.copy(fontSize = 12.5.sp))
                }
            }
            Spacer(Modifier.height(22.dp))
            BasicText("测试版登录服务还没上线，所有数据都保存在本机", style = T.Cap)
            Spacer(Modifier.height(18.dp))
            BasicText("先不登录，仅在本机使用", Modifier.clip(Capsule()).clickable {
                Store.updateSettings(Store.settings.value.copy(account = "guest")); Nav.pop()
            }.padding(horizontal = 16.dp, vertical = 10.dp), style = T.Btn.copy(color = C.Blue, fontSize = 16.sp))
            Spacer(Modifier.height(40.dp))
        }
    }, overlay = { b -> BackBar(b) })
}
