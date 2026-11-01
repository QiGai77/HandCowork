#!/usr/bin/env python3
"""笔记模块 4 屏矢量复刻（SVG），全部取值来自 notes.tokens.json。
输出：笔记-设计稿.svg（4 屏横排）+ 每屏单独 svg + 预览 png。"""
import json, os, math
HERE = os.path.dirname(os.path.abspath(__file__))
TK = json.load(open(os.path.join(HERE, 'notes.tokens.json'), encoding='utf-8'))
def g(path):
    d = TK
    for p in path.split('.'): d = d[p]
    return d['$value']
def C(path):
    v = g('color.' + path)
    if len(v) == 9:
        return f'{v[:7]}" fill-opacity="{int(v[7:],16)/255:.2f}'
    return v
def hexc(path):
    v = g('color.' + path); return v[:7]
def alpha(path):
    v = g('color.' + path); return int(v[7:], 16) / 255 if len(v) == 9 else 1
D = lambda p: g('dimension.' + p)

W, H = 393, 852
SANS = "Noto Sans SC, PingFang SC, sans-serif"
SERIF = "Noto Serif SC, Songti SC, serif"
def ty(name):
    t = g('typography.' + name)
    fam = SERIF if t['fontFamily'] == 'serif' else SANS
    return f'font-family="{fam}" font-weight="{t["fontWeight"]}" font-size="{t["fontSize"]}"'
def text(x, y, s, style, fill, anchor='start', extra=''):
    import re as _re
    base = ty(style)
    for a in ('font-weight', 'font-size'):
        if a + '=' in extra: base = _re.sub(a + r'="[^"]*"', '', base)
    return f'<text x="{x}" y="{y}" {base} fill="{fill}" text-anchor="{anchor}" {extra}>{s}</text>'

# ---- Lucide 图标（24 网格） ----
LU = {
 'book': '<path d="M12 7v14"/><path d="M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z"/>',
 'mic': '<path d="M12 19v3"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><rect x="9" y="2" width="6" height="13" rx="3"/>',
 'note': '<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10 9H8"/><path d="M16 13H8"/><path d="M16 17H8"/>',
 'user': '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
 'search': '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
 'back': '<path d="m15 18-6-6 6-6"/>',
 'toc': '<path d="M3 5h.01"/><path d="M3 12h.01"/><path d="M3 19h.01"/><path d="M8 5h13"/><path d="M8 12h13"/><path d="M8 19h13"/>',
 'cards': '<path d="M13.4 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7.4"/><path d="M2 6h4"/><path d="M2 10h4"/><path d="M2 14h4"/><path d="M2 18h4"/><path d="M21.378 5.626a1 1 0 1 0-3.004-3.004l-5.01 5.012a2 2 0 0 0-.506.854l-.837 2.87a.5.5 0 0 0 .62.62l2.87-.837a2 2 0 0 0 .854-.506z"/>',
 'map': '<circle cx="12" cy="12" r="3"/><circle cx="19" cy="5" r="2"/><circle cx="5" cy="5" r="2"/><circle cx="19" cy="19" r="2"/><circle cx="5" cy="19" r="2"/><path d="M10.4 10.4 6.4 6.4M13.6 10.4l4-4M10.4 13.6l-4 4M13.6 13.6l4 4"/>',
 'layers': '<rect x="3" y="7" width="13" height="15" rx="2"/><path d="M7 3h11a2 2 0 0 1 2 2v13"/>',
 'monitor': '<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/><path d="M6 8h.01M6 11h4"/>',
 'wave': '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M5 12c1.5-5 3.5-5 5 0s3.5 5 5 0 2.5-4 4-2"/>',
 'chart': '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M6 18c2-1 3-9 5-9s2 6 4 6 3-6 4-8"/><path d="M6 6v12h12"/>',
 'upload': '<path d="M12 3v12"/><path d="m17 8-5-5-5 5"/><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>',
}
def icon(name, x, y, s=24, color='#111', sw=2):
    k = s / 24
    return (f'<g transform="translate({x},{y}) scale({k})" fill="none" stroke="{color}" stroke-width="{sw}" '
            f'stroke-linecap="round" stroke-linejoin="round">{LU[name]}</g>')
def sparkle(cx, cy, r, color):
    p = f'M{cx},{cy-r} Q{cx+r*0.18},{cy-r*0.18} {cx+r},{cy} Q{cx+r*0.18},{cy+r*0.18} {cx},{cy+r} Q{cx-r*0.18},{cy+r*0.18} {cx-r},{cy} Q{cx-r*0.18},{cy-r*0.18} {cx},{cy-r}Z'
    return f'<path d="{p}" fill="{color}"/>'

PAL = {k: {kk: hexc(f'course.{k}.{kk}') for kk in ('spine', 'cover', 'chip', 'deep')} for k in ('green', 'blue', 'rust')}
GOLD, GOLDS, GOLDI = hexc('gold.line'), hexc('gold.shade'), hexc('gold.icon')
INK, SEC, TER = hexc('ink.primary'), hexc('ink.secondary'), hexc('ink.tertiary')

def defs(uid):
    return f'''<defs>
<linearGradient id="bg{uid}" x1="0" y1="0" x2="0.25" y2="1">
 <stop offset="0" stop-color="{hexc('bg.top')}"/><stop offset=".45" stop-color="{hexc('bg.mid')}"/><stop offset="1" stop-color="{hexc('bg.bottom')}"/></linearGradient>
<radialGradient id="glow{uid}" cx=".1" cy=".05" r=".6"><stop offset="0" stop-color="#FFF6D6" stop-opacity=".9"/><stop offset="1" stop-color="#FFF6D6" stop-opacity="0"/></radialGradient>
<pattern id="dots{uid}" width="16" height="16" patternUnits="userSpaceOnUse"><circle cx="8" cy="8" r=".9" fill="#000" fill-opacity=".07"/></pattern>
<linearGradient id="dotfade{uid}" x1="0" y1="0" x2="0" y2="1"><stop offset=".35" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#fff" stop-opacity="1"/></linearGradient>
<mask id="dm{uid}"><rect width="{W}" height="{H}" fill="url(#dotfade{uid})"/></mask>
<linearGradient id="gen{uid}" x1="0" x2="1"><stop offset="0" stop-color="{hexc('progress.g0')}"/><stop offset=".5" stop-color="{hexc('progress.g1')}"/><stop offset="1" stop-color="{hexc('progress.g2')}"/></linearGradient>
<linearGradient id="org{uid}" x1="0" x2="1"><stop offset="0" stop-color="{hexc('organize.bar0')}"/><stop offset="1" stop-color="{hexc('organize.bar1')}"/></linearGradient>
<linearGradient id="navrim{uid}" x1="0" x2="1"><stop offset="0" stop-color="#F7C8E8"/><stop offset=".35" stop-color="#C9D8FF"/><stop offset=".7" stop-color="#C8F1E4"/><stop offset="1" stop-color="#F3D2F2"/></linearGradient>
<linearGradient id="cloth{uid}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".10"/><stop offset=".5" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".18"/></linearGradient>
<linearGradient id="spineL{uid}" x1="0" x2="1"><stop offset="0" stop-color="#000" stop-opacity=".22"/><stop offset=".25" stop-color="#fff" stop-opacity=".10"/><stop offset=".6" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".25"/></linearGradient>
<linearGradient id="paperL{uid}" x1="0" x2="1"><stop offset="0" stop-color="#E8DCBC"/><stop offset=".06" stop-color="{hexc('reader.paper')}"/><stop offset=".94" stop-color="{hexc('reader.paper')}"/><stop offset="1" stop-color="#E6D9B8"/></linearGradient>
<linearGradient id="curl{uid}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#E9DCB9"/><stop offset=".45" stop-color="#FBF4E2"/><stop offset=".8" stop-color="#E2D3AE"/><stop offset="1" stop-color="#C9B88F"/></linearGradient>
<linearGradient id="board{uid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".95"/><stop offset="1" stop-color="#E7E2DA" stop-opacity=".9"/></linearGradient>
<linearGradient id="scrub{uid}" x1="0" x2="1"><stop offset="0" stop-color="#F6E3EC"/><stop offset=".5" stop-color="#E2F2F0"/><stop offset="1" stop-color="#ECE6F7"/></linearGradient>
<linearGradient id="navfade{uid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{hexc('bg.bottom')}" stop-opacity="0"/><stop offset=".45" stop-color="{hexc('bg.bottom')}" stop-opacity=".92"/><stop offset="1" stop-color="{hexc('bg.bottom')}" stop-opacity="1"/></linearGradient>
<filter id="sh{uid}" x="-20%" y="-20%" width="140%" height="160%"><feDropShadow dx="0" dy="4" stdDeviation="7" flood-color="#3C3250" flood-opacity=".09"/></filter>
<filter id="shB{uid}" x="-30%" y="-10%" width="160%" height="130%"><feDropShadow dx="1" dy="3" stdDeviation="3" flood-color="#000" flood-opacity=".22"/></filter>
<filter id="shC{uid}" x="-30%" y="-20%" width="160%" height="150%"><feDropShadow dx="0" dy="6" stdDeviation="7" flood-color="#000" flood-opacity=".22"/></filter>
<filter id="shL{uid}" x="-20%" y="-20%" width="140%" height="160%"><feDropShadow dx="0" dy="16" stdDeviation="18" flood-color="#2A2040" flood-opacity=".16"/></filter>
<filter id="blur{uid}"><feGaussianBlur stdDeviation="9"/></filter>
<clipPath id="scr{uid}"><rect width="{W}" height="{H}" rx="0"/></clipPath>
</defs>'''

def background(u):
    return (f'<rect width="{W}" height="{H}" fill="url(#bg{u})"/><rect width="{W}" height="{H}" fill="url(#glow{u})"/>'
            f'<rect width="{W}" height="{H}" fill="url(#dots{u})" mask="url(#dm{u})"/>')

def header(u, active):
    s = text(24, 102, '笔记', 'pageTitle', INK)
    s += text(24, 134, '3 门课 · 15 本笔记', 'pageSub', SEC)
    x0, y0, w, h = 219, 77, D('segment.w'), D('segment.h')
    s += f'<rect x="{x0}" y="{y0}" width="{w}" height="{h}" rx="20" fill="#000" fill-opacity=".055"/>'
    tx = x0 + 3 if active == 0 else x0 + w - 3 - D('segment.thumbW')
    s += f'<rect x="{tx}" y="{y0+3}" width="{D("segment.thumbW")}" height="{h-6}" rx="17" fill="{hexc("segment.thumb")}" filter="url(#sh{u})"/>'
    for i, lab in enumerate(['书架', '列表']):
        cx = x0 + 3 + D('segment.thumbW') / 2 if i == 0 else x0 + w - 3 - D('segment.thumbW') / 2
        s += text(cx, y0 + 26, lab, 'segment', hexc('segment.on') if i == active else hexc('segment.off'), 'middle')
    return s

def navbar(u, active=2):
    y = H - D('nav.bottom') - D('nav.h'); x = D('nav.insetH'); w = W - x * 2; h = D('nav.h')
    s = f'<rect x="0" y="{y-40}" width="{W}" height="{H-y+40}" fill="url(#navfade{u})"/>'
    s += f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="32" fill="{hexc("glass.nav")}" fill-opacity=".97" stroke="url(#navrim{u})" stroke-width="2" filter="url(#sh{u})"/>'
    items = [('book', '课程'), ('mic', '上课'), ('note', '笔记'), ('user', '我的')]
    step = w / 4
    for i, (ic, lab) in enumerate(items):
        cx = x + step * i + step / 2
        col = hexc('navTab.active') if i == active else hexc('navTab.inactive')
        if i == active:
            s += f'<rect x="{cx-36}" y="{y+6}" width="72" height="52" rx="26" fill="{hexc("glass.navActive")}" fill-opacity=".8"/>'
        s += icon(ic, cx - 12, y + 12, 24, col, 1.9)
        s += text(cx, y + 52, lab, 'nav', col, 'middle')
    return s

def book_cover(x, y, w, h, pal, ic, u, ribbon=True, r=3, icon_size=None):
    s = f'<g filter="url(#shC{u})"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{pal["cover"]}"/>'
    s += f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="url(#cloth{u})"/>'
    s += f'<rect x="{x}" y="{y}" width="{max(3,w*0.05)}" height="{h}" rx="{r}" fill="#000" fill-opacity=".18"/></g>'
    bb = h - max(8, h * 0.1)
    s += f'<rect x="{x}" y="{y+bb}" width="{w}" height="1.4" fill="{GOLD}"/><rect x="{x}" y="{y+bb+3}" width="{w}" height="1" fill="{GOLD}" opacity=".8"/>'
    # 纯背景封面：不再居中绘制图标/汉字，保留纯净装帧肌理与金色纹理
    if ribbon:
        rx = x + w * 0.18
        s += f'<path d="M{rx},{y+h} h9 v14 l-4.5,-4 l-4.5,4z" fill="{pal["chip"]}" stroke="#000" stroke-opacity=".15" stroke-width=".5"/>'
    return s

# ---------------- 1 列表 ----------------
NOTES = [
 ('1.1 连续时间信号概念', 'Oct 25', '详细总解：连续时间信号概念概念概念，拉发', '和信号涑均概念，是里对统计数的信号有度…', 'blue', 'wave', None),
 ('3.1 离散时间信号与系统', 'Oct 28', '详细总解：信号与拉能方程念信息，概生留时', '间信号，离散时间信号与系统的重度和的信…', 'blue', 'wave', None),
 ('2.1 Z变换及其应用', 'Oct 31', '详细总解：  Z变换及其应用，Z变换计有的流', '理，计算随及及应用应用的效能和过库，流…', 'green', 'monitor', 'gen'),
 ('4.1 中断系统结构', 'Nov 2', '详细总解：中断系统结构构理、4-5考点的流', '理，让中断系统，中断系统结构，（1Ⅱ系统…', 'green', 'monitor', 'tags'),
 ('5.2 滤波器设计方法', 'Nov 5', '详细总解：滤波器设计方法、系统分析、拉普', '换滤波器设计方法，非常有需要投析，普度…', 'blue', 'wave', None),
 ('1.1 极限与连续性', 'Nov 7', '详细总解：极限与连续性性、号积分微与定级', '性，空间简解分，有效的态. 要极限与连续…', 'rust', 'chart', None),
]
def screen_list(u='L'):
    s = background(u) + header(u, 1)
    # chips
    y = D('chip.rowTop'); x = 24
    s += f'<rect x="{x}" y="{y}" width="54" height="28" rx="14" fill="{hexc("chip.allBg")}" filter="url(#sh{u})"/>'
    s += text(x + 27, y + 19, '全部', 'chip', INK, 'middle')
    x += 54 + 8
    for name, key, n, w in [('微机原理与接口技术', 'green', 5, 154), ('信号与系统', 'blue', 6, 100), ('高等数学', 'rust', 5, 96)]:
        s += f'<rect x="{x}" y="{y}" width="{w}" height="28" rx="14" fill="{PAL[key]["chip"]}"/>'
        s += f'<circle cx="{x+14}" cy="{y+14}" r="3" fill="#fff" fill-opacity=".75"/>'
        s += text(x + 23, y + 19, name, 'chip', '#fff')
        if key != 'rust':
            s += f'<circle cx="{x+w-10}" cy="{y-1}" r="7" fill="{hexc("chip.badgeBg")}" stroke="#fff" stroke-width="1"/>'
            s += text(x + w - 10, y + 2.5, str(n), 'chipBadge', hexc('chip.badgeText'), 'middle')
        x += w + 8
    # cards
    y = D('screen.listTop')
    for title, date, l1, l2, key, ic, extra in NOTES:
        h = 87 if extra is None else 104
        s += f'<rect x="20" y="{y}" width="353" height="{h}" rx="16" fill="#fff" fill-opacity=".62" stroke="#fff" stroke-opacity=".7" filter="url(#sh{u})"/>'
        s += book_cover(32, y + 11, 56, 66, PAL[key], ic, u, ribbon=False, r=2, icon_size=20)
        s += text(102, y + 30, title, 'cardTitle', INK)
        s += text(361, y + 29, date, 'cardDate', INK, 'end')
        s += text(102, y + 51, l1[:19], 'cardBody', '#2A2A2A')
        s += text(102, y + 70, l2[:18] + ('…' if not l2[:18].endswith('…') else ''), 'cardBody', '#2A2A2A')
        if extra == 'gen':
            s += text(108, y + 94, '生成中 62%', 'progressLabel', '#333')
            s += f'<rect x="180" y="{y+88}" width="181" height="4" rx="2" fill="{hexc("progress.track")}"/>'
            s += f'<rect x="180" y="{y+88}" width="104" height="4" rx="2" fill="url(#gen{u})"/>'
            s += sparkle(284, y + 90, 7, '#FFF4E0') + sparkle(284, y + 90, 3.5, '#FFFFFF')
        if extra == 'tags':
            s += f'<rect x="102" y="{y+77}" width="92" height="22" rx="11" fill="#000" fill-opacity=".05"/>'
            s += icon('layers', 109, y + 82, 12, '#444', 2) + text(126, y + 93, '12 张复习卡', 'tag', INK)
            s += f'<rect x="200" y="{y+77}" width="62" height="22" rx="11" fill="#000" fill-opacity=".05"/>'
            s += text(208, y + 93, '★ 5 考点', 'tag', INK)
        y += h + 8
    s += navbar(u)
    return s

# ---------------- 2 书架 ----------------
SHELVES = [
 ('微机原理与接口技术', 'green', 5, 'monitor', ['微机原理', 'Z变换', '离散时间信号', '中断系统', '总线技术'], [124, 106, 124, 116, 104]),
 ('信号与系统', 'blue', 6, 'wave', ['连续时间信号', '系统分析', '拉普拉斯变换', '傅里叶级数', '滤波器设计'], [124, 108, 116, 120, 106]),
 ('高等数学', 'rust', 5, 'chart', ['极限与连续', '微积分', '导数与微分', '定积分', '空间解析几何'], [124, 108, 124, 114, 106]),
]
def spine(x, base_y, w, h, pal, title, u, lean=0):
    y = base_y - h
    s = f'<g transform="rotate({lean} {x+w} {base_y})" filter="url(#shB{u})">'
    s += f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="3" fill="{pal["spine"]}"/>'
    s += f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="3" fill="url(#spineL{u})"/>'
    s += f'<rect x="{x}" y="{y+6}" width="{w}" height="1.5" fill="{GOLD}"/><rect x="{x}" y="{y+11}" width="{w}" height="1.2" fill="{GOLD}" opacity=".85"/>'
    s += f'<rect x="{x}" y="{base_y-7}" width="{w}" height="1" fill="{GOLD}" opacity=".7"/>'
    cy = y + 30
    n = int((h - 30 - 22) // 14.5) + 1
    for ch in title[:n]:
        s += text(x + w / 2, cy, ch, 'spine', '#fff', 'middle', 'fill-opacity=".92"'); cy += 14.5
    s += text(x + w / 2, base_y - 16, '2023', 'spineDate', '#fff', 'middle', 'fill-opacity=".5"')
    s += text(x + w / 2, base_y - 10, '09·10', 'spineDate', '#fff', 'middle', 'fill-opacity=".5"')
    return s + '</g>'

def shelf_section(top, name, key, n, ic, titles, hs, u):
    pal = PAL[key]
    s = f'<circle cx="34" cy="{top+13}" r="3.5" fill="{pal["chip"]}"/>'
    s += text(46, top + 20, name, 'shelfSection', pal['deep'])
    s += text(368, top + 19, f'{n} 本', 'shelfCount', hexc('ink.count') if key != 'rust' else pal['deep'], 'end')
    base = top + 26 + 18 + 124
    x = 50
    for i, (t, h) in enumerate(zip(titles, hs)):
        lean = -12 if i == len(titles) - 1 else 0
        if lean: x += 4
        s += spine(x, base, 23, h, pal, t, u, lean)
        x += 24
    s += book_cover(268, base - 116, 90, 116, pal, ic, u, ribbon=True, r=3, icon_size=34)
    s += f'<rect x="36" y="{base-1}" width="321" height="12" rx="6" fill="url(#board{u})" stroke="#fff" stroke-width="1" filter="url(#sh{u})"/>'
    s += f'<rect x="40" y="{base+9}" width="313" height="2" rx="1" fill="{hexc("glass.boardEdge")}" fill-opacity=".4"/>'
    return s

def screen_shelf(u='S', overlay=False):
    s = background(u) + header(u, 0)
    for i, sec in enumerate(SHELVES):
        s += shelf_section(D('shelf.firstTop') + D('shelf.pitch') * i, *sec, u)
    s += navbar(u)
    return s

# ---------------- 3 翻书阅读 ----------------
def slide_table(x, y, w, h):
    s = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#fff"/>'
    s += f'<rect x="{x+6}" y="{y+6}" width="{w-12}" height="{h-12}" fill="#fff" stroke="#E5E2DA" stroke-width=".6"/>'
    s += f'<rect x="{x+6}" y="{y+6}" width="18" height="{h-12}" fill="#111"/><rect x="{x+w-24}" y="{y+6}" width="18" height="{h-12}" fill="#111"/>'
    s += f'<text x="{x+34}" y="{y+30}" font-family="{SANS}" font-weight="700" font-size="11" fill="#111">中断向量表</text>'
    s += f'<line x1="{x+30}" y1="{y+38}" x2="{x+w-30}" y2="{y+38}" stroke="#555" stroke-width=".7"/><line x1="{x+w-80}" y1="{y+38}" x2="{x+w-30}" y2="{y+38}" stroke="#4FA684" stroke-width="2"/>'
    s += f'<text x="{x+w/2}" y="{y+50}" font-family="{SANS}" font-size="6" fill="#222" text-anchor="middle">中断向量表</text>'
    tx, tw, ty0, rh = x + 46, w - 92, y + 54, 9.6
    cols = [0, 0.16, 0.52, 0.68, 1]
    rows = [('序号', '地址', '编号', '类型')] + [(str(i), '0x00%05X' % (i * 0x10 + 0x1000 if i else 0), str(9 + i), '中断向量') for i in range(11)]
    for r, row in enumerate(rows):
        yy = ty0 + r * rh
        if r == 0: s += f'<rect x="{tx}" y="{yy}" width="{tw}" height="{rh}" fill="#ECECEC"/>'
        for c, cell in enumerate(row):
            cx = tx + tw * (cols[c] + cols[c + 1]) / 2
            s += f'<text x="{cx}" y="{yy+7}" font-family="{SANS}" font-size="5.2" fill="#111" text-anchor="middle">{cell}</text>'
        s += f'<line x1="{tx}" y1="{yy}" x2="{tx+tw}" y2="{yy}" stroke="#555" stroke-width=".4"/>'
    s += f'<rect x="{tx}" y="{ty0}" width="{tw}" height="{rh*len(rows)}" fill="none" stroke="#333" stroke-width=".6"/>'
    for c in cols[1:-1]:
        s += f'<line x1="{tx+tw*c}" y1="{ty0}" x2="{tx+tw*c}" y2="{ty0+rh*len(rows)}" stroke="#555" stroke-width=".4"/>'
    return s

def screen_reader(u='R'):
    s = background(u)
    green = PAL['green']
    s += f'<circle cx="45" cy="77" r="19" fill="#fff" fill-opacity=".8" stroke="#fff" filter="url(#sh{u})"/>' + icon('back', 33, 65, 24, '#333', 2)
    s += text(200, 84, '微机原理 · 中断系统', 'readerNav', INK, 'middle')
    top, bot = D('reader.bookTop'), D('reader.bookBottom')
    # 封皮
    s += f'<rect x="0" y="{top}" width="384" height="{bot-top}" rx="6" fill="{green["cover"]}" filter="url(#shC{u})"/>'
    s += f'<path d="M62,{top-14} h30 v22 l-15,-7 l-15,7z" fill="{green["chip"]}"/>'
    # 页面层
    s += f'<rect x="0" y="{top+5}" width="378" height="{bot-top-10}" fill="#E4D7B6"/>'
    s += f'<rect x="0" y="{top+3}" width="374" height="{bot-top-8}" fill="url(#paperL{u})"/>'
    s += f'<rect x="0" y="{top+3}" width="26" height="{bot-top-8}" fill="#000" fill-opacity=".05"/><line x1="28" y1="{top+3}" x2="28" y2="{bot-5}" stroke="#000" stroke-opacity=".08"/>'
    # 章节
    s += f'<line x1="100" y1="{top+56}" x2="300" y2="{top+56}" stroke="{hexc("reader.ornament")}" stroke-width=".8"/><path d="M200,{top+52} l4,4 -4,4 -4,-4z" fill="none" stroke="{hexc("reader.ornament")}" stroke-width=".8"/>'
    s += text(200, top + 82, '第二节 · 8086 中断系统', 'chapter', hexc('reader.heading'), 'middle')
    s += f'<line x1="100" y1="{top+94}" x2="300" y2="{top+94}" stroke="{hexc("reader.ornament")}" stroke-width=".8"/><path d="M200,{top+90} l4,4 -4,4 -4,-4z" fill="none" stroke="{hexc("reader.ornament")}" stroke-width=".8"/>'
    sy = top + 114
    s += f'<g filter="url(#shB{u})">' + slide_table(76, sy, 252, 178) + '</g>'
    s += text(200, sy + 196, '课件第 14 页', 'readerCaption', hexc('reader.caption'), 'middle')
    by = sy + 226
    lines = [('　　中断向量表，8086引流到', '中断向量', '，而且具有破'),]
    body = ['　　中断向量表，8086引流到中断向量表获得', None, '是中断的向量过程，中断向量的地址过程。它', '的中断量发是一个中断行的中断量地址。']
    s += text(68, by, body[0], 'readerBody', hexc('reader.text'))
    s += f'<rect x="146" y="{by+9}" width="60" height="20" rx="3" fill="{hexc("reader.hlBg")}"/>'
    s += text(68, by + 24, '接权式类型的<tspan fill="' + hexc('reader.hlText') + '">中断向量</tspan>，而且同时对应报告，', 'readerBody', hexc('reader.text'))
    s += text(68, by + 48, body[2], 'readerBody', hexc('reader.text'))
    s += text(68, by + 72, body[3], 'readerBody', hexc('reader.text'))
    ey = by + 98
    s += f'<rect x="68" y="{ey-14}" width="46" height="20" rx="6" fill="{hexc("reader.examBg")}"/>' + text(73, ey + 1, '★考点', 'tag', hexc('reader.examText'), 'start', 'font-weight="700" font-size="13"')
    s += text(68, ey + 26, '　　8086 是合理统中断向量表的中断向量结', 'readerBody', hexc('reader.text'))
    s += text(68, ey + 50, '构，实更存优剪选题的内容，中断的', 'readerBody', hexc('reader.text'))
    # 移除常驻卷页遮挡，提供纯净沉浸式正文阅读体验
    # 进度气泡
    py = bot
    s += f'<rect x="152" y="{py}" width="98" height="72" rx="12" fill="#fff" fill-opacity=".75" stroke="#fff" filter="url(#sh{u})"/>'
    s += f'<rect x="159" y="{py+6}" width="84" height="46" rx="3" fill="{hexc("reader.paper")}" stroke="#D8CCAA" stroke-width=".6"/><line x1="201" y1="{py+6}" x2="201" y2="{py+52}" stroke="#C9B98F" stroke-width=".6"/>'
    s += f'<rect x="164" y="{py+16}" width="22" height="14" fill="#fff" stroke="#999" stroke-width=".4"/>'
    for k in range(6):
        s += f'<line x1="206" y1="{py+14+k*6}" x2="238" y2="{py+14+k*6}" stroke="#A89B7A" stroke-width=".8"/>'
        s += f'<line x1="164" y1="{py+34+k*4}" x2="196" y2="{py+36+k*4}" stroke="#A89B7A" stroke-width=".6"/>' if k < 4 else ''
    s += text(201, py + 66, '第 3 节 · 23/36', 'scrubLabel', '#333', 'middle')
    s += f'<path d="M195,{py+72} l6,6 6,-6z" fill="#fff" fill-opacity=".85"/>'
    ty0 = 754
    s += f'<rect x="26" y="{ty0}" width="350" height="26" rx="13" fill="url(#scrub{u})" stroke="#fff" stroke-width="1.5" filter="url(#sh{u})"/>'
    for i in range(37):
        tx = 40 + i * 9; mh = 12 if i % 5 == 0 else 8
        s += f'<line x1="{tx}" y1="{ty0+13-mh/2}" x2="{tx}" y2="{ty0+13+mh/2}" stroke="{hexc("reader.tick")}" stroke-width="1"/>'
    s += f'<circle cx="201" cy="{ty0+13}" r="8" fill="{hexc("reader.knob")}" stroke="#fff" stroke-width="2" filter="url(#sh{u})"/>'
    yb = H - 32
    for cx, ic, lab in [(52, 'toc', '目录'), (162, 'cards', '复习卡'), (350, 'map', '导图')]:
        s += icon(ic, cx - 11, yb - 26, 22, '#222', 1.8) + text(cx, yb + 10, lab, 'toolbar', '#222', 'middle')
    return s

# ---------------- 4 整理笔记 + 灵动岛 ----------------
def mindmap(x, y, w, h, crop=False):
    s = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="9" fill="#fff"/>'
    cx, cy = x + w * 0.48, y + h * 0.5
    s += f'<rect x="{cx-10}" y="{cy-3}" width="20" height="6" rx="2" fill="#7FCBA0"/>'
    for side in (-1, 1):
        for k in range(4):
            ty = y + h * (0.18 + 0.21 * k); ex = cx + side * w * 0.22
            col = ['#7FA8E0', '#E3A86A', '#7FCBA0', '#D98AA6'][k] if side > 0 else ['#9DB5D8', '#A0C9B0', '#C9B6E0', '#E0C08A'][k]
            s += f'<path d="M{cx+side*10},{cy} C{cx+side*16},{cy} {ex-side*8},{ty} {ex},{ty}" fill="none" stroke="{col}" stroke-width="1"/>'
            s += f'<rect x="{ex if side>0 else ex-14}" y="{ty-2}" width="14" height="4" rx="1.5" fill="{col}" fill-opacity=".5"/>'
            for j in range(2):
                lx = ex + side * 16
                s += f'<line x1="{lx}" y1="{ty-3+j*5}" x2="{lx+side*9}" y2="{ty-3+j*5}" stroke="{col}" stroke-width=".8"/>'
    return s

def screen_organize(u='O'):
    s = screen_shelf(u)
    # 遮罩模糊感
    s += f'<rect width="{W}" height="{H}" fill="#fff" fill-opacity=".08"/>'
    # 收起灵动岛（顶部）
    s += f'<rect x="{W/2-59}" y="14" width="118" height="36" rx="18" fill="#000"/>'
    # 展开灵动岛
    ix, iy, iw, ih = 10, 126, 372, 178
    s += f'<rect x="{ix}" y="{iy}" width="{iw}" height="{ih}" rx="30" fill="#000" filter="url(#shL{u})"/>'
    s += f'<rect x="{ix+1}" y="{iy+1}" width="{iw-2}" height="{ih-2}" rx="29" fill="none" stroke="#fff" stroke-opacity=".12"/>'
    s += sparkle(ix + 32, iy + 30, 9, '#C9D6FF') + sparkle(ix + 42, iy + 22, 4, '#C9D6FF') + sparkle(ix + 25, iy + 39, 3, '#C9D6FF')
    s += text(ix + 54, iy + 37, 'AI 导图', 'islandTitle', hexc('ink.onDark'))
    s += text(ix + 112, iy + 37, '· gpt-image-2.5-flare · 已生成 3 张', 'islandMeta', '#FFFFFF', 'start', 'fill-opacity=".55"')
    ty0 = iy + 56
    s += mindmap(ix + 30, ty0, 78, 76)
    s += f'<rect x="{ix+118}" y="{ty0-2}" width="82" height="80" rx="11" fill="none" stroke="{hexc("island.select")}" stroke-width="2.5"/>' + mindmap(ix + 120, ty0, 78, 76)
    s += f'<circle cx="{ix+198}" cy="{ty0+2}" r="9" fill="{hexc("island.select")}" stroke="#000" stroke-width="1.5"/><path d="M{ix+194},{ty0+2} l3,3 5,-6" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>'
    s += f'<clipPath id="ic{u}"><rect x="{ix+210}" y="{ty0}" width="55" height="76"/></clipPath><g clip-path="url(#ic{u})">' + mindmap(ix + 210, ty0, 78, 76) + '</g>'
    s += f'<rect x="{ix+246}" y="{ty0}" width="20" height="76" fill="url(#fadeR{u})"/>'
    s += f'<defs><linearGradient id="fadeR{u}" x1="0" x2="1"><stop offset="0" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".9"/></linearGradient></defs>'
    s += f'<rect x="{ix+280}" y="{ty0+2}" width="72" height="34" rx="17" fill="#fff"/>' + text(ix + 316, ty0 + 25, '应用', 'islandBtn', '#000', 'middle')
    s += f'<rect x="{ix+280}" y="{ty0+44}" width="72" height="34" rx="17" fill="{hexc("island.discardBg")}"/>' + text(ix + 316, ty0 + 67, '丢弃', 'islandBtn', '#fff', 'middle')
    s += text(ix + 30, iy + 160, '小彩蛋：导图越简洁越好记', 'islandHint', '#FFFFFF', 'start', 'fill-opacity=".55"')
    # 整理笔记浮层
    sx, sy, sw, sh = 76, 368, 256, 356
    s += f'<rect x="{sx}" y="{sy}" width="{sw}" height="{sh}" rx="24" fill="#F4F2F1" fill-opacity=".93" stroke="#fff" stroke-width="1.2" filter="url(#shL{u})"/>'
    s += text(sx + sw / 2, sy + 44, '整理笔记', 'sheetTitle', INK, 'middle')
    steps = [('1. 转写', 'done'), ('2. 分段', 'done'), ('3. 提炼考点 3/5', 'active'), ('4. 生成复习卡', 'pending'), ('5. 生成导图', 'pending')]
    yy = sy + 82; ixc = sx + 28
    pts = []
    for lab, st in steps:
        if st == 'done':
            s += f'<circle cx="{ixc}" cy="{yy}" r="7.5" fill="{hexc("organize.done")}"/><path d="M{ixc-3.5},{yy} l2.5,2.6 4.5,-5" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>'
        elif st == 'active':
            s += f'<circle cx="{ixc}" cy="{yy}" r="7" fill="#fff" stroke="{hexc("organize.active")}" stroke-width="3"/>'
        else:
            s += f'<circle cx="{ixc}" cy="{yy}" r="6" fill="#fff" stroke="{hexc("organize.pending")}" stroke-width="1.5"/>'
        s += text(sx + 50, yy + 6, lab, 'step', INK, 'start', 'font-weight="600"' if st == 'active' else '')
        pts.append(yy)
        if st == 'active':
            yy += 34
            r = 9; cx2 = sx + 60
            s += f'<circle cx="{cx2}" cy="{yy}" r="{r}" fill="none" stroke="{hexc("organize.barTrack")}" stroke-width="3"/>'
            circ = 2 * math.pi * r
            s += f'<circle cx="{cx2}" cy="{yy}" r="{r}" fill="none" stroke="{hexc("organize.active")}" stroke-width="3" stroke-dasharray="{circ*0.62:.1f} {circ:.1f}" transform="rotate(-90 {cx2} {yy})" stroke-linecap="round"/>'
            s += text(sx + 77, yy + 5, '62% · 00:41 remaining', 'stepMeta', '#222', 'start', 'font-size="13.5"')
        yy += 34
    for a, b in zip(pts, pts[1:]):
        s += f'<line x1="{ixc}" y1="{a+10}" x2="{ixc}" y2="{b-10}" stroke="{hexc("organize.connector")}" stroke-width="1.5"/>'
    by = sy + 268
    s += f'<rect x="{sx+18}" y="{by}" width="{sw-36}" height="10" rx="5" fill="{hexc("organize.barTrack")}"/>'
    s += f'<rect x="{sx+18}" y="{by}" width="{(sw-36)*0.62:.0f}" height="10" rx="5" fill="url(#org{u})"/>'
    s += sparkle(sx + 18 + (sw - 36) * 0.62, by + 5, 8, '#E9FFF5') + sparkle(sx + 18 + (sw - 36) * 0.62, by + 5, 3.5, '#fff')
    qy = by + 24
    s += f'<rect x="{sx+18}" y="{qy}" width="{sw-36}" height="46" rx="12" fill="#fff" fill-opacity=".75"/>'
    s += text(sx + 30, qy + 19, '…信号时域分析的核心是冲激响', 'quote', '#222')
    s += text(sx + 30, qy + 37, '应和卷积积分…', 'quote', '#222')
    s += text(sx + sw - 30, qy + 37, '…', 'quote', '#222', 'end')
    return s

SCREENS = [('01-笔记-列表', screen_list), ('02-笔记-书架', screen_shelf), ('03-翻书阅读', screen_reader), ('04-整理笔记-灵动岛', screen_organize)]
def wrap(inner, u):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">{defs(u)}<g clip-path="url(#scr{u})">{inner}</g></svg>'

if __name__ == '__main__':
    out = os.path.join(HERE, 'svg'); os.makedirs(out, exist_ok=True)
    GAP = 24; parts = []
    for i, (name, fn) in enumerate(SCREENS):
        u = 'abcd'[i]
        svg = wrap(fn(u), u)
        open(os.path.join(out, name + '.svg'), 'w', encoding='utf-8').write(svg)
        parts.append(f'<g transform="translate({GAP + i*(W+GAP)},{GAP})"><rect width="{W}" height="{H}" rx="28" fill="#fff"/>'
                     f'<svg width="{W}" height="{H}" viewBox="0 0 {W} {H}">{defs(u)}<clipPath id="fr{u}"><rect width="{W}" height="{H}" rx="28"/></clipPath><g clip-path="url(#fr{u})">{fn(u)}</g></svg></g>')
    TW = GAP + 4 * (W + GAP); TH = H + 2 * GAP
    full = f'<svg xmlns="http://www.w3.org/2000/svg" width="{TW}" height="{TH}" viewBox="0 0 {TW} {TH}"><rect width="{TW}" height="{TH}" fill="#EEECEA"/>{"".join(parts)}</svg>'
    open(os.path.join(HERE, '笔记-设计稿.svg'), 'w', encoding='utf-8').write(full)
    try:
        import cairosvg
        cairosvg.svg2png(bytestring=full.encode('utf-8'), write_to=os.path.join(HERE, '笔记-设计稿-预览.png'), output_width=TW * 2)
    except Exception as e:
        print('cairosvg skipped:', e)
    print('ok', TW, TH)
