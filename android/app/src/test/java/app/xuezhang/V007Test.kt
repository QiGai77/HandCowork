package app.xuezhang

import app.xuezhang.data.NoteSection
import app.xuezhang.data.Segment
import app.xuezhang.engine.AiProgress
import app.xuezhang.engine.AiTips
import app.xuezhang.engine.Frames
import app.xuezhang.engine.Live
import app.xuezhang.net.GDrive
import app.xuezhang.ui.BookLook
import app.xuezhang.ui.ClassPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 不足 30 秒的录音按误触作废。 */
class ShortRecordingTest {
    @Test fun underThirtySecondsIsDiscarded() {
        assertTrue(Live.tooShort(0))
        assertTrue(Live.tooShort(1_000))
        assertTrue(Live.tooShort(29_999))
    }
    @Test fun thirtySecondsOrMoreIsKept() {
        assertFalse(Live.tooShort(30_000))
        assertFalse(Live.tooShort(30_001))
        assertFalse(Live.tooShort(95 * 60_000L))
        assertEquals(30_000L, Live.MIN_RECORD_MS)
    }
}

class AiProgressTest {
    @Test fun estimateMovesButNeverReachesPhaseEnd() {
        var last = 0f
        for (t in listOf(0L, 500L, 2_000L, 10_000L, 30_000L, 120_000L, 600_000L)) {
            val v = AiProgress.estimate(0.1f, 0.9f, t, 10_000)
            assertTrue("monotonic at $t", v >= last); last = v
            assertTrue("below phase end at $t", v < 0.9f)
        }
        assertEquals(0.1f, AiProgress.estimate(0.1f, 0.9f, 0, 10_000), 1e-4f)
        // 到预计耗时时大约走完 75%~80%
        val atEst = (AiProgress.estimate(0f, 1f, 10_000, 10_000))
        assertTrue(atEst in 0.7f..0.85f)
        // 超过预计耗时后仍在缓慢前进（进度条不会「卡死」）
        assertTrue(AiProgress.estimate(0f, 1f, 40_000, 10_000) > AiProgress.estimate(0f, 1f, 20_000, 10_000))
    }
    @Test fun emptyPhaseStays() { assertEquals(0.5f, AiProgress.estimate(0.5f, 0.5f, 5000, 1000), 0f) }
    @Test fun tipsRotateAndWrap() {
        assertEquals(AiTips.at(0), AiTips.at(AiTips.ALL.size))
        assertEquals(AiTips.at(AiTips.ALL.size - 1), AiTips.at(-1))
        assertTrue(AiTips.ALL.any { it.startsWith("小彩蛋") })
    }
}

class FramesTest {
    @Test fun parseAtFormats() {
        assertEquals(63_000L, Frames.parseAt("01:03"))
        assertEquals(98 * 60_000L + 12_000, Frames.parseAt("98:12"))
        assertEquals(3_723_000L, Frames.parseAt("1:02:03"))
        assertEquals(185_000L, Frames.parseAt("[03:05 P.4 ★考点]"))
        assertEquals(-1L, Frames.parseAt(""))
        assertEquals(-1L, Frames.parseAt("开头"))
        assertEquals(-1L, Frames.parseAt(null))
    }

    private val segs = listOf(
        Segment(startMs = 0, voice = "今天我们讲变压器的基本结构，铁心和绕组"),
        Segment(startMs = 300_000, voice = "接下来是空载运行，空载电流很小，主要是励磁电流"),
        Segment(startMs = 900_000, voice = "最后讲短路试验，测铜耗和短路阻抗"),
    )

    @Test fun modelTimestampWins() {
        val t = Frames.assignTimes(listOf(NoteSection("短路试验", body = "x", atMs = 905_000)), segs, 1_200_000)
        assertEquals(listOf(905_000L), t)
    }
    @Test fun keywordOverlapFindsSegment() {
        val secs = listOf(NoteSection("1. 变压器结构", body = "铁心和绕组是变压器的基本结构"),
            NoteSection("2. 空载运行", body = "空载电流主要是励磁电流"), NoteSection("3. 短路试验", body = "测铜耗、短路阻抗"))
        assertEquals(listOf(0L, 300_000L, 900_000L), Frames.assignTimes(secs, segs, 1_200_000))
    }
    @Test fun fallbackSplitsDurationEvenly() {
        val secs = listOf(NoteSection("A", body = "alpha"), NoteSection("B", body = "beta"))
        assertEquals(listOf(0L, 600_000L), Frames.assignTimes(secs, emptyList(), 1_200_000))
    }
    @Test fun outOfRangeTimestampIgnored() {
        val t = Frames.assignTimes(listOf(NoteSection("空载运行", body = "空载电流 励磁电流", atMs = 99_000_000)), segs, 1_200_000)
        assertEquals(listOf(300_000L), t)
    }

    private val shotJson = """{"code":0,"message":"0","data":{"pvdata":"//i0.hdslb.com/x.bin","img_x_len":10,"img_y_len":10,"img_x_size":160,"img_y_size":90,
        "image":["//i0.hdslb.com/bfs/videoshot/a.jpg","http://i0.hdslb.com/bfs/videoshot/b.jpg"],"index":[0,0,5,10,15,20,25,30]}}"""

    @Test fun parseVideoshot() {
        val s = Frames.parseVideoshot(shotJson)!!
        assertEquals(2, s.images.size)
        assertEquals("https://i0.hdslb.com/bfs/videoshot/a.jpg", s.images[0])
        assertEquals("https://i0.hdslb.com/bfs/videoshot/b.jpg", s.images[1])
        assertEquals(10, s.xLen); assertEquals(8, s.index.size)
        assertNull(Frames.parseVideoshot("""{"code":-404,"data":null}"""))
    }
    @Test fun locateUsesIndex() {
        val s = Frames.parseVideoshot(shotJson)!!
        assertEquals(Triple(0, 3, 0), Frames.locate(s, 12, 40))   // index[3]=10 ≤ 12 < 15
        assertEquals(Triple(0, 7, 0), Frames.locate(s, 999, 40))
    }
    @Test fun locateWithoutIndexSpreadsOverDuration() {
        val s = Frames.Shot(listOf("a", "b"), 10, 10, 160, 90, emptyList())
        assertEquals(Triple(0, 0, 0), Frames.locate(s, 0, 2000))
        assertEquals(Triple(1, 0, 0), Frames.locate(s, 1000, 2000))   // 200 帧中的第 100 帧 → 第 2 张图左上
        assertEquals(Triple(1, 9, 9), Frames.locate(s, 5000, 2000))   // 越界夹到最后一帧
    }
}

class GDriveTest {
    @Test fun authUrlHasPkceAndScope() {
        val u = GDrive.authUrl("123-abc.apps.googleusercontent.com", "http://127.0.0.1:45678", "CHAL_lenge-1", "st8")
        assertTrue(u.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        assertTrue(u.contains("client_id=123-abc.apps.googleusercontent.com"))
        assertTrue(u.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A45678"))
        assertTrue(u.contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.file"))
        assertTrue(u.contains("code_challenge=CHAL_lenge-1")); assertTrue(u.contains("code_challenge_method=S256"))
        assertTrue(u.contains("state=st8")); assertTrue(u.contains("access_type=offline"))
        assertFalse(u.contains("login_hint"))
        assertTrue(GDrive.authUrl("x", "r", "c", "s", "a@b.com").contains("login_hint=a%40b.com"))
    }
    @Test fun resumableRangeHelpers() {
        assertEquals(1_048_576L, GDrive.nextOffset("bytes=0-1048575"))
        assertEquals(0L, GDrive.nextOffset(null))
        assertEquals(0L, GDrive.nextOffset("garbage"))
        assertEquals("bytes 0-1048575/3000000", GDrive.contentRange(0, 1_048_576, 3_000_000))
        assertEquals("bytes 2097152-2999999/3000000", GDrive.contentRange(2_097_152, 902_848, 3_000_000))
        assertEquals("bytes */3000000", GDrive.contentRange(0, 0, 3_000_000))
        assertEquals(0, GDrive.CHUNK % (256 * 1024))
    }
    @Test fun timeNameMd5() {
        assertEquals(1_761_955_200_000L, GDrive.parseTime("2025-11-01T00:00:00.000Z"))
        assertEquals(0L, GDrive.parseTime("bad"))
        assertEquals("电机学 - 第_1讲_ 变压器", GDrive.safeName("电机学 - 第/1讲: 变压器"))
        assertEquals("笔记", GDrive.safeName("  "))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", GDrive.md5("abc".toByteArray()))
    }
}

class ShelfAndDeckTest {
    @Test fun deckWrapsBothWays() {
        assertEquals(1, ClassPick.step(0, 3, 1))
        assertEquals(0, ClassPick.step(2, 3, 1))
        assertEquals(2, ClassPick.step(0, 3, -1))
        assertEquals(0, ClassPick.step(0, 1, 1))
        assertEquals(0, ClassPick.step(5, 0, 1))
    }
    @Test fun spineTitle() {
        assertEquals("第3讲变压器", BookLook.spineTitle("电机学 · 第 3 讲 变压器", "电机学"))
        assertEquals("方向导数与梯", BookLook.spineTitle("方向导数与梯度（示例）", "高数", 6))
        assertNotNull(BookLook.spineTitle("电机学", "电机学"))
    }
}
