package app.xuezhang.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 账号登录（OAuth）令牌。保存在 app 私有目录，用 Android Keystore 的 AES-GCM 加密。 */
data class Token(
    val access: String,
    val refresh: String = "",
    val expires: Long = 0,          // epoch ms，0 = 未知
    val accountId: String = "",     // ChatGPT: chatgpt_account_id
    val email: String = "",
    val plan: String = "",
    val idToken: String = "",
) {
    fun j() = JSONObject().put("a", access).put("r", refresh).put("e", expires).put("acc", accountId).put("em", email).put("pl", plan).put("id", idToken)
    companion object {
        fun of(o: JSONObject?) = o?.let { Token(it.optString("a"), it.optString("r"), it.optLong("e"), it.optString("acc"), it.optString("em"), it.optString("pl"), it.optString("id")) }
            ?.takeIf { it.access.isNotBlank() }
    }
}

/** 5 小时 / 每周额度。percent 为已用百分比（0..100），reset 为重置时刻 epoch ms。 */
data class Quota(val fivePct: Double? = null, val fiveReset: Long? = null, val weekPct: Double? = null, val weekReset: Long? = null,
                 val at: Long = System.currentTimeMillis(), val error: String? = null)

object Auth {
    const val GPT_CLIENT = "app_EMoamEEZ73f0CkXaXp7hrann"
    const val GPT_REDIRECT = "http://localhost:1455/auth/callback"
    const val GPT_ISSUER = "https://auth.openai.com"
    const val CLAUDE_CLIENT = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    const val CLAUDE_REDIRECT = "https://console.anthropic.com/oauth/code/callback"
    const val CLAUDE_TOKEN = "https://console.anthropic.com/v1/oauth/token"

    val chatgpt = mutableStateOf<Token?>(null)
    val claude = mutableStateOf<Token?>(null)
    val gptQuota = mutableStateOf<Quota?>(null)
    val claudeQuota = mutableStateOf<Quota?>(null)
    /** ChatGPT 登录进行中时的状态文字（null = 空闲）。 */
    val gptLoginState = mutableStateOf<String?>(null)

    private lateinit var file: File
    private val JSON = "application/json".toMediaType()

    fun init(ctx: Context) {
        file = File(File(ctx.filesDir, "data").apply { mkdirs() }, "auth.bin")
        runCatching {
            if (!file.exists()) return
            val o = JSONObject(decrypt(file.readBytes()))
            chatgpt.value = Token.of(o.optJSONObject("chatgpt"))
            claude.value = Token.of(o.optJSONObject("claude"))
        }
    }

    @Synchronized
    private fun persist() {
        if (!::file.isInitialized) return
        val o = JSONObject()
        chatgpt.value?.let { o.put("chatgpt", it.j()) }
        claude.value?.let { o.put("claude", it.j()) }
        runCatching { val tmp = File(file.parentFile, "auth.bin.tmp"); tmp.writeBytes(encrypt(o.toString())); tmp.renameTo(file) }
    }

    fun logoutChatGPT() { chatgpt.value = null; gptQuota.value = null; persist() }
    fun logoutClaude() { claude.value = null; claudeQuota.value = null; persist() }

    // ---------------- Keystore AES-GCM ----------------
    private const val ALIAS = "xuezhang_auth"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }
    private fun encrypt(s: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        return byteArrayOf(c.iv.size.toByte()) + c.iv + c.doFinal(s.toByteArray())
    }
    private fun decrypt(b: ByteArray): String {
        val n = b[0].toInt(); val iv = b.copyOfRange(1, 1 + n)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(b.copyOfRange(1 + n, b.size)))
    }

    // ---------------- PKCE ----------------
    private fun b64url(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun rnd(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }
    private fun challenge(v: String) = b64url(MessageDigest.getInstance("SHA-256").digest(v.toByteArray()))
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun query(m: Map<String, String>) = m.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }

    fun jwtClaims(jwt: String): JSONObject? = runCatching {
        JSONObject(String(Base64.decode(jwt.split(".")[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
    }.getOrNull()

    private fun open(ctx: Context, url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ================= ChatGPT（与 Codex CLI 相同的 PKCE 流程） =================
    @Volatile private var gptServer: ServerSocket? = null

    /** 阻塞直到登录完成或失败（在 IO 线程调用）。浏览器回调到 http://localhost:1455/auth/callback，由 app 内的小 HTTP 服务接住。 */
    fun loginChatGPT(ctx: Context) {
        cancelChatGPT()
        val verifier = b64url(rnd(64)); val state = b64url(rnd(32))
        val server = runCatching { ServerSocket(1455, 4, InetAddress.getByName("127.0.0.1")) }
            .getOrElse { throw ApiException("端口 1455 被占用，关掉其他正在登录的应用再试") }
        gptServer = server
        // 有的浏览器把 localhost 解析到 ::1，额外监听 IPv6 回环
        val v6 = runCatching { ServerSocket(1455, 4, InetAddress.getByName("::1")) }.getOrNull()
        try {
            val url = "$GPT_ISSUER/oauth/authorize?" + query(linkedMapOf(
                "response_type" to "code", "client_id" to GPT_CLIENT, "redirect_uri" to GPT_REDIRECT,
                "scope" to "openid profile email offline_access", "code_challenge" to challenge(verifier),
                "code_challenge_method" to "S256", "id_token_add_organizations" to "true",
                "codex_cli_simplified_flow" to "true", "state" to state, "originator" to "codex_cli_rs"))
            gptLoginState.value = "已打开浏览器，请在浏览器里登录 ChatGPT…"
            android.os.Handler(android.os.Looper.getMainLooper()).post { runCatching { open(ctx, url) }.onFailure { gptLoginState.value = "没有可用的浏览器" } }
            val code = waitCallback(listOfNotNull(server, v6), state)
            gptLoginState.value = "正在换取令牌…"
            val body = FormBody.Builder().add("grant_type", "authorization_code").add("code", code).add("redirect_uri", GPT_REDIRECT)
                .add("client_id", GPT_CLIENT).add("code_verifier", verifier).build()
            val t = Api.http.newCall(Request.Builder().url("$GPT_ISSUER/oauth/token").post(body).build()).execute().use { r ->
                val s = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw ApiException("换取令牌失败 ${r.code}：${s.take(120)}")
                JSONObject(s)
            }
            chatgpt.value = gptToken(t, null); persist()
        } finally {
            runCatching { server.close() }; runCatching { v6?.close() }; gptServer = null; gptLoginState.value = null
        }
    }

    fun cancelChatGPT() { runCatching { gptServer?.close() }; gptServer = null }

    private fun waitCallback(servers: List<ServerSocket>, state: String): String {
        val deadline = System.currentTimeMillis() + 5 * 60_000
        var result: String? = null; var error: String? = null
        val lock = Object()
        val threads = servers.map { srv ->
            Thread {
                srv.soTimeout = 1000
                while (System.currentTimeMillis() < deadline && result == null && error == null && !srv.isClosed) {
                    val sock = try { srv.accept() } catch (_: SocketTimeoutException) { continue } catch (_: Exception) { break }
                    sock.use { so ->
                        so.soTimeout = 5000
                        val line = runCatching { so.getInputStream().bufferedReader().readLine() }.getOrNull().orEmpty()
                        val path = line.split(" ").getOrNull(1).orEmpty()
                        val q = path.substringAfter('?', "").split("&").filter { it.contains('=') }
                            .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
                        val ok = path.startsWith("/auth/callback") && q["code"] != null && q["state"] == state
                        val msg = when {
                            ok -> "登录成功，可以回到「课堂学长」了。"
                            path.startsWith("/auth/callback") -> "登录失败：" + (q["error_description"] ?: q["error"] ?: "state 不匹配")
                            else -> "课堂学长正在等待 ChatGPT 登录…"
                        }
                        val html = "<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'></head><body style='font-family:sans-serif;padding:40px;text-align:center'><h2>$msg</h2></body></html>"
                        val bytes = html.toByteArray()
                        runCatching { so.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(bytes); flush() } }
                        synchronized(lock) {
                            if (ok) { result = q["code"]; lock.notifyAll() }
                            else if (path.startsWith("/auth/callback")) { error = msg; lock.notifyAll() }
                        }
                    }
                }
                synchronized(lock) { lock.notifyAll() }
            }.apply { isDaemon = true; start() }
        }
        synchronized(lock) {
            while (result == null && error == null && System.currentTimeMillis() < deadline && servers.any { !it.isClosed } && threads.any { it.isAlive })
                lock.wait(1000)
        }
        result?.let { return it }
        throw ApiException(error ?: if (servers.all { it.isClosed }) "已取消登录" else "登录超时，请重试")
    }

    private fun gptToken(t: JSONObject, old: Token?): Token {
        val id = t.optString("id_token").ifBlank { old?.idToken.orEmpty() }
        val claims = jwtClaims(id)
        val authC = claims?.optJSONObject("https://api.openai.com/auth")
        val access = t.getString("access_token")
        val exp = jwtClaims(access)?.optLong("exp")?.takeIf { it > 0 }?.times(1000)
            ?: t.optLong("expires_in").takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 1000 } ?: 0L
        return Token(access, t.optString("refresh_token").ifBlank { old?.refresh.orEmpty() }, exp,
            authC?.optString("chatgpt_account_id").orEmpty().ifBlank { old?.accountId.orEmpty() },
            claims?.optString("email").orEmpty().ifBlank { old?.email.orEmpty() },
            authC?.optString("chatgpt_plan_type").orEmpty().ifBlank { old?.plan.orEmpty() }, id)
    }

    @Synchronized
    fun refreshChatGPT(): Token {
        val old = chatgpt.value ?: throw ApiException("请先用 ChatGPT 账号登录")
        if (old.refresh.isBlank()) throw ApiException("ChatGPT 登录已过期，请重新登录")
        val o = JSONObject().put("client_id", GPT_CLIENT).put("grant_type", "refresh_token").put("refresh_token", old.refresh).put("scope", "openid profile email")
        val t = Api.http.newCall(Request.Builder().url("$GPT_ISSUER/oauth/token").post(o.toString().toRequestBody(JSON)).build()).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException("ChatGPT 登录已过期，请重新登录（${r.code}）")
            JSONObject(s)
        }
        return gptToken(t, old).also { chatgpt.value = it; persist() }
    }

    /** 取一个有效的 ChatGPT 令牌，快过期（< 5 分钟）时自动刷新。 */
    fun gpt(): Token {
        val t = chatgpt.value ?: throw ApiException("请先在「我的 › 大模型」里用 ChatGPT 账号登录")
        return if (t.expires in 1 until System.currentTimeMillis() + 5 * 60_000) refreshChatGPT() else t
    }

    // ================= Claude 账号（Claude Code 风格：手动粘贴 code#state） =================
    @Volatile private var claudeVerifier: String? = null

    fun startClaude(ctx: Context) {
        val v = b64url(rnd(32)); claudeVerifier = v
        val url = "https://claude.ai/oauth/authorize?" + query(linkedMapOf(
            "code" to "true", "client_id" to CLAUDE_CLIENT, "response_type" to "code", "redirect_uri" to CLAUDE_REDIRECT,
            "scope" to "org:create_api_key user:profile user:inference", "code_challenge" to challenge(v),
            "code_challenge_method" to "S256", "state" to v))
        open(ctx, url)
    }

    /** 用户把授权页显示的「code#state」粘回来。 */
    fun finishClaude(pasted: String) {
        val v = claudeVerifier ?: throw ApiException("请先点「去浏览器授权」")
        val raw = pasted.trim().substringAfter("code=").substringBefore('&')
        val code = raw.substringBefore('#').trim(); val state = raw.substringAfter('#', v).trim()
        if (code.isBlank()) throw ApiException("没有识别到授权码")
        val o = JSONObject().put("code", code).put("state", state).put("grant_type", "authorization_code").put("client_id", CLAUDE_CLIENT)
            .put("redirect_uri", CLAUDE_REDIRECT).put("code_verifier", v)
        val t = Api.http.newCall(Request.Builder().url(CLAUDE_TOKEN).post(o.toString().toRequestBody(JSON)).build()).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException("Claude 授权失败 ${r.code}：${s.take(120)}")
            JSONObject(s)
        }
        claude.value = claudeToken(t, null); claudeVerifier = null; persist()
    }

    private fun claudeToken(t: JSONObject, old: Token?) = Token(t.getString("access_token"),
        t.optString("refresh_token").ifBlank { old?.refresh.orEmpty() },
        t.optLong("expires_in").takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 1000 } ?: 0L,
        t.optJSONObject("organization")?.optString("uuid").orEmpty(),
        t.optJSONObject("account")?.optString("email_address").orEmpty().ifBlank { old?.email.orEmpty() })

    @Synchronized
    fun refreshClaude(): Token {
        val old = claude.value ?: throw ApiException("请先用 Claude 账号登录")
        if (old.refresh.isBlank()) throw ApiException("Claude 登录已过期，请重新登录")
        val o = JSONObject().put("grant_type", "refresh_token").put("refresh_token", old.refresh).put("client_id", CLAUDE_CLIENT)
        val t = Api.http.newCall(Request.Builder().url(CLAUDE_TOKEN).post(o.toString().toRequestBody(JSON)).build()).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException("Claude 登录已过期，请重新登录（${r.code}）")
            JSONObject(s)
        }
        return claudeToken(t, old).also { claude.value = it; persist() }
    }

    fun cl(): Token {
        val t = claude.value ?: throw ApiException("请先在「我的 › 大模型」里用 Claude 账号登录")
        return if (t.expires in 1 until System.currentTimeMillis() + 5 * 60_000) refreshClaude() else t
    }
}
