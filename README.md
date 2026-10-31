<div align="center">

<img src="https://img.shields.io/badge/课堂学长-学-111?style=for-the-badge" alt="课堂学长" />

# 课堂学长

**把一节课，变成一份看得懂的笔记。**

录音实时转写，AI 学长按课件逐页讲解，图表能拖，题目能做。

![version](https://img.shields.io/badge/version-0.0.5-0A84FF)
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
| **同步** | 备份到 Google Drive、手机云盘（系统文件选择器）或 WebDAV |
| **分享** | 笔记导出 DOCX、PDF、Markdown 或长图 |

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

## 构建

```bash
cd android
gradle assembleRelease   # 或直接用 Android Studio 打开 android/
```

环境：AGP 9.3 · Gradle 9.8 · compileSdk 37 · minSdk 26 · Kotlin + Jetpack Compose

## 隐私

录音与笔记只保存在本机；API Key 只发给你填写的接口，账号登录凭据用 Android Keystore 加密，两者都不进入云同步备份。

## 致谢

液态玻璃组件改编自 [Kyant](https://github.com/Kyant0) 的实现，许可见 `android/app/src/main/java/app/xuezhang/glass/LICENSE-kyant.txt`。

<sub>私有仓库 · 测试版 · © Qi Gai</sub>
