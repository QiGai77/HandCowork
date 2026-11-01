import subprocess, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W0 = '/workspace/'
FILES = {1:'c1-courses.png',2:'n02-camera.png',3:'n03-timetable-confirm.png',4:'n04-course-detail.png',
5:'c2-class-deck.png',6:'n06-materials.png',7:'n07-bilibili.png',8:'n08-import-progress.png',
9:'c3-live-class.png',10:'n10-slide-full.png',11:'n11-end-class.png',12:'c5-ai-island.png',
13:'bookshelf-concept-v2.png',14:'c9-notes-list.png',15:'c4-reader.png',16:'n16-note-detail.png',
17:'n17-concept-map.png',18:'c6-review-cards.png',19:'n19-ask.png',20:'n20-share.png',
21:'n21-search.png',22:'c7-me-settings.png',23:'n23-models.png',24:'n24-prompts.png',
25:'c8-drive.png',26:'n26-google-connect.png',27:'n27-logs.png',28:'n28-island-states.png'}
TITLES = {1:'课程表（首页）',2:'拍照导入课表',3:'识别预览确认',4:'课程详情',5:'上课 · 卡片选课',6:'课前资料',
7:'B站 · 分P与截图',8:'录播导入进度',9:'上课中',10:'课件全屏',11:'下课 · 简单/详细',12:'AI 生成 + 灵动岛',
13:'笔记 · 书架',14:'笔记 · 列表',15:'翻书阅读',16:'笔记详情',17:'概念图',18:'复习卡',19:'问学长',20:'分享导出',
21:'全局搜索',22:'我的',23:'模型与供应商',24:'提示词编辑',25:'云同步 Drive',26:'Google 授权',27:'日志',28:'灵动岛状态'}
FONT = '/workspace/work/NotoSansSC.otf'
def font(s): return ImageFont.truetype(FONT, s)

def screen(n):
    im = Image.open(W0+FILES[n]).convert('RGB')
    return im.resize((600, round(im.height*600/im.width)), Image.LANCZOS)

# coordinate helper: sheet coords -> screen coords (600 wide)
def sc(n, x, y):
    col = (n-1) % 4
    return (x - (8+616*col), y - 78)

# Hotspots: (from, sheet_x, sheet_y, to, label)
HOT = [
 (1,420,220,2,'拍照导入'),(1,540,220,4,'新建课程'),(1,190,520,4,'点课程块'),(1,190,1300,5,'底栏 上课'),(1,292,1300,13,'底栏 笔记'),(1,395,1300,22,'底栏 我的'),(1,521,1303,21,'搜索'),
 (2,924,1100,3,'拍照'),(2,738,1100,3,'相册选图'),
 (3,1675,1290,1,'导入'),(3,1405,1290,2,'重拍'),(3,1540,1142,23,'换模型重试'),
 (4,2318,1197,9,'开始上课'),(4,2045,690,6,'课前资料'),(4,2140,895,13,'这门课的笔记'),
 (5,308,1020,6,'资料上传'),(5,308,1125,9,'开始上课'),(5,520,640,5,'左右滑动换课'),
 (6,1060,560,7,'B站录播课'),(6,1118,915,7,'粘贴 BV'),(6,788,560,8,'选 PPT/PDF'),
 (7,1540,1200,8,'导入并生成'),(7,1540,1010,7,'选截图'),
 (8,2156,1200,28,'后台运行'),
 (9,516,555,10,'全屏课件'),(9,445,220,9,'原声/学长'),(9,413,1305,11,'结束上课'),(9,90,1305,9,'标记考点'),
 (10,675,153,9,'关闭'),(10,924,1295,10,'选页'),
 (11,1540,1220,12,'生成笔记'),(11,1540,1305,13,'稍后再说'),
 (12,2354,365,13,'应用'),(12,2354,428,12,'丢弃'),(12,2156,800,28,'离开页面'),
 (13,513,220,14,'切到列表'),(13,206,500,15,'点书脊'),(13,470,500,15,'点封面'),
 (14,1021,220,13,'切到书架'),(14,920,470,15,'点笔记'),
 (15,1540,1242,15,'拖动跳页'),(15,1762,1310,17,'导图'),(15,1480,1310,18,'复习卡'),(15,1318,1310,15,'目录'),
 (16,2335,183,15,'翻书模式'),(16,2395,183,20,'分享'),(16,1972,1310,19,'问学长'),(16,2160,1310,18,'复习卡'),(16,2345,1310,17,'导图'),
 (17,72,183,16,'返回'),(17,268,277,17,'AI导图/可拖动'),(17,505,1194,17,'重新生成'),
 (18,920,700,18,'点卡翻面'),(18,1107,1168,18,'记得'),(18,740,1168,18,'忘了'),(18,687,195,16,'返回'),
 (19,1770,1305,19,'发送'),(19,1350,1205,19,'追问'),
 (20,2156,1268,20,'导出'),
 (21,920,470,16,'打开笔记'),(21,175,800,10,'打开课件页'),
 (22,880,297,25,'Google Drive'),(22,1005,535,23,'模型'),(22,860,763,22,'拖动排序'),(22,920,1187,24,'提示词'),(22,920,1243,27,'日志'),
 (23,1650,645,22,'选模型'),(23,1665,942,23,'刷新列表'),
 (24,2330,1207,22,'保存'),(24,1980,1207,24,'恢复默认'),
 (25,186,552,25,'立即同步'),(25,428,552,26,'断开/重连'),(25,315,1120,25,'失败重试'),
 (26,924,945,25,'用 Google 继续'),
 (27,1295,168,22,'返回'),
 (28,2196,475,12,'点岛回到生成页'),(28,2196,1003,16,'完成→查看'),(28,2196,1120,12,'失败→重试'),
]

def annotate(n):
    im = screen(n)
    d = ImageDraw.Draw(im, 'RGBA')
    pts = [h for h in HOT if h[0]==n]
    for i,(a,x,y,to,lab) in enumerate(pts):
        X,Y = sc(n,x,y)
        d.ellipse([X-20,Y-20,X+20,Y+20], fill=(220,38,38,235), outline='white', width=3)
        d.text((X,Y), str(i+1), font=font(24), fill='white', anchor='mm')
    head = Image.new('RGB',(600,70),'#1f2a24')
    hd = ImageDraw.Draw(head)
    hd.text((20,14), f'{n:02d}  {TITLES[n]}', font=font(34), fill='white')
    hd.text((580,35), '重构版 v0.0.1Ex', font=font(20), fill='#6fd3a8', anchor='rm')
    LH = 44; leg = Image.new('RGB',(600, 20+LH*7),'#ffffff')
    ld = ImageDraw.Draw(leg)
    for i,(a,x,y,to,lab) in enumerate(pts):
        cx = 30 + (i//7)*300; cy = 32 + (i%7)*LH
        ld.ellipse([cx-16,cy-16,cx+16,cy+16], fill='#dc2626')
        ld.text((cx,cy), str(i+1), font=font(20), fill='white', anchor='mm')
        t = f'{lab}  → {to:02d}' if to!=n else f'{lab}（本页）'
        ld.text((cx+26,cy), t, font=font(23), fill='#1f2a24', anchor='lm')
    out = Image.new('RGB',(600,70+im.height+leg.height),'white')
    out.paste(head,(0,0)); out.paste(im,(0,70)); out.paste(leg,(0,70+im.height))
    return out

def sheets():
    tiles = [annotate(n) for n in range(1,29)]
    for s in range(7):
        row = tiles[s*4:s*4+4]
        Wt = 8+616*4; Ht = row[0].height+16
        sh = Image.new('RGB',(Wt,Ht),'#f4f1ea')
        for i,t in enumerate(row): sh.paste(t,(8+616*i,8))
        sh.save(f'/workspace/work/flow/guide-{s+1}.jpg', quality=88)

# ---------- video ----------
STEPS = [ # (screen, sheet_x, sheet_y, caption) ; tap leads to next screen
 (1,420,220,'课程表首页 → 点「拍照导入」'),
 (2,924,1100,'对准课表 → 按快门'),
 (3,1675,1290,'核对识别结果 → 导入 17 节课'),
 (1,190,520,'回到课程表 → 点一门课'),
 (4,2037,1300,'课程详情 → 底栏「上课」'),
 (5,520,640,'左右滑动选课','swipe'),
 (5,308,1020,'点「资料上传」'),
 (6,1060,560,'选「B站录播课」'),
 (7,1540,1200,'选分P和截图 → 导入并生成'),
 (8,2156,1200,'导入进度 → 后台运行'),
 (5,308,1125,'回到上课页 → 红色「开始上课」'),
 (9,516,555,'上课中 → 点课件放大'),
 (10,675,153,'全屏课件 → 关闭'),
 (9,413,1305,'下课 → 红色停止键'),
 (11,1540,1220,'选「详细」→ 生成笔记'),
 (12,2156,800,'生成中，可直接离开页面'),
 (28,2196,475,'灵动岛继续显示进度 → 轻点回来'),
 (12,2354,365,'导图出图 → 在灵动岛里「应用」'),
 (13,513,220,'书架 → 切到列表'),
 (14,1021,220,'列表 → 切回书架'),
 (13,206,500,'点书脊打开笔记'),
 (15,1540,1242,'翻书阅读 → 拖动进度条跳页','drag'),
 (15,1762,1310,'点「导图」'),
 (17,72,183,'概念图 → 返回'),
 (16,2160,1310,'笔记详情 → 复习卡'),
 (18,1107,1168,'翻面后点「记得」'),
 (18,687,195,'返回笔记'),
 (16,1972,1310,'点「问学长」'),
 (19,1770,1305,'提问 → 发送'),
 (16,2395,183,'点分享'),
 (20,2156,1268,'选格式 → 导出'),
 (13,521,1303,'任意页 → 搜索'),
 (21,395,1300,'底栏「我的」'),
 (22,860,763,'长按拖动「上课」到第一位','drag'),
 (22,1005,535,'点「模型」'),
 (23,1650,645,'选模型'),
 (22,920,1187,'点「模型提示词」'),
 (24,2330,1207,'编辑后保存'),
 (22,880,297,'点 Google Drive'),
 (25,428,552,'云同步 → 断开/重连'),
 (26,924,945,'用 Google 账号继续'),
 (25,66,135,'连接成功 → 返回'),
 (22,920,1243,'点「日志」'),
 (27,None,None,'日志：错误可展开、可导出'),
]

VW,VH,FPS = 1080,1920,30
SCALE = 1.2
cache = {}
def scr(n):
    if n not in cache: cache[n] = screen(n).resize((720, round(1295*1.2)), Image.LANCZOS)
    return cache[n]
SX = (VW-720)//2; SY = 250

bg = Image.new('RGB',(VW,VH),'#efe9f3')
def base_frame(img, n, cap, off=0, img2=None, n2=None):
    fr = bg.copy()
    if img2 is None:
        fr.paste(img,(SX+off,SY))
    else:
        fr.paste(img,(SX+off,SY)); fr.paste(img2,(SX+off+VW,SY))
    d = ImageDraw.Draw(fr)
    d.rounded_rectangle([60,60,VW-60,200],36,fill='#1f2a24')
    d.text((100,82), f'{n:02d}', font=font(54), fill='#6fd3a8')
    d.text((210,90), cap, font=font(44), fill='white')
    d.text((VW//2,VH-70), '课堂学长 重构版 v0.0.1Ex · 交互演示', font=font(30), fill='#8a8296', anchor='mm')
    return fr

def tap_overlay(fr, X, Y, t):
    d = ImageDraw.Draw(fr,'RGBA')
    r = 20+60*t; a = int(200*(1-t))
    d.ellipse([X-r,Y-r,X+r,Y+r], outline=(255,59,48,a), width=8)
    d.ellipse([X-24,Y-24,X+24,Y+24], fill=(255,59,48,170))

def main():
    sheets()
    p = subprocess.Popen(['ffmpeg','-y','-f','rawvideo','-pix_fmt','rgb24','-s',f'{VW}x{VH}','-r',str(FPS),'-i','-',
        '-c:v','libx264','-pix_fmt','yuv420p','-crf','23','-preset','medium','/workspace/work/flow/xuezhang-demo.mp4'],stdin=subprocess.PIPE)
    W = lambda f: p.stdin.write(f.tobytes())
    for i,st in enumerate(STEPS):
        n,x,y,cap = st[:4]; kind = st[4] if len(st)>4 else 'tap'
        img = scr(n)
        for _ in range(int(FPS*0.9)): W(base_frame(img,n,cap))
        if x is not None:
            lx,ly = sc(n,x,y); X = SX+lx*SCALE; Y = SY+ly*SCALE
            frames = int(FPS*0.6)
            for k in range(frames):
                t = k/(frames-1); fr = base_frame(img,n,cap)
                if kind=='tap': tap_overlay(fr,X,Y,t)
                else:
                    dx = (-220 if kind=='swipe' else 200)*t
                    dy = (0 if kind=='swipe' else -0)
                    if kind=='drag' and n==22: dx,dy = 0,-90*t
                    tap_overlay(fr,X+dx,Y+dy,0.2)
                W(fr)
        else:
            for _ in range(int(FPS*1.2)): W(base_frame(img,n,cap))
        if i+1 < len(STEPS):
            n2 = STEPS[i+1][0]
            if n2 != n:
                img2 = scr(n2); frames = int(FPS*0.35)
                for k in range(1,frames+1):
                    t = k/frames; e = 1-(1-t)**3
                    fr = bg.copy()
                    fr.paste(img,(SX-int(VW*e),SY)); fr.paste(img2,(SX+VW-int(VW*e),SY))
                    d = ImageDraw.Draw(fr)
                    d.rounded_rectangle([60,60,VW-60,200],36,fill='#1f2a24')
                    W(fr)
    p.stdin.close(); p.wait()

import sys
if sys.argv[1:]==["sheets"]: sheets()
else: main()
