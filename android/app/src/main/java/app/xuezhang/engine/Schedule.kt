package app.xuezhang.engine

import app.xuezhang.data.Course
import app.xuezhang.data.Slot
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** 课表/节次/「今天」相关的纯逻辑（可单元测试）。 */
object Schedule {
    /** 默认作息：第 n 节的开始/结束（分钟）。大多数高校接近这个表。 */
    val SECTIONS: List<Pair<Int, Int>> = listOf(
        8 * 60 to 8 * 60 + 45, 8 * 60 + 50 to 9 * 60 + 35,
        10 * 60 + 10 to 10 * 60 + 55, 11 * 60 to 11 * 60 + 45,
        14 * 60 to 14 * 60 + 45, 14 * 60 + 50 to 15 * 60 + 35,
        16 * 60 to 16 * 60 + 45, 16 * 60 + 50 to 17 * 60 + 35,
        19 * 60 to 19 * 60 + 45, 19 * 60 + 50 to 20 * 60 + 35,
        20 * 60 + 40 to 21 * 60 + 25, 21 * 60 + 30 to 22 * 60 + 15,
    )
    /** 当前时间来源（测试里可替换）。 */
    var clock: () -> Calendar = { Calendar.getInstance() }

    val WEEK = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    fun startMin(s: Slot): Int? = SECTIONS.getOrNull(s.start - 1)?.first
    fun endMin(s: Slot): Int? = SECTIONS.getOrNull((if (s.end >= s.start) s.end else s.start) - 1)?.second

    fun timeRange(s: Slot): String {
        val a = startMin(s) ?: return ""
        return hm(a) + "–" + hm(endMin(s) ?: (a + 45))
    }

    /** 「周一 1-2 节」 */
    fun slotLabel(s: Slot): String {
        val d = WEEK.getOrNull(s.weekday - 1) ?: return ""
        if (s.start <= 0) return d
        return if (s.end > s.start) "$d ${s.start}-${s.end} 节" else "$d 第 ${s.start} 节"
    }

    fun courseSlotLabel(c: Course): String = c.slots.joinToString("、") { slotLabel(it) }.ifBlank { c.time }

    /** Calendar.DAY_OF_WEEK → 1(周一)..7(周日) */
    fun isoWeekday(cal: Calendar = Calendar.getInstance()): Int = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

    fun parseWeekday(s: String?): Int {
        if (s.isNullOrBlank()) return 0
        val t = s.trim()
        t.toIntOrNull()?.let { return if (it in 1..7) it else if (it == 0) 7 else 0 }
        val map = listOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7, "七" to 7)
        Regex("(?:周|星期|礼拜)([一二三四五六日天七])").find(t)?.let { m -> return map.first { it.first == m.groupValues[1] }.second }
        val en = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        en.indexOfFirst { t.lowercase().startsWith(it) }.let { if (it >= 0) return it + 1 }
        return 0
    }

    /** 旧版本里 time 是自由文本「周六 14:00」：取出时钟分钟数。 */
    fun legacyClock(t: String): Int? = Regex("(\\d{1,2})[:：](\\d{2})").find(t)?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }

    /** 把旧数据（只有 time 文本）迁移成上课时段。 */
    fun migrate(c: Course): Course {
        if (c.slots.isNotEmpty()) return c
        val wd = parseWeekday(c.time)
        if (wd == 0) return c
        val clock = legacyClock(c.time)
        val sec = if (clock == null) 0 else SECTIONS.indexOfFirst { clock < it.second }.let { if (it < 0) 0 else it + 1 }
        return c.copy(slots = listOf(Slot(wd, sec, if (sec > 0) minOf(sec + 1, SECTIONS.size) else 0, c.room)))
    }

    data class Today(val course: Course, val slot: Slot)

    /** 今天的课（一门课一天上两次就出现两次），按开始时间排序。 */
    fun today(courses: List<Course>, weekday: Int): List<Today> =
        courses.flatMap { c -> c.slots.filter { it.weekday == weekday }.map { Today(c, it) } }
            .sortedWith(compareBy({ startMin(it.slot) ?: 24 * 60 }, { it.course.name }))

    /** 下一节（或正在上的）课：结束时间还没过的第一节；都过了返回 null。 */
    fun next(today: List<Today>, nowMin: Int): Today? = today.firstOrNull { (endMin(it.slot) ?: (24 * 60)) > nowMin }

    // ---------------- 课程表图片识别结果 ----------------
    data class Entry(val name: String, val weekday: Int, val startSection: Int, val endSection: Int,
                     val room: String = "", val teacher: String = "", val weeks: String = "", val exists: Boolean = false)

    private fun Any?.asInt(): Int = when (this) {
        is Number -> toInt()
        is String -> Regex("\\d+").find(this)?.value?.toIntOrNull() ?: 0
        else -> 0
    }

    /** 宽松解析模型返回的 JSON：允许 ```json 包裹、顶层是数组或 {courses:[...]}、字段名中英文混用。 */
    fun parseTimetable(text: String): List<Entry> {
        val arr: JSONArray = findCourseArray(JsonLoose.values(text)) ?: throw IllegalArgumentException("没有找到课程列表")
        val out = mutableListOf<Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = (o.optString("name").ifBlank { o.optString("course") }.ifBlank { o.optString("courseName") }
                .ifBlank { o.optString("课程") }.ifBlank { o.optString("课程名") }.ifBlank { o.optString("title") }.ifBlank { o.optString("subject") }).trim()
            if (name.isBlank()) continue
            val wdRaw = o.opt("weekday") ?: o.opt("day") ?: o.opt("星期") ?: o.opt("周")
            val wd = when (wdRaw) { is Number -> wdRaw.toInt().let { if (it == 0) 7 else it }; else -> parseWeekday(wdRaw?.toString()) }
            var s = (o.opt("startSection") ?: o.opt("start") ?: o.opt("start_section") ?: o.opt("开始节次")).asInt()
            var e = (o.opt("endSection") ?: o.opt("end") ?: o.opt("end_section") ?: o.opt("结束节次")).asInt()
            val secStr = (o.optString("sections").ifBlank { o.optString("section") }.ifBlank { o.optString("节次") }).trim()
            if (secStr.isNotBlank()) {
                Regex("(\\d+)[^0-9]+(\\d+)").find(secStr)?.let {
                    s = it.groupValues[1].toInt(); e = it.groupValues[2].toInt()
                } ?: run {
                    if (s == 0) Regex("(\\d+)").find(secStr)?.let { s = it.groupValues[1].toInt(); e = s }
                }
            }
            // 只有时间没有节次：按默认作息换算成节次
            if (s == 0) {
                val st = legacyClock(o.optString("startTime").ifBlank { o.optString("start_time") }.ifBlank { o.optString("time") }.ifBlank { o.optString("上课时间") })
                val et = legacyClock(o.optString("endTime").ifBlank { o.optString("end_time") }.ifBlank { o.optString("下课时间") })
                    ?: Regex("\\d{1,2}[:：]\\d{2}\\D+(\\d{1,2}[:：]\\d{2})").find(o.optString("time"))?.groupValues?.get(1)?.let { legacyClock(it) }
                if (st != null) {
                    s = sectionAt(st, true); e = if (et != null) sectionAt(et, false) else s + 1
                }
            }
            if (e < s) e = s
            val weeks = when (val w = o.opt("weeks") ?: o.opt("周次") ?: o.opt("week")) {
                is JSONArray -> (0 until w.length()).joinToString(",") { w.opt(it).toString() }
                null -> ""
                else -> w.toString()
            }
            val room = o.optString("room").ifBlank { o.optString("location") }.ifBlank { o.optString("教室") }.ifBlank { o.optString("地点") }.take(30)
            val teacher = o.optString("teacher").ifBlank { o.optString("老师") }.ifBlank { o.optString("教师") }.take(20)
            out.add(Entry(name.take(30), if (wd in 1..7) wd else 0, s.coerceIn(0, 14), e.coerceIn(0, 14), room, teacher, weeks.take(30)))
        }
        // 同名同时间去重
        return out.distinctBy { "${norm(it.name)}|${it.weekday}|${it.startSection}" }
    }

    private fun looksLikeCourse(o: JSONObject?) = o != null && listOf("name", "course", "课程", "courseName", "课程名", "title", "subject").any { o.has(it) }

    /** 在模型给出的所有 JSON 值里找课程数组：顶层数组，或任意层级下 courses/data/items/schedule/课程… 字段。 */
    private fun findCourseArray(vals: List<Any>, depth: Int = 0): JSONArray? {
        if (depth > 4) return null
        for (v in vals) {
            if (v is JSONArray && (0 until v.length()).any { looksLikeCourse(v.optJSONObject(it)) }) return v
            if (v is JSONObject) {
                if (looksLikeCourse(v) && (v.has("weekday") || v.has("day") || v.has("星期"))) return JSONArray().put(v)
                val kids = v.keys().asSequence().mapNotNull { v.opt(it) as? Any }.filter { it is JSONObject || it is JSONArray }.toList()
                findCourseArray(kids, depth + 1)?.let { return it }
            }
        }
        // 嵌套数组（按星期分组的 [[...],[...]]）
        for (v in vals) if (v is JSONArray) {
            val flat = JSONArray(); for (i in 0 until v.length()) v.optJSONArray(i)?.let { a -> for (k in 0 until a.length()) if (looksLikeCourse(a.optJSONObject(k))) flat.put(a.get(k)) }
            if (flat.length() > 0) return flat
        }
        return null
    }

    /** 时间 → 最接近的节次（start=true 按开始时间匹配，否则按结束时间）。 */
    fun sectionAt(min: Int, start: Boolean): Int =
        SECTIONS.indices.minByOrNull { i -> kotlin.math.abs((if (start) SECTIONS[i].first else SECTIONS[i].second) - min) }!! + 1

    fun norm(s: String) = s.replace(Regex("[\\s（）()·\\-—_A-Za-z]"), "").replace("Ⅰ", "").replace("Ⅱ", "")

    /** 识别结果按课程名合并：一门课一周多次课 → 一门课多个时段。 */
    data class Group(val name: String, val entries: List<Entry>, val exists: Boolean)

    fun group(entries: List<Entry>, existing: List<Course>): List<Group> =
        entries.groupBy { norm(it.name).ifBlank { it.name } }.values.map { es ->
            Group(es.first().name, es.sortedWith(compareBy({ it.weekday }, { it.startSection })),
                existing.any { c -> norm(c.name) == norm(es.first().name) })
        }

    fun toCourse(g: Group, colorIdx: Int): Course {
        val first = g.entries.first()
        val slots = g.entries.filter { it.weekday in 1..7 }.map { Slot(it.weekday, it.startSection, it.endSection, it.room) }
        return Course(name = g.name, teacher = g.entries.firstOrNull { it.teacher.isNotBlank() }?.teacher ?: "", room = first.room,
            colorIdx = colorIdx, slots = slots, weeks = first.weeks,
            time = slots.firstOrNull()?.let { s -> "${WEEK[s.weekday - 1]} ${startMin(s)?.let { hm(it) } ?: ""}".trim() } ?: "")
    }
}
