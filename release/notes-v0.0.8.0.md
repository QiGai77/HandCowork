## 课堂学长 v0.0.8.0 更新日志 · 两个转写插件重做

这一版只改了「识别优化与考点萃取」和「理工科学长」两个插件（插件市场 2.0.0，需要 App 0.0.8.0+，在「插件市场」里更新）。App 核心只加了通用插件钩子和断网重试修复；**插件关闭时上课页和 v0.0.7.9 完全一样**，旧课照常显示。

### 识别优化与考点萃取 2.0.0
- **按停顿切段**：说话停顿处切段（单段 3–30 秒），句子不再被固定秒数切断；纯静音段不送识别。
- **一句一行**：原声视图每句单独一行，左侧 mm:ss 时间，同一话题成段、段间空行；卡片头显示起止时间、「提问 / 考点」标签和老师名；老师提问句淡蓝底。
- **热词纠错**：课程热词 + 课件标题 / 公式自动提取 + 插件词表。拼音模糊匹配（zh/z、ch/c、sh/s、n/l、ang/an、eng/en、ing/in）、型号编辑距离（82555 → 8255，只在唯一最近时替换），AO → A口 走词表别名。改过的词蓝色虚线下划线 + 上标「原:xx」，**点一下还原**。
- **学长对话气泡**：「学」精炼稿（原文 40–60%，头部 时间 · P.页 · 原文 xx%）、「学」要点（考点红 / 结论橙 / 定义绿 / 公式紫 / 提问蓝 / 例题青）、「问」老师提问 + 绿色「答：」、「结」本页小结（课件翻页时汇总上一页要点）。
- **强度**：轻 = 只清洗分句；中 = + 精炼稿与老师提问；强 = + 要点与本页小结。

### 理工科学长 · 公式与代码强化 2.0.0
- 芯片 / 寄存器 / 引脚名词表参与热词纠错：8255、8253、8259、8251、8086、INTE、IBF、OBF、STB、ACK、INTR、INTA、CS、RD、WR、A0/A1、PC0–PC7、ICW/OCW、A口/B口/C口、控制字、计数初值……
- 实时整理提示词追加：口述公式转 LaTeX（「x 平方加 y 平方」→ $x^2+y^2$），口述代码整理成代码块。
- 修复：旧版这个插件会抢占识别优化的文本管线（且规则格式不被识别），现在只提供词表和提示词追加。

### 断网不丢段（bug 修复）
- 转写 / 大模型失败的段落进本地队列，1s / 3s / 10s 退避重试，之后每 10s 再试，下课后再补几轮。
- 上课页显示虚线占位卡「⟳ 网络断了，这段稍后补上」，补上后原地替换；不再显示红字 connection closed。

### 插件框架新增的通用钩子（给插件作者）
- 规则步骤：`split`（分句）、`hotwords`（热词纠错）、`lexicon`（词表）、`spacing`（中英文间距）。
- `hooks`：`capture.vad`、`segment.refine`、`voice.render=sentences`、`senior.render=bubbles`、`page.summary`。
- 提示词追加：规则 `llm` 里以 `+` 结尾的键（如 `"segment+"`）追加到内置提示词末尾。
- `Segment.ext` 保存插件数据；没有它的旧课照旧显示。

### 没做的
- **▶ 回听**：App 目前不保存上课录音（分段识别后即删除），没有能复用的时间戳跳播，所以提问气泡里没放这个按钮。
- **sherpa-onnx Silero VAD**：插件只能是声明式资源包（不允许 .so），sherpa-onnx 需要随 App 打包每个 ABI 数 MB 的原生库，等于改核心；所以用能量 + 过零率 + 自适应噪声底的轻量 VAD 实现同样的「停顿处切段」。
- 没有真机 / 真实课堂录音测试；截图来自 Robolectric 渲染，大模型回复为模拟数据。

### 致谢（借鉴的开源项目）
- [WhisperX](https://github.com/m-bain/whisperX)（BSD-2）：按时间戳重切句 → 句子时间按字数在段内线性估算（`Splitter.timestamps`）
- [FunASR](https://github.com/modelscope/FunASR)（MIT）：VAD + 标点 + 时间戳流水线、ct-punc 分句（`Splitter.split`、插件整体流程）
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)（Apache-2.0）：按停顿切段（`EnergyVad`）
- [CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline)（MIT）：拼音模糊热词替换、中英文间距（`Hotwords`、`spacing` 步骤）
- [wtpsplit](https://github.com/segment-any-text/wtpsplit)（MIT）：长句切分规则（`Splitter.splitLong`）
- [pypinyin](https://github.com/mozillazg/python-pinyin)（MIT）：生成插件里的 `data/pinyin.txt`
- FormalASR：精炼提示词里口语 → 书面的示例思路

### 质量验证
- 单元测试 121 项全部通过（新增 V0080TranscriptTest 16 项：分句、长句切分、碎句合并、热词替换 82555→8255 / AO→A口 / 拼音模糊、精炼 JSON 新旧格式、VAD）。
- Roborazzi 截图：原声 / 学长 × 插件开 / 关 四张。
