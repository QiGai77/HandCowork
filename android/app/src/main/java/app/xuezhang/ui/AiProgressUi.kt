package app.xuezhang.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.engine.AiJob
import app.xuezhang.engine.AiProgress
import com.kyant.shapes.Capsule

/** 屏幕内的 AI 进度条：步骤 + 百分比 + 带流光的进度条 + 已用时 + 轮播小彩蛋。与灵动岛同一个数据源。 */
@Composable
fun AiProgressBar(job: AiJob?, modifier: Modifier = Modifier, showTip: Boolean = true) {
    if (job == null) return
    val f by animateFloatAsState(job.fraction.floatValue, spring(0.9f, 90f), label = "aif")
    val sheen = rememberInfiniteTransition(label = "aish").animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1400)), label = "aishx")
    val err = job.error.value
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(if (err != null) "失败：$err" else job.step.value, Modifier.weight(1f), style = T.Btn.copy(fontSize = 14.sp, color = if (err != null) C.Red else C.Ink),
                maxLines = if (err != null) 4 else 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            BasicText("${(f * 100).toInt()}%", style = T.Btn.copy(fontSize = 14.sp, color = C.Blue))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(Capsule()).background(C.Fill)) {
            Box(Modifier.fillMaxWidth(f.coerceIn(0.02f, 1f)).height(8.dp).clip(Capsule()).background(if (err != null) C.Red else C.Blue)
                .then(if (err == null && !job.finished.value) Modifier.sheen(sheen.value) else Modifier))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(job.detail.value, Modifier.weight(1f), style = T.Cap.copy(fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("${job.elapsed.longValue / 1000}s", style = T.Cap.copy(fontSize = 11.5.sp))
        }
        if (showTip && err == null && !job.finished.value) {
            Spacer(Modifier.height(8.dp))
            Crossfade(job.tip.value, label = "tip") { t ->
                BasicText(t, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x0F0A72E8)).padding(horizontal = 12.dp, vertical = 8.dp),
                    style = T.Cap.copy(fontSize = 12.sp, color = C.HlBlue), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 卡片版：放在列表里。 */
@Composable
fun AiProgressCard(job: AiJob?, modifier: Modifier = Modifier) {
    if (job == null) return
    PlainCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 22.dp, padding = PaddingValues(16.dp)) {
        BasicText(job.title, style = T.Cap.copy(color = C.Blue))
        Spacer(Modifier.height(6.dp))
        AiProgressBar(job)
    }
}

/** 按 key 找正在进行（或刚结束）的任务。 */
@Composable
fun aiJob(key: String): AiJob? = AiProgress.jobs.lastOrNull { it.key == key }
