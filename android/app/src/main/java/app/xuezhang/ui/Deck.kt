package app.xuezhang.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Lecture
import app.xuezhang.data.Store
import app.xuezhang.engine.Log
import app.xuezhang.engine.Prompts
import app.xuezhang.net.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/** 概念图节点位置（相对坐标 0~1），按笔记存。 */
object MapPos {
    private fun f(id: String) = File(Store.dir, "mappos_$id.json")
    fun load(id: String): Map<Int, Offset> = runCatching {
        val o = JSONObject(f(id).readText())
        o.keys().asSequence().associate { k -> k.toInt() to o.getJSONArray(k).let { Offset(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } }
    }.getOrDefault(emptyMap())
    fun save(id: String, m: Map<Int, Offset>) = runCatching {
        f(id).writeText(JSONObject().also { o -> m.forEach { (k, v) -> o.put(k.toString(), org.json.JSONArray().put(v.x.toDouble()).put(v.y.toDouble())) } }.toString())
    }
}

/** 概念图：OpenAI / ChatGPT 账号直接用 gpt-image 画成图（笔记生成后自动画）；其他服务商用可拖动的代码绘制版。 */
@Composable
fun ConceptMapV6(l: Lecture) {
    if (!Api.isGpt() || l.nodes.isEmpty()) { ConceptMap(l); return }
    val file = app.xuezhang.engine.MindMap.file(l.id)
    val ver = app.xuezhang.engine.MindMap.version.intValue
    val img = remember(l.id, ver) { runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull() }
    val job = aiJob("mindmap:${l.id}")
    val busy = job != null && !job.finished.value
    var err by remember { mutableStateOf<String?>(null) }
    var showCode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun gen() {
        if (busy) return
        err = null
        scope.launch { try { app.xuezhang.engine.MindMap.generate(l.id) } catch (e: Exception) { err = e.message; Log.err("概念图", e) } }
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("AI 导图" to false, "可拖动" to true).forEach { (t, v) ->
            BasicText(t, Modifier.clip(com.kyant.shapes.Capsule()).background(if (showCode == v) C.Blue else C.Fill).clickable { showCode = v }.padding(horizontal = 14.dp, vertical = 6.dp),
                style = T.Btn.copy(fontSize = 13.sp, color = if (showCode == v) Color.White else C.Ink))
        }
    }
    if (showCode) { ConceptMap(l); return }
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 26.dp, padding = PaddingValues(10.dp)) {
        when {
            busy -> Column(Modifier.padding(10.dp)) { AiProgressBar(job) }
            img != null -> Image(img, "概念图", Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.FillWidth)
            else -> BasicText("用 GPT 把这节课的概念树画成一张导图", Modifier.fillMaxWidth().padding(30.dp), style = T.Sub.copy(textAlign = TextAlign.Center))
        }
        err?.let { BasicText(it, Modifier.padding(8.dp), style = T.Cap.copy(color = C.Red)) }
        if (!busy) BasicText(if (img == null) "生成导图" else "重新生成", Modifier.padding(8.dp).clickable { gen() }, style = T.Btn.copy(color = C.Blue))
    }
}

/** 复习卡：叠放的卡组，左右滑走顶上一张（弹簧动画），点按翻面。 */
@Composable
fun CardDeck(l: Lecture) {
    if (l.cards.isEmpty()) { EmptyHint("还没有复习卡", "生成笔记后会出现"); return }
    var idx by remember(l.id) { mutableIntStateOf(0) }
    val total = l.cards.size
    val scope = rememberCoroutineScope()
    Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(if (idx < total) "第 ${idx + 1} / $total 张" else "全部过完", Modifier.weight(1f), style = T.Sub)
        BasicText("重来", Modifier.clip(com.kyant.shapes.Capsule()).background(C.Fill).clickable { idx = 0 }.padding(horizontal = 14.dp, vertical = 6.dp), style = T.Btn.copy(fontSize = 13.sp))
    }
    Box(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().height(380.dp), contentAlignment = Alignment.TopCenter) {
        if (idx >= total) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                BasicText("这组复习卡都过完了", style = T.Item)
                BasicText("记得 ${l.cards.count { it.level == 2 }} · 模糊 ${l.cards.count { it.level == 1 }} · 忘了 ${l.cards.count { it.level == 0 }}", Modifier.padding(top = 6.dp), style = T.Sub)
            }
        }
        // 从底往上画：最多露出 3 张
        (minOf(total - 1, idx + 2) downTo idx).forEach { i ->
            key(i) {
                val depth = i - idx
                val card = l.cards[i]
                val dx = remember { Animatable(0f) }
                var flipped by remember { mutableStateOf(false) }
                val flip by animateFloatAsState(if (flipped) 180f else 0f, spring(0.7f, 260f), label = "flip")
                val lift by animateFloatAsState(depth.toFloat(), spring(0.7f, 300f), label = "lift")
                fun out(dir: Float) = scope.launch {
                    card.level = if (dir > 0) 2 else 0; Store.save()
                    dx.animateTo(dir * 1400f, spring(1f, 220f)); idx = i + 1
                }
                Box(Modifier.fillMaxWidth().height(340.dp)
                    .graphicsLayer {
                        translationY = lift * 16.dp.toPx(); scaleX = 1f - lift * 0.05f; scaleY = 1f - lift * 0.05f
                        translationX = dx.value; rotationZ = dx.value / 40f
                        rotationY = flip; cameraDistance = 14f * density
                    }
                    .shadow(if (depth == 0) 14.dp else 4.dp, RoundedCornerShape(28.dp), clip = false)
                    .clip(RoundedCornerShape(28.dp)).background(if (flip > 90f) Color(0xFFF4F8FF) else Color.White)
                    .then(if (depth == 0) Modifier.pointerInput(i) {
                        detectDragGestures(
                            onDrag = { ch, d -> ch.consume(); scope.launch { dx.snapTo(dx.value + d.x) } },
                            onDragEnd = { if (abs(dx.value) > 260f) out(if (dx.value > 0) 1f else -1f) else scope.launch { dx.animateTo(0f, spring(0.55f, 380f)) } })
                    }.clickable { flipped = !flipped } else Modifier)
                    .padding(22.dp)
                ) {
                    val back = flip > 90f
                    Column(Modifier.fillMaxSize().graphicsLayer { if (back) rotationY = 180f }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(if (back) "答案" else "问题", Modifier.weight(1f), style = T.Sub)
                            Tag(card.tag, when (card.tag) { "概念" -> C.HlBlue; "易错" -> C.HlOrange; else -> C.HlRed })
                        }
                        Spacer(Modifier.height(14.dp))
                        if (depth == 0) RichText(if (back) card.a else card.q, style = T.Body.copy(fontSize = 17.sp, lineHeight = 28.sp))
                        Spacer(Modifier.weight(1f))
                        if (depth == 0) BasicText(if (back) "右滑 记得 · 左滑 忘了" else "点按翻面 · 左右滑切换", Modifier.fillMaxWidth(), style = T.Cap.copy(textAlign = TextAlign.Center))
                    }
                }
            }
        }
    }
    if (idx < total) Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("忘了", C.Red, 0), Triple("模糊", C.Orange, 1), Triple("记得", C.Green, 2)).forEach { (t, col, lv) ->
            Btn(t, { l.cards[idx].level = lv; Store.save(); idx += 1 }, Modifier.weight(1f), tint = col)
        }
    }
}
