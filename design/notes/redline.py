#!/usr/bin/env python3
"""把概念图标注成红线稿：每个组件框 + token 名 + 关键尺寸。坐标为 2x 截图像素（≈ dp×2）。"""
import os
from PIL import Image, ImageDraw, ImageFont
HERE = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.join(HERE, '../../android/app/src/main/res/font/serif_semibold.otf')
A = Image.open('/workspace/uploads/1000287788.jpg').convert('RGB')
B = Image.open('/workspace/uploads/1000287785.jpg').convert('RGB')
def crop(im, x0, x1): return im.crop((x0, 0, x1, 875)).resize(((x1 - x0) * 2, 1750), Image.LANCZOS)
screens = [
 ('笔记 · 列表', crop(A, 1240, 1645), [
   ((48, 120, 240, 220), 'Type.PageTitle 46sp serif', 'r'),
   ((48, 240, 310, 280), 'Type.PageSub 15sp', 'r'),
   ((456, 162, 756, 242), 'Dim.Segment 150×40 r20', 'l'),
   ((50, 344, 160, 400), 'Chip h28 r14 gap8', 'r'),
   ((440, 330, 478, 356), 'badge 14', 'l'),
   ((50, 442, 758, 618), 'Card minH87 r16 pad12 · Glass.Card', 'r'),
   ((72, 462, 186, 596), 'thumb 56×66', 'b'),
   ((210, 470, 540, 512), 'Type.CardTitle 18sp/700', 'l'),
   ((210, 518, 740, 590), 'Type.CardBody 13.5/19 ×2行', 'l'),
   ((370, 972, 740, 994), 'Progress h4 · Brushes.Generating', 'l'),
   ((212, 1170, 404, 1210), 'Tag h24 r12', 'r'),
   ((38, 1580, 598, 1712), 'Nav h64 r32', 'r'),
   ((620, 1580, 760, 1712), 'search 64', 'l'),
 ]),
 ('笔记 · 书架', crop(A, 1650, 2048), [
   ((60, 385, 430, 430), 'Type.ShelfSection 20sp · Course.*.Deep', 'r'),
   ((680, 390, 742, 425), 'ShelfCount', 'l'),
   ((98, 448, 372, 700), 'spines w23 h≤124 gap1 lean-12°', 'r'),
   ((534, 462, 718, 696), 'cover 90×116 r4 x268', 'l'),
   ((556, 692, 586, 720), 'ribbon 9×14', 'l'),
   ((72, 684, 744, 722), 'board h12 r6 inset36', 'r'),
   ((60, 760, 744, 1100), 'Shelf.pitch 194', 'r'),
 ]),
 ('笔记 · 翻书阅读', crop(B, 1240, 1645), [
   ((52, 116, 130, 192), 'back 38 glass', 'r'),
   ((228, 130, 580, 180), 'Type.ReaderNav 18sp serif', 'l'),
   ((4, 276, 770, 1366), 'book top138 bottom680 · Reader.Paper', 'r'),
   ((200, 384, 610, 466), 'Type.Chapter 20sp + ornament', 'l'),
   ((150, 516, 656, 878), 'slide 248×178 frame6', 'r'),
   ((340, 888, 470, 916), 'caption 11sp', 'l'),
   ((134, 960, 676, 1124), 'Type.ReaderBody 15/24 indent2em', 'r'),
   ((290, 1004, 402, 1036), 'hl Reader.HlText/HlBg', 'l'),
   ((134, 1150, 228, 1188), 'exam Reader.ExamText', 'r'),
   ((306, 1364, 504, 1530), 'Scrub popup 84×58 r12', 'l'),
   ((52, 1540, 756, 1592), 'Scrub track 350×26 r13 tick9', 'r'),
   ((70, 1620, 740, 1704), 'Toolbar h48 icon22', 'r'),
 ]),
 ('整理笔记 · 灵动岛', crop(B, 1650, 2048), [
   ((290, 30, 526, 98), 'Island collapsed 118×36', 'l'),
   ((36, 252, 782, 606), 'Island w372 r30 pad18 · bg #000', 'r'),
   ((84, 362, 244, 512), 'thumb 78×76 r10', 'b'),
   ((264, 358, 432, 518), 'selected stroke2.5 + check18', 'b'),
   ((600, 362, 744, 428), 'apply 72×34 r17', 'l'),
   ((600, 448, 744, 512), 'discard #3A3A3C', 'l'),
   ((150, 732, 666, 1446), 'Sheet w256 r24 pad18 blur24', 'r'),
   ((190, 866, 520, 1040), 'step h34 icon14 · Organize.Done/Active', 'l'),
   ((184, 1266, 632, 1290), 'bar h10 r5 · Brushes.OrganizeBar', 'r'),
   ((186, 1322, 632, 1414), 'quote r12 pad10', 'r'),
 ]),
]
F = ImageFont.truetype(FONT, 22); FT = ImageFont.truetype(FONT, 34)
M = (226, 32, 140)
W = sum(im.width for _, im, _ in screens) + 60 * (len(screens) + 1)
H = 1750 + 140
canvas = Image.new('RGB', (W, H), (246, 244, 240))
x = 60
for title, im, ann in screens:
    canvas.paste(im, (x, 110))
    d = ImageDraw.Draw(canvas, 'RGBA')
    d.text((x, 40), title, font=FT, fill=(26, 26, 26))
    for (x0, y0, x1, y1), label, side in ann:
        bx = (x + x0, 110 + y0, x + x1, 110 + y1)
        d.rectangle(bx, outline=M + (255,), width=3)
        tw = d.textlength(label, font=F)
        tx = bx[0] + 4 if side == 'r' else bx[2] - tw - 8
        ty = bx[1] - 30 if (bx[1] - 30 > 112 and side != 'b') else bx[3] + 4
        if side == 'b': tx = bx[0] + 4
        d.rounded_rectangle((tx - 4, ty, tx + tw + 6, ty + 28), 6, fill=M + (235,))
        d.text((tx + 1, ty + 1), label, font=F, fill='white')
    x += im.width + 60
canvas.save(os.path.join(HERE, '笔记-红线标注稿.png'))
canvas.resize((W // 2, H // 2), Image.LANCZOS).save('/tmp/redline_small.png')
print(canvas.size)
