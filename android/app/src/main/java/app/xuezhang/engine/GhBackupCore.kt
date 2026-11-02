package app.xuezhang.engine

import app.xuezhang.data.Settings
import org.json.JSONObject
import java.security.MessageDigest

/**
 * v0.0.7.7 GitHub 备份：纯逻辑（无 Android 依赖，可单测）。
 *
 * 仓库布局（默认私有仓库 xuezhang-backup）：
 *   manifest.json                   清单：备份时间、设备、每个文件的 git blob SHA 与大小、本机路径 → 仓库路径
 *   data/courses.json               课程 / 课表
 *   data/lectures/<id>.json         每节笔记一个文件（含复习卡、概念图节点、口诀），只有改过的笔记才会重新上传
 *   data/settings.json              偏好与自定义提示词（白名单字段，绝不含 Key / 令牌 / 密码）
 *   files/<目录>/<文件名>            封面、课件、视频截图、AI 生成图、头像、（可选）录音
 */
object GhBackupCore {
    const val DEFAULT_REPO = "xuezhang-backup"
    const val MAX_FILE = 50L * 1024 * 1024        // GitHub API 单文件上限约 100MB，这里留足余量
    const val MANIFEST = "manifest.json"

    /** 会被打包的 filesDir 子目录 → 仓库里的目录名。 */
    val DIRS = linkedMapOf("covers" to "covers", "slides" to "slides", "frames" to "frames", "gen" to "gen", "avatars" to "avatars", "audio" to "audio")
    val AUDIO_EXT = setOf("wav", "m4a", "mp3", "aac", "ogg", "opus", "amr", "flac", "3gp")
    /** 永远不进备份的文件：数据库（含 Key）、登录凭据、日志、签名文件、缓存。 */
    private val SECRET_NAMES = setOf("db.json", "db.json.tmp", "auth.bin", "log.jsonl")
    private val SECRET_EXT = setOf("jks", "keystore", "p12", "pem", "key", "bin")
    private val SECRET_DIRS = setOf("data", "shared_prefs", "datastore", "slidecache", "cache", "code_cache", "no_backup")

    /** 仓库名规则：字母数字 . _ -，最长 100。 */
    fun cleanRepo(s: String): String = s.trim().replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-').take(100).ifBlank { DEFAULT_REPO }

    // ---------------- 文件选择 ----------------
    data class Local(val rel: String, val size: Long)          // rel：相对 filesDir，如 "covers/a.jpg"
    data class Pick(val included: List<Local>, val skipped: List<Pair<Local, String>>)

    fun isAudio(name: String) = name.substringAfterLast('.', "").lowercase() in AUDIO_EXT

    /** 哪些文件进备份：只收白名单目录；排除凭据/数据库/日志；录音默认不收；超过 50MB 跳过。 */
    fun select(files: List<Local>, includeAudio: Boolean): Pick {
        val inc = ArrayList<Local>(); val skip = ArrayList<Pair<Local, String>>()
        for (f in files) {
            val parts = f.rel.replace('\\', '/').split('/').filter { it.isNotBlank() }
            val name = parts.lastOrNull().orEmpty()
            val top = parts.firstOrNull().orEmpty()
            val reason = when {
                parts.size < 2 || parts.any { it == ".." } -> "不在备份目录里"
                top in SECRET_DIRS || name in SECRET_NAMES || name.substringAfterLast('.', "").lowercase() in SECRET_EXT || name.startsWith(".") -> "含账号或密钥，不备份"
                top !in DIRS -> "不在备份目录里"
                isAudio(name) && !includeAudio -> "录音（未开启「包含录音」）"
                f.size > MAX_FILE -> "超过 50MB，GitHub 放不下"
                else -> null
            }
            if (reason == null) inc.add(f) else skip.add(f to reason)
        }
        return Pick(inc.sortedBy { it.rel }, skip)
    }

    fun repoPath(rel: String): String { val p = rel.replace('\\', '/').trimStart('/'); return "files/" + (DIRS[p.substringBefore('/')] ?: p.substringBefore('/')) + "/" + p.substringAfter('/') }

    // ---------------- git blob SHA ----------------
    /** 与 `git hash-object` 一致：sha1("blob <len>\0" + 内容)。 */
    fun blobSha(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${bytes.size}\u0000".toByteArray(Charsets.US_ASCII)); md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    fun blobSha(stream: java.io.InputStream, size: Long): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob $size\u0000".toByteArray(Charsets.US_ASCII))
        val buf = ByteArray(64 * 1024)
        stream.use { while (true) { val n = it.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ---------------- 变化检测 ----------------
    data class Plan(val upload: List<String>, val unchanged: List<String>, val deleted: List<String>) {
        val changed get() = upload.isNotEmpty() || deleted.isNotEmpty()
    }

    /** local / remote：仓库路径 → blob SHA。manifest.json 不参与比较（每次都会重写）。 */
    fun plan(local: Map<String, String>, remote: Map<String, String>): Plan {
        val l = local - MANIFEST; val r = remote - MANIFEST
        val up = l.filter { (p, sha) -> r[p] != sha }.keys.sorted()
        val same = l.filter { (p, sha) -> r[p] == sha }.keys.sorted()
        val del = r.keys.filter { it !in l && (it.startsWith("data/") || it.startsWith("files/")) }.sorted()
        return Plan(up, same, del)
    }

    // ---------------- 清单 ----------------
    data class Entry(val sha: String, val size: Long)
    data class Manifest(val at: Long, val device: String, val appVersion: String, val courses: Int, val lectures: Int,
                        val files: Map<String, Entry>, val paths: Map<String, String> = emptyMap(), val includeAudio: Boolean = false) {
        fun toJson(): JSONObject = JSONObject().put("app", "xuezhang").put("kind", "github-backup").put("v", 1).put("at", at).put("device", device)
            .put("appVersion", appVersion).put("courses", courses).put("lectures", lectures).put("includeAudio", includeAudio)
            .put("files", JSONObject().also { o -> files.toSortedMap().forEach { (k, e) -> o.put(k, JSONObject().put("sha", e.sha).put("size", e.size)) } })
            .put("paths", JSONObject().also { o -> paths.toSortedMap().forEach { (k, v) -> o.put(k, v) } })

        companion object {
            fun parse(s: String): Manifest {
                val o = JSONObject(s)
                if (o.optString("app") != "xuezhang") throw IllegalArgumentException("这不是课堂学长的备份")
                val f = o.optJSONObject("files") ?: JSONObject(); val p = o.optJSONObject("paths") ?: JSONObject()
                return Manifest(o.optLong("at"), o.optString("device"), o.optString("appVersion"), o.optInt("courses"), o.optInt("lectures"),
                    f.keys().asSequence().associateWith { k -> f.getJSONObject(k).let { Entry(it.optString("sha"), it.optLong("size")) } },
                    p.keys().asSequence().associateWith { p.optString(it) }, o.optBoolean("includeAudio"))
            }
        }
    }

    // ---------------- 设置（白名单，绝不带密钥） ----------------
    fun safeSettings(s: Settings): JSONObject = JSONObject()
        .put("asrBase", s.asrBase).put("asrModel", s.asrModel).put("llmBase", s.llmBase).put("llmModel", s.llmModel)
        .put("visionModel", s.visionModel).put("imageModel", s.imageModel).put("provider", s.provider)
        .put("defaultMode", s.defaultMode).put("chunkSec", s.chunkSec).put("confirmFill", s.confirmFill).put("latex", s.latex)
        .put("noteDepth", s.noteDepth).put("theme", s.theme).put("shelfFold", s.shelfFold).put("cardStyle", s.cardStyle)
        .put("userName", s.userName).put("modelMode", s.modelMode)
        .put("prompts", JSONObject().also { o -> s.promptOverrides.forEach { (k, v) -> o.put(k, v) } })

    /** 恢复时只合并偏好与提示词，本机的 Key / 账号 / 令牌保持不变。 */
    fun mergeSettings(local: Settings, o: JSONObject): Settings = local.copy(
        asrModel = o.optString("asrModel", local.asrModel).ifBlank { local.asrModel },
        llmModel = o.optString("llmModel", local.llmModel).ifBlank { local.llmModel },
        visionModel = o.optString("visionModel", local.visionModel).ifBlank { local.visionModel },
        imageModel = o.optString("imageModel", local.imageModel).ifBlank { local.imageModel },
        defaultMode = o.optInt("defaultMode", local.defaultMode), chunkSec = o.optInt("chunkSec", local.chunkSec),
        confirmFill = o.optBoolean("confirmFill", local.confirmFill), latex = o.optBoolean("latex", local.latex),
        noteDepth = o.optInt("noteDepth", local.noteDepth), theme = o.optInt("theme", local.theme), shelfFold = o.optBoolean("shelfFold", local.shelfFold),
        cardStyle = o.optInt("cardStyle", local.cardStyle),
        promptOverrides = local.promptOverrides + (o.optJSONObject("prompts")?.let { p -> p.keys().asSequence().associateWith { p.optString(it) }.filterValues { it.isNotBlank() } } ?: emptyMap()),
    )

    /** 本机所有密钥（用于最后一道防线：备份文本里出现就抹掉）。 */
    fun secrets(s: Settings): List<String> = listOf(s.asrKey, s.llmKey, s.ghToken, s.giteeToken, s.gClientSecret, s.davPass).map { it.trim() }.filter { it.length >= 6 }.distinct()

    fun scrub(text: String, secrets: List<String>): String = secrets.fold(text) { t, k -> if (t.contains(k)) t.replace(k, "***") else t }

    // ---------------- 错误信息 ----------------
    fun readable(code: Int, body: String, what: String = ""): String {
        val m = runCatching { JSONObject(body).optString("message") }.getOrDefault("").take(160)
        return when {
            code == 401 -> "GitHub 令牌已失效或被撤销，请到「连接账户」断开后重新连接"
            code == 403 && (m.contains("rate limit", true)) -> "GitHub 接口调用太频繁，过一会儿再试"
            code == 403 || (code == 404 && what == "create") -> "令牌缺少仓库权限：经典令牌请勾选 repo；细粒度令牌请给 Contents（读写）和 Administration（读写）权限"
            code == 404 -> "找不到备份仓库或没有权限访问（$what）"
            code == 409 -> "备份仓库是空的或正在被修改，请再试一次"
            code == 422 -> "GitHub 拒绝了请求：${m.ifBlank { "参数不对" }}"
            code >= 500 -> "GitHub 服务暂时不可用（$code），稍后再试"
            else -> "GitHub 返回 $code${if (m.isNotBlank()) "：$m" else ""}"
        }
    }
}
