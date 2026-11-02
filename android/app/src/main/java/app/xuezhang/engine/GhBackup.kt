package app.xuezhang.engine

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import app.xuezhang.BuildConfig
import app.xuezhang.data.Course
import app.xuezhang.data.Lecture
import app.xuezhang.data.Store
import app.xuezhang.net.Api
import app.xuezhang.net.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** GitHub 接口错误（code = HTTP 状态码），message 是给人看的中文。 */
class GhException(val code: Int, msg: String) : ApiException(msg)

/**
 * GitHub 备份与恢复：用「连接账户」里的个人访问令牌，把课程、笔记、复习卡、概念图、偏好和附件
 * 提交到用户自己的私有仓库（默认 xuezhang-backup）。用 Git Data API（blobs → tree → commit → ref）一次提交多文件，
 * 本地先算 git blob SHA，和远端树比较，只上传变了的文件。令牌只在请求头里，绝不进备份内容和日志。
 */
object GhBackup {
    const val API = "https://api.github.com"
    const val TAG = "GitHub 备份"
    val running = mutableStateOf(false)
    val lastMsg = mutableStateOf<String?>(null)
    private val lock = Mutex()
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun connected() = Store.settings.value.ghToken.isNotBlank() && Store.settings.value.ghUser.isNotBlank()
    fun repo() = GhBackupCore.cleanRepo(Store.settings.value.ghRepo)
    fun owner() = Store.settings.value.ghUser
    fun webUrl() = "https://github.com/${owner()}/${repo()}"

    // ---------------- HTTP ----------------
    private fun call(method: String, path: String, body: JSONObject? = null, what: String = ""): String {
        val token = Store.settings.value.ghToken.trim()
        if (token.isBlank()) throw GhException(401, "还没有连接 GitHub")
        val rb = body?.toString()?.toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(if (path.startsWith("http")) path else API + path)
            .header("Authorization", "Bearer $token").header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").header("User-Agent", "Xuezhang-Android/" + BuildConfig.VERSION_NAME)
            .method(method, rb ?: if (method == "POST" || method == "PATCH" || method == "PUT") ByteArray(0).toRequestBody(null) else null).build()
        Api.http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw GhException(r.code, GhBackupCore.readable(r.code, s, what))
            return s
        }
    }
    private fun get(path: String, what: String = "") = JSONObject(call("GET", path, what = what))
    private fun post(path: String, body: JSONObject, what: String = "") = JSONObject(call("POST", path, body, what))

    private fun r(p: String) = "/repos/${owner()}/${repo()}$p"

    /** 仓库存在就返回默认分支，否则创建私有仓库（auto_init 带一个 README 提交）。 */
    fun ensureRepo(): String {
        val o = try { get(r(""), "repo") } catch (e: GhException) {
            if (e.code != 404) throw e
            Log.ok(TAG, "创建私有仓库 ${owner()}/${repo()}")
            post("/user/repos", JSONObject().put("name", repo()).put("private", true).put("auto_init", true)
                .put("description", "课堂学长 App 的笔记备份（自动生成，请保持私有）"), "create")
        }
        if (!o.optBoolean("private", true)) Log.fail(TAG, "注意：备份仓库 ${repo()} 是公开的，建议在 GitHub 改成 Private")
        return o.optString("default_branch").ifBlank { "main" }
    }

    /** 分支 HEAD（空仓库时先放一个 README 让它有提交）。 */
    private fun head(branch: String): String = try {
        get(r("/git/ref/heads/$branch"), "ref").getJSONObject("object").getString("sha")
    } catch (e: GhException) {
        if (e.code != 409 && e.code != 404) throw e
        JSONObject(call("PUT", r("/contents/README.md"), JSONObject().put("message", "init").put("branch", branch)
            .put("content", Base64.encodeToString("# 课堂学长备份\n".toByteArray(), Base64.NO_WRAP)), "init"))
        get(r("/git/ref/heads/$branch"), "ref").getJSONObject("object").getString("sha")
    }

    /** 远端某个提交的完整文件树：路径 → blob SHA。 */
    private fun tree(commitSha: String): Map<String, String> {
        val treeSha = get(r("/git/commits/$commitSha")).getJSONObject("tree").getString("sha")
        val t = get(r("/git/trees/$treeSha?recursive=1"))
        val a = t.optJSONArray("tree") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("type") == "blob" }.associate { it.getString("path") to it.getString("sha") }
    }

    private fun blob(sha: String): ByteArray {
        val o = get(r("/git/blobs/$sha"))
        return Base64.decode(o.optString("content").replace("\n", ""), Base64.DEFAULT)
    }

    // ---------------- 打包 ----------------
    private class Src(val path: String, val sha: String, val size: Long, val bytes: ByteArray? = null, val file: File? = null) {
        fun read(): ByteArray = bytes ?: file!!.readBytes()
    }

    private fun walk(root: File): List<GhBackupCore.Local> = GhBackupCore.DIRS.keys.flatMap { d ->
        File(root, d).walkTopDown().filter { it.isFile }.map { GhBackupCore.Local(it.relativeTo(root).path.replace('\\', '/'), it.length()) }.toList()
    }

    /** 收集要备份的东西（数据 JSON + 附件），返回 (源列表, 清单, 跳过说明)。 */
    private fun collect(ctx: Context, includeAudio: Boolean): Triple<List<Src>, GhBackupCore.Manifest, List<String>> {
        val st = Store.settings.value
        val secrets = GhBackupCore.secrets(st)
        val bundle = Store.exportBundle()
        val srcs = ArrayList<Src>()
        fun json(path: String, o: Any) {
            val b = GhBackupCore.scrub(if (o is JSONObject) o.toString(1) else (o as JSONArray).toString(1), secrets).toByteArray()
            srcs.add(Src(path, GhBackupCore.blobSha(b), b.size.toLong(), bytes = b))
        }
        json("data/courses.json", JSONObject().put("app", "xuezhang").put("courses", bundle.getJSONArray("courses")))
        val lecs = bundle.getJSONArray("lectures")
        for (i in 0 until lecs.length()) { val l = lecs.getJSONObject(i); json("data/lectures/" + GhBackupCore.cleanRepo(l.optString("id")) + ".json", l) }
        json("data/settings.json", GhBackupCore.safeSettings(st))

        val root = ctx.filesDir
        val pick = GhBackupCore.select(walk(root), includeAudio)
        // 外部绝对路径（不在 filesDir 白名单目录里）但被课程/笔记引用的附件也带上
        val refs = (Store.courses.flatMap { listOf(it.cover.takeIf { c -> c.startsWith("file:") }?.removePrefix("file:"), it.slidePath) } +
            Store.lectures.flatMap { l -> l.sections.map { it.frame } + l.coverImg } + st.userAvatar).filterNotNull().filter { it.isNotBlank() }.distinct()
        val paths = HashMap<String, String>()
        pick.included.forEach { f ->
            val file = File(root, f.rel); val rp = GhBackupCore.repoPath(f.rel)
            srcs.add(Src(rp, GhBackupCore.blobSha(file.inputStream(), file.length()), file.length(), file = file))
            paths[file.absolutePath] = rp
        }
        refs.forEach { p ->
            val f = File(p)
            if (paths.containsKey(f.absolutePath) || !f.isFile) return@forEach
            if (f.absolutePath.startsWith(File(root, "data").absolutePath)) return@forEach
            if (f.length() > GhBackupCore.MAX_FILE || (GhBackupCore.isAudio(f.name) && !includeAudio)) return@forEach
            val rp = "files/ext/" + GhBackupCore.blobSha(f.absolutePath.toByteArray()).take(8) + "-" + f.name
            srcs.add(Src(rp, GhBackupCore.blobSha(f.inputStream(), f.length()), f.length(), file = f)); paths[f.absolutePath] = rp
        }
        val m = GhBackupCore.Manifest(System.currentTimeMillis(), (Build.MANUFACTURER + " " + Build.MODEL).trim(), BuildConfig.VERSION_NAME,
            Store.courses.size, Store.lectures.size, srcs.associate { it.path to GhBackupCore.Entry(it.sha, it.size) }, paths, includeAudio)
        return Triple(srcs, m, pick.skipped.map { "${it.first.rel}：${it.second}" })
    }

    data class Result(val uploaded: Int, val unchanged: Int, val deleted: Int, val bytes: Long, val commit: String?, val skipped: List<String>)

    /** 备份（IO 线程）。progress(0..1, 说明)。没有变化时不产生新提交。 */
    suspend fun backup(ctx: Context, reason: String = "手动", progress: (Float, String) -> Unit = { _, _ -> }): Result = lock.withLock {
        running.value = true
        try {
            withContext(Dispatchers.IO) {
                val st = Store.settings.value
                progress(0.03f, "检查备份仓库")
                val branch = ensureRepo()
                val parent = head(branch)
                progress(0.08f, "读取远端文件列表")
                val remote = tree(parent)
                progress(0.12f, "计算本机文件指纹")
                val (srcs, manifest, skipped) = collect(ctx, st.ghIncludeAudio)
                val plan = GhBackupCore.plan(srcs.associate { it.path to it.sha }, remote)
                if (!plan.changed && remote.containsKey(GhBackupCore.MANIFEST)) {
                    Store.updateSettings(Store.settings.value.copy(ghLastBackup = System.currentTimeMillis()))
                    Log.ok(TAG, "$reason · 内容没有变化，跳过提交（${plan.unchanged.size} 个文件）")
                    return@withContext Result(0, plan.unchanged.size, 0, 0, null, skipped)
                }
                val bySha = HashMap<String, String>() // path -> sha（上传后）
                val todo = srcs.filter { it.path in plan.upload }
                val total = todo.sumOf { it.size }.coerceAtLeast(1)
                var sent = 0L
                todo.forEachIndexed { i, s ->
                    progress(0.12f + 0.78f * sent / total, "上传 ${i + 1}/${todo.size} · ${s.path.substringAfterLast('/')}")
                    val b = s.read()
                    val o = post(r("/git/blobs"), JSONObject().put("content", Base64.encodeToString(b, Base64.NO_WRAP)).put("encoding", "base64"), "blob")
                    val got = o.optString("sha")
                    if (got.isNotBlank() && got != s.sha) Log.fail(TAG, "文件指纹不一致：${s.path}")
                    bySha[s.path] = got.ifBlank { s.sha }; sent += s.size
                }
                progress(0.92f, "写入清单并提交")
                val mBytes = GhBackupCore.scrub(manifest.toJson().toString(1), GhBackupCore.secrets(st)).toByteArray()
                val entries = JSONArray()
                fun add(path: String, sha: String) = entries.put(JSONObject().put("path", path).put("mode", "100644").put("type", "blob").put("sha", sha))
                srcs.forEach { add(it.path, bySha[it.path] ?: it.sha) }
                entries.put(JSONObject().put("path", GhBackupCore.MANIFEST).put("mode", "100644").put("type", "blob").put("content", String(mBytes)))
                remote.keys.filter { !it.startsWith("data/") && !it.startsWith("files/") && it != GhBackupCore.MANIFEST }.forEach { add(it, remote.getValue(it)) } // README 等保留
                val treeSha = post(r("/git/trees"), JSONObject().put("tree", entries), "tree").getString("sha")
                val msg = "备份 ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())} · ${manifest.courses} 门课 ${manifest.lectures} 节笔记 · $reason"
                val commit = post(r("/git/commits"), JSONObject().put("message", msg).put("tree", treeSha).put("parents", JSONArray().put(parent)), "commit").getString("sha")
                JSONObject(call("PATCH", r("/git/refs/heads/$branch"), JSONObject().put("sha", commit), "ref"))
                Store.updateSettings(Store.settings.value.copy(ghLastBackup = System.currentTimeMillis(), ghLastCommit = commit))
                progress(1f, "完成")
                Log.ok(TAG, "$reason · 上传 ${todo.size} 个、未变化 ${plan.unchanged.size} 个、删除 ${plan.deleted.size} 个 · ${sent / 1024} KB · ${commit.take(7)}")
                if (skipped.isNotEmpty()) Log.ok(TAG, "跳过 ${skipped.size} 个：" + skipped.take(5).joinToString("；"))
                Result(todo.size, plan.unchanged.size, plan.deleted.size, sent, commit, skipped)
            }
        } catch (e: Exception) {
            Log.err(TAG, e, "$reason · 失败：${e.message}")
            throw e
        } finally { running.value = false }
    }

    data class Snapshot(val sha: String, val message: String, val at: Long)

    /** 最近的备份提交。 */
    suspend fun history(limit: Int = 20): List<Snapshot> = withContext(Dispatchers.IO) {
        ensureRepo()
        val a = JSONArray(call("GET", r("/commits?per_page=$limit"), what = "commits"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { c ->
            val cm = c.getJSONObject("commit")
            Snapshot(c.getString("sha"), cm.optString("message").lineSequence().first(), parseTime(cm.optJSONObject("committer")?.optString("date").orEmpty()))
        }.filter { it.message.startsWith("备份") }
    }

    fun parseTime(s: String): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(s.take(19))!!.time
    }.getOrDefault(0L)

    /** 下载某次备份（IO），返回待合并的数据；附件写进 filesDir。 */
    class Restore(val bundle: JSONObject, val settings: JSONObject?, val fixPath: (String) -> String, val files: Int)

    suspend fun download(ctx: Context, sha: String, progress: (Float, String) -> Unit = { _, _ -> }): Restore = withContext(Dispatchers.IO) {
        progress(0.05f, "读取备份清单")
        val remote = tree(sha)
        val manifest = remote[GhBackupCore.MANIFEST]?.let { GhBackupCore.Manifest.parse(String(blob(it))) } ?: throw ApiException("这次提交里没有备份清单")
        val courses = remote["data/courses.json"]?.let { JSONObject(String(blob(it))).optJSONArray("courses") } ?: JSONArray()
        val lecPaths = remote.keys.filter { it.startsWith("data/lectures/") && it.endsWith(".json") }
        val lectures = JSONArray()
        lecPaths.forEachIndexed { i, p -> progress(0.1f + 0.3f * i / lecPaths.size.coerceAtLeast(1), "下载笔记 ${i + 1}/${lecPaths.size}"); lectures.put(JSONObject(String(blob(remote.getValue(p))))) }
        val settings = remote["data/settings.json"]?.let { runCatching { JSONObject(String(blob(it))) }.getOrNull() }
        val files = remote.keys.filter { it.startsWith("files/") }
        val local = HashMap<String, String>() // 仓库路径 -> 本机路径
        files.forEachIndexed { i, p ->
            progress(0.4f + 0.55f * i / files.size.coerceAtLeast(1), "下载附件 ${i + 1}/${files.size}")
            val dir = p.removePrefix("files/").substringBefore('/'); val name = p.substringAfterLast('/')
            if (name.isBlank() || name.contains("..") || dir.contains("..")) return@forEachIndexed
            val f = File(File(ctx.filesDir, dir).apply { mkdirs() }, name)
            val want = remote.getValue(p)
            if (!(f.isFile && GhBackupCore.blobSha(f.inputStream(), f.length()) == want)) f.writeBytes(blob(want))
            local[p] = f.absolutePath
        }
        val byName = local.values.associateBy { File(it).name }
        val fix: (String) -> String = { orig -> manifest.paths[orig]?.let { local[it] } ?: byName[File(orig).name] ?: orig }
        Restore(JSONObject().put("app", "xuezhang").put("courses", courses).put("lectures", lectures), settings, fix, local.size)
    }

    /** 主线程：合并进本机（同 id 覆盖，其余新增；Key / 令牌不动）。 */
    fun apply(rs: Restore): Pair<Int, Int> {
        val fix = rs.fixPath
        val res = Store.importBundle(rs.bundle, { c: Course ->
            c.copy(cover = if (c.cover.startsWith("file:")) "file:" + fix(c.cover.removePrefix("file:")) else c.cover, slidePath = if (c.slidePath.isNotBlank()) fix(c.slidePath) else "")
        }) { l: Lecture -> l.copy(sections = l.sections.map { s -> if (s.frame.isNotBlank()) s.copy(frame = fix(s.frame)) else s }, coverImg = if (l.coverImg.isNotBlank()) fix(l.coverImg) else "") }
        rs.settings?.let { Store.updateSettings(GhBackupCore.mergeSettings(Store.settings.value, it)) }
        Log.ok(TAG, "已从备份恢复 ${res.first} 门课、${res.second} 节笔记、${rs.files} 个附件")
        return res
    }

    // ---------------- 自动备份 ----------------
    const val JOB_ID = 7707

    fun onWifi(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val c = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    /** 开启自动备份 → 系统每天在 Wi‑Fi（不计流量网络）下跑一次；关闭则取消。App 每次启动都会重新登记。 */
    fun schedule(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        val s = Store.settings.value
        if (!s.ghAutoBackup || !connected()) { js.cancel(JOB_ID); return }
        if (js.getPendingJob(JOB_ID) != null) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(ctx, GhBackupJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            .setPeriodic(24 * 3600_000L, 3 * 3600_000L).build()
        runCatching { js.schedule(info) }.onFailure { Log.err(TAG, it, "登记每日自动备份失败") }
    }

    /** 笔记生成完：开了自动备份并且在 Wi‑Fi 上就在后台备份一次。 */
    @Volatile var appCtx: Context? = null
    fun afterNote(ctx: Context? = appCtx) {
        ctx ?: return
        if (!Store.settings.value.ghAutoBackup || !connected() || running.value || !onWifi(ctx)) return
        val app = ctx.applicationContext
        bg.launch { runCatching { backup(app, "笔记生成后自动") }.onSuccess { lastMsg.value = summary(it) } }
    }

    fun summary(r: Result) = if (r.commit == null) "内容没有变化，无需提交" else "已备份：上传 ${r.uploaded} 个文件、未变化 ${r.unchanged} 个" + (if (r.deleted > 0) "、删除 ${r.deleted} 个" else "") + " · ${r.commit.take(7)}"
}

/** 每日自动备份（JobScheduler，要求不计流量网络）。 */
class GhBackupJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onStartJob(params: JobParameters): Boolean {
        if (!Store.ready()) Store.init(applicationContext)
        if (!GhBackup.connected() || !Store.settings.value.ghAutoBackup) return false
        scope.launch {
            val ok = runCatching { GhBackup.backup(applicationContext, "每日自动") }.isSuccess
            jobFinished(params, !ok)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean = true
}
