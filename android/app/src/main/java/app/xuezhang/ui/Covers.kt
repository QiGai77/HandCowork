package app.xuezhang.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.xuezhang.data.Course
import app.xuezhang.engine.HardCovers
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** 课程封面：相册自定义图片优先，否则统一用精装预设封面（竖版区域整张封面，横版区域布面 + 烫金图标）。 */
@Composable
fun CourseCover(c: Course, modifier: Modifier = Modifier, radius: Dp = 16.dp, showTitle: Boolean = true) = CoverView(c.name, c.cover, modifier, radius, showTitle)

@Composable
fun CoverView(name: String, cover: String, modifier: Modifier = Modifier, radius: Dp = 16.dp, showTitle: Boolean = true) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(radius)
    if (cover.startsWith("file:")) {
        val img = remember(cover) { CoverImages.load(cover.removePrefix("file:")) }
        if (img != null) {
            Image(img, name, modifier.clip(shape), contentScale = ContentScale.Crop)
            return
        }
    }
    val hc = remember(name, cover) { HardCovers.forCourse(name, cover) }
    val glyph = remember(name) { HardCovers.watermark(name) }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.clip(shape)) {
        if (maxWidth > maxHeight * 0.95f) HcCloth(hc, glyph, Modifier.fillMaxSize())
        else HardCover(hc, name, Modifier.fillMaxSize(), glyph, showTitle)
    }
}

object CoverImages {
    private val cache = LruCache<String, ImageBitmap>(24)
    fun load(path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        val f = File(path); if (!f.exists()) return null
        return runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var s = 1; while (o.outWidth / s > 1200) s *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
        }.getOrNull()?.also { cache.put(path, it) }
    }
    fun evict(path: String) { cache.remove(path) }
}

