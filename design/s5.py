import os, sys, math
sys.path.insert(0, "/workspace/work/classapp"); sys.path.insert(0, "/workspace/work/classapp/v4")
os.chdir("/workspace/work/classapp")
import me
from me import *
from me import T, lu, LU, logo, base, gcard, tog, finger, wave, header
from screens3 import TS, dim, TABS, rich_card, legend
from screens import icon
from rich import rich

LU.update({
 "scan": '<path d="M3 7V5a2 2 0 0 1 2-2h2"/><path d="M17 3h2a2 2 0 0 1 2 2v2"/><path d="M21 17v2a2 2 0 0 1-2 2h-2"/><path d="M7 21H5a2 2 0 0 1-2-2v-2"/><path d="M7 12h10"/>',
 "plus": '<path d="M5 12h14"/><path d="M12 5v14"/>',
 "mega": '<path d="m3 11 18-5v12L3 14v-3z"/><path d="M11.6 16.8a3 3 0 1 1-5.8-1.6"/>',
 "tv": '<rect width="20" height="15" x="2" y="7" rx="2" ry="2"/><polyline points="17 2 12 7 7 2"/>',
 "pres": '<path d="M2 3h20"/><path d="M21 3v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V3"/><path d="m7 21 5-5 5 5"/>',
 "cal": '<rect width="18" height="18" x="3" y="4" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/>',
 "pin": '<path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"/><circle cx="12" cy="10" r="3"/>',
 "clock": '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
 "list": '<path d="M3 12h.01"/><path d="M3 18h.01"/><path d="M3 6h.01"/><path d="M8 12h13"/><path d="M8 18h13"/><path d="M8 6h13"/>',
 "image": '<rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/>',
 "back": '<path d="m15 18-6-6 6-6"/>',
 "share": '<path d="M12 2v13"/><path d="m16 6-4-4-4 4"/><path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/>',
 "more": '<circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/>',
 "flask": '<path d="M10 2v7.527a2 2 0 0 1-.211.896L4.72 20.55a1 1 0 0 0 .9 1.45h12.76a1 1 0 0 0 .9-1.45l-5.069-10.127A2 2 0 0 1 14 9.527V2"/><path d="M8.5 2h7"/><path d="M7 16h10"/>',
 "chevd": '<path d="m6 9 6 6 6-6"/>',
 "sparkle": '<path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/>',
})

# ---------------- covers ----------------
COV = {
 "电机学": ("#4A78E0", "motor"), "信号与系统": ("#7A5AD6", "signal"), "电力电子技术": ("#E07A2E", "pwm"),
 "微机原理": ("#2E9E6A", "chip"), "习思想": ("#C9372C", "xi"), "高等数学": ("#2F6FD0", "int"),
 "线性代数": ("#5468C4", "matrix"), "电路原理": ("#2D8F9E", "circuit"), "自动控制原理": ("#8A5BB8", "control"),
 "大学物理": ("#3D86C6", "atom"), "模拟电子技术": ("#D2603A", "opamp"), "数字电子技术": ("#3C9C8C", "gate"),
}
def motif(kind, x, y, w, h):
    st = 'fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"'
    cx, cy = x + w * 0.70, y + h * 0.52
    R = h * 0.36
    s = ""
    if kind == "motor":
        s += f'<circle cx="{cx}" cy="{cy}" r="{R}" {st}/><circle cx="{cx}" cy="{cy}" r="{R*0.55}" {st}/>'
        for i in range(12):
            a = i * math.pi / 6
            s += f'<line x1="{cx+R*0.62*math.cos(a)}" y1="{cy+R*0.62*math.sin(a)}" x2="{cx+R*0.88*math.cos(a)}" y2="{cy+R*0.88*math.sin(a)}" {st}/>'
        s += f'<circle cx="{cx}" cy="{cy}" r="{R*0.14}" fill="#fff" fill-opacity=".8"/>'
    elif kind == "signal":
        pts = " ".join(f"{x+w*0.38+i*2},{cy+R*0.6*math.sin(i*0.11)*math.exp(-i*0.004)}" for i in range(int(w*0.28)))
        s += f'<polyline points="{pts}" {st}/>'
        for i in range(5):
            xx = x + w * 0.42 + i * w * 0.11; hh = R * (1.1 - i * 0.18)
            s += f'<line x1="{xx}" y1="{cy+R}" x2="{xx}" y2="{cy+R-hh}" {st} stroke-opacity=".55"/><circle cx="{xx}" cy="{cy+R-hh}" r="2.6" fill="#fff" fill-opacity=".55"/>'
    elif kind == "pwm":
        xx = x + w * 0.40; p = f"M{xx} {cy+R*0.5}"
        for i in range(8):
            d = 0.5 + 0.45 * math.sin(i * 0.8); seg = w * 0.065
            p += f" v{-R} h{seg*d} v{R} h{seg*(1-d)}"
        s += f'<path d="{p}" {st} stroke-opacity=".75"/>'
        pts = " ".join(f"{xx+i*2},{cy-R*0.1-R*0.45*math.sin(i*0.06)}" for i in range(int(w*0.26)))
        s += f'<polyline points="{pts}" {st}/>'
    elif kind == "chip":
        a = R * 1.3
        s += f'<rect x="{cx-a/2}" y="{cy-a/2}" width="{a}" height="{a}" rx="8" {st}/><rect x="{cx-a/4}" y="{cy-a/4}" width="{a/2}" height="{a/2}" rx="4" fill="#fff" fill-opacity=".35"/>'
        for i in range(5):
            o = -a / 2 + a * (i + 0.5) / 5
            s += f'<path d="M{cx+o} {cy-a/2} v-9 M{cx+o} {cy+a/2} v9 M{cx-a/2} {cy+o} h-9 M{cx+a/2} {cy+o} h9" {st}/>'
    elif kind == "xi":
        def star(sx, sy, rr):
            p = []
            for i in range(10):
                ang = -math.pi / 2 + i * math.pi / 5; rad = rr if i % 2 == 0 else rr * 0.4
                p.append(f"{sx+rad*math.cos(ang)},{sy+rad*math.sin(ang)}")
            return f'<polygon points="{" ".join(p)}" fill="#FFD66B"/>'
        s += star(x + w * 0.62, y + h * 0.36, R * 0.42)
        for i, (dx, dy) in enumerate([(0.74, 0.22), (0.79, 0.34), (0.79, 0.48), (0.74, 0.58)]):
            s += star(x + w * dx, y + h * dy, R * 0.13)
        for i in range(3):
            yy = y + h * (0.70 + i * 0.09)
            s += f'<line x1="{x+w*0.55}" y1="{yy}" x2="{x+w*0.9-i*14}" y2="{yy}" {st} stroke-opacity=".5"/>'
    elif kind == "int":
        s += f'<text x="{cx}" y="{cy+R*0.55}" font-family="Noto Serif SC" font-size="{R*2.1}" fill="#fff" fill-opacity=".9" text-anchor="middle" font-style="italic">∫</text>'
        s += f'<text x="{cx+R*0.95}" y="{cy+R*0.3}" font-family="Noto Serif SC" font-size="{R*0.7}" fill="#fff" fill-opacity=".7" font-style="italic">dx</text>'
    elif kind == "matrix":
        a = R * 1.1
        s += f'<path d="M{cx-a} {cy-a} h-7 v{2*a} h7 M{cx+a} {cy-a} h7 v{2*a} h-7" {st}/>'
        for i in range(3):
            for j in range(3):
                s += f'<circle cx="{cx-a*0.62+j*a*0.62}" cy="{cy-a*0.62+i*a*0.62}" r="{4 if i==j else 2.6}" fill="#fff" fill-opacity="{.95 if i==j else .5}"/>'
    elif kind == "circuit":
        xx = x + w * 0.42
        p = f"M{xx} {cy} h20 l5 -10 l8 20 l8 -20 l8 20 l8 -20 l5 10 h18 M{xx+80+18} {cy-12} v24 M{xx+80+26} {cy-12} v24 M{xx+106} {cy} h22"
        s += f'<path d="{p}" {st}/><path d="M{xx} {cy} v{R} h128 v{-R}" {st} stroke-opacity=".5"/>'
    elif kind == "control":
        xx = x + w * 0.42
        s += f'<rect x="{xx+30}" y="{cy-16}" width="44" height="32" rx="6" {st}/><circle cx="{xx+10}" cy="{cy}" r="9" {st}/>'
        s += f'<path d="M{xx-12} {cy} h13 M{xx+19} {cy} h11 M{xx+74} {cy} h40 M{xx+96} {cy} v{R*0.9} h-86 v{-R*0.9+9}" {st}/>'
    elif kind == "atom":
        for a in (0, 60, 120):
            s += f'<ellipse cx="{cx}" cy="{cy}" rx="{R}" ry="{R*0.36}" transform="rotate({a} {cx} {cy})" {st}/>'
        s += f'<circle cx="{cx}" cy="{cy}" r="5" fill="#fff"/>'
    elif kind == "opamp":
        a = R
        s += f'<path d="M{cx-a} {cy-a} L{cx+a} {cy} L{cx-a} {cy+a} Z M{cx-a-16} {cy-a*0.45} h16 M{cx-a-16} {cy+a*0.45} h16 M{cx+a} {cy} h18" {st}/>'
        s += f'<text x="{cx-a+8}" y="{cy-a*0.3}" font-size="14" fill="#fff" font-family="Noto Sans SC">−</text><text x="{cx-a+8}" y="{cy+a*0.6}" font-size="14" fill="#fff" font-family="Noto Sans SC">+</text>'
    elif kind == "gate":
        a = R
        s += f'<path d="M{cx-a} {cy-a} h{a} a{a} {a} 0 0 1 0 {2*a} h{-a} Z M{cx-a-18} {cy-a*0.5} h18 M{cx-a-18} {cy+a*0.5} h18 M{cx+a} {cy} h18" {st}/>'
    return s

def cover(name, x, y, w, h, rr=18, cid=[0]):
    col, kind = COV.get(name, ("#6B7280", "int"))
    cid[0] += 1; k = f"cv{cid[0]}"
    ch = name[0] if name != "习思想" else "思"
    return (f'<defs><clipPath id="{k}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rr}"/></clipPath>'
            f'<linearGradient id="{k}g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".16"/><stop offset="1" stop-color="#000" stop-opacity=".06"/></linearGradient></defs>'
            f'<g clip-path="url(#{k})"><rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{col}"/>'
            f'<text x="{x+w*0.04}" y="{y+h*0.98}" font-family="Noto Serif SC" font-weight="700" font-size="{h*1.05}" fill="#fff" fill-opacity=".10">{ch}</text>'
            f'{motif(kind, x, y, w, h)}<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="url(#{k}g)"/></g>')

def beta(x, y):
    return r(x, y, 46, 20, 10, ORANGE, .15) + T(x + 23, y + 14.5, "测试版", 11, 700, ORANGE, "middle")

def tabbar5(c, active):
    c = glass(c, 16, 768, 292, 62, 31, "large", alpha=.74)
    c = glass(c, 316, 768, 62, 62, 31, "large", alpha=.74)
    if active >= 0:
        x = 22 + active * 70
        c = glass(c, x, 774, 70, 50, 25, "regular", alpha=.94, lens=6)
    body = ""
    for i, (n, ic) in enumerate(TABS):
        cx = 57 + i * 70
        col = BLUE if i == active else INK
        body += icon(ic, cx, 792, col) + T(cx, 817, n, 10.5, 600, col, "middle")
    body += lu("mic", 347, 799, 25, RED, 2.2) + home_ind()
    return over(c, svg_layer(body))

def circ(c, x, y, ic, col=INK, d=44, tint=None):
    c = glass(c, x, y, d, d, d / 2, "regular", alpha=.7, tint=tint, tint_a=.9 if tint else 0)
    return over(c, svg_layer(lu(ic, x + d / 2, y + d / 2, 20, col, 2.1)))

# ================= frames =================
def f_splash():
    c = base()
    c = over(c, svg_layer(status()))
    c = glass(c, 155, 120, 80, 80, 24, "regular", alpha=.85, lens=10)
    c = over(c, svg_layer(TS(195, 173, "学", 38, INK, "middle") + TS(195, 246, "课堂学长", 30, INK, "middle") +
                          beta(144, 262) + T(214, 276.5, "v0.0.4", 13, 600, SEC)))
    c = dim(c, .12)
    c = glass(c, 24, 318, 342, 420, 34, "large", alpha=.82, blur=16)
    body = lu("mega", 52, 350, 20, BLUE, 2.1) + TS(72, 357, "公告", 20) + T(342, 356, "10月31日", 12, 500, SEC, "end")
    items = [("新", "首页只显示今天的课，全部课程在「课程」里"), ("新", "拍一张课程表，一键导入整学期"),
             ("新", "每门课自带封面，可在课程设置里换"), ("新", "导入 B 站录播课 + PPT，先体验一节"),
             ("改", "右下角改成麦克风，点一下就开始上课"), ("改", "「我的」模型配置合并成一张卡片")]
    y = 392
    for tag, s in items:
        col = BLUE if tag == "新" else ORANGE
        body += r(44, y - 14, 24, 20, 6, col, .14) + T(56, y + 0.5, tag, 11.5, 700, col, "middle") + T(78, y + 1, s, 13.5, 400, INK)
        y += 34
    body += r(44, y + 2, 302, 58, 14, "#FF9500", .10) + T(58, y + 26, "测试版提示：录音与笔记保存在本机，", 12.5, 500, "#B25000") + T(58, y + 46, "API Key 不会上传。遇到问题点「我的 › 反馈」。", 12.5, 500, "#B25000")
    c = over(c, svg_layer(body))
    c = glass(c, 44, 654, 302, 52, 26, "regular", tint=(10, 114, 232), tint_a=.9, lens=10)
    c = over(c, svg_layer(T(195, 686, "知道了", 16, 700, "#fff", "middle") +
                          f'<rect x="120" y="752" width="16" height="16" rx="5" fill="none" stroke="{SEC}" stroke-width="1.5"/>' + T(144, 765, "本版本不再显示", 13, 500, SEC) + home_ind()))
    return c

TODAY = [("电机学", "08:00–09:35", "教二 201", "王建国"), ("信号与系统", "10:10–11:45", "教三 305", "李敏"), ("习思想", "14:00–15:35", "综合楼 108", "陈晓")]
def f_home():
    c = base()
    c = over(c, svg_layer(status() + T(20, 92, "10月31日 星期六 · 今天 3 节", 13, 500, SEC) + TS(20, 134, "今天", 34) + beta(98, 112)))
    c = glass(c, 220, 102, 92, 40, 20, "regular", alpha=.7)
    c = over(c, svg_layer(lu("scan", 244, 122, 18, INK, 2.1) + T(260, 127.5, "导入", 14.5, 600, INK)))
    c = circ(c, 322, 100, "plus", "#fff", 44, tint=(29, 29, 31))
    # hero
    n, tm, room, tch = TODAY[0]
    c = gcard(c, 16, 158, 358, 290, 28)
    body = cover(n, 28, 170, 334, 132, 20)
    body += r(40, 182, 74, 24, 12, "#000", .25) + T(77, 198.5, "下一节", 12, 700, "#fff", "middle")
    body += TS(32, 336, n, 24) + lu("clock", 40, 357, 13, SEC) + T(52, 362, f"{tm}  ·  ", 13, 500, SEC) + lu("pin", 148, 357, 13, SEC) + T(160, 362, f"{room}  ·  {tch}", 13, 500, SEC)
    c = over(c, svg_layer(body))
    c = glass(c, 28, 382, 163, 52, 26, "regular", tint=(29, 29, 31), tint_a=.9, lens=10)
    c = glass(c, 199, 382, 163, 52, 26, "regular", alpha=.6, lens=8)
    c = over(c, svg_layer(lu("mic", 76, 408, 17, "#fff", 2.2) + T(90, 414, "开始上课", 15, 700, "#fff") +
                          lu("file", 240, 408, 17, INK, 2.1) + T(254, 414, "课前资料", 15, 700, INK)))
    y = 462
    c = over(c, svg_layer(T(36, y + 12, "稍后", 13, 500, SEC)))
    y += 22
    c = gcard(c, 16, y, 358, 2 * 84, 24)
    body = ""
    for i, (n, tm, room, tch) in enumerate(TODAY[1:]):
        yy = y + i * 84
        body += cover(n, 30, yy + 12, 88, 60, 14)
        body += TS(132, yy + 38, n, 17, INK, w=600) + T(132, yy + 60, f"{tm} · {room}", 12.5, 500, SEC)
        if i == 0: body += f'<line x1="132" y1="{yy+84}" x2="374" y2="{yy+84}" stroke="#000" stroke-opacity=".07"/>'
    c = over(c, svg_layer(body))
    for i in range(2):
        yy = y + i * 84
        c = glass(c, 300, yy + 24, 60, 36, 18, "regular", alpha=.6, shadow=False)
    c = over(c, svg_layer(T(330, y + 47, "资料", 13, 600, INK, "middle") + T(330, y + 131, "资料", 13, 600, INK, "middle")))
    yy = y + 186
    c = glass(c, 16, yy, 358, 50, 25, "regular", alpha=.55)
    c = over(c, svg_layer(lu("list", 42, yy + 25, 17, INK) + T(60, yy + 30.5, "课程", 15, 600, INK) + T(330, yy + 30.5, "共 12 门", 13, 500, SEC, "end") + lu("chev", 350, yy + 25, 15, SEC)))
    return tabbar5(c, 0)

ALL = ["电机学", "信号与系统", "电力电子技术", "微机原理", "习思想", "高等数学", "线性代数", "电路原理", "自动控制原理", "大学物理", "模拟电子技术", "数字电子技术"]
def f_courses():
    c = base()
    c = over(c, svg_layer(status() + TS(20, 134, "课程", 34) + T(20, 160, "2026 秋季学期 · 12 门", 13, 500, SEC)))
    c = circ(c, 16, 50, "back")
    c = glass(c, 220, 102, 92, 40, 20, "regular", alpha=.7)
    c = over(c, svg_layer(lu("scan", 244, 122, 18, INK, 2.1) + T(260, 127.5, "导入", 14.5, 600, INK)))
    c = circ(c, 322, 100, "plus", "#fff", 44, tint=(29, 29, 31))
    days = ["周一", "周二", "周三", "周四", "周五"]
    for i in range(10):
        x = 16 + (i % 2) * 183; y = 176 + (i // 2) * 168
        if y > 760: break
        c = gcard(c, x, y, 175, 156, 22)
    body = ""
    for i in range(10):
        x = 16 + (i % 2) * 183; y = 176 + (i // 2) * 168
        if y > 760: break
        n = ALL[i]
        body += cover(n, x + 8, y + 8, 159, 88, 16)
        body += TS(x + 14, y + 122, n, 15.5, INK, w=600) + T(x + 14, y + 143, f"{days[i%5]} {['1-2','3-4','5-6','7-8'][i%4]} 节 · {[12,9,11,7,14,10,8,6,5,9][i]} 节笔记", 11.5, 500, SEC)
    c = over(c, svg_layer(body))
    return tabbar5(c, 0)

def timetable_img(x, y, w, h):
    s = r(x, y, w, h, 8, "#FDFCF7") + f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="none" stroke="#000" stroke-opacity=".12"/>'
    cols, rows = 6, 6
    cw, rh = w / cols, (h - 22) / rows
    for j, d in enumerate(["", "一", "二", "三", "四", "五"]):
        s += T(x + cw * j + cw / 2, y + 15, d, 9, 600, "#555", "middle")
    for i in range(rows + 1):
        s += f'<line x1="{x}" y1="{y+22+i*rh}" x2="{x+w}" y2="{y+22+i*rh}" stroke="#000" stroke-opacity=".12"/>'
    for j in range(1, cols):
        s += f'<line x1="{x+cw*j}" y1="{y}" x2="{x+cw*j}" y2="{y+h}" stroke="#000" stroke-opacity=".12"/>'
    cells = [(1, 0, "电机学"), (2, 1, "信号与系统"), (3, 0, "微机原理"), (4, 2, "习思想"), (1, 3, "电力电子"), (5, 1, "电路原理"), (2, 4, "高数"), (3, 2, "自控原理"), (5, 4, "大学物理")]
    for (j, i, n) in cells:
        s += r(x + cw * j + 2, y + 24 + i * rh, cw - 4, rh - 4, 4, "#8DB8FF", .35) + T(x + cw * j + cw / 2, y + 24 + i * rh + rh / 2 + 3, n, 7.5, 600, "#333", "middle")
    return s

def f_import():
    c = base()
    c = over(c, svg_layer(status() + TS(20, 134, "导入课程表", 30)))
    c = circ(c, 16, 50, "back")
    c = gcard(c, 16, 152, 358, 236, 26)
    body = timetable_img(30, 166, 330, 180)
    body += f'<rect x="30" y="250" width="330" height="3" fill="{BLUE}" fill-opacity=".8"/><rect x="30" y="226" width="330" height="27" fill="{BLUE}" fill-opacity=".08"/>'
    body += lu("sparkle", 40, 368, 15, BLUE, 2, fill=BLUE) + T(54, 373, "识别到 9 门课 · 第 1–16 周 · 教务系统截图", 12.5, 600, BLUE)
    c = over(c, svg_layer(body))
    rows = [("电机学", "周一 1-2 节 · 教二 201", True), ("信号与系统", "周二 3-4 节 · 教三 305", True), ("微机原理", "周三 1-2 节 · 实验楼 402", True),
            ("习思想", "周四 5-6 节 · 综合楼 108", True), ("电力电子技术", "周一 7-8 节 · 教二 103", True), ("电路原理", "周五 3-4 节 · 教一 210", False)]
    y = 402
    c = gcard(c, 16, y, 358, len(rows) * 52, 22)
    body = ""
    for i, (n, sub, on) in enumerate(rows):
        yy = y + i * 52
        body += cover(n, 30, yy + 9, 50, 34, 9)
        body += T(92, yy + 24, n, 14.5, 600, INK) + T(92, yy + 42, sub, 11.5, 500, SEC)
        if on: body += f'<circle cx="352" cy="{yy+26}" r="11" fill="{BLUE}"/>' + lu("check", 352, yy + 26, 13, "#fff", 3)
        else: body += f'<circle cx="352" cy="{yy+26}" r="10.5" fill="none" stroke="{SEC}" stroke-opacity=".5" stroke-width="1.5"/>'
        if n == "电路原理": body += r(200, yy + 15, 70, 18, 9, ORANGE, .15) + T(235, yy + 28, "已存在", 10.5, 700, ORANGE, "middle")
        if i < len(rows) - 1: body += f'<line x1="92" y1="{yy+52}" x2="374" y2="{yy+52}" stroke="#000" stroke-opacity=".07"/>'
    c = over(c, svg_layer(body))
    c = glass(c, 16, 740, 358, 54, 27, "regular", tint=(10, 114, 232), tint_a=.9, lens=10)
    c = over(c, svg_layer(T(195, 773, "一键导入 8 门课", 16, 700, "#fff", "middle") + home_ind()))
    return c

def f_bili():
    c = base()
    c = over(c, svg_layer(status() + TS(20, 134, "导入录播课", 30) + beta(190, 112) + T(20, 160, "用一节 B 站教学视频 + 对应 PPT 体验完整流程", 13, 500, SEC)))
    c = circ(c, 16, 50, "back")
    c = glass(c, 16, 178, 358, 48, 24, "regular", alpha=.6)
    c = over(c, svg_layer(lu("tv", 40, 202, 18, "#FB7299", 2.1) + T(60, 207.5, "bilibili.com/video/BV1…", 14.5, 500, INK) + T(352, 207.5, "粘贴", 14, 600, BLUE, "end")))
    c = gcard(c, 16, 238, 358, 116, 24)
    body = cover("电机学", 28, 250, 132, 92, 14) + f'<circle cx="94" cy="296" r="16" fill="#000" fill-opacity=".35"/>' + lu("play", 96, 296, 14, "#fff", 2, fill="#fff")
    body += r(118, 324, 36, 14, 4, "#000", .5) + T(136, 334.5, "42:18", 9, 600, "#fff", "middle")
    body += T(172, 270, "电机学 第 3 讲", 15, 700, INK) + T(172, 290, "变压器的等效电路与相量图", 13, 500, INK) + T(172, 312, "UP 主 · 某高校电机学公开课", 11.5, 500, SEC) + T(172, 332, "将只下载音轨，约 19 MB", 11.5, 500, SEC)
    c = over(c, svg_layer(body))
    c = glass(c, 16, 366, 358, 60, 24, "regular", alpha=.6)
    c = over(c, svg_layer(lu("pres", 44, 396, 19, ORANGE, 2.1) + T(66, 392, "第3讲-变压器.pptx", 14.5, 600, INK) + T(66, 411, "36 页 · 提取 214 个热词、18 个公式", 11.5, 500, SEC) + lu("check", 350, 396, 18, GREEN, 2.6)))
    steps = [("下载音轨", "完成", 1), ("对齐课件页码", "完成", 1), ("语音识别", "62%", .62), ("实时标注与补全", "等待", 0), ("生成笔记", "等待", 0)]
    y = 440
    c = gcard(c, 16, y, 358, 58 * len(steps) + 10, 24)
    body = ""
    for i, (n, st, p) in enumerate(steps):
        yy = y + 14 + i * 58
        if p == 1: body += f'<circle cx="44" cy="{yy+16}" r="11" fill="{GREEN}"/>' + lu("check", 44, yy + 16, 13, "#fff", 3)
        elif p > 0: body += f'<circle cx="44" cy="{yy+16}" r="11" fill="none" stroke="{BLUE}" stroke-opacity=".25" stroke-width="2.5"/><path d="M44 {yy+5} a11 11 0 1 1 -10.5 14" fill="none" stroke="{BLUE}" stroke-width="2.5" stroke-linecap="round"/>'
        else: body += f'<circle cx="44" cy="{yy+16}" r="10" fill="none" stroke="{SEC}" stroke-opacity=".4" stroke-width="1.6"/>'
        body += T(66, yy + 21, n, 14.5, 600 if p > 0 else 400, INK if p > 0 else SEC) + T(352, yy + 21, st, 12.5, 600, GREEN if p == 1 else BLUE if p > 0 else SEC, "end")
        if 0 < p < 1:
            body += r(66, yy + 32, 286, 5, 2.5, "#787880", .15) + r(66, yy + 32, 286 * p, 5, 2.5, BLUE)
            body += T(66, yy + 52, "“…所以励磁支路可以移到一次侧前面，[r:简化等效电路]…”".replace("[r:", "").replace("]", ""), 11, 500, SEC)
        if i < len(steps) - 1: body += f'<line x1="44" y1="{yy+30 if p!=.62 else yy+58}" x2="44" y2="{yy+46 if p!=.62 else yy+60}" stroke="#000" stroke-opacity=".1" stroke-width="1.5"/>'
    c = over(c, svg_layer(body))
    c = glass(c, 16, 752, 358, 50, 25, "regular", alpha=.6)
    return over(c, svg_layer(T(195, 782, "后台处理中，完成后灵动岛提醒你", 14, 600, SEC, "middle") + home_ind()))

def f_note():
    c = base()
    # cover header band
    c = over(c, svg_layer(cover("电机学", 0, 0, 390, 210, 0)))
    c = over(c, svg_layer(f'<rect x="0" y="120" width="390" height="100" fill="url(#fade)"/><defs><linearGradient id="fade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F2F2F7" stop-opacity="0"/><stop offset="1" stop-color="#F2F2F7"/></linearGradient></defs>'))
    c = over(c, svg_layer(status("#fff")))
    c = circ(c, 16, 54, "back"); c = circ(c, 278, 54, "share"); c = circ(c, 330, 54, "more")
    c = gcard(c, 16, 150, 358, 104, 26)
    c = over(c, svg_layer(T(32, 176, "电机学 · 第 3 讲 · 10月31日", 12, 600, BLUE) + TS(32, 208, "变压器的等效电路", 23) +
                          lu("clock", 38, 234, 12, SEC) + T(50, 238.5, "95 分钟 · 原声模式 · 12 个考点 · 3 处补全待确认", 11.5, 500, SEC)))
    # segmented scroll chips
    segs = ["笔记", "助记", "概念图", "复习卡", "实录"]
    c = glass(c, 16, 266, 358, 44, 22, "regular", alpha=.5)
    c = glass(c, 20, 270, 68, 36, 18, "regular", alpha=.96, lens=4)
    body = "".join(T(54 + i * 71, 293, s, 14, 700 if i == 0 else 500, BLUE if i == 0 else INK, "middle") for i, s in enumerate(segs))
    c = over(c, svg_layer(body))
    # toc + legend pill row
    c = glass(c, 16, 320, 120, 32, 16, "regular", alpha=.55, shadow=False)
    c = over(c, svg_layer(lu("list", 34, 336, 14, INK) + T(48, 341, "目录 · 4 节", 12.5, 600, INK) + lu("chevd", 122, 336, 13, SEC)))
    leg = [("#E4EEFF", "#1758C7", "概念"), ("#FFE3E1", "#C2271D", "考点"), ("#FFEBD3", "#B25000", "助记")]
    body = ""
    x = 150
    for bgc, fg, n in leg:
        body += r(x, 326, 40, 20, 6, bgc) + T(x + 20, 340.5, n, 11, 700, fg, "middle"); x += 46
    body += f'<line x1="{x+2}" y1="344" x2="{x+30}" y2="344" stroke="#1758C7" stroke-width="1.6"/>' + T(x + 16, 340, "强调", 11, 500, INK, "middle")
    c = over(c, svg_layer(body))
    y = 366
    blocks = [r"**1. 等效电路的由来** 把二次侧折算到一次侧：$U_2'=kU_2,\ I_2'=\frac{I_2}{k}$，[b:折算前后磁动势与功率不变]。",
              r"**2. T 形等效电路** [r:励磁支路 $Z_m=R_m+jX_m$ 并在中间]，两侧分别是漏阻抗 $Z_1$、$Z_2'$。[u:考试必画]",
              r"**3. 简化等效电路** 因为 [o:$I_0\ll I_N$，励磁支路可前移]：$$Z_k=R_k+jX_k$$"]
    for b in blocks:
        c, h = rich_card(c, 16, y, 358, b, size=14.5)
        y += h + 10
        if y > 740: break
    # floating ask bar
    c = glass(c, 16, 702, 358, 50, 25, "regular", alpha=.72)
    c = over(c, svg_layer(lu("sparkle", 40, 727, 16, BLUE, 2, fill=BLUE) + T(58, 732, "问学长：这一节哪里最容易考？", 14, 500, SEC) + home_ind()))
    return tabbar5(c, 2) if False else c

def f_covers():
    c = base()
    c = over(c, svg_layer(status() + TS(20, 134, "选择封面", 30) + T(20, 160, "已为常见课程预制，按课程名自动匹配", 13, 500, SEC)))
    c = circ(c, 16, 50, "back")
    body = ""
    for i, n in enumerate(ALL):
        x = 16 + (i % 3) * 122; y = 178 + (i // 3) * 132
        body += cover(n, x, y, 114, 92, 16) + T(x + 57, y + 112, n, 12.5, 600 if i else 700, BLUE if i == 0 else INK, "middle")
        if i == 0: body += f'<rect x="{x-3}" y="{y-3}" width="120" height="98" rx="18" fill="none" stroke="{BLUE}" stroke-width="2.4"/>' + f'<circle cx="{x+100}" cy="{y+14}" r="10" fill="{BLUE}"/>' + lu("check", x + 100, y + 14, 12, "#fff", 3)
    c = over(c, svg_layer(body))
    c = glass(c, 16, 712, 358, 52, 26, "regular", alpha=.6)
    c = over(c, svg_layer(lu("image", 150, 738, 18, INK) + T(168, 743.5, "从相册选择", 15, 600, INK) + home_ind()))
    return c

def model_card2(c, state="idle"):
    CX, CY, CW, CH = 16, 278, 358, 200
    c = gcard(c, CX, CY, CW, CH, 28)
    col_w = CW / 4; lx = CX + col_w / 2
    c = glass(c, lx - 33, CY + 46, 66, 66, 20, "regular", alpha=.92, lens=5)
    rx0 = CX + col_w + 4; rw = CW - col_w - 4 - 14
    for i in range(2):
        c = glass(c, rx0, CY + 16 + i * 60, rw, 52, 26, "regular", alpha=.62, lens=6)
    zx, zy, zd = CX + CW - 14 - 40, CY + CH - 12 - 40, 40
    c = glass(c, zx, zy, zd, zd, 20, "regular", tint=(10, 114, 232), tint_a=.9, lens=7)
    body = T(lx, CY + 34, "SiliconFlow", 13, 700, INK, "middle") + logo("siliconcloud-color", lx - 21, CY + 58, 42)
    body += f'<circle cx="{lx-26}" cy="{CY+132}" r="3.5" fill="{GREEN}"/>' + T(lx - 18, CY + 136.5, "已连接", 12, 500, SEC)
    body += T(lx, CY + 156, "余额 ¥14.20", 11, 500, SEC, "middle", op=.8)
    rows = [("mic", "语音识别", "SenseVoiceSmall", "0.82s"), ("box", "大模型", "DeepSeek-V3.2", "46 tok/s")]
    for i, (ic, lab, m, st) in enumerate(rows):
        yy = CY + 16 + i * 60
        body += f'<circle cx="{rx0+26}" cy="{yy+26}" r="18" fill="{BLUE}" fill-opacity=".10"/>' + lu(ic, rx0 + 26, yy + 26, 19, BLUE, 2.1)
        body += T(rx0 + 52, yy + 21, lab, 11.5, 500, SEC) + T(rx0 + 52, yy + 40, m, 14, 600, INK)
        body += T(rx0 + rw - 14, yy + 32, st, 11, 600, GREEN, "end")
    body += lu("zap", zx + 20, zy + 20, 17, "#fff", 2, fill="#fff")
    body += f'<circle cx="{zx+36}" cy="{zy+5}" r="7.5" fill="#fff"/>' + lu("mic", zx + 36, zy + 5, 9.5, BLUE, 2.4)
    body += T(rx0 + 4, CY + CH - 34, "点闪电测试，长按切换测试对象", 11, 500, SEC, op=.8) + T(rx0 + 4, CY + CH - 18, "长按麦克风或方块图标换模型", 11, 500, SEC, op=.8)
    return over(c, svg_layer(body))

def f_me2():
    c = base(); c = header(c); c = me.account(c)
    c = over(c, svg_layer(T(36, 268, "模型", 13, 500, SEC)))
    c = model_card2(c)
    c = me.lower_sections(c, 510)
    return tabbar5(c, 3)

FR = [("01-开屏公告", f_splash), ("02-首页-今天", f_home), ("03-课程", f_courses), ("04-导入课程表", f_import),
      ("05-导入B站录播课", f_bili), ("06-笔记", f_note), ("07-选择封面", f_covers), ("08-我的-模型卡片", f_me2)]
if __name__ == "__main__":
    only = sys.argv[1:]
    for n, f in FR:
        if only and not any(o in n for o in only): continue
        f().convert("RGB").save(f"v5/{n}.png"); print(n, flush=True)
