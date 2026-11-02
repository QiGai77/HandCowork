package app.xuezhang

import app.xuezhang.data.Settings
import app.xuezhang.engine.GhBackupCore
import app.xuezhang.engine.GhBackupCore.Local
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.0.7.7 GitHub 备份：文件选择 / 密钥排除、blob SHA、变化检测、清单往返。 */
class V0077Test {
    private val MB = 1024L * 1024

    @Test fun selectionIncludesAttachmentsAndExcludesSecrets() {
        val files = listOf(
            Local("covers/c1.jpg", 200_000), Local("slides/ch5.pdf", 3 * MB), Local("frames/f1.jpg", 40_000), Local("gen/ai.png", 900_000),
            Local("avatars/me.jpg", 30_000),
            Local("data/db.json", 10_000), Local("data/auth.bin", 500), Local("data/log.jsonl", 9_000), Local("covers/db.json", 10),
            Local("covers/.hidden", 10), Local("slides/key.jks", 2_000), Local("shared_prefs/x.xml", 100), Local("slidecache/1.png", 100),
            Local("random/a.txt", 10), Local("top.txt", 10), Local("covers/../data/db.json", 10),
        )
        val p = GhBackupCore.select(files, includeAudio = false)
        assertEquals(listOf("avatars/me.jpg", "covers/c1.jpg", "frames/f1.jpg", "gen/ai.png", "slides/ch5.pdf"), p.included.map { it.rel })
        assertEquals(files.size - 5, p.skipped.size)
        assertTrue(p.skipped.first { it.first.rel == "data/db.json" }.second.contains("密钥"))
        assertTrue(p.skipped.first { it.first.rel == "data/auth.bin" }.second.contains("密钥"))
    }

    @Test fun audioToggleAndSizeLimit() {
        val files = listOf(Local("audio/l1.m4a", 8 * MB), Local("audio/big.wav", 60 * MB), Local("slides/huge.pdf", 51 * MB), Local("slides/ok.pdf", 50 * MB))
        val off = GhBackupCore.select(files, includeAudio = false)
        assertEquals(listOf("slides/ok.pdf"), off.included.map { it.rel })
        assertTrue(off.skipped.first { it.first.rel == "audio/l1.m4a" }.second.contains("录音"))
        val on = GhBackupCore.select(files, includeAudio = true)
        assertEquals(listOf("audio/l1.m4a", "slides/ok.pdf"), on.included.map { it.rel })
        assertTrue(on.skipped.first { it.first.rel == "audio/big.wav" }.second.contains("50MB"))
        assertEquals("files/covers/a b.jpg", GhBackupCore.repoPath("covers/a b.jpg"))
    }

    @Test fun settingsNeverContainSecrets() {
        val st = Settings(asrKey = "sk-asr-SECRET-111111", llmKey = "sk-llm-SECRET-222222", ghToken = "ghp_SECRETTOKEN333333", ghUser = "QiGai77",
            giteeToken = "gitee-SECRET-444444", gClientSecret = "GOCSPX-SECRET555555", davPass = "dav-SECRET-666666",
            promptOverrides = mapOf("notes" to "写得详细一点"), theme = 2)
        val json = GhBackupCore.safeSettings(st).toString()
        GhBackupCore.secrets(st).forEach { assertFalse("leaked $it", json.contains(it)) }
        assertEquals(6, GhBackupCore.secrets(st).size)
        assertFalse(json.contains("SECRET"))
        assertTrue(json.contains("写得详细一点"))
        // 恢复只合并偏好，本机 Key 不变
        val local = Settings(llmKey = "sk-local-key-999999", ghToken = "ghp_local_777777")
        val merged = GhBackupCore.mergeSettings(local, GhBackupCore.safeSettings(st))
        assertEquals("sk-local-key-999999", merged.llmKey); assertEquals("ghp_local_777777", merged.ghToken)
        assertEquals(2, merged.theme); assertEquals("写得详细一点", merged.promptOverrides["notes"])
        // 最后一道防线：笔记正文里意外出现令牌也会被抹掉
        val scrubbed = GhBackupCore.scrub("用户贴了 ghp_SECRETTOKEN333333 进笔记", GhBackupCore.secrets(st))
        assertEquals("用户贴了 *** 进笔记", scrubbed)
    }

    @Test fun blobShaMatchesGit() {
        assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", GhBackupCore.blobSha(ByteArray(0)))
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", GhBackupCore.blobSha("hello\n".toByteArray()))
        val big = ByteArray(300_000) { (it * 31).toByte() }
        assertEquals(GhBackupCore.blobSha(big), GhBackupCore.blobSha(big.inputStream(), big.size.toLong()))
        val zh = "课堂学长".toByteArray()
        assertEquals(GhBackupCore.blobSha(zh), GhBackupCore.blobSha(zh.inputStream(), zh.size.toLong()))
    }

    @Test fun changeDetection() {
        val remote = mapOf("data/courses.json" to "a1", "data/lectures/l1.json" to "b1", "data/lectures/old.json" to "c1",
            "files/covers/x.jpg" to "d1", "README.md" to "r", "manifest.json" to "m0")
        val local = mapOf("data/courses.json" to "a1", "data/lectures/l1.json" to "b2", "data/lectures/l2.json" to "e1",
            "files/covers/x.jpg" to "d1", "manifest.json" to "m1")
        val p = GhBackupCore.plan(local, remote)
        assertEquals(listOf("data/lectures/l1.json", "data/lectures/l2.json"), p.upload)
        assertEquals(listOf("data/courses.json", "files/covers/x.jpg"), p.unchanged)
        assertEquals(listOf("data/lectures/old.json"), p.deleted)   // README 不算
        assertTrue(p.changed)
        val same = GhBackupCore.plan(local - "data/lectures/l2.json" + ("data/lectures/l1.json" to "b1"), remote - "data/lectures/old.json")
        assertFalse(same.changed)
    }

    @Test fun manifestRoundTrip() {
        val m = GhBackupCore.Manifest(1_793_000_000_000L, "Xiaomi 14", "0.0.7.7", 3, 12,
            mapOf("data/courses.json" to GhBackupCore.Entry("a1", 1234), "files/covers/封面.jpg" to GhBackupCore.Entry("d1", 99_999)),
            mapOf("/data/user/0/app.xuezhang/files/covers/封面.jpg" to "files/covers/封面.jpg"), includeAudio = true)
        val back = GhBackupCore.Manifest.parse(m.toJson().toString(1))
        assertEquals(m, back)
        assertTrue(runCatching { GhBackupCore.Manifest.parse("{\"app\":\"other\"}") }.isFailure)
    }

    @Test fun repoNameAndErrors() {
        assertEquals("xuezhang-backup", GhBackupCore.cleanRepo("  "))
        assertEquals("my-notes", GhBackupCore.cleanRepo("my notes"))
        assertEquals("课堂".let { GhBackupCore.DEFAULT_REPO }, GhBackupCore.cleanRepo("课堂"))
        assertTrue(GhBackupCore.readable(401, "{}").contains("重新连接"))
        assertTrue(GhBackupCore.readable(403, "{\"message\":\"Resource not accessible by personal access token\"}").contains("repo"))
        assertTrue(GhBackupCore.readable(403, "{\"message\":\"API rate limit exceeded\"}").contains("频繁"))
        assertTrue(GhBackupCore.readable(404, "{}", "create").contains("repo"))
    }
}
