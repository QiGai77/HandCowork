package app.xuezhang.engine.plugin

import kotlin.math.sqrt

/**
 * v0.0.8.0 插件通用钩子 capture.vad：按停顿切段（能量 + 过零率的轻量 VAD，纯 Kotlin）。
 *
 * 首选方案是 sherpa-onnx 的 Silero VAD（Apache-2.0 / 模型 MIT，https://github.com/k2-fsa/sherpa-onnx），
 * 但插件按设计只能是声明式资源包（不允许 .so / dex），而 sherpa-onnx 的 Android 版需要随 App 打包原生库（每个 ABI 数 MB），
 * 等于改 App 核心；所以这里实现同样「在说话停顿处切」语义的轻量 VAD：
 *  - 每 100ms 一帧算 RMS 与过零率，噪声底自适应（慢速跟随最安静的帧）；
 *  - 帧能量 > max(energy, 噪声底×ratio) 且过零率不像纯噪声 → 说话帧；
 *  - 段长 ≥ minSec 且连续静音 ≥ silenceMs → 在停顿处切；段长到 maxSec 强制切；
 *  - 整段几乎没有说话（< minSpeechMs）的不送去识别。
 * 参数来自插件规则里的 hooks."capture.vad"。
 */
class EnergyVad(val cfg: Config) {
    data class Config(
        val minSec: Int = 3, val maxSec: Int = 30, val silenceMs: Int = 600, val energy: Float = 350f,
        val ratio: Float = 2.5f, val minSpeechMs: Int = 400, val zcrMax: Float = 0.45f,
    )

    var lenMs = 0L; private set
    var speechMs = 0L; private set
    var silenceMs = 0L; private set
    private var noise = -1f

    fun reset() { lenMs = 0; speechMs = 0; silenceMs = 0 }

    /** 喂一帧 16-bit PCM，返回是否应该在这里切段。 */
    fun feed(buf: ShortArray, n: Int, rate: Int): Boolean {
        if (n <= 0) return false
        var sum = 0.0; var zc = 0
        for (i in 0 until n) { val v = buf[i].toDouble(); sum += v * v; if (i > 0 && (buf[i] >= 0) != (buf[i - 1] >= 0)) zc++ }
        val rms = sqrt(sum / n).toFloat()
        val zcr = zc.toFloat() / n
        val ms = n * 1000L / rate
        // 噪声底：遇到更安静的帧快速下降，否则极慢上升（说话不会把噪声底抬上去）
        if (noise < 0) noise = cfg.energy / 2
        noise = if (rms < noise) noise * 0.7f + rms * 0.3f else noise * 0.998f + rms * 0.002f
        val speech = isSpeech(rms, zcr)
        lenMs += ms
        if (speech) { speechMs += ms; silenceMs = 0 } else silenceMs += ms
        return (lenMs >= cfg.minSec * 1000L && silenceMs >= cfg.silenceMs) || lenMs >= cfg.maxSec * 1000L
    }

    fun isSpeech(rms: Float, zcr: Float): Boolean = rms > maxOf(cfg.energy, noise * cfg.ratio) && zcr < cfg.zcrMax

    /** 这一段值不值得送去识别。 */
    fun hasSpeech() = speechMs >= cfg.minSpeechMs

    companion object {
        fun config(o: org.json.JSONObject?): Config? {
            o ?: return null
            if (!o.optBoolean("enabled", true)) return null
            val d = Config()
            return Config(o.optInt("minSec", d.minSec).coerceIn(1, 20), o.optInt("maxSec", d.maxSec).coerceIn(5, 60),
                o.optInt("silenceMs", d.silenceMs).coerceIn(200, 3000), o.optDouble("energy", d.energy.toDouble()).toFloat(),
                o.optDouble("ratio", d.ratio.toDouble()).toFloat(), o.optInt("minSpeechMs", d.minSpeechMs), o.optDouble("zcrMax", d.zcrMax.toDouble()).toFloat())
        }
    }
}
