package app.xuezhang.engine.plugin

import androidx.compose.runtime.mutableStateOf
import java.io.File
import okhttp3.Request
import org.json.JSONObject

/**
 * v0.0.7.8 插件市场：读 QiGai77/HandCowork 仓库 plugins 分支的 index.json。
 * 1) raw.githubusercontent.com（默认）；2) 失败时用已连接的 GitHub 令牌走 contents API（私有仓库也能读）；
 * 3) 也可在「插件市场」里填自定义市场地址（如 Gitee 镜像的 raw 地址）。
 */
object Market {
    const val OWNER = "QiGai77"
    const val REPO = "HandCowork"
    const val BRANCH = "plugins"
    const val DEFAULT_URL = "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/index.json"

    val entries = mutableStateOf<List<MarketEntry>?>(null)
    val loading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    /** 实际读到 index 的地址（相对路径以它为基准）。 */
    var indexUrl: String = DEFAULT_URL
        private set
    private var viaApi = false

    fun url(): String = Plugins.state.value.marketUrl.ifBlank { DEFAULT_URL }

    private fun token(): String = app.xuezhang.data.Store.settings.value.ghToken

    private fun get(url: String, api: Boolean = false): ByteArray {
        val rb = Request.Builder().url(url).header("User-Agent", "xuezhang-app")
        if (api) { rb.header("Accept", "application/vnd.github.raw+json"); token().takeIf { it.isNotBlank() }?.let { rb.header("Authorization", "Bearer $it") } }
        app.xuezhang.net.Api.http.newCall(rb.build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            val b = r.body?.bytes() ?: ByteArray(0)
            if (b.size > PluginFiles.MAX_PACKAGE) throw IllegalStateException("文件太大")
            return b
        }
    }

    private fun apiUrl(path: String) = "https://api.github.com/repos/$OWNER/$REPO/contents/${path.trimStart('/')}?ref=$BRANCH"

    /** 刷新市场列表（阻塞，放 IO 线程调用）。 */
    fun refresh(): List<MarketEntry> {
        loading.value = true; error.value = null
        try {
            val u = url()
            val text = try {
                viaApi = false; indexUrl = u
                String(get(u))
            } catch (e: Exception) {
                if (u != DEFAULT_URL) throw IllegalStateException("读取市场失败（$u）：${e.message}")
                viaApi = true; indexUrl = DEFAULT_URL
                String(get(apiUrl("index.json"), api = true))
            }
            val list = MarketIndex.parse(text)
            entries.value = list
            return list
        } catch (e: Exception) {
            error.value = e.message ?: "读取失败"; throw e
        } finally { loading.value = false }
    }

    fun fileUrl(path: String): String = MarketIndex.resolve(indexUrl, path)

    fun download(path: String): ByteArray =
        if (viaApi && !path.startsWith("http")) get(apiUrl(path), api = true) else get(fileUrl(path))

    /** 下载并安装（sha256 校验 + 回滚由 Plugins.installZip 负责）。 */
    fun install(e: MarketEntry, enable: Boolean = true): PluginManifest {
        val c = Compatibility.check(e.asManifest(), Plugins.appVersion)
        require(c.ok) { c.reason }
        val bytes = download(e.path)
        require(e.size <= 0 || bytes.size.toLong() == e.size) { "大小不一致（${bytes.size} ≠ ${e.size}），可能下载不完整" }
        return Plugins.installZip(bytes, e.sha256, enable)
    }

    /** 已安装版本是否落后于市场。 */
    fun hasUpdate(e: MarketEntry): Boolean = Plugins.installed.firstOrNull { it.m.id == e.id }?.let { Semver.compare(e.version, it.m.version) > 0 } == true

    /** 读取截图/图标：返回字节（失败 null）。 */
    fun image(path: String): ByteArray? = runCatching { download(path) }.getOrNull()

    /** 获取作者的 GitHub 头像（默认使用 QiGai77），存放在本机 cache/avatars 中 */
    fun authorAvatar(author: String): ByteArray? {
        val user = author.trim().takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } } ?: OWNER
        val cacheDir = File(app.xuezhang.data.Store.dir, "cache/avatars").apply { mkdirs() }
        val f = File(cacheDir, "$user.png")
        if (f.exists() && f.length() > 0 && System.currentTimeMillis() - f.lastModified() < 7 * 86400 * 1000L) {
            return runCatching { f.readBytes() }.getOrNull()
        }
        val url = "https://github.com/$user.png?size=128"
        return runCatching {
            val req = Request.Builder().url(url).header("User-Agent", "xuezhang-app").build()
            app.xuezhang.net.Api.http.newCall(req).execute().use { r ->
                if (r.isSuccessful) {
                    val bytes = r.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        runCatching { f.writeBytes(bytes) }
                        bytes
                    } else null
                } else null
            }
        }.getOrNull()
    }

    @Suppress("unused")
    private fun parseErr(s: String) = runCatching { JSONObject(s).optString("message") }.getOrNull()
}
