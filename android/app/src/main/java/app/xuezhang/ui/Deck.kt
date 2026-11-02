package app.xuezhang.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.size
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
    var err by app.xuezhang.engine.keep<String?>("mindmap.err:${l.id}") { null }
    var showCode by remember { mutableStateOf(false) }
    val scope = app.xuezhang.engine.Bg.scope
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

// =================== v0.0.7.6 复习卡（按概念图 06-review-card 重做）===================
private val Forgot = Color(0xFFB83A33)
private val Fuzzy = Color(0xFFC98A1F)
private val Remember = Color(0xFF2E8B5E)
private val Ivory0 = Color(0xFFF7F2E6)
private val Ivory1 = Color(0xFFEFE8D7)
private val CardInk = Color(0xFF1E2433)

/** 全屏复习：衬线标题「复习卡 · 课程」+ 卡组。 */
@Composable
fun ReviewScreen(id: String) {
    val l = Store.lecture(id) ?: run { Nav.pop(); return }
    val course = Store.course(l.courseId)
    Box(Modifier.fillMaxSize().background(NT.Brushes.Background).dotGrid()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp)) {
                Box(Modifier.align(Alignment.CenterStart).size(46.dp).shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                    .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.85f)).border(1.dp, Color.White, RoundedCornerShape(14.dp))
                    .clickable { Nav.pop() }, contentAlignment = Alignment.Center) { Icon("back", Color(0xFF55524D), 20.dp, 2.2f) }
                BasicText("复习卡·" + (course?.name ?: l.title).let { BookLook.spineTitle(it, "", 9) }, Modifier.align(Alignment.Center).padding(horizontal = 58.dp),
                    style = T.Title.copy(fontSize = 25.sp, lineHeight = 32.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Black), maxLines = 1)
            }
            CardDeck(l, full = true)
        }
    }
}

/** 复习卡：精装象牙白卡 + 课程色装订带，后面露出红（忘了）/ 绿（记得）两张；点按 3D 翻面，左右滑判定。支持大模型重新出题。 */
@Composable
fun CardDeck(l: Lecture, full: Boolean = false) {
    val scope = rememberCoroutineScope()
    var regenerating by remember(l.id) { mutableStateOf(false) }
    var idx by remember(l.id) { mutableIntStateOf(0) }
    val total = l.cards.size
    val band = Store.course(l.courseId)?.let { courseHc(it).color } ?: lectureHc(l, null).color

    fun regenCards() {
        if (regenerating) return
        val text = (l.notes.ifBlank { l.sections.joinToString("\n") { "${it.title}\n${it.body}" } }).take(10000)
        if (text.isBlank()) {
            Island.show("笔记内容为空，无法生成复习卡", Island.Kind.Warn)
            return
        }
        regenerating = true
        scope.launch {
            try {
                Island.show("学长正在重新出题…", Island.Kind.Info)
                val o = withContext(Dispatchers.IO) {
                    Api.chatJson(Prompts.CARDS_REGEN, text)
                }
                val arr = o.optJSONArray("cards") ?: org.json.JSONArray()
                val newCards = (0 until arr.length()).map { i ->
                    val c = arr.getJSONObject(i)
                    app.xuezhang.data.Card(
                        q = c.optString("q"),
                        a = c.optString("a"),
                        tag = c.optString("tag").ifBlank { "概念" },
                        level = 0
                    )
                }.filter { it.q.isNotBlank() && it.a.isNotBlank() }
                if (newCards.isNotEmpty()) {
                    Store.upsertLecture(l.copy(cards = newCards))
                    idx = 0
                    Island.show("已重新生成 ${newCards.size} 张复习卡", Island.Kind.Done)
                } else {
                    Island.show("未能生成有效题目，请重试", Island.Kind.Warn)
                }
            } catch (e: Exception) {
                Island.show("出题失败：${e.message?.take(30)}", Island.Kind.Warn)
            } finally {
                regenerating = false
            }
        }
    }

    if (l.cards.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyHint("还没有复习卡", "可以让学长根据这节课笔记自动出题")
            Spacer(Modifier.height(12.dp))
            Btn(if (regenerating) "学长出题中…" else "重新出题", { regenCards() }, Modifier.fillMaxWidth(0.6f), tint = C.Blue)
        }
        return
    }

    // 计数：大号衬线「8 / 24」+ 进度环
    Row(Modifier.fillMaxWidth().padding(top = if (full) 2.dp else 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        BasicText("${minOf(idx + 1, total)}", style = T.Large.copy(fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black))
        BasicText(" / $total", Modifier.padding(top = 10.dp), style = T.Sub.copy(fontSize = 22.sp, color = Color(0xFF8E8A84)))
        Spacer(Modifier.width(16.dp))
        val pct = if (total == 0) 0f else idx.toFloat() / total
        val anim by animateFloatAsState(pct, label = "ring")
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val sw = 4.dp.toPx()
                drawArc(Color(0xFFDCDCE2), 0f, 360f, false, topLeft = Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                drawArc(Color(0xFF1E2A4A), -90f, 360f * anim, false, topLeft = Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            BasicText("${(pct * 100).toInt()}%", style = T.Cap.copy(fontSize = 12.sp, color = Color(0xFF3A3A3C), fontWeight = FontWeight.Medium))
        }
    }
    Spacer(Modifier.height(if (full) 26.dp else 14.dp))

    val dx = remember(l.id, idx) { Animatable(0f) }
    var flipped by remember(l.id, idx) { mutableStateOf(false) }
    val flip by animateFloatAsState(if (flipped) 180f else 0f, spring(0.72f, 240f), label = "flip")
    val drag = (dx.value / 600f).coerceIn(-1f, 1f) // <0 忘了，>0 记得
    fun judge(level: Int) = scope.launch {
        if (idx >= total) return@launch
        l.cards[idx].level = level; Store.save()
        dx.animateTo(when (level) { 2 -> 1400f; 0 -> -1400f; else -> 0f }, spring(1f, 220f))
        idx += 1
    }

    Box(Modifier.fillMaxWidth().height(if (full) 430.dp else 380.dp).padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        if (idx >= total) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText("这组复习卡都过完了", style = T.Title.copy(fontSize = 22.sp))
                Spacer(Modifier.height(8.dp))
                BasicText("记得 ${l.cards.count { it.level == 2 }} · 模糊 ${l.cards.count { it.level == 1 }} · 忘了 ${l.cards.count { it.level == 0 }}", style = T.Sub)
                Spacer(Modifier.height(14.dp))
                BasicText(if (regenerating) "出题中…" else "让学长重新出题", Modifier.clip(com.kyant.shapes.Capsule()).background(Color.White.copy(alpha = 0.85f))
                    .clickable(enabled = !regenerating) { regenCards() }.padding(horizontal = 16.dp, vertical = 8.dp), style = T.Btn.copy(fontSize = 14.sp, color = C.Blue))
            }
        } else {
            val hasNext = idx + 1 < total
            // 后面的红卡（忘了）
            if (hasNext) Box(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f).graphicsLayer {
                rotationZ = -6f; translationX = -16.dp.toPx(); translationY = 18.dp.toPx()
            }.shadow((4 + 14 * (-drag).coerceAtLeast(0f)).dp, RoundedCornerShape(16.dp), ambientColor = Forgot, spotColor = Forgot)
                .clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(Color(0xFFF8D9D3), Color(0xFFF1C7C0))))) {
                BasicText("忘了", Modifier.align(Alignment.BottomStart).padding(14.dp), style = T.Btn.copy(fontFamily = Serif, color = Forgot.copy(alpha = 0.8f), fontSize = 15.sp))
            }
            // 后面的绿卡（记得）
            if (hasNext) Box(Modifier.fillMaxWidth(0.88f).fillMaxHeight(0.84f).graphicsLayer {
                rotationZ = 6f; translationX = 28.dp.toPx(); translationY = (-10).dp.toPx()
            }.shadow((8 + 14 * drag.coerceAtLeast(0f)).dp, RoundedCornerShape(16.dp), ambientColor = Remember, spotColor = Remember)
                .clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(Color(0xFFEAF6EE), Color(0xFFDDF0E3))))
                .border(1.5.dp, Color(0xFFBFE6CC), RoundedCornerShape(16.dp))) {
                BasicText("记\n得", Modifier.align(Alignment.TopEnd).padding(14.dp), style = T.Btn.copy(fontFamily = Serif, color = Remember, fontSize = 17.sp, lineHeight = 20.sp))
            }
            // 主卡下面的纸页厚度
            repeat(3) { k ->
                Box(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.88f).graphicsLayer {
                    rotationZ = -3f + dx.value / 60f * 0.2f; translationX = (-3 - 3 * k).dp.toPx() + dx.value * 0.15f; translationY = (5 + 5 * k).dp.toPx()
                }.clip(RoundedCornerShape(14.dp)).background(lerp(Ivory1, Color(0xFFE2D9C4), k / 3f)).border(0.6.dp, Color(0x22000000), RoundedCornerShape(14.dp)))
            }
            // 主卡
            val card = l.cards[idx]
            Box(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.88f)
                .graphicsLayer {
                    translationX = dx.value; rotationZ = -3f + dx.value / 40f
                    rotationY = flip; cameraDistance = 16f * density
                }
                .shadow(14.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x552A2040), spotColor = Color(0x552A2040))
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Ivory0, Ivory1)))
                .pointerInput(idx) {
                    detectDragGestures(
                        onDrag = { ch, d -> ch.consume(); scope.launch { dx.snapTo(dx.value + d.x) } },
                        onDragEnd = { if (abs(dx.value) > 260f) judge(if (dx.value > 0) 2 else 0) else scope.launch { dx.animateTo(0f, spring(0.55f, 380f)) } })
                }
                .clickable(interactionSource = null, indication = null) { flipped = !flipped }
            ) {
                val back = flip > 90f
                Column(Modifier.fillMaxSize().graphicsLayer { if (back) rotationY = 180f }) {
                    // 课程色装订带
                    Box(Modifier.fillMaxWidth().height(34.dp).background(Brush.verticalGradient(listOf(lerp(band, Color.White, 0.12f), band, lerp(band, Color.Black, 0.18f))))) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.5.dp).background(Color.Black.copy(alpha = 0.18f)))
                        if (back) BasicText("答案", Modifier.align(Alignment.CenterStart).padding(start = 16.dp), style = T.Btn.copy(color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp))
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), contentAlignment = Alignment.CenterStart) {
                        RichText(if (back) card.a else card.q, style = T.Title.copy(fontFamily = Serif, fontSize = if (back) 18.sp else 23.sp, lineHeight = if (back) 30.sp else 34.sp, color = CardInk,
                            fontWeight = if (back) FontWeight.SemiBold else FontWeight.Black))
                    }
                    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText("★ " + card.tag.ifBlank { "考点" }, Modifier.clip(com.kyant.shapes.Capsule()).background(Color(0xFFE6DFCF)).padding(horizontal = 12.dp, vertical = 4.dp),
                            style = T.Btn.copy(fontSize = 13.sp, color = Color(0xFF2B3550), fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(8.dp))
                        BasicText(if (back) "· 右滑记得 · 左滑忘了 ·" else "· 轻点翻面 ·", style = T.Cap.copy(fontSize = 13.sp, color = Color(0xFF9A9384)))
                    }
                }
                // 滑动着色
                if (drag != 0f) Box(Modifier.matchParentSize().background((if (drag > 0) Remember else Forgot).copy(alpha = 0.16f * abs(drag))))
            }
        }
    }
    if (idx < total) {
        Spacer(Modifier.height(if (full) 22.dp else 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("忘了", Forgot, 0), Triple("模糊", Fuzzy, 1), Triple("记得", Remember, 2)).forEach { (t, col, lv) ->
                GlowButton(t, col, Modifier.weight(1f)) { if (lv == 1) { l.cards[idx].level = 1; Store.save(); idx += 1 } else judge(lv) }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(if (regenerating) "学长出题中…" else "重新出题", Modifier.clip(com.kyant.shapes.Capsule()).background(Color.White.copy(alpha = 0.7f))
            .clickable(enabled = !regenerating) { regenCards() }.padding(horizontal = 14.dp, vertical = 7.dp), style = T.Btn.copy(fontSize = 13.sp, color = if (regenerating) C.Sec else C.Blue))
        Spacer(Modifier.weight(1f))
        Column(Modifier.size(72.dp).shadow(10.dp, com.kyant.shapes.Capsule(), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(com.kyant.shapes.Capsule()).background(Color.White.copy(alpha = 0.95f)).clickable { idx = 0 },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon("repeat", Color(0xFF1A1A1A), 20.dp, 2.2f)
            BasicText("重来", style = T.Cap.copy(fontSize = 12.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Medium))
        }
    }
}

/** 玻璃质感按钮 + 同色柔光。 */
@Composable
private fun GlowButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(76.dp)
        .shadow(14.dp, RoundedCornerShape(20.dp), ambientColor = color.copy(alpha = 0.5f), spotColor = color.copy(alpha = 0.6f))
        .clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.96f), lerp(Color.White, color, 0.20f))))
        .border(1.4.dp, Brush.verticalGradient(listOf(Color.White, lerp(Color.White, color, 0.3f))), RoundedCornerShape(20.dp))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        BasicText(text, style = T.Title.copy(fontFamily = Serif, fontSize = 26.sp, color = color, fontWeight = FontWeight.Black))
    }
}
