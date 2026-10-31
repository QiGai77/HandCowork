package app.xuezhang.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.engine.Expr
import app.xuezhang.engine.NotePart
import app.xuezhang.engine.Param
import app.xuezhang.engine.UiBlock
import app.xuezhang.engine.UiBlocks
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/** 渲染一段笔记正文：文本走 RichText，```ui 块走原生组件。任何组件内部出错都只影响这一块。 */
@Composable
fun NoteBody(body: String, onMath: ((String) -> Unit)? = null) {
    val parts = remember(body) { runCatching { UiBlocks.split(body) }.getOrElse { listOf(NotePart.Text(body)) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        parts.forEach { p ->
            when (p) {
                is NotePart.Text -> RichText(p.text, onMath = onMath)
                is NotePart.Ui -> UiBlockView(p.block)
            }
        }
    }
}

@Composable
fun UiBlockView(b: UiBlock) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFF6F8FC))
            .border(0.6.dp, Color(0x140A72E8), RoundedCornerShape(18.dp)).padding(12.dp).animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(when (b) { is UiBlock.Plot -> "图像"; is UiBlock.Steps -> "推导"; is UiBlock.Quiz -> "小测"; is UiBlock.Compare -> "对比"; is UiBlock.Phasor -> "相量图" },
                Modifier.clip(RoundedCornerShape(6.dp)).background(C.Blue.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp),
                style = T.Cap.copy(color = C.Blue, fontWeight = FontWeight.Bold, fontSize = 11.sp))
            if (b.title.isNotBlank()) { Spacer(Modifier.width(8.dp)); BasicText(b.title, style = T.Btn.copy(fontSize = 14.5.sp)) }
        }
        Spacer(Modifier.height(8.dp))
        when (b) {
            is UiBlock.Plot -> PlotView(b)
            is UiBlock.Steps -> StepsView(b)
            is UiBlock.Quiz -> QuizView(b)
            is UiBlock.Compare -> CompareView(b)
            is UiBlock.Phasor -> PhasorView(b)
        }
    }
}

private val SERIES = listOf(Color(0xFF0A72E8), Color(0xFFE5342B), Color(0xFF2DA44E))

@Composable
private fun ParamSlider(p: Param, value: Double, onChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        BasicText(p.label, Modifier.width(72.dp), style = T.Cap.copy(color = C.Ink, fontSize = 12.5.sp), maxLines = 1)
        BoxWithConstraints(Modifier.weight(1f).height(32.dp)) {
            val mw = maxWidth
            val wPx = with(LocalDensity.current) { mw.toPx() }
            val f = ((value - p.min) / (p.max - p.min)).toFloat().coerceIn(0f, 1f)
            fun set(x: Float) = onChange(p.min + (x / wPx).coerceIn(0f, 1f) * (p.max - p.min))
            Box(Modifier.fillMaxSize().pointerInput(p) {
                detectTapGestures { set(it.x) }
            }.pointerInput(p, wPx) { detectHorizontalDragGestures { ch, _ -> set(ch.position.x) } }) {
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).clip(Capsule()).background(Color(0x22787880)))
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(f.coerceAtLeast(0.001f)).height(4.dp).clip(Capsule()).background(C.Blue))
                Box(Modifier.align(Alignment.CenterStart).offset(x = mw * f - 11.dp).size(22.dp).clip(Capsule()).background(Color.White)
                    .border(0.5.dp, Color(0x22000000), Capsule()))
            }
        }
        Spacer(Modifier.width(8.dp))
        BasicText(fmtNum(value) + p.unit, Modifier.widthIn(min = 40.dp), style = T.Cap.copy(color = C.Blue, fontWeight = FontWeight.Bold))
    }
}

fun fmtNum(v: Double): String = when {
    v.isNaN() -> "—"
    abs(v) >= 100 -> v.roundToInt().toString()
    abs(v) >= 10 -> "%.1f".format(v)
    else -> "%.2f".format(v).trimEnd('0').trimEnd('.').ifBlank { "0" }
}

@Composable
private fun PlotView(b: UiBlock.Plot) {
    val vals = remember(b) { mutableStateListOf<Double>().apply { b.params.forEach { add(it.value) } } }
    val parsed = remember(b) { b.exprs.map { (l, e) -> l to Expr.tryParse(e) } }
    val env = HashMap<String, Double>().apply { b.params.forEachIndexed { i, p -> put(p.name, vals[i]) } }
    val n = 160
    val xs = (0..n).map { b.xMin + (b.xMax - b.xMin) * it / n }
    val ys = parsed.map { (_, e) -> xs.map { x -> Expr.safeEval(e, env + ("x" to x)) } }
    val finite = ys.flatten().filter { it.isFinite() }
    val y0 = b.yMin ?: (finite.minOrNull() ?: -1.0).let { if (it > 0) 0.0 else it }
    val y1 = (b.yMax ?: (finite.maxOrNull() ?: 1.0)).let { if (it <= y0) y0 + 1 else it }
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val padL = 6.dp.toPx(); val w = size.width - padL; val h = size.height
        fun px(x: Double) = padL + ((x - b.xMin) / (b.xMax - b.xMin) * w).toFloat()
        fun py(y: Double) = (h - (y - y0) / (y1 - y0) * h).toFloat()
        // 网格与坐标轴
        for (i in 0..4) { val yy = h * i / 4; drawLine(Color(0x11000000), Offset(padL, yy), Offset(size.width, yy), 1f) }
        if (y0 < 0 && y1 > 0) drawLine(Color(0x55000000), Offset(padL, py(0.0)), Offset(size.width, py(0.0)), 1.2f)
        if (b.xMin < 0 && b.xMax > 0) drawLine(Color(0x55000000), Offset(px(0.0), 0f), Offset(px(0.0), h), 1.2f)
        ys.forEachIndexed { si, s ->
            val path = Path(); var pen = false
            s.forEachIndexed { i, y ->
                if (!y.isFinite() || y < y0 - (y1 - y0) * 2 || y > y1 + (y1 - y0) * 2) { pen = false; return@forEachIndexed }
                val p = Offset(px(xs[i]), py(y).coerceIn(-h, 2 * h))
                if (pen) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y); pen = true
            }
            drawPath(path, SERIES[si % SERIES.size], style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
        }
        drawContext.canvas.nativeCanvas.apply {
            val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.sp.toPx(); color = 0xFF8A8A8E.toInt() }
            drawText(fmtNum(y1), padL + 4, tp.textSize, tp); drawText(fmtNum(y0), padL + 4, h - 4, tp)
            tp.textAlign = android.graphics.Paint.Align.RIGHT
            drawText("${b.xLabel} ∈ [${fmtNum(b.xMin)}, ${fmtNum(b.xMax)}]", size.width - 4, h - 4, tp)
        }
    }
    if (parsed.size > 1 || parsed.firstOrNull()?.first?.let { it != "y" } == true) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        parsed.forEachIndexed { i, (l, _) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp, 3.dp).background(SERIES[i % SERIES.size])); Spacer(Modifier.width(4.dp))
                BasicText(l, style = T.Cap.copy(fontSize = 11.5.sp))
            }
        }
    }
    b.params.forEachIndexed { i, p -> ParamSlider(p, vals[i]) { vals[i] = it } }
}

@Composable
private fun StepsView(b: UiBlock.Steps) {
    var i by remember(b) { mutableIntStateOf(0) }
    val n = b.steps.size
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(n) { k -> Box(Modifier.weight(1f).height(3.dp).clip(Capsule()).background(if (k <= i) C.Blue else Color(0x22787880))) }
    }
    Spacer(Modifier.height(8.dp))
    b.steps.take(i + 1).forEachIndexed { k, (text, tex) ->
        Column(Modifier.padding(vertical = 3.dp)) {
            if (text.isNotBlank()) RichText("**${k + 1}.** $text", style = T.Body.copy(fontSize = 14.5.sp, color = if (k == i) C.Ink else C.Sec))
            if (tex.isNotBlank()) MathBlock(tex, T.Body.copy(fontSize = 14.5.sp, color = if (k == i) C.Ink else C.Sec), scale = 1.1f)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallPill("上一步", i > 0) { i-- }
        Spacer(Modifier.weight(1f))
        BasicText("${i + 1} / $n", Modifier.align(Alignment.CenterVertically), style = T.Cap)
        Spacer(Modifier.weight(1f))
        SmallPill(if (i < n - 1) "下一步" else "重来", true, primary = true) { i = if (i < n - 1) i + 1 else 0 }
    }
}

@Composable
fun SmallPill(text: String, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    BasicText(text, Modifier.clip(Capsule()).background(if (primary) C.Blue else Color(0x14787880)).clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 7.dp),
        style = T.Btn.copy(fontSize = 13.sp, color = when { primary -> Color.White; enabled -> C.Ink; else -> C.Ter }))
}

@Composable
private fun QuizView(b: UiBlock.Quiz) {
    var pick by remember(b) { mutableIntStateOf(-1) }
    RichText(b.q, style = T.Body.copy(fontSize = 15.sp))
    Spacer(Modifier.height(6.dp))
    b.options.forEachIndexed { i, o ->
        val right = pick >= 0 && i == b.answer
        val wrong = pick == i && i != b.answer
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                .background(when { right -> C.Green.copy(alpha = 0.12f); wrong -> C.Red.copy(alpha = 0.10f); else -> Color.White })
                .border(0.6.dp, when { right -> C.Green; wrong -> C.Red; else -> Color(0x14000000) }, RoundedCornerShape(12.dp))
                .clickable(enabled = pick < 0) { pick = i }.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText("ABCDEF"[i].toString(), style = T.Btn.copy(fontSize = 13.sp, color = when { right -> C.Green; wrong -> C.Red; else -> C.Sec }))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { RichText(o, style = T.Body.copy(fontSize = 14.5.sp)) }
            if (right) Icon("check", C.Green, 16.dp, 2.6f)
        }
    }
    if (pick >= 0) {
        Spacer(Modifier.height(6.dp))
        RichText((if (pick == b.answer) "[b:答对了。]" else "[r:再想想：]正确答案是 ${"ABCDEF"[b.answer]}。") + " " + b.explain, style = T.Body.copy(fontSize = 14.sp))
        Row(Modifier.padding(top = 6.dp)) { SmallPill("再做一次", true) { pick = -1 } }
    }
}

@Composable
private fun CompareView(b: UiBlock.Compare) {
    val colW = if (b.columns.size <= 3) 120.dp else 96.dp
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            b.columns.forEachIndexed { i, h ->
                BasicText(h, Modifier.width(if (i == 0) 84.dp else colW).padding(6.dp), style = T.Btn.copy(fontSize = 13.sp, color = if (i == 0) C.Sec else C.Blue))
            }
        }
        b.rows.forEachIndexed { ri, r ->
            Row(Modifier.background(if (ri % 2 == 0) Color.White else Color.Transparent)) {
                r.forEachIndexed { i, cell ->
                    Box(Modifier.width(if (i == 0) 84.dp else colW).padding(6.dp)) {
                        RichText(cell, style = T.Body.copy(fontSize = 13.sp, lineHeight = 19.sp, color = if (i == 0) C.Sec else C.Ink,
                            fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal))
                    }
                }
            }
        }
    }
}

private fun pColor(s: String) = when (s.lowercase()) {
    "red" -> Color(0xFFE5342B); "green" -> Color(0xFF2DA44E); "orange" -> Color(0xFFE8770F); "purple" -> Color(0xFF7D5BD9)
    "gray", "grey" -> Color(0xFF8E8E93); "teal" -> Color(0xFF30B0C7); else -> Color(0xFF0A72E8)
}

@Composable
private fun PhasorView(b: UiBlock.Phasor) {
    var v by remember(b) { mutableDoubleStateOf(b.param?.value ?: 0.0) }
    val pts = remember(b, v) { runCatching { UiBlocks.phasorPoints(b, v) }.getOrDefault(emptyList()) }
    Canvas(Modifier.fillMaxWidth().height(220.dp).pointerInput(b) {
        val p = b.param ?: return@pointerInput
        detectDragGestures { ch, _ ->
            // 拖动：按触点相对原点的角度设置参数（参数是角度时最直观；否则按横向位移比例）
            val o = Offset(size.width * 0.32f, size.height * 0.62f)
            val d = ch.position - o
            val ang = Math.toDegrees(atan2(-d.y, d.x).toDouble())
            v = if (p.min >= -360 && p.max <= 360 && (p.label.contains("角") || p.name.contains("phi") || p.name.contains("theta") || p.name.contains("delta")))
                (-ang).coerceIn(p.min, p.max) else (p.min + (ch.position.x / size.width) * (p.max - p.min)).coerceIn(p.min, p.max)
        }
    }) {
        if (pts.isEmpty()) return@Canvas
        val all = pts.flatMap { listOf(it.first, it.second) }
        val maxR = max(all.maxOf { abs(it[0]) }, all.maxOf { abs(it[1]) }).coerceAtLeast(1e-6)
        val o = Offset(size.width * 0.32f, size.height * 0.62f)
        val sc = (minOf(size.width * 0.62f, size.height * 0.56f) / maxR).toFloat()
        fun P(p: DoubleArray) = Offset(o.x + p[0].toFloat() * sc, o.y - p[1].toFloat() * sc)
        drawLine(Color(0x22000000), Offset(0f, o.y), Offset(size.width, o.y), 1f)
        drawLine(Color(0x22000000), Offset(o.x, 0f), Offset(o.x, size.height), 1f)
        val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 12.sp.toPx(); isFakeBoldText = true }
        pts.forEachIndexed { i, (s, e) ->
            val vec = b.vectors[i]; val col = pColor(vec.color)
            val a = P(s); val z = P(e)
            val len = (z - a).getDistance(); if (len < 1f) return@forEachIndexed
            drawLine(col, a, z, 2.6.dp.toPx(), StrokeCap.Round, if (vec.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null)
            val dir = (z - a) / len; val nrm = Offset(-dir.y, dir.x); val hl = 9.dp.toPx().coerceAtMost(len * 0.4f)
            val head = Path().apply { moveTo(z.x, z.y); lineTo(z.x - dir.x * hl + nrm.x * hl * 0.45f, z.y - dir.y * hl + nrm.y * hl * 0.45f)
                lineTo(z.x - dir.x * hl - nrm.x * hl * 0.45f, z.y - dir.y * hl - nrm.y * hl * 0.45f); close() }
            drawPath(head, col)
            tp.color = android.graphics.Color.argb(255, (col.red * 255).toInt(), (col.green * 255).toInt(), (col.blue * 255).toInt())
            val lab = vec.label.replace("_", "")
            drawContext.canvas.nativeCanvas.drawText(lab, z.x + nrm.x * 12f + 4f, z.y + nrm.y * 12f + 4f, tp)
        }
    }
    b.param?.let { p ->
        ParamSlider(p, v) { v = it }
        BasicText("拖动图上任意位置或滑块，看相量怎么跟着变", Modifier.padding(top = 2.dp), style = T.Cap.copy(fontSize = 11.sp))
    }
}
