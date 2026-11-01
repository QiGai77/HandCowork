package app.xuezhang.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object ImgUtil {
    /** 按最长边缩小解码，用于灵动岛 / 列表缩略图。 */
    fun thumb(path: String, maxSide: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0) return null
        var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }
}
