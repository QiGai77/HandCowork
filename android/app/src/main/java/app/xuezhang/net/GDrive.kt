package app.xuezhang.net

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import app.xuezhang.BuildConfig
import app.xuezhang.data.Store
import app.xuezhang.engine.Log
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Google Drive 连接器：浏览器 / Custom Tab 里完成 Google OAuth（PKCE + 本机回环地址回调），
 * 用 Drive REST v3 可续传上传（resumable upload）把备份传到「课堂学长备份」文件夹。
 * 只申请 drive.file 权限：只能看到本应用自己创建的文件。令牌用 Android Keystore 加密保存，绝不写日志。
 */
object GDrive {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val FOLDER = "课堂学长备份"
    const val AUTH = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN = "https://oauth2.googleapis.com/token"
    const val API = "https://www.googleapis.com/drive/v3"
    const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    const val CHUNK = 4 * 256 * 1024 // 必须是 256 KiB 的整数倍

    fun clientId(): String = Store.settings.value.gClientId.trim().ifBlank { BuildConfig.GOOGLE_CLIENT_ID }
    fun clientSecret(): String = Store.settings.value.gClientSecret.trim().ifBlank { BuildConfig.GOOGLE_CLIENT_SECRET }
    fun configured() = clientId().endsWith(".apps.googleusercontent.com")

    /** 登录进行中的状态文字（null = 空闲）。 */
    val loginState = mutableStateOf<String?>(null)

    // ---------------- 纯函数（可单测） ----------------
    private fun e(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun authUrl(clientId: String, redirect: String, challenge: String, state: String, loginHint: String = ""): String =
        AUTH + "?" + linkedMapOf("client_id" to clientId, "redirect_uri" to redirect, "response_type" to "code", "scope" to SCOPE,
            "code_challenge" to challenge, "code_challenge_method" to "S256", "state" to state, "access_type" to "offline", "prompt" to "consent")
            .let { m -> if (loginHint.isNotBlank()) m + ("login_hint" to loginHint) else m }
            .entries.joinToString("&") { "${it.key}=${e(it.value)}" }

    /** 308 响应的 Range 头 "bytes=0-1048575" → 下一个要传的字节位置。 */
    fun nextOffset(range: String?): Long = range?.let { Regex("bytes=\\d+-(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull()?.plus(1) } ?: 0L

    fun contentRange(start: Long, len: Int, total: Long): String = if (len <= 0) "bytes */$total" else "bytes $start-${start + len - 1}/$total"

    fun parseTime(rfc3339: String): Long = runCatching {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        f.parse(rfc3339.take(19))!!.time
    }.getOrDefault(0L)

    fun safeName(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().take(80).ifBlank { "笔记" }

    fun md5(b: ByteArray): String = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }

    // ---------------- 授权 ----------------
    private val noRedirect: OkHttpClient by lazy { Api.http.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    private fun openTab(ctx: Context, url: String) {
        // Custom Tab 协议：ACTION_VIEW + EXTRA_SESSION（无需引入 androidx.browser）。没有支持的浏览器时退化为普通浏览器。
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        i.putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
        i.putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1)
        if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun bringBack(ctx: Context) {
        val i = Intent(ctx, app.xuezhang.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { ctx.startActivity(i) }
    }

    @Volatile private var server: ServerSocket? = null
    fun cancel() { runCatching { server?.close() }; server = null; loginState.value = null }

    /** 阻塞直到授权完成（IO 线程）。 */
    fun login(ctx: Context) {
        if (!configured()) throw ApiException("还没有配置 Google OAuth 客户端 ID（见「Google Drive 设置说明」）")
        cancel()
        val verifier = Auth.b64url(Auth.rnd(48)); val state = Auth.b64url(Auth.rnd(24))
        val srv = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")); server = srv
        val redirect = "http://127.0.0.1:${srv.localPort}"
        try {
            loginState.value = "已打开 Google 授权页，请在浏览器里选择账号并允许…"
            val url = authUrl(clientId(), redirect, Auth.challenge(verifier), state, Auth.google.value?.email.orEmpty())
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                runCatching { openTab(ctx, url) }.onFailure { loginState.value = "没有可用的浏览器" }
            }
            val back = "<p><a href='intent://back#Intent;scheme=xuezhang;package=app.xuezhang;end' style='display:inline-block;margin-top:24px;padding:14px 28px;border-radius:999px;background:#0A72E8;color:#fff;text-decoration:none;font-size:18px'>返回课堂学长</a></p>"
            val code = Auth.waitCallback(listOf(srv), state, "/", "Google Drive", { bringBack(ctx.applicationContext) }, back)
            loginState.value = "正在换取令牌…"
            val form = FormBody.Builder().add("grant_type", "authorization_code").add("code", code).add("redirect_uri", redirect)
                .add("client_id", clientId()).add("code_verifier", verifier)
            clientSecret().takeIf { it.isNotBlank() }?.let { form.add("client_secret", it) }
            val t = Api.http.newCall(Request.Builder().url(TOKEN).post(form.build()).build()).execute().use { r ->
                val s = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw ApiException("Google 换取令牌失败 ${r.code}：" + errText(s))
                JSONObject(s)
            }
            if (!t.optString("scope").contains("drive.file")) throw ApiException("没有勾选「查看、修改和删除此应用使用的 Google 云端硬盘文件」权限，请重新连接并勾选")
            var tok = Token(t.getString("access_token"), t.optString("refresh_token"), System.currentTimeMillis() + t.optLong("expires_in", 3600) * 1000)
            Auth.google.value = tok
            loginState.value = "读取账号信息…"
            val about = getJson("$API/about?fields=user(displayName,emailAddress,photoLink)")
            val u = about.optJSONObject("user")
            tok = tok.copy(email = u?.optString("emailAddress").orEmpty(), plan = u?.optString("displayName").orEmpty())
            Auth.google.value = tok; Auth.persist()
            Store.updateSettings(Store.settings.value.copy(gFolderId = ""))
            Log.ok("Google Drive", "已连接 ${tok.email.replace(Regex("(?<=.).(?=[^@]*@)"), "*")}")
        } finally { runCatching { srv.close() }; server = null; loginState.value = null }
    }

    private fun errText(s: String): String = runCatching { JSONObject(s).let { o -> o.optString("error_description").ifBlank { o.optJSONObject("error")?.optString("message") ?: o.optString("error") } } }.getOrNull().orEmpty().ifBlank { s.take(120) }

    @Synchronized
    fun token(): String {
        val t = Auth.google.value ?: throw ApiException("请先连接 Google Drive")
        if (t.expires > System.currentTimeMillis() + 120_000) return t.access
        if (t.refresh.isBlank()) throw ApiException("Google 授权已过期，请重新连接")
        val form = FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", t.refresh).add("client_id", clientId())
        clientSecret().takeIf { it.isNotBlank() }?.let { form.add("client_secret", it) }
        val o = Api.http.newCall(Request.Builder().url(TOKEN).post(form.build()).build()).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException("Google 授权已过期或被撤销，请重新连接（${r.code}）")
            JSONObject(s)
        }
        val n = t.copy(access = o.getString("access_token"), expires = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000,
            refresh = o.optString("refresh_token").ifBlank { t.refresh })
        Auth.google.value = n; Auth.persist()
        return n.access
    }

    fun logout() {
        val t = Auth.google.value
        Auth.google.value = null; Auth.persist()
        Store.updateSettings(Store.settings.value.copy(gFolderId = ""))
        uploads.clear()
        Log.ok("Google Drive", "已断开连接")
        if (t != null) Thread { runCatching { Api.http.newCall(Request.Builder().url("https://oauth2.googleapis.com/revoke").post(FormBody.Builder().add("token", t.refresh.ifBlank { t.access }).build()).build()).execute().close() } }.start()
    }

    // ---------------- Drive REST ----------------
    private fun req(url: String) = Request.Builder().url(url).header("Authorization", "Bearer " + token())

    private fun getJson(url: String): JSONObject = Api.http.newCall(req(url).get().build()).execute().use { r ->
        val s = r.body?.string().orEmpty()
        if (r.code == 401) { Auth.google.value = Auth.google.value?.copy(expires = 0); throw ApiException("Google 授权失效，请重新连接") }
        if (!r.isSuccessful) throw ApiException("Drive 请求失败 ${r.code}：" + errText(s))
        JSONObject(s)
    }

    data class DFile(val id: String, val name: String, val size: Long, val modified: Long, val mime: String, val md5: String)

    private fun q(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")

    /** 找到或创建应用文件夹。 */
    fun folder(): String {
        Store.settings.value.gFolderId.takeIf { it.isNotBlank() }?.let { id ->
            val ok = runCatching { getJson("$API/files/$id?fields=id,trashed").optBoolean("trashed").not() }.getOrDefault(false)
            if (ok) return id
        }
        val found = getJson("$API/files?q=" + e("name='${q(FOLDER)}' and mimeType='application/vnd.google-apps.folder' and trashed=false") + "&fields=files(id)&spaces=drive")
            .optJSONArray("files")?.optJSONObject(0)?.optString("id")
        val id = found ?: Api.http.newCall(req("$API/files?fields=id").post(JSONObject().put("name", FOLDER)
            .put("mimeType", "application/vnd.google-apps.folder").toString().toRequestBody("application/json; charset=UTF-8".toMediaType())).build()).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException("创建 Drive 文件夹失败 ${r.code}：" + errText(s))
            JSONObject(s).getString("id")
        }
        Store.updateSettings(Store.settings.value.copy(gFolderId = id))
        return id
    }

    fun list(): List<DFile> {
        val fid = folder()
        val out = mutableListOf<DFile>(); var page: String? = null
        do {
            val o = getJson("$API/files?q=" + e("'$fid' in parents and trashed=false") + "&orderBy=modifiedTime%20desc&pageSize=200" +
                "&fields=nextPageToken,files(id,name,size,modifiedTime,mimeType,md5Checksum)" + (page?.let { "&pageToken=" + e(it) } ?: ""))
            o.optJSONArray("files")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { f ->
                out.add(DFile(f.optString("id"), f.optString("name"), f.optString("size").toLongOrNull() ?: 0L, parseTime(f.optString("modifiedTime")), f.optString("mimeType"), f.optString("md5Checksum")))
            } }
            page = o.optString("nextPageToken").ifBlank { null }
        } while (page != null)
        return out
    }

    /**
     * 可续传上传：先 POST（新建）或 PATCH（覆盖已有文件）拿到会话地址，再按 1 MiB 分块 PUT，
     * 服务器回 308 + Range 表示还要继续；网络中断时用 "bytes * /总长" 查询已收到多少再续传。
     */
    fun upload(name: String, bytes: ByteArray, mime: String, existingId: String?, onProgress: (Long, Long) -> Unit): DFile {
        val total = bytes.size.toLong()
        val meta = JSONObject().put("name", name).apply { if (existingId == null) put("parents", org.json.JSONArray().put(folder())) }
        val init = req(if (existingId == null) "$UPLOAD/files?uploadType=resumable&fields=id,name,size,modifiedTime,mimeType,md5Checksum"
            else "$UPLOAD/files/$existingId?uploadType=resumable&fields=id,name,size,modifiedTime,mimeType,md5Checksum")
            .header("X-Upload-Content-Type", mime).header("X-Upload-Content-Length", total.toString())
        val body = meta.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
        val session = noRedirect.newCall((if (existingId == null) init.post(body) else init.patch(body)).build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("开始上传失败 ${r.code}：" + errText(r.body?.string().orEmpty()))
            r.header("Location") ?: throw ApiException("Drive 没有返回上传会话地址")
        }
        var off = 0L; var fails = 0
        onProgress(0, total)
        while (true) {
            val len = minOf(CHUNK.toLong(), total - off).toInt()
            try {
                val rb = bytes.copyOfRange(off.toInt(), off.toInt() + len).toRequestBody(mime.toMediaType())
                noRedirect.newCall(Request.Builder().url(session).header("Content-Range", contentRange(off, len, total)).put(rb).build()).execute().use { r ->
                    when {
                        r.code == 308 -> { off = nextOffset(r.header("Range")); onProgress(off, total) }
                        r.isSuccessful -> { onProgress(total, total); val f = JSONObject(r.body?.string().orEmpty())
                            return DFile(f.optString("id"), f.optString("name"), f.optString("size").toLongOrNull() ?: total, parseTime(f.optString("modifiedTime")), f.optString("mimeType"), f.optString("md5Checksum")) }
                        r.code == 404 || r.code == 410 -> throw ApiException("上传会话已失效（${r.code}），请重试")
                        r.code in 500..599 -> throw java.io.IOException("服务器 ${r.code}")
                        else -> throw ApiException("上传失败 ${r.code}：" + errText(r.body?.string().orEmpty()))
                    }
                }
                fails = 0
            } catch (e: java.io.IOException) {
                if (++fails > 4) throw ApiException("网络中断，上传失败：${e.message}")
                Thread.sleep(800L * fails)
                // 查询服务器已经收到多少字节
                runCatching {
                    noRedirect.newCall(Request.Builder().url(session).header("Content-Range", contentRange(0, 0, total)).put(ByteArray(0).toRequestBody(null)).build()).execute().use { r ->
                        if (r.code == 308) off = nextOffset(r.header("Range"))
                    }
                }
            }
        }
    }

    fun download(id: String): ByteArray = Api.http.newCall(req("$API/files/$id?alt=media").get().build()).execute().use { r ->
        if (!r.isSuccessful) throw ApiException("下载失败 ${r.code}")
        r.body!!.bytes()
    }

    fun delete(id: String) = Api.http.newCall(req("$API/files/$id").delete().build()).execute().use { r ->
        if (!r.isSuccessful && r.code != 404) throw ApiException("删除失败 ${r.code}")
    }

    // ---------------- 上传状态（界面展示 + 持久化） ----------------
    enum class St { Waiting, Uploading, Done, Skipped, Failed }
    data class Item(val name: String, val size: Long, val st: St, val sent: Long = 0, val at: Long = System.currentTimeMillis(), val err: String = "")
    val uploads = mutableStateListOf<Item>()
    val syncing = mutableStateOf(false)

    private fun statusFile() = File(Store.dir, "gdrive_status.json")
    fun loadStatus() = runCatching {
        if (uploads.isNotEmpty()) return@runCatching
        val a = org.json.JSONArray(statusFile().readText())
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { o ->
            val st = runCatching { St.valueOf(o.optString("st")) }.getOrDefault(St.Failed).let { if (it == St.Uploading || it == St.Waiting) St.Failed else it }
            uploads.add(Item(o.optString("n"), o.optLong("s"), st, o.optLong("x"), o.optLong("t"), o.optString("e").ifBlank { if (st == St.Failed && o.optString("st") != "Failed") "上次上传被中断" else "" }))
        }
    }
    private fun saveStatus() = runCatching {
        statusFile().writeText(org.json.JSONArray().also { a -> uploads.toList().forEach { a.put(JSONObject().put("n", it.name).put("s", it.size).put("st", it.st.name).put("x", it.sent).put("t", it.at).put("e", it.err)) } }.toString())
    }
    private fun setItem(i: Int, it: Item) { if (i in uploads.indices) uploads[i] = it }

    data class Local(val name: String, val mime: String, val bytes: () -> ByteArray)

    /** 待同步的本地文件：完整备份包 + 每节笔记的 Markdown（主线程取数据快照）。 */
    fun locals(): List<Local> {
        val out = mutableListOf(Local(app.xuezhang.engine.Sync.FILE, "application/zip") { app.xuezhang.engine.Sync.pack() })
        Store.lectures.filter { it.status == 2 }.forEach { l ->
            val c = Store.course(l.courseId)?.name.orEmpty()
            val n = safeName(listOf(c, l.title).filter { it.isNotBlank() }.joinToString(" - ")) + "-" + l.id.take(4) + ".md"
            out.add(Local(n, "text/markdown") { app.xuezhang.engine.NoteExport.markdown(l).toByteArray() })
        }
        return out
    }

    /** 手动同步：逐个上传，内容没变的跳过（对比 Drive 返回的 md5）。返回 (上传数, 跳过数, 失败数)。 */
    fun syncAll(items: List<Local>, onOverall: (Float, String) -> Unit): Triple<Int, Int, Int> {
        syncing.value = true
        try {
            val remote = list().associateBy { it.name }
            uploads.clear(); items.forEach { uploads.add(Item(it.name, 0, St.Waiting)) }
            var up = 0; var skip = 0; var fail = 0
            items.forEachIndexed { i, l ->
                onOverall(i.toFloat() / items.size, l.name)
                try {
                    val b = l.bytes()
                    val r = remote[l.name]
                    if (r != null && r.md5.isNotBlank() && r.md5 == md5(b)) { setItem(i, Item(l.name, b.size.toLong(), St.Skipped, b.size.toLong(), r.modified)); skip++; return@forEachIndexed }
                    setItem(i, Item(l.name, b.size.toLong(), St.Uploading))
                    val f = upload(l.name, b, l.mime, r?.id) { s, t ->
                        setItem(i, Item(l.name, t, St.Uploading, s))
                        onOverall((i + if (t > 0) s.toFloat() / t else 0f) / items.size, l.name)
                    }
                    setItem(i, Item(l.name, f.size, St.Done, f.size, if (f.modified > 0) f.modified else System.currentTimeMillis())); up++
                } catch (e: Exception) {
                    setItem(i, Item(l.name, uploads.getOrNull(i)?.size ?: 0, St.Failed, uploads.getOrNull(i)?.sent ?: 0, err = e.message ?: "失败")); fail++
                    Log.err("Google Drive", e)
                }
            }
            onOverall(1f, "")
            Store.updateSettings(Store.settings.value.copy(gLastSync = System.currentTimeMillis(), lastSync = System.currentTimeMillis()))
            Log.ok("Google Drive", "同步完成：上传 $up · 未变 $skip · 失败 $fail")
            return Triple(up, skip, fail)
        } finally { saveStatus(); syncing.value = false }
    }
}
