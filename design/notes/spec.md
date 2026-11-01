# 笔记模块设计稿 · token 规格（v0.0.9 改版）

来源：v0.0.8 概念图 4 屏（笔记列表 / 书架 / 翻书阅读 / 整理笔记 + 灵动岛 AI 导图）。
画布 393dp，概念图 1px ≈ 0.99dp，误差约 ±2dp。颜色由原图取样（JPEG 中值/暗部 3%），正文黑统一归到 `ink.primary #1A1A1A`。

文件：
- `notes.tokens.json`：W3C Design Tokens 格式，可导入 Figma Tokens Studio。
- `gen_tokens.py`：唯一数据源，改这里，然后 `python3 gen_tokens.py` 重新生成 JSON 和 `ui/NoteTokens.kt`。
- `NoteTokens.kt`：Compose 用，`NT.Colors / NT.Type / NT.Dim / NT.Shadow / NT.Brushes / NT.palette(i)`。
- `笔记-红线标注稿.png`：每个组件对应的 token 名。

## 全局
| 项 | token |
|---|---|
| 页面背景 | `NT.Brushes.Background`（#FEF9E6 → #F6F1E9 → #EAE7EE），下半屏叠 `Bg.Dot` 点阵 16dp |
| 页面左右边距 | `Dim.Screen.PadH` 24 |
| 大标题「笔记」 | `Type.PageTitle` 46sp serif bold，顶 `TitleTop` 52 |
| 副标题「3 门课 · 15 本笔记」 | `Type.PageSub` 15sp `Ink.Secondary` |
| 书架/列表切换 | `Dim.Segment` 150×40 r20，thumb 72 宽内缩 3，弹簧 `motion.segment` |
| 底栏 | `Dim.Nav` 280×64 r32 + 64 圆形搜索；选中 pill `Glass.NavActive`，图标/文字 `NavTab.Active` |

## 1 · 列表（替换 NoteScreens/LibraryTab 的列表态）
- 课程筛选 chip 行：顶 170，h28 r14 gap8 padH12；「全部」白底 `Chip.AllBg`，课程 chip 用 `palette(i).chip` 底 + 白字 + 6dp 白点；右上角数量 badge 14dp。横向滚动，最后一个可被裁切。
- 笔记卡片：`Glass.Card` + 1dp `CardBorder` + `Shadow.Card`，r16 pad12，最小高 87，卡间距 8。
  - 左：书封缩略图 56×66 r3，颜色 `palette(i).cover`，底部 2 条 `Gold.Line`，中间 `Gold.Icon` 课程图标线框。
  - 右：标题 `Type.CardTitle` 18sp/700 单行；日期右对齐 `Type.CardDate`（Oct 25 格式）；摘要 `Type.CardBody` 2 行省略。
  - 附加行（顶 8，三选一）：生成中进度（标签 + h4 渐变条 `Brushes.Generating` + 头部闪光 12dp）/ 标签组（`Tag` h24 r12，复习卡数、考点数）/ 无。

## 2 · 书架（替换 Shelf.kt Bookshelf / BookSpine）
- 每门课一层，首层顶 196，层间距 194。
- 层标题：7dp 圆点 + `Type.ShelfSection` 20sp serif，颜色 `palette(i).deep`；右侧「5 本」`Type.ShelfCount`。
- 书脊：左起 x50，宽 23，间距 1，高度在 104~124 间按笔记长度取，最多 5 本，最后一本倾斜 -12°。顶部两条金线（距顶 8、13，高 1.5）；竖排书名 `Type.Spine` 白 90%；底部日期 `Type.SpineDate` 白 50%。`Shadow.Book`。
- 主封面（最新一本）：x268，90×116 r4，`palette(i).cover` 布纹，居中课程图标 32dp `Gold.Icon`，距底 12 双金线，底部书签丝带 9×14。`Shadow.Cover`。
- 隔板：h12 r6，左右内缩 36，`Glass.Board` + 底边 `BoardEdge` + `Shadow.Board`。
- 点击书：`motion.shelfPress`（缩 0.96、上抬 6）后进入阅读页。

## 3 · 翻书阅读（替换 Shelf.kt BookScreen / PageScroll）
- 顶栏：返回 38dp 圆形玻璃按钮（顶 58，左 24）；居中标题 `Type.ReaderNav`「课程 · 章节」。
- 书体：顶 138，底 680，满宽；封皮 6dp 用 `palette(i).cover`，左上丝带书签 30×22（x62）。
- 书页：`Reader.Paper`，左右内边距 34；章节标题 `Type.Chapter` 居中，上下菱形装饰线 `Reader.Ornament` 宽 200。
- 课件图：248×178，白框 6dp + 投影，下方 10dp 题注「课件第 N 页」`Type.ReaderCaption`。
- 正文：`Type.ReaderBody` 15/24，首行缩进 2em，段距 14。关键词 `Reader.HlText` on `HlBg`；「★考点」`Reader.ExamText` on `ExamBg` r6。
- 翻页：右下角卷页 70dp，`motion.pageTurn` 420ms。
- 进度拖动：缩略图气泡 84×58 r12 +「第 3 节 · 23/36」；刻度尺 350×26 r13，刻度间距 9（大刻度 12 高），旋钮 16。
- 底部工具栏 h48：目录 / 复习卡 / 导图，图标 22。

## 4 · 整理笔记浮层 + 灵动岛
- 浮层：x76 w256 顶 368，r24 pad18，`Glass.Sheet` + 背景模糊 24 + `Shadow.Sheet`。
  - 标题 `Type.SheetTitle` 24sp serif 居中。
  - 步骤行 h34：完成 = `Organize.Done` 实心勾；进行中 = `Organize.Active` 空心环 + 下方子行（20dp 进度环 stroke3 +「62% · 00:41 remaining」`Type.StepMeta`）；未开始 = `Organize.Pending` 空心圆。图标间竖线 2dp。
  - 总进度条 h10 r5 `Brushes.OrganizeBar`，头部闪光。
  - 预览引用框 r12 pad10 `Organize.QuoteBg`，`Type.Quote` 2 行省略。
- 灵动岛：收起 118×36；展开 w372 x10 顶 126 r30 pad18 纯黑。
  - 行 1：✦ 图标 + `Type.IslandTitle`「AI 导图」+ `Type.IslandMeta`「· 模型名 · 已生成 3 张」。
  - 行 2：候选图 78×76 r10 gap10 横排，选中描边 2.5 `Island.Select` + 右上 18dp 勾；右侧「应用」白底黑字 / 「丢弃」`#3A3A3C` 白字，72×34 r17。
  - 行 3：小彩蛋 `Type.IslandHint` 白 55%。
  - 展开/收起 `motion.island`。

## 课程配色
前三门课固定用 green / blue / rust；第 4 门起 `NT.palette(i)` 按色相旋转，保持同样的饱和度和明度，和书架整体协调。
