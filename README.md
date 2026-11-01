<div align="center">

<img src="https://img.shields.io/badge/课堂学长-学-111?style=for-the-badge" alt="课堂学长" />

# 课堂学长

**把一节课，变成一份看得懂的笔记。**

录音实时转写，AI 学长按课件逐页讲解，图表能拖，题目能做。

![version](https://img.shields.io/badge/version-0.0.8-2E8B6A)
![android](https://img.shields.io/badge/Android-8.0%2B-34C759)
![compose](https://img.shields.io/badge/Jetpack-Compose-4285F4)
![status](https://img.shields.io/badge/状态-测试版-FF9500)

</div>

---

## 能做什么

| | |
|---|---|
| **上课** | 录音分段转写，「原声 / 学长」两种模式，上下文补全，灵动岛实时状态 |
| **笔记** | 按课件页图文讲解，彩色标注概念、考点、易错，图表可拖、题目可做 |
| **课程** | 拍一张课程表，看图模型一键导入整学期；首页只看今天的课 |
| **课件** | 导入 PPT / PDF / B 站录播课，实时进度条 + 灵动岛，还有彩蛋 |
| **模型** | SiliconFlow、OpenAI、阿里云百炼、火山、DeepSeek、Claude 等；支持 ChatGPT / Claude 账号登录，并查看 5 小时与周额度 |
| **同步** | Google Drive 连接器（OAuth 授权、可续传上传、文件夹与上传状态）、手机云盘（系统文件选择器）、WebDAV |
| **分享** | 笔记导出 DOCX、PDF、Markdown 或长图 |

## v0.0.8 更新 · 重构版 v0.0.1Ex

按已批准的「重构版 v0.0.1Ex」设计稿（`design/redesign-v0.0.1Ex/`，28 个页面 + 交互热区指引）重做整套界面：

- **新视觉**：象牙白 → 淡薰衣草渐变底 + 纸张点阵纹理，衬线标题，明亮的白色液态玻璃卡片，玉绿色强调，每门课有自己的科目色；底部玻璃导航栏 + 独立圆形搜索键
- **01 课程表首页**：液态玻璃周课表（节次 × 星期），红色 now 线、「正在上」标记，点课程块进入课程详情；右上「拍照导入」和「＋新建」
- **04 课程详情**：精装封面卡、课前资料（课件 / B 站 / 录音）、这门课的笔记书脊（点开即按课程筛选的书架）、下次上课倒计时、编辑课程 / 更换封面（AI）/ 红色开始上课
- **底部导航栏可自定义**：我的 › 底部导航栏，长按拖动排序，第一个就是启动页（例如把「上课」放第一）；顺序持久保存
- **后台 AI 任务不丢**：所有长任务改到应用级协程作用域，离开页面继续跑；灵动岛点按回到发起页（生成页 / 导入页 / 问学长），完成后轻点查看，失败轻点重试；两个任务同时进行时并排显示
- **灵动岛图片候选**：AI 导图一次出多张，停在灵动岛里预览，选一张「应用」或「丢弃」；概念图页也能选
- **09–12 上课 → 下课 → 生成**：上课页新底栏（标记考点 / 波形 / 暂停 / 红色停止）；课件全屏支持「跟随老师翻页」和底部胶片条选页；下课页显示录制统计，选简单 / 详细、复习卡 / AI 导图开关后再生成；生成页可「后台运行」
- **13–16 笔记**：书架 / 列表两种模式（列表带课程筛选和生成进度）；翻书阅读改为精装书样式，底部进度条可拖动快速跳页（拖动时显示缩略气泡），底栏 目录 / 复习卡 / 导图；笔记详情顶部「翻书模式 / 分享」，底栏「问学长 / 复习卡 / 导图」
- **17–20**：概念图独立页（AI 导图 | 可拖动，缩放、重新生成、重新排布）；复习卡独立页；问学长改为整页流式对话，基于笔记 + 课件，可追问（举个例子 / 出一道题 …）；分享导出页（长图 / PDF / DOCX / Markdown，可选是否包含复习卡）
- **21 全局搜索**：笔记、课件页、课堂转写、复习卡分类结果，关键词高亮，最近搜索；课件页结果直接打开全屏课件
- **22–27**：我的页显示 Google Drive 状态、模型卡进入「模型与供应商」整页；新增 Google 授权页（drive.file 说明 + 用 Google 账号继续）；课表识别失败卡可「换模型重试」「查看模型原始回复」，识别结果可改课程名、缺地点 / 时间的条目标「不确定」
- **08 录播导入**：大圆环进度（已耗时 / 预计剩余）、「深度选取 简单 / 详细」、「后台运行」

### 修复

- 概念图「没有返回图片」（ChatGPT 账号 gpt-image）：强制调用 image_generation 工具；兼容 output_item / response.completed / partial_image / data URL / markdown 链接等返回格式；仍然没有图时把原始回复（base64 打码）写入日志
- 「rememberCoroutineScope left the composition」：概念图、课表识别、AI 封面、问学长、公式讲解、Drive 同步、课件解析等长任务全部改用应用级作用域
- B 站音轨下载 403（如 BV1vC9EB5Epe P12）：带 `Referer: https://www.bilibili.com`、桌面 UA、Range；依次尝试 baseUrl、backupUrl、官方 upos 镜像（PCDN/MCDN 地址自动替换），每条重试；仍失败换 html5 mp4 线路；界面提示「已自动切换备用线路」
- 短课笔记「0 张卡」：复习卡宽松解析（q/question/front…），不足 3 张时单独再请求一次模型，仍不够就从助记、概念节点、小节本地生成

## v0.0.7 更新

- **Google Drive 连接器**：我的 › 云同步 › 连接 Google Drive。浏览器 / Custom Tab 里完成 Google 授权（PKCE，权限只有 `drive.file`），返回后显示已连接的账号；可查看 Drive 里「课堂学长备份」文件夹的内容、每个文件的上传状态（上传中百分比 / 已上传 / 未变化 / 失败、大小、时间），手动「立即同步」，从 Drive 上的备份恢复。上传走 Drive REST v3 可续传上传（1 MiB 分块，断网自动查询偏移续传），内容没变的文件按 md5 跳过。原有系统文件选择器和 WebDAV 同步保留。令牌用 Android Keystore 加密，不写日志（日志会把 `ya29.` / `1//` / `GOCSPX-` / `code=` 打码）
- **统一 AI 进度**：整理笔记、提炼考点、课表识别、课件解析、AI 导图、录播课导入、问学长、公式讲解、AI 封面，全部有屏幕内进度条（步骤 + 百分比 + 已用时）、灵动岛进度胶囊和轮播的「小彩蛋」提示；没有真实进度时按预计耗时渐近估算，进度条带流光，不会卡住不动；流式输出按已生成字数推进
- **笔记书架**：笔记库按科目分层，每门课一层书架，每节笔记是一本书（书脊竖排标题、日期、按科目配色）；点开后左右翻页阅读（以书脊为轴的 3D 翻页 + 阴影），页面内容可上下滚动，公式、图表、选择题等交互组件照常可用
- **上课页卡组**：课程叠成卡组，左右滑动选课（循环），默认选中今天下一节课；下方「资料上传」（课前资料：PPT/PDF、B 站 BV 号 / 链接、本地音频 / 视频）和红色大按钮「开始上课」
- **误触作废**：录音不足 30 秒视为误触，不保存这节课、不生成笔记，提示「录制不足30秒，已作废」，并记入日志
- **录播视频配原画面**：B 站和本地视频导入的笔记，每节按时间点配视频原画面截图（本地视频用 MediaMetadataRetriever 截帧，B 站用官方 videoshot 截图），不使用 AI 生成图；B 站来源可在笔记里点「换一张」从前后约 1 分钟的截图中挑选
- B 站 / 本地录播课导入并入「课前资料」流程（导入时直接放进当前课程）
- OpenAI / ChatGPT 用户：笔记生成后自动用 `gpt-image-2.5-flare` 画 AI 导图
- 课件页面加载时显示微光占位
- 云同步备份包含录播课截图

## v0.0.6 更新

- 修复课表照片总是「没认出来」：「看图」模型与服务商不匹配时自动改用对话模型、接口报错自动回退；解析兼容推理段、代码块、前后废话、嵌套结构和尾逗号；失败时显示模型原话或 HTTP 错误
- 修复真实笔记里交互组件渲染失败：LaTeX 单反斜杠（`\frac` 被 JSON 吞成控制字符）、一行写法的代码块、正文裸 JSON、字母答案、对象形式的表格行都能识别；认不出的退回文字
- 整理笔记：分步进度条 + 百分比 + 实时字数 / 耗时 / 正在写的小节；长课分段生成再合并；可选简单 / 详细
- 提示词要求逐页逐段完整覆盖；设置里可预览、修改、恢复默认每项任务的系统提示词
- 学长模式跳过无关闲聊，原声模式折叠淡化
- 导入手机录音 / 本地音频（m4a、mp3、aac、amr、wav、ogg、opus）当录播课；B 站视频有 CC / AI 字幕时直接使用
- 上课页顶部显示当前课件页原图预览，点开全屏；笔记配图为课件原页（磁盘缓存、高分辨率）
- 复习卡改为叠放卡组：左右滑、点按翻面、计数、重来
- 概念图：OpenAI / ChatGPT 账号用 `gpt-image-2.5-flare` 直接画导图；其他服务商可长按拖动节点并按笔记保存位置
- 我的 › 日志：记录导入、模型调用（服务商 / 模型 / 状态 / 耗时）、笔记生成、错误堆栈，可筛选错误、复制、分享；不记录任何密钥
- ChatGPT 账号模型列表从上游 `codex/models` 实时获取，内置列表更新为 gpt-6 系列；修复上课页「上下文补全」标签挤压文字

## v0.0.5 更新

- 公告改为实体卡片
- 课程表导入改用看图模型识别，预览含老师和周次
- 新增 ChatGPT 账号登录、Claude 供应商，显示 5h / 周额度
- 新增 DeepSeek 官方接口（`deepseek-flash` 可看图）
- OpenAI 默认 `gpt-6.1-sol`，画图默认 `gpt-image-2.5-flare`
- 「看图」「画图」改为液态玻璃选择器
- 开关重新设计
- 云同步上线，笔记可分享为 DOCX / PDF / MD / 图片
- 课件导入实时进度与彩蛋
- 修复拍照导入课程表闪退

## 快速开始

1. 在 [Releases](../../releases) 下载最新 APK 安装
2. 「我的 › 大模型」选择供应商，填 Key 或登录账号
3. 「课程」拍一张课程表导入
4. 「上课」开始录音，课后在「笔记」里看学长讲解

<details>
<summary><b>ChatGPT / Claude 账号登录说明</b></summary>

- **ChatGPT**：登录时浏览器会回跳 `http://localhost:1455`，请保持课堂学长在后台运行，直到页面显示「登录成功」。部分省电策略会杀后台，导致登录失败。
- **Claude**：授权后复制页面上的 code，粘贴回 App。
- 两者使用与 Codex / Claude Code 相同的非公开接口，可能随时变化。

</details>

<details>
<summary><b>Google Drive 一次性设置（开发者）</b></summary>

Google 已不允许「Android」类型的 OAuth 客户端在浏览器里用自定义 scheme 回跳，所以课堂学长用 Google 官方支持的「桌面应用」流程：浏览器 / Custom Tab 授权 + PKCE + 回调到手机本机 `http://127.0.0.1:随机端口`（由 App 内的临时小服务接住）。不需要 Google Play 服务。

1. 打开 [Google Cloud 控制台](https://console.cloud.google.com/)，新建或选择一个项目
2. 「API 和服务 › 库」搜索 **Google Drive API**，启用
3. 「Google Auth Platform」配置品牌塑造（应用名「课堂学长」、支持邮箱）；受众群体选「外部」，测试阶段把要用的 Google 账号加入 **测试用户**（未发布的应用只有测试用户能授权，测试状态下刷新令牌 7 天过期，到期后重新连接即可；正式发布后不限）
4. 「数据访问」添加范围 `https://www.googleapis.com/auth/drive.file`（非敏感范围，无需 Google 审核）
5. 「客户端 › 创建客户端」，应用类型选 **桌面应用**，得到客户端 ID（`…apps.googleusercontent.com`）和客户端密钥（`GOCSPX-…`，Google 视桌面客户端为公开客户端，这个密钥不算机密）
6. 两种填法任选：
   - App 里：我的 › 云同步 › Google Drive › 「Google Drive 设置说明」，粘贴客户端 ID 和密钥，保存
   - 编译时：`gradle assembleRelease -PgoogleClientId=xxx.apps.googleusercontent.com -PgoogleClientSecret=GOCSPX-xxx`（写进 `BuildConfig`，App 里留空时使用）
7. 点「连接 Google Drive」→ 浏览器里选账号、勾选权限 → 页面显示「授权成功」后点「返回课堂学长」

签名证书（`android/xuezhang.jks`）指纹，以后若改用 Android 类型客户端 / Google Play 服务登录时需要：

```
SHA-1:   86:20:1A:BD:19:F4:17:DC:5E:C4:A2:38:5E:17:80:9E:6E:DB:82:7E
SHA-256: F8:B8:B4:71:04:44:C2:11:77:86:11:48:D8:2D:B9:EF:08:12:7B:2C:F4:B4:59:C5:AC:F0:B1:03:47:C1:CE:4F
包名:    app.xuezhang
```

</details>

## 构建

```bash
cd android
gradle assembleRelease   # 或直接用 Android Studio 打开 android/
# 可选：-PgoogleClientId=… -PgoogleClientSecret=…（Google Drive 云同步）
```

环境：AGP 9.3 · Gradle 9.8 · compileSdk 37 · minSdk 26 · Kotlin + Jetpack Compose

## 隐私

录音与笔记只保存在本机；API Key 只发给你填写的接口，账号登录凭据（含 Google Drive 令牌）用 Android Keystore 加密，两者都不进入云同步备份，也不写进日志。Google Drive 只申请 `drive.file` 权限，只能访问本应用创建的文件。

## 致谢

液态玻璃组件改编自 [Kyant](https://github.com/Kyant0) 的实现，许可见 `android/app/src/main/java/app/xuezhang/glass/LICENSE-kyant.txt`。

<sub>私有仓库 · 测试版 · © Qi Gai</sub>
