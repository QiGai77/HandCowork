package app.xuezhang.engine

import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 应用内日志：环形缓冲（最近 2000 条）持久化到 files/data/log.jsonl。绝不记录 Key / Token。 */
object Log {
    const val MAX = 2000
    data class E(val t: Long, val tag: String, val msg: String, val ok: Boolean, val detail: String = "")

    private val lock = Any()
    private val buf = ArrayDeque<E>()
    private var file: File? = null
    private var appended = 0
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun init(dir: File) = synchronized(lock) {
        file = File(dir, "log.jsonl")
        runCatching {
            file!!.takeIf { it.exists() }?.readLines()?.takeLast(MAX)?.forEach { l ->
                runCatching { JSONObject(l) }.getOrNull()?.let { j -> buf.addLast(E(j.optLong("t"), j.optString("g"), j.optString("m"), j.optBoolean("ok", true), j.optString("d"))) }
            }
        }
    }

    private val SECRET = Regex("(?i)(sk-[A-Za-z0-9_\\-]{8,}|Bearer\\s+[A-Za-z0-9._\\-]+|eyJ[A-Za-z0-9._\\-]{20,}|(api[_-]?key|token|access_token|refresh_token|password|SESSDATA)[\"'=:\\s]+[^\\s,\"'&]+)")
    fun redact(s: String): String = s.replace(SECRET) { m -> m.value.take(6) + "***" }

    fun add(tag: String, msg: String, ok: Boolean = true, detail: String = "") {
        val e = E(System.currentTimeMillis(), tag, redact(msg).take(600), ok, redact(detail).take(1500))
        synchronized(lock) {
            buf.addLast(e); while (buf.size > MAX) buf.removeFirst()
            file?.let { f ->
                runCatching {
                    f.appendText(JSONObject().put("t", e.t).put("g", e.tag).put("m", e.msg).put("ok", e.ok).put("d", e.detail).toString() + "\n")
                    if (++appended > 200) { appended = 0; f.writeText(buf.joinToString("") { x -> JSONObject().put("t", x.t).put("g", x.tag).put("m", x.msg).put("ok", x.ok).put("d", x.detail).toString() + "\n" }) }
                }
            }
        }
        version.value++
    }

    fun ok(tag: String, msg: String) = add(tag, msg, true)
    fun fail(tag: String, msg: String) = add(tag, msg, false)
    fun err(tag: String, e: Throwable, msg: String? = null) = add(tag, msg ?: (e.message ?: e.javaClass.simpleName), false,
        e.javaClass.name + "\n" + e.stackTrace.take(8).joinToString("\n") { "  at $it" })

    fun all(): List<E> = synchronized(lock) { buf.toList() }
    fun clear() = synchronized(lock) { buf.clear(); runCatching { file?.writeText("") }; version.value++ }

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
    fun line(e: E) = "${fmt.format(Date(e.t))} ${if (e.ok) "OK " else "ERR"} [${e.tag}] ${e.msg}" + if (e.detail.isNotBlank()) "\n${e.detail}" else ""
    fun export(errorsOnly: Boolean = false): String = all().filter { !errorsOnly || !it.ok }.joinToString("\n") { line(it) }

    /** 计时一次 API 调用并记录 provider/model/状态/耗时。 */
    inline fun <T> api(what: String, provider: String, model: String, block: () -> T): T {
        val t0 = System.currentTimeMillis()
        return try { block().also { ok("API", "$what · $provider · $model · 成功 · ${System.currentTimeMillis() - t0}ms") } }
        catch (e: Throwable) { err("API", e, "$what · $provider · $model · 失败 · ${System.currentTimeMillis() - t0}ms · ${e.message?.take(200)}"); throw e }
    }
}
