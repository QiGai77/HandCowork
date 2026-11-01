#!/usr/bin/env python3
"""笔记模块设计 token —— 唯一数据源。
从 v0.0.8 概念图（笔记列表 / 书架 / 翻书阅读 / 整理笔记 / 灵动岛 AI 导图）量取。
画布 393dp 宽，概念图 1px ≈ 0.99dp，测量误差约 ±2dp。
运行：python3 gen_tokens.py  → notes.tokens.json + ../../android/.../ui/NoteTokens.kt
"""
import json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
KT = os.path.join(HERE, '../../android/app/src/main/java/app/xuezhang/ui/NoteTokens.kt')

# ---------- 颜色 ----------
# 值写 #RRGGBB 或 #RRGGBBAA（AA=透明度）
color = {
  'bg': {
    'top': '#FEF9E6', 'mid': '#F6F1E9', 'bottom': '#EAE7EE',     # 纵向渐变 0 / 0.45 / 1
    'dot': '#0000000F',                                          # 底部点阵 1dp 圆点，16dp 间距
  },
  'ink': {
    'primary': '#1A1A1A', 'secondary': '#78766B', 'tertiary': '#8B8784',
    'count': '#837E79', 'onDark': '#F6F6F6', 'onDarkSec': '#FFFFFF8C',
  },
  'glass': {
    'card': '#FFFFFF9E',          # 列表卡片：白 62%，叠底后约 #EFEDF2
    'cardBorder': '#FFFFFFB3',
    'sheet': '#FFFFFFB8',         # 整理笔记浮层：白 72% + 背景模糊 24dp
    'board': '#FFFFFF8C',         # 书架隔板
    'boardEdge': '#C6B69F66',
    'nav': '#F2F1F6CC',
    'navActive': '#C7DBDC',
    'button': '#FFFFFFCC',        # 返回按钮等圆形玻璃按钮
  },
  'segment': {'track': '#0000000F', 'thumb': '#FCFCFC', 'on': '#111111', 'off': '#8B8784'},
  'chip': {'allBg': '#F8F8F3', 'allText': '#111111', 'text': '#FFFFFF', 'badgeBg': '#D9D7D2', 'badgeText': '#6E6B66'},
  'tag': {'bg': '#0000000D', 'text': '#111111'},
  'progress': {
    'track': '#D1CFDC',
    'g0': '#BBE7E8', 'g1': '#ADCEED', 'g2': '#BAAFF4',   # 生成中进度条渐变
    'spark': '#FFF4E0',
  },
  # 每门课一套书色；新课程按 C.course 顺序映射到这三套之外时用 HSL 推导（见 spec.md）
  'course': {
    'green': {'spine': '#4C906B', 'cover': '#498C6B', 'chip': '#3F8762', 'deep': '#1F5F3F'},
    'blue':  {'spine': '#4B607B', 'cover': '#3A5475', 'chip': '#325072', 'deep': '#14284A'},
    'rust':  {'spine': '#B06953', 'cover': '#A65F4B', 'chip': '#AA5F48', 'deep': '#8A3C25'},
  },
  'gold': {'line': '#D4B06A', 'shade': '#A8823E', 'icon': '#E2C88A'},
  'reader': {
    'paper': '#F7F0DE', 'paperEdge': '#E9DEC2', 'gutter': '#00000014',
    'text': '#2A271E', 'heading': '#111111', 'caption': '#8E8A7E',
    'hlText': '#2F79A8', 'hlBg': '#D6E9F5',
    'examText': '#A6352A', 'examBg': '#F6D9D2',
    'ornament': '#B9AE95',
    'scrubTrack': '#FFFFFFB3', 'tick': '#B8B4C2', 'knob': '#E7E9EC',
  },
  'organize': {
    'done': '#3B886D', 'active': '#4FA684', 'pending': '#C9C9CE', 'connector': '#C9C9CE',
    'barTrack': '#DCE3DC', 'bar0': '#3FA27C', 'bar1': '#7FD3B0',
    'quoteBg': '#FFFFFFB3',
  },
  'island': {
    'bg': '#000000', 'select': '#4FA684',
    'applyBg': '#FFFFFF', 'applyText': '#000000',
    'discardBg': '#3A3A3C', 'discardText': '#FFFFFF',
  },
  'navTab': {'active': '#5696AB', 'inactive': '#101011'},
}

# ---------- 字体 ----------
# family: serif = R.font.serif_*（已在工程里）；sans = 系统默认
type_ = {
  'pageTitle':    dict(family='serif', weight=700, size=46, line=52),
  'pageSub':      dict(family='sans',  weight=400, size=15, line=20, color='ink.secondary'),
  'segment':      dict(family='sans',  weight=600, size=15, line=20),
  'chip':         dict(family='sans',  weight=500, size=14, line=18),
  'chipBadge':    dict(family='sans',  weight=600, size=10, line=12),
  'cardTitle':    dict(family='sans',  weight=700, size=18, line=24),
  'cardBody':     dict(family='sans',  weight=400, size=13.5, line=19),
  'cardDate':     dict(family='sans',  weight=400, size=13, line=18),
  'tag':          dict(family='sans',  weight=500, size=12.5, line=16),
  'progressLabel':dict(family='sans',  weight=500, size=12.5, line=16),
  'shelfSection': dict(family='serif', weight=700, size=20, line=26),
  'shelfCount':   dict(family='sans',  weight=400, size=15, line=20, color='ink.count'),
  'spine':        dict(family='serif', weight=600, size=12.5, line=15),
  'spineDate':    dict(family='sans',  weight=400, size=5.5, line=7),
  'readerNav':    dict(family='serif', weight=700, size=18, line=24),
  'chapter':      dict(family='serif', weight=700, size=20, line=28),
  'readerBody':   dict(family='sans',  weight=400, size=15, line=24),
  'readerCaption':dict(family='sans',  weight=400, size=11, line=14),
  'scrubLabel':   dict(family='sans',  weight=500, size=11, line=14),
  'toolbar':      dict(family='sans',  weight=500, size=11, line=14),
  'sheetTitle':   dict(family='serif', weight=700, size=24, line=30),
  'step':         dict(family='sans',  weight=500, size=17, line=22),
  'stepMeta':     dict(family='sans',  weight=400, size=13, line=18),
  'quote':        dict(family='sans',  weight=400, size=13.5, line=19),
  'islandTitle':  dict(family='sans',  weight=600, size=15, line=20),
  'islandMeta':   dict(family='sans',  weight=400, size=13, line=18),
  'islandHint':   dict(family='sans',  weight=400, size=12, line=16),
  'islandBtn':    dict(family='sans',  weight=600, size=15, line=20),
  'nav':          dict(family='sans',  weight=500, size=10.5, line=13),
}

# ---------- 尺寸（dp） ----------
dim = {
  'screen': {'padH': 24, 'titleTop': 52, 'subGap': 4, 'listTop': 220},
  'segment': {'w': 150, 'h': 40, 'r': 20, 'inset': 3, 'thumbW': 72},
  'chip': {'h': 28, 'r': 14, 'padH': 12, 'gap': 8, 'dot': 6, 'dotGap': 6, 'badge': 14,
           'badgeOffX': -2, 'badgeOffY': -6, 'rowTop': 170},
  'card': {'minH': 87, 'r': 16, 'pad': 12, 'gap': 8, 'thumbW': 56, 'thumbH': 66, 'thumbR': 3,
           'thumbGap': 14, 'titleBodyGap': 4, 'extraTop': 8, 'border': 1},
  'tag': {'h': 24, 'r': 12, 'padH': 10, 'gap': 8, 'icon': 13},
  'progress': {'h': 4, 'r': 2, 'labelGap': 8, 'spark': 12},
  'shelf': {'firstTop': 196, 'pitch': 194, 'headerH': 26, 'dot': 7, 'dotGap': 9, 'headerToBooks': 18,
            'bookAreaH': 124, 'spineW': 23, 'spineGap': 1, 'spineR': 3, 'leanDeg': -12,
            'band1': 8, 'band2': 13, 'bandH': 1.5, 'coverW': 90, 'coverH': 116, 'coverR': 4,
            'coverX': 268, 'coverIcon': 32, 'coverBandFromBottom': 12, 'ribbonW': 9, 'ribbonH': 14,
            'boardH': 12, 'boardR': 6, 'boardInsetH': 36, 'spineLeft': 50, 'maxSpines': 5},
  'reader': {'backSize': 38, 'backTop': 58, 'navTop': 62, 'bookTop': 138, 'bookBottom': 680,
             'cloth': 6, 'pagePadH': 34, 'chapterTop': 52, 'ornamentW': 200,
             'slideW': 248, 'slideH': 178, 'slideFrame': 6, 'slideR': 2, 'captionGap': 10,
             'bodyTop': 26, 'paraGap': 14, 'indentEm': 2, 'ribbonW': 30, 'ribbonH': 22, 'ribbonX': 62,
             'curlW': 70},
  'scrub': {'thumbW': 84, 'thumbH': 58, 'popR': 12, 'popPad': 6, 'trackW': 350, 'trackH': 26,
            'trackR': 13, 'tickStep': 9, 'tickH': 8, 'tickMajorH': 12, 'knob': 16, 'top': 730},
  'toolbar': {'h': 48, 'icon': 22, 'labelGap': 2, 'bottom': 18},
  'sheet': {'w': 256, 'x': 76, 'top': 368, 'r': 24, 'pad': 18, 'blur': 24,
            'stepH': 34, 'stepIcon': 14, 'stepIconGap': 18, 'connector': 2, 'ring': 20, 'ringStroke': 3,
            'barH': 10, 'barR': 5, 'barTop': 16, 'quoteR': 12, 'quotePad': 10, 'quoteTop': 14},
  'island': {'collapsedW': 118, 'collapsedH': 36, 'w': 372, 'x': 10, 'top': 126, 'r': 30, 'pad': 18,
             'thumbW': 78, 'thumbH': 76, 'thumbR': 10, 'thumbGap': 10, 'selectStroke': 2.5,
             'check': 18, 'btnW': 72, 'btnH': 34, 'btnR': 17, 'btnGap': 10, 'hintTop': 12},
  'nav': {'h': 64, 'r': 32, 'insetH': 18, 'bottom': 22, 'barW': 280, 'pillW': 68, 'pillH': 52,
          'search': 64, 'icon': 24},
}

shadow = {
  'card':    {'y': 4, 'blur': 16, 'color': '#3C325014'},
  'segment': {'y': 2, 'blur': 6,  'color': '#0000001F'},
  'book':    {'y': 3, 'blur': 6,  'color': '#00000033'},
  'cover':   {'y': 6, 'blur': 14, 'color': '#00000038'},
  'board':   {'y': 8, 'blur': 14, 'color': '#5A4A3026'},
  'sheet':   {'y': 18,'blur': 40, 'color': '#2A204026'},
  'island':  {'y': 12,'blur': 30, 'color': '#00000040'},
  'nav':     {'y': 8, 'blur': 24, 'color': '#2A20401F'},
}

motion = {
  'segment':   {'type': 'spring', 'damping': 0.8, 'stiffness': 380},
  'pageTurn':  {'type': 'tween', 'ms': 420, 'easing': 'cubic(0.3,0,0.2,1)'},
  'shelfPress':{'type': 'spring', 'scale': 0.96, 'liftY': -6},
  'island':    {'type': 'spring', 'damping': 0.75, 'stiffness': 300},
  'spark':     {'type': 'loop', 'ms': 1600},
}

# ---------- JSON（W3C Design Tokens 格式） ----------
def tok(v, t): return {'$value': v, '$type': t}
def walk(d, t):
    return {k: (walk(v, t) if isinstance(v, dict) else tok(v, t)) for k, v in d.items()}

out = {
  '$description': '课堂学长 · 笔记模块 token（v0.0.9 改版）。单位 dp/sp，画布 393dp。',
  'color': walk(color, 'color'),
  'typography': {k: tok({'fontFamily': v['family'], 'fontWeight': v['weight'],
                         'fontSize': v['size'], 'lineHeight': v['line'],
                         **({'color': v['color']} if 'color' in v else {})}, 'typography') for k, v in type_.items()},
  'dimension': walk(dim, 'dimension'),
  'shadow': {k: tok(v, 'shadow') for k, v in shadow.items()},
  'motion': {k: tok(v, 'transition') for k, v in motion.items()},
}
with open(os.path.join(HERE, 'notes.tokens.json'), 'w') as f:
    json.dump(out, f, ensure_ascii=False, indent=2)

# ---------- Kotlin ----------
def kc(h):
    h = h.lstrip('#')
    a = h[6:8] if len(h) == 8 else 'FF'
    return f'Color(0x{(a+h[:6]).upper()})'
def ident(k): return k[0].upper() + k[1:]
def num(v): return (f'{v}' if isinstance(v, float) else f'{v}')

L = ['// 由 design/notes/gen_tokens.py 生成，请改 token 源文件后重新生成，不要手改。',
     'package app.xuezhang.ui', '',
     'import androidx.compose.ui.graphics.Brush',
     'import androidx.compose.ui.graphics.Color',
     'import androidx.compose.ui.text.TextStyle',
     'import androidx.compose.ui.text.font.FontFamily',
     'import androidx.compose.ui.text.font.FontWeight',
     'import androidx.compose.ui.unit.dp',
     'import androidx.compose.ui.unit.sp', '',
     '/** 笔记模块 token：NT.Colors / NT.Type / NT.Dim / NT.Shadow */',
     'object NT {']

L.append('    object Colors {')
def emit_colors(d, ind):
    for k, v in d.items():
        if isinstance(v, dict):
            L.append(' ' * ind + f'object {ident(k)} {{')
            emit_colors(v, ind + 4)
            L.append(' ' * ind + '}')
        else:
            L.append(' ' * ind + f'val {ident(k)} = {kc(v)}')
emit_colors(color, 8)
L.append('    }')

def cref(path):  # 'ink.secondary' -> NT.Color.Ink.Secondary
    return 'NT.Colors.' + '.'.join(ident(p) for p in path.split('.'))
L.append('    object Type {')
for k, v in type_.items():
    fam = 'Serif' if v['family'] == 'serif' else 'FontFamily.Default'
    col = cref(v.get('color', 'ink.primary'))
    L.append(f'        val {ident(k)} = TextStyle(fontFamily = {fam}, fontWeight = FontWeight.W{v["weight"]}, '
             f'fontSize = {num(v["size"])}.sp, lineHeight = {num(v["line"])}.sp, color = {col})'.replace('.0.sp', '.sp'))
L.append('    }')

L.append('    object Dim {')
for g, d in dim.items():
    L.append(f'        object {ident(g)} {{')
    for k, v in d.items():
        if k.endswith('Deg') or k.endswith('Em') or k == 'maxSpines':
            L.append(f'            const val {ident(k)} = {num(v)}{"f" if isinstance(v,float) else ""}')
        else:
            L.append(f'            val {ident(k)} = {num(v)}.dp')
    L.append('        }')
L.append('    }')

L.append('    /** y 偏移 / 模糊半径 / 颜色；用 Modifier.shadow(elevation=blur/2, spotColor=color) 近似 */')
L.append('    object Shadow {')
for k, v in shadow.items():
    L.append(f'        val {ident(k)} = Triple({v["y"]}.dp, {v["blur"]}.dp, {kc(v["color"])})')
L.append('    }')

L += ['    object Brushes {',
      '        val Background = Brush.verticalGradient(0f to NT.Colors.Bg.Top, 0.45f to NT.Colors.Bg.Mid, 1f to NT.Colors.Bg.Bottom)',
      '        val Generating = Brush.horizontalGradient(listOf(NT.Colors.Progress.G0, NT.Colors.Progress.G1, NT.Colors.Progress.G2))',
      '        val OrganizeBar = Brush.horizontalGradient(listOf(NT.Colors.Organize.Bar0, NT.Colors.Organize.Bar1))',
      '    }',
      '',
      '    data class BookPalette(val spine: androidx.compose.ui.graphics.Color, val cover: androidx.compose.ui.graphics.Color,',
      '                           val chip: androidx.compose.ui.graphics.Color, val deep: androidx.compose.ui.graphics.Color)',
      '    val Palettes = listOf(',
      '        BookPalette(NT.Colors.Course.Green.Spine, NT.Colors.Course.Green.Cover, NT.Colors.Course.Green.Chip, NT.Colors.Course.Green.Deep),',
      '        BookPalette(NT.Colors.Course.Blue.Spine, NT.Colors.Course.Blue.Cover, NT.Colors.Course.Blue.Chip, NT.Colors.Course.Blue.Deep),',
      '        BookPalette(NT.Colors.Course.Rust.Spine, NT.Colors.Course.Rust.Cover, NT.Colors.Course.Rust.Chip, NT.Colors.Course.Rust.Deep),',
      '    )',
      '    /** 第 i 门课的书色；超过 3 门按色相旋转推导，保持同样的明度/饱和度 */',
      '    fun palette(i: Int): BookPalette {',
      '        if (i < Palettes.size) return Palettes[i]',
      '        val hue = (i * 47f + 200f) % 360f',
      '        fun hsl(s: Float, l: Float) = androidx.compose.ui.graphics.Color.hsl(hue, s, l)',
      '        return BookPalette(hsl(0.30f, 0.43f), hsl(0.33f, 0.38f), hsl(0.37f, 0.36f), hsl(0.50f, 0.20f))',
      '    }',
      '}']
code = '\n'.join(L) + '\n'
code = code.replace('NT.Color.', 'NT.Color.')
with open(KT, 'w') as f: f.write(code)
print('ok', len(color), len(type_), len(dim))
