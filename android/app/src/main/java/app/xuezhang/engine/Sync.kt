package app.xuezhang.engine

import android.content.Context
import android.net.Uri
import app.xuezhang.data.Course
import app.xuezhang.data.Store
import app.xuezhang.net.Api
import app.xuezhang.net.ApiException
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 云同步：课程 + 笔记打包成一个 zip（data.json + 封面 + 课件副本）。
 * 存储位置：Android 存储访问框架（Google 云端硬盘、手机厂商云盘等任何「文档提供方」）或 WebDAV。
 */
object Sync {
    const val FILE = "课堂学长备份.zip"

    /** 必须在主线程取数据（快照状态），这里只读不改，IO 线程也安全。 */
    fun pack(): ByteArray {
        val json = Store.exportBundle()
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("data.json")); z.write(json.toString().toByteArray()); z.closeEntry()
            val added = HashSet<String>()
            Store.courses.forEach { c ->
                listOfNotNull(c.cover.takeIf { it.startsWith("file:") }?.removePrefix("file:")?.let { "covers" to it },
                    c.slidePath.takeIf { it.isNotBlank() }?.let { "slides" to it }).forEach { (dir, path) ->
                    val f = File(path); val name = "files/$dir/${f.name}"
                    if (f.isFile && added.add(name)) { z.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
                }
            }
        }
        return bos.toByteArray()
    }

    data class Unpacked(val json: JSONObject, val files: Map<String, String>) // 备份里的文件名 -> 本机绝对路径

    /** 解包（IO 线程）：文件写到私有目录，返回 data.json。 */
    fun unpack(ctx: Context, bytes: ByteArray): Unpacked {
        var json: JSONObject? = null
        val files = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name
                when {
                    n == "data.json" -> json = JSONObject(z.readBytes().toString(Charsets.UTF_8))
                    n.startsWith("files/covers/") || n.startsWith("files/slides/") -> {
                        val dir = File(ctx.filesDir, n.removePrefix("files/").substringBefore('/')).apply { mkdirs() }
                        val base = File(n).name
                        if (base.isNotBlank() && !base.contains("..")) {
                            val f = File(dir, base); f.outputStream().use { z.copyTo(it) }; files[base] = f.absolutePath
                        }
                    }
                }
                e = z.nextEntry
            }
        }
        return Unpacked(json ?: throw ApiException("备份里没有 data.json"), files)
    }

    /** 主线程调用：合并进本机数据。 */
    fun apply(u: Unpacked): Pair<Int, Int> = Store.importBundle(u.json) { c: Course ->
        fun fix(p: String) = u.files[File(p).name] ?: p
        c.copy(cover = if (c.cover.startsWith("file:")) "file:" + fix(c.cover.removePrefix("file:")) else c.cover,
            slidePath = if (c.slidePath.isNotBlank()) fix(c.slidePath) else "")
    }

    // ---------------- SAF ----------------
    fun write(ctx: Context, uri: Uri, bytes: ByteArray) {
        val os = runCatching { ctx.contentResolver.openOutputStream(uri, "wt") }.getOrNull() ?: ctx.contentResolver.openOutputStream(uri, "w")
            ?: throw ApiException("没法写入这个位置")
        os.use { it.write(bytes) }
    }
    fun read(ctx: Context, uri: Uri): ByteArray =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw ApiException("没法读取这个文件")

    // ---------------- WebDAV ----------------
    private fun davBase(url: String) = url.trimEnd('/') + "/xuezhang"
    private fun Request.Builder.auth(user: String, pass: String) = apply { if (user.isNotBlank()) header("Authorization", Credentials.basic(user, pass)) }

    fun davUpload(url: String, user: String, pass: String, bytes: ByteArray) {
        if (!url.startsWith("http")) throw ApiException("WebDAV 地址要以 http:// 或 https:// 开头")
        runCatching { Api.http.newCall(Request.Builder().url(davBase(url) + "/").method("MKCOL", null).auth(user, pass).build()).execute().close() }
        val req = Request.Builder().url(davBase(url) + "/" + FILE).auth(user, pass).put(bytes.toRequestBody("application/zip".toMediaType())).build()
        Api.http.newCall(req).execute().use { r ->
            if (r.code == 401) throw ApiException("WebDAV 账号或密码不对")
            if (!r.isSuccessful) throw ApiException("上传失败 ${r.code}")
        }
    }

    fun davDownload(url: String, user: String, pass: String): ByteArray {
        val req = Request.Builder().url(davBase(url) + "/" + FILE).auth(user, pass).get().build()
        Api.http.newCall(req).execute().use { r ->
            if (r.code == 401) throw ApiException("WebDAV 账号或密码不对")
            if (r.code == 404) throw ApiException("WebDAV 上还没有备份")
            if (!r.isSuccessful) throw ApiException("下载失败 ${r.code}")
            return r.body!!.bytes()
        }
    }
}
