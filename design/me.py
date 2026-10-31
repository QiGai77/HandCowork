import os, re, sys
sys.path.insert(0, "/workspace/work/classapp")
os.chdir("/workspace/work/classapp")
from glass import *
from glass import t as T
from screens3 import TS, dim, check, TABS
from screens import icon
from screens2 import BLUE, INK, SEC, RED, ORANGE, GREEN

ASSET = "v4/assets/"
LU = {
 "mic": '<path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" x2="12" y1="19" y2="22"/>',
 "box": '<path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/><path d="m3.3 7 8.7 5 8.7-5"/><path d="M12 22V12"/>',
 "zap": '<path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"/>',
 "key": '<path d="M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z"/><circle cx="16.5" cy="7.5" r=".5"/>',
 "refresh": '<path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/>',
 "check": '<path d="M20 6 9 17l-5-5"/>',
 "eye": '<path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/>',
 "link": '<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>',
 "logout": '<path d="m16 17 5-5-5-5"/><path d="M21 12H9"/><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>',
 "play": '<polygon points="6 3 20 12 6 21 6 3"/>',
 "search": '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
 "phone": '<rect width="14" height="20" x="5" y="2" rx="2" ry="2"/><path d="M12 18h.01"/>',
 "mail": '<rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>',
 "cloud": '<path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"/>',
 "chev": '<path d="m9 18 6-6-6-6"/>',
 "shield": '<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/>',
 "chat": '<path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z"/>',
 "x": '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
 "file": '<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10 9H8"/><path d="M16 13H8"/><path d="M16 17H8"/>',
 "hand": '<path d="M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2"/><path d="M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2"/><path d="M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8"/><path d="M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15"/>',
}
def lu(n, cx, cy, size=20, col=INK, sw=2.0, fill="none"):
    k = size / 24
    return (f'<g transform="translate({cx-size/2},{cy-size/2}) scale({k})" fill="{fill}" stroke="{col}" '
            f'stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round">{LU[n]}</g>')

def logo(name, x, y, size):
    s = open(ASSET + name + ".svg").read()
    inner = re.sub(r'^.*?<svg[^>]*>', '', s, flags=re.S).replace('</svg>', '')
    inner = re.sub(r'<title>.*?</title>', '', inner)
    if 'currentColor' in inner: inner = inner.replace('currentColor', '#1D1D1F')
    k = size / 24
    return f'<g transform="translate({x},{y}) scale({k})" fill="#1D1D1F">{inner}</g>'

def bg():
    return aurora([(20, 80, 300, (141, 184, 255), .30), (390, 320, 280, (255, 192, 138), .26),
                   (40, 590, 260, (158, 230, 184), .22), (350, 800, 280, (201, 182, 255), .24)])

def dots():
    s = ""
    for y in range(9, 844, 18):
        for x in range(9, 390, 18):
            s += f'<circle cx="{x}" cy="{y}" r="1" fill="#000" fill-opacity=".06"/>'
    return s

def base():
    c = bg()
    return over(c, svg_layer(dots()))

def gcard(c, x, y, w, h, r=24):
    return glass(c, x, y, w, h, r, "material", alpha=.56, blur=14)

def tog(x, y, on=True):
    return (r(x, y, 50, 30, 15, GREEN if on else "#787880", 1 if on else 0.20) +
            f'<circle cx="{x+35 if on else x+15}" cy="{y+15}" r="13" fill="#fff"/>')

def tabbar(c, active):
    c = glass(c, 16, 768, 292, 62, 31, "large", alpha=.74)
    c = glass(c, 316, 768, 62, 62, 31, "large", alpha=.74)
    x = 22 + active * 70
    c = glass(c, x, 774, 70, 50, 25, "regular", alpha=.94, lens=6, shadow=True)
    body = ""
    for i, (n, ic) in enumerate(TABS):
        cx = 57 + i * 70
        col = BLUE if i == active else INK
        body += icon(ic, cx, 792, col) + T(cx, 817, n, 10.5, 600, col, "middle")
    body += icon("search", 347, 799, INK, 1.15, 2.1) + home_ind()
    return over(c, svg_layer(body))

# ---------- shared: model card ----------
CX, CY, CW, CH = 16, 278, 358, 196
def model_card(c, zap_state="idle", pressed=None, target="mic"):
    c = gcard(c, CX, CY, CW, CH, 28)
    # left 1/4 column
    col_w = CW / 4
    lx = CX + col_w / 2
    c = glass(c, lx - 33, CY + 46, 66, 66, 20, "regular", alpha=.92, lens=5)
    # right: two model chips
    rx0, rw = CX + col_w + 4, 178
    for i in range(2):
        yy = CY + 22 + i * 64
        sc = 1.04 if pressed == i else 1.0
        c = glass(c, rx0 - (sc - 1) * 90, yy - (sc - 1) * 27, rw * sc, 54 * sc, 27 * sc, "regular", alpha=.62 if pressed != i else .8, lens=8 if pressed == i else 6)
    # zap big button
    zx, zy, zw, zh = rx0 + rw + 10, CY + 22, CW - (col_w + 4) - rw - 10 - 14, 118
    tintc = (10, 114, 232) if zap_state != "ok" else (40, 170, 80)
    if pressed == "zap":
        c = glass(c, zx - 4, zy - 5, zw + 8, zh + 10, 26, "regular", tint=tintc, tint_a=.86, lens=14, blur=3)
    else:
        c = glass(c, zx, zy, zw, zh, 24, "regular", tint=tintc, tint_a=.9, lens=9)
    body = T(lx, CY + 34, "SiliconFlow", 13, 700, INK, "middle")
    body += logo("siliconcloud-color", lx - 21, CY + 58, 42)
    body += f'<circle cx="{lx-26}" cy="{CY+132}" r="3.5" fill="{GREEN}"/>' + T(lx - 18, CY + 136.5, "已连接", 12, 500, SEC)
    body += T(lx, CY + 156, "余额 ¥14.20", 11, 500, SEC, "middle", op=.8)
    rows = [("mic", "语音识别", "SenseVoiceSmall"), ("box", "大模型", "DeepSeek-V3.2")]
    for i, (ic, lab, m) in enumerate(rows):
        yy = CY + 22 + i * 64
        body += f'<circle cx="{rx0+27}" cy="{yy+27}" r="18" fill="{BLUE}" fill-opacity=".10"/>' + lu(ic, rx0 + 27, yy + 27, 19, BLUE, 2.1)
        body += T(rx0 + 54, yy + 22, lab, 11.5, 500, SEC) + T(rx0 + 54, yy + 41, m, 13.5, 600, INK)
    zcx, zcy = zx + zw / 2, zy + zh / 2
    if zap_state == "ok":
        body += lu("check", zcx, zcy - 8, 26, "#fff", 2.6) + T(zcx, zcy + 24, "通过", 12, 700, "#fff", "middle")
    elif zap_state == "run":
        body += f'<circle cx="{zcx}" cy="{zcy-8}" r="12" fill="none" stroke="#fff" stroke-opacity=".3" stroke-width="2.6"/>'
        body += f'<path d="M{zcx} {zcy-20} a12 12 0 0 1 12 12" fill="none" stroke="#fff" stroke-width="2.6" stroke-linecap="round"/>'
        body += T(zcx, zcy + 24, "测试中", 12, 700, "#fff", "middle")
    else:
        body += lu("zap", zcx, zcy - 8, 22, "#fff", 2.2, fill="#fff") + T(zcx, zcy + 24, "测试", 12, 700, "#fff", "middle")
    # tiny target badge on zap: which model gets tested
    badge = "box" if (zap_state == "llm" or target == "box") else "mic"
    body += f'<circle cx="{zx+zw-10}" cy="{zy+11}" r="9" fill="#fff" fill-opacity=".95"/>' + lu(badge, zx + zw - 10, zy + 11, 11, BLUE, 2.4)
    body += T(CX + CW / 2, CY + CH - 14, "点闪电测试 · 长按闪电切换对象 · 长按图标换模型", 11, 500, SEC, "middle", op=.75)
    return over(c, svg_layer(body)), (zx, zy, zw, zh)

def header(c, title="我的"):
    return over(c, svg_layer(status() + TS(20, 134, title, 34)))

def account(c):
    c = gcard(c, 16, 154, 358, 84, 26)
    body = (f'<circle cx="58" cy="196" r="26" fill="{BLUE}" fill-opacity=".14"/>' + TS(58, 204, "Q", 21, BLUE, "middle") +
            TS(98, 190, "Qi Gai", 18, INK, w=600) +
            lu("cloud", 106, 210, 14, GREEN, 2.2) + T(118, 214.5, "已同步 · 3 分钟前 · qigai730@gmail.com", 11.5, 500, SEC) +
            lu("chev", 354, 196, 18, SEC, 2.2))
    return over(c, svg_layer(body))

def lower_sections(c, y0=506):
    body = T(36, y0, "转写与理解", 13, 500, SEC)
    c = gcard(c, 16, y0 + 10, 358, 3 * 50, 22)
    rows = ["默认模式", "上下文补全（需确认）", "分段时长"]
    for i, k in enumerate(rows):
        yy = y0 + 10 + i * 50
        body += T(34, yy + 31, k, 15.5, 400, INK)
        if i < 2: body += f'<line x1="34" y1="{yy+50}" x2="374" y2="{yy+50}" stroke="#000" stroke-opacity=".07"/>'
    c = over(c, svg_layer(body))
    yy = y0 + 10
    c = glass(c, 238, yy + 9, 122, 32, 16, "regular", alpha=.35, tint=(120, 120, 128), tint_a=.08, shadow=False)
    c = glass(c, 300, yy + 11, 58, 28, 14, "regular", alpha=.95, lens=4)
    b2 = T(269, yy + 30, "原声", 13, 500, INK, "middle") + T(329, yy + 30, "学长", 13, 700, BLUE, "middle")
    b2 += tog(310, yy + 60)
    for j, n in enumerate(["10s", "15s", "20s"]):
        x = 236 + j * 42
        b2 += r(x, yy + 112, 36, 26, 13, BLUE if n == "15s" else "#787880", 1 if n == "15s" else 0.13) + T(x + 18, yy + 129.5, n, 12.5, 600, "#fff" if n == "15s" else INK, "middle")
    c = over(c, svg_layer(b2))
    y1 = y0 + 186
    body = T(36, y1, "账号与数据", 13, 500, SEC)
    c = gcard(c, 16, y1 + 10, 358, 100, 22)
    body += lu("cloud", 40, y1 + 35, 18, INK) + T(60, y1 + 41, "云同步课程与笔记", 15.5, 400, INK) + tog(310, y1 + 20)
    body += f'<line x1="34" y1="{y1+60}" x2="374" y2="{y1+60}" stroke="#000" stroke-opacity=".07"/>'
    body += lu("logout", 40, y1 + 85, 18, RED) + T(60, y1 + 91, "退出登录", 15.5, 500, RED)
    return over(c, svg_layer(body))

# ================= frames =================
def f0_login():
    c = base()
    c = over(c, svg_layer(status()))
    c = glass(c, 155, 140, 80, 80, 24, "regular", alpha=.85, lens=10)
    c = over(c, svg_layer(TS(195, 193, "学", 38, INK, "middle") + TS(195, 268, "课堂学长", 32, INK, "middle") +
                          T(195, 296, "把老师讲的，变成你记得住的", 14, 500, SEC, "middle")))
    c = gcard(c, 16, 334, 358, 268, 30)
    c = glass(c, 32, 354, 326, 52, 26, "regular", alpha=.6, shadow=False)
    c = glass(c, 32, 418, 210, 52, 26, "regular", alpha=.6, shadow=False)
    c = glass(c, 250, 418, 108, 52, 26, "regular", alpha=.5, tint=(10, 114, 232), tint_a=.12, shadow=False)
    c = glass(c, 32, 494, 326, 54, 27, "regular", tint=(29, 29, 31), tint_a=.9, lens=10)
    body = (lu("phone", 58, 380, 18, SEC) + T(76, 385.5, "+86", 15.5, 600, INK) + f'<line x1="110" y1="368" x2="110" y2="392" stroke="#000" stroke-opacity=".12"/>' +
            T(122, 385.5, "133 2147 3567", 16, 500, INK) +
            lu("shield", 58, 444, 18, SEC) + T(76, 449.5, "6 位验证码", 15.5, 400, SEC, op=.7) +
            T(304, 449.5, "获取验证码", 14, 600, BLUE, "middle") +
            T(195, 527, "登录 / 注册", 16.5, 700, "#fff", "middle") +
            f'<circle cx="42" cy="574" r="7.5" fill="{BLUE}"/>' + lu("check", 42, 574, 10, "#fff", 3) +
            T(56, 578.5, "已阅读并同意《用户协议》《隐私政策》", 12, 500, SEC))
    c = over(c, svg_layer(body))
    c = over(c, svg_layer(f'<line x1="40" y1="646" x2="150" y2="646" stroke="#000" stroke-opacity=".1"/><line x1="240" y1="646" x2="350" y2="646" stroke="#000" stroke-opacity=".1"/>' +
                          T(195, 650.5, "其他方式", 12.5, 500, SEC, "middle")))
    for i, ic in enumerate(["chat", "mail", "key"]):
        c = glass(c, 99 + i * 70, 672, 52, 52, 26, "regular", alpha=.6)
    body = lu("chat", 125, 698, 22, GREEN, 2.1) + lu("mail", 195, 698, 22, INK, 2) + T(265, 705, "G", 20, 700, "#4285F4", "middle")
    body += T(125, 744, "微信", 11.5, 500, SEC, "middle") + T(195, 744, "邮箱", 11.5, 500, SEC, "middle") + T(265, 744, "Google", 11.5, 500, SEC, "middle")
    body += T(195, 800, "先不登录，仅在本机使用", 14, 600, BLUE, "middle") + home_ind()
    return over(c, svg_layer(body))

def f1_me():
    c = base(); c = header(c); c = account(c)
    c = over(c, svg_layer(T(36, 268, "模型", 13, 500, SEC)))
    c, _ = model_card(c)
    c = lower_sections(c)
    return tabbar(c, 3)

def finger(cx, cy, ring=True):
    s = f'<circle cx="{cx}" cy="{cy}" r="20" fill="#000" fill-opacity=".10"/><circle cx="{cx}" cy="{cy}" r="11" fill="#fff" fill-opacity=".9" stroke="#000" stroke-opacity=".15"/>'
    if ring: s += f'<circle cx="{cx}" cy="{cy}" r="28" fill="none" stroke="{BLUE}" stroke-width="2" stroke-dasharray="132 44" stroke-linecap="round"/>'
    return s

def f2_longzap():
    c = base(); c = header(c); c = account(c)
    c = over(c, svg_layer(T(36, 268, "模型", 13, 500, SEC)))
    c, (zx, zy, zw, zh) = model_card(c, "llm", pressed="zap")
    c = lower_sections(c)
    c = tabbar(c, 3)
    c = dim(c, .18)
    # re-draw card bright? keep simple: popover above zap
    px, pw, py, ph = 180, 190, 196, 132
    c = glass(c, px, py, pw, ph, 26, "large", alpha=.78)
    c = glass(c, px + 8, py + 70, pw - 16, 54, 20, "regular", alpha=.3, tint=(10, 114, 232), tint_a=.14, shadow=False)
    body = T(px + 18, py + 26, "闪电测试对象", 12, 600, SEC)
    body += f'<circle cx="{px+34}" cy="{py+50}" r="15" fill="#787880" fill-opacity="0.13"/>' + lu("mic", px + 34, py + 50, 16, INK) + T(px + 58, py + 47, "语音模型", 14.5, 600, INK) + T(px + 58, py + 63, "测试音频 6.2 秒", 11, 500, SEC)
    body += f'<circle cx="{px+34}" cy="{py+97}" r="15" fill="{BLUE}" fill-opacity=".15"/>' + lu("box", px + 34, py + 97, 16, BLUE) + T(px + 58, py + 94, "LLM 模型", 14.5, 700, BLUE) + T(px + 58, py + 110, "标准提示词 · 测速", 11, 500, SEC)
    body += lu("check", px + pw - 26, py + 97, 18, BLUE, 2.6)
    body += f'<path d="M{zx+zw/2-9} {py+ph} l9 10 l9 -10z" fill="#fff" fill-opacity=".86"/>'
    body += finger(zx + zw / 2, zy + zh / 2 + 6)
    body += T(195, 760, "长按闪电 · 拖到选项上松手即切换", 12.5, 600, "#fff", "middle")
    return over(c, svg_layer(body))

def wave(x, y, w, n=44, col=BLUE, played=0.6):
    s = ""
    import math
    for i in range(n):
        hh = 4 + 18 * abs(math.sin(i * .62) * math.cos(i * .23))
        s += r(x + i * (w / n), y - hh / 2, 2.6, hh, 1.3, col if i / n < played else "#787880", 1 if i / n < played else 0.33)
    return s

def f3_test_asr():
    c = base(); c = header(c); c = account(c)
    c = over(c, svg_layer(T(36, 268, "模型", 13, 500, SEC)))
    c, _ = model_card(c, "ok")
    # result panel
    y = 488
    c = gcard(c, 16, y, 358, 196, 24)
    c = glass(c, 30, y + 48, 40, 40, 20, "regular", alpha=.9, lens=5)
    body = (lu("mic", 40, y + 24, 15, BLUE, 2.2) + T(54, y + 29, "语音模型测试 · SenseVoiceSmall", 13, 700, INK) +
            T(360, y + 29, "0.82 s", 13, 700, GREEN, "end") +
            lu("play", 50, y + 68, 14, INK, 2, fill=INK) + wave(84, y + 68, 236) + T(360, y + 73, "6.2s", 11.5, 500, SEC, "end") +
            T(30, y + 114, "标准稿", 11.5, 600, SEC) +
            T(30, y + 134, "这节课我们讲方向导数与梯度，梯度方向就是", 13.5, 400, SEC) +
            T(30, y + 154, "函数增长最快的方向。", 13.5, 400, SEC) +
            T(30, y + 178, "识别结果  ", 11.5, 600, SEC) +
            f'<rect x="88" y="{y+165}" width="58" height="18" rx="9" fill="{GREEN}" fill-opacity=".14"/>' + T(117, y + 178, "一致 100%", 11, 700, GREEN, "middle"))
    c = over(c, svg_layer(body))
    # island notification
    c = over(c, svg_layer(r(86, 11, 218, 36, 18, "#000") + f'<circle cx="108" cy="29" r="9" fill="{GREEN}"/>' + lu("check", 108, 29, 11, "#000", 3) +
                          T(124, 34, "语音模型测试通过 · 0.82s", 13, 600, "#fff")))
    return tabbar(c, 3)

def f4_test_llm():
    c = base(); c = header(c); c = account(c)
    c = over(c, svg_layer(T(36, 268, "模型", 13, 500, SEC)))
    c, _ = model_card(c, "run", target="box")
    y = 488
    c = gcard(c, 16, y, 358, 214, 24)
    body = (lu("box", 40, y + 24, 15, BLUE, 2.2) + T(54, y + 29, "LLM 测试 · DeepSeek-V3.2", 13, 700, INK) +
            T(360, y + 29, "生成中…", 12.5, 600, BLUE, "end") +
            T(30, y + 58, "提示词", 11.5, 600, SEC) +
            T(30, y + 78, "用学长的口吻，一句话讲清楚什么是梯度。", 13.5, 400, SEC) +
            T(30, y + 106, "回复", 11.5, 600, SEC) +
            T(30, y + 127, "梯度就是一个“指路箭头”：站在山坡上，它永远", 14, 400, INK) +
            T(30, y + 148, "指向最陡的上坡方向，长度就是这个方向有多陡。", 14, 400, INK) +
            f'<rect x="355" y="{y+136}" width="2" height="16" fill="{BLUE}"/>')
    stats = [("首字", "0.61 s"), ("速度", "46 tok/s"), ("用量", "0.0004 元")]
    for i, (k, v) in enumerate(stats):
        x = 30 + i * 112
        body += r(x, y + 166, 104, 36, 12, "#787880", 0.08) + T(x + 12, y + 189, k, 11.5, 500, SEC) + T(x + 96, y + 189, v, 12.5, 700, INK, "end")
    c = over(c, svg_layer(body))
    return tabbar(c, 3)

PROV = [("siliconcloud-color", "SiliconFlow"), ("openai", "OpenAI"), ("alibabacloud-color", "阿里云百炼"), ("volcengine-color", "火山引擎")]
def picker(kind):
    c = base(); c = header(c); c = account(c)
    c, _ = model_card(c, pressed=0 if kind == "asr" else 1)
    c = tabbar(c, 3)
    c = dim(c, .28)
    c = glass(c, 8, 150, 374, 700, 40, "large", alpha=.82, blur=18)
    body = r(175, 160, 40, 5, 2.5, "#000", .18)
    title = "语音识别模型" if kind == "asr" else "大语言模型"
    body += lu("mic" if kind == "asr" else "box", 38, 196, 22, BLUE, 2.1) + TS(58, 205, title, 22)
    c = over(c, svg_layer(body))
    c = glass(c, 330, 178, 36, 36, 18, "regular", alpha=.7)
    c = over(c, svg_layer(lu("x", 348, 196, 16, INK, 2.4)))
    # providers
    y = 230
    for i, (lg, n) in enumerate(PROV):
        x = 24 + i * 86
        sel = i == 0
        c = glass(c, x, y, 78, 78, 22, "regular", alpha=.92 if sel else .55, tint=(10, 114, 232) if sel else None, tint_a=.06 if sel else 0, lens=6)
    body = ""
    for i, (lg, n) in enumerate(PROV):
        x = 24 + i * 86
        body += logo(lg, x + 25, y + 13, 28) + T(x + 39, y + 64, n, 11, 700 if i == 0 else 500, BLUE if i == 0 else INK, "middle")
        if i == 0: body += f'<rect x="{x}" y="{y}" width="78" height="78" rx="22" fill="none" stroke="{BLUE}" stroke-width="1.6"/>'
    body += T(36, y + 108, "接口", 12.5, 600, SEC) + T(356, y + 108, "+ 自定义", 12.5, 600, BLUE, "end")
    c = over(c, svg_layer(body))
    # key + base url
    y2 = y + 118
    c = glass(c, 24, y2, 342, 100, 22, "regular", alpha=.62, shadow=False)
    body = (lu("key", 46, y2 + 26, 17, SEC) + T(64, y2 + 31, "sk-••••••3f2a", 14.5, 500, INK) +
            lu("eye", 316, y2 + 26, 17, SEC) + f'<circle cx="345" cy="{y2+26}" r="8" fill="{GREEN}"/>' + lu("check", 345, y2 + 26, 10, "#fff", 3) +
            f'<line x1="40" y1="{y2+50}" x2="350" y2="{y2+50}" stroke="#000" stroke-opacity=".07"/>' +
            lu("link", 46, y2 + 75, 17, SEC) + T(64, y2 + 80, "https://api.siliconflow.cn/v1", 14, 500, SEC))
    c = over(c, svg_layer(body))
    # models
    y3 = y2 + 128
    if kind == "asr":
        models = [("siliconcloud-color", "FunAudioLLM/SenseVoiceSmall", ["中英粤日韩", "快", "免费"], True),
                  ("siliconcloud-color", "TeleAI/TeleSpeechASR", ["方言", "免费"], False),
                  ("siliconcloud-color", "FunAudioLLM/CosyVoice2-0.5B", ["仅TTS"], None)]
        cnt = "从上游获取 · 2 个可用"
    else:
        models = [("deepseek-color", "deepseek-ai/DeepSeek-V3.2", ["对话", "128K"], True),
                  ("deepseek-color", "deepseek-ai/DeepSeek-R1", ["推理", "慢"], False),
                  ("qwen-color", "Qwen/Qwen3-235B-A22B", ["对话", "推理"], False),
                  ("moonshot", "moonshotai/Kimi-K2-Instruct", ["长文"], False),
]
        cnt = "从上游获取 · 86 个"
    body = T(36, y3, cnt, 12.5, 600, SEC) + lu("refresh", 350, y3 - 4, 15, BLUE, 2.2)
    c = over(c, svg_layer(body))
    c = glass(c, 24, y3 + 12, 342, 40, 20, "regular", alpha=.5, shadow=False)
    c = over(c, svg_layer(lu("search", 46, y3 + 32, 15, SEC) + T(62, y3 + 37, "搜索模型", 14, 400, SEC, op=.7)))
    yy = y3 + 64
    rowh = 58
    c = glass(c, 24, yy, 342, rowh * len(models), 22, "regular", alpha=.62, shadow=False)
    body = ""
    for i, (lg, n, tags, sel) in enumerate(models):
        ry = yy + i * rowh
        op = .45 if sel is None else 1
        body += f'<g opacity="{op}">' + logo(lg, 40, ry + 15, 26)
        body += T(78, ry + 26, n, 14, 600 if sel else 500, BLUE if sel else INK)
        tx = 78
        for tg in tags:
            w = 12 * len(tg) * (0.62 if tg.isascii() else 1) + 14
            body += r(tx, ry + 34, w, 17, 8.5, "#787880", 0.09) + T(tx + w / 2, ry + 46.5, tg, 10.5, 600, SEC, "middle")
            tx += w + 6
        body += '</g>'
        if sel: body += lu("check", 344, ry + 29, 18, BLUE, 2.6)
        if i < len(models) - 1: body += f'<line x1="78" y1="{ry+rowh}" x2="366" y2="{ry+rowh}" stroke="#000" stroke-opacity=".07"/>'
    c = over(c, svg_layer(body))
    yb = min(yy + rowh * len(models) + 18, 760)
    c = glass(c, 24, yb, 342, 52, 26, "regular", tint=(10, 114, 232), tint_a=.9, lens=10)
    c = over(c, svg_layer(lu("zap", 158, yb + 26, 17, "#fff", 2, fill="#fff") + T(172, yb + 31.5, "保存并测试", 15.5, 700, "#fff") + home_ind()))
    return c

FR = [("v4-01-登录", f0_login), ("v4-02-我的", f1_me), ("v4-03-长按闪电切换", f2_longzap), ("v4-04-测试语音模型", f3_test_asr),
      ("v4-05-测试LLM", f4_test_llm), ("v4-06-语音模型选择器", lambda: picker("asr")), ("v4-07-LLM模型选择器", lambda: picker("llm"))]
if __name__ == "__main__":
    only = sys.argv[1:]
    for n, f in FR:
        if only and not any(o in n for o in only): continue
        f().convert("RGB").save(f"v4/{n}.png"); print(n, flush=True)
