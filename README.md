<div align="center">

<img src="https://img.shields.io/badge/课堂学长-学-111?style=for-the-badge" alt="课堂学长" />

# 课堂学长

**把一节课，变成一份看得懂的笔记。**

录音实时转写，AI 学长按课件逐页讲解，图表能拖，题目能做。

![version](https://img.shields.io/badge/version-0.0.8.0-0A84FF)
![android](https://img.shields.io/badge/Android-8.0%2B-34C759)
![compose](https://img.shields.io/badge/Jetpack-Compose-4285F4)
![status](https://img.shields.io/badge/状态-测试版-FF9500)

</div>

---

## 能做什么

| | |
|---|---|
| **上课** | 录音分段转写，「原声 / 学长」两种模式，长按 1.5s 防误触下课，灵动岛实时状态 |
| **笔记** | 按课件页图文讲解，彩色标注概念、考点、易错，图表可拖、题目可做 |
| **课程** | 拍一张课程表，看图模型一键导入整学期；首页 Today 只看课表条条，空处完全透明 |
| **课件** | 内置 PPT/PPTX 转 PDF 引擎，原生高保真渲染，支持 B 站录播课知识点关键帧截取 |
| **模型** | 全新「模型配置」卡片，支持原生模式与 Agent 模式切换，支持 SiliconFlow、DeepSeek、OpenAI、阿里云百炼等 |
| **备份** | 本地一键云备份（`Documents/课堂学长备份`）、Google Drive（原生 4 色标志）、Gitee 远程更新、GitHub 备份 |
| **分享** | 笔记导出 DOCX、PDF、Markdown 或长图 |

## v0.0.8.0 更新：两个转写插件重做

这一版只动「识别优化与考点萃取」和「理工科学长」两个插件（插件市场 2.0.0，需 App 0.0.8.0+）。App 核心只加了通用插件钩子和断网重试修复；**插件关闭时上课页和 v0.0.7.9 一样**。

- **识别优化与考点萃取 2.0.0**
  - 按说话停顿切段（`capture.vad`，单段 3–30 秒），不再按固定秒数把句子切断。
  - 原声视图一句一行，左侧时间，同一话题成段；卡片头显示起止时间、提问 / 考点标签和老师名；老师提问句淡蓝底（`transcript.split` + `voice.render=sentences`）。
  - 热词纠错（`transcript.hotwords`）：课程热词 + 课件标题 / 公式自动提取 + 插件词表；拼音模糊匹配（zh/z、n/l、ang/an…）和型号编辑距离（82555 → 8255），AO → A口 这类写法走词表别名。改过的词蓝色虚线下划线，上标「原:xx」，点一下还原。
  - 学长视图改成对话气泡（`senior.render=bubbles`）：「学」精炼稿（原文 40–60%，头部 时间 · 页码 · 原文 xx%）、「学」彩色要点（考点 / 结论 / 定义 / 公式 / 提问 / 例题）、「问」老师提问 + 答案、「结」本页小结（翻页时汇总上一页）。强度：轻 = 只清洗分句；中 = + 精炼稿与提问；强 = + 要点与本页小结。
- **理工科学长 2.0.0**：芯片 / 寄存器 / 引脚名词表（8255、8253、8259、8251、INTE、IBF、OBF、STB、ACK、INTR、CS、A0/A1、PC6/PC7、A口/B口/C口…）参与纠错；实时整理提示词追加「口述公式转 LaTeX、口述代码整理成代码块」。修复：只带词表的插件不再抢占识别优化的文本管线。
- **断网不丢段（核心 bug 修复）**：转写或大模型失败的段落进本地队列，按 1s / 3s / 10s 退避重试，上课页显示虚线占位卡「⟳ 网络断了，这段稍后补上」，不再显示 connection closed。
- **插件框架新增的通用钩子**：规则步骤 `split` / `hotwords` / `lexicon` / `spacing`；`hooks`（`capture.vad`、`segment.refine`、`voice.render`、`senior.render`、`page.summary`）；提示词追加（`llm` 里 `"segment+"`）；`Segment.ext` 存插件数据，旧课没有它照常显示。
- **没做的**：「▶ 回听」——App 目前不保存上课录音（分段识别完即删除），没有可复用的按时间戳跳播能力，所以提问气泡里没放这个按钮。VAD 用的是能量 + 过零率的轻量实现，没有上 sherpa-onnx Silero VAD：插件按设计只能是声明式资源包（不允许 .so），而 sherpa-onnx 需要随 App 打包每个 ABI 数 MB 的原生库，等于改核心。

**借鉴的开源项目（代码注释里标了落点）**：[WhisperX](https://github.com/m-bain/whisperX)（BSD-2，按时间戳重切句 → 句子时间估算）、[FunASR](https://github.com/modelscope/FunASR)（MIT，VAD + 标点 + 时间戳流水线、ct-punc 分句）、[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)（Apache-2.0，按停顿切段）、[CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline)（MIT，拼音模糊热词替换、中英文间距）、[wtpsplit](https://github.com/segment-any-text/wtpsplit)（MIT，长句切分规则）；拼音表由 [pypinyin](https://github.com/mozillazg/python-pinyin)（MIT）生成；精炼提示词的口语 → 书面示例参考 FormalASR 的样本思路。

## v0.0.7.9 更新

- **版本升级**：升级版本号至 v0.0.7.9（versionCode 17），运行时兼容性矩阵平滑演进。
- **智能复习「艾宾浩斯记忆流」与期末考点冲刺包**：基于遗忘曲线智能编排每日复习卡，一键汇聚全课考点标签生成考前冲刺手册，支持长图/Markdown导出。
- **课堂声画对照「时间戳划线跳播」与师生互动分离**：重点笔记锚定录音毫秒时间戳，轻触即刻调出迷你胶囊回听老师原声；提问与答疑自动归纳为对话气泡。
- **插件生态扩充**：官方示范插件新增「理工科学长 · LaTeX 公式与代码块高亮」技能插件，以及「暗夜星芒 (OLED 极夜黑)」与「极简手账 (Muji 风格)」双新主题。
- **桌面微拟态小组件 (Glance AppWidget)**：手机主屏幕直击今日课表、倒计时提醒与每日考点背诵卡，支持桌面一键闪电上课录音。

## v0.0.7.8 更新

- **修复翻书阅读长章节截断（动态多页流式排版）**：彻底解决长章节因物理高度超限被屏幕底端截断、翻页直接跳至下一节导致内容丢失的严重问题。引入基于行高测量的动态分页算法（ReaderPaging），文字按行平滑跨页，续页顶部展示「节标题（续）」，图片公式整块后移，考点标签防孤行，目录与刻度进度条全量联动。
- **全新声明式插件系统与扩展架构**：App 保持轻量核心，个性化与进阶需求 DLC 化。采用声明式配置（plugin.json）+ 资源包 + 自研文本规则 DSL，无需执行外部原生代码，即开即用、一键禁用、失败安全回滚。扩展点覆盖水墨/自定义主题、封面包、开屏动画、转录清洗规则及大模型供应商。
- **三大官方示范插件就绪**：
  - **识别优化**：端侧轻量清洗（语气助词过滤、口吃去重、口误锚点自纠）+ 考点雷达与大模型要点提炼，告别转录磨叽流水账；
  - **墨韵江南**：宣纸纹理微底色、焦墨排版、霞鹜文楷书法字体、毛笔墨痕进度条、朱砂印章按钮与水墨晕染开屏动画；
  - **供应商兼容**：支持导入任意 OpenAI 兼容、Anthropic、Gemini 协议接口与本地自建 Ollama；API Key 本地硬件 Keystore 加密隔离，绝不随插件与备份导出。
- **去中心化插件市场**：支持通过仓库 plugins 独立分支或 Gitee 镜像免服务器订阅与安装插件。
