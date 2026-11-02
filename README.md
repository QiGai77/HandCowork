# 课堂学长官方插件市场 (Plugins Market)

欢迎来到《课堂学长》官方插件分支！这里提供官方和社区贡献的各类扩展 DLC，无需编译，即装即用。

## 📦 当前精选插件

1. **识别优化与考点萃取 (`skill-speech-refine`)**
   - 针对课堂录音的规则清洗 DSL + LLM 考点提炼，告别转录流水账。
2. **墨韵江南 (`theme-moyun-jiangnan`)**
   - 宣纸底色、霞鹜文楷、毛笔墨迹进度条、朱砂印章按钮与水墨晕染开屏动画。
3. **大模型供应商兼容中心 (`provider-universal-hub`)**
   - 预设 DeepSeek、Kimi、硅基流动、Ollama 等常用厂商与接口配置。

## 🛠️ 如何为课堂学长开发插件？

插件采用声明式结构，根目录包含 `plugin.json`：
```json
{
  "id": "my-plugin",
  "name": "我的插件",
  "version": "1.0.0",
  "types": ["theme"],
  "apiVersion": 1,
  "minApp": "0.0.7.8"
}
```
打包成 `.xzplugin`（标准的 zip 压缩包）并提交 PR 到本分支即可！
