# Localis 2.1.0

![Localis 应用图标](docs/assets/app-icon.png)

> **本地 AI，不只是聊天。**

Localis 是一个面向 Android 的本地 AI 应用：以 llama.cpp + GGML 为核心，在设备上运行 GGUF 模型，并把模型管理、联网搜索、长期记忆、视觉模型、隐私控制与 Material 3 UI 集中到一个应用中。

## 技术栈

- **Android / Kotlin / Jetpack Compose**：应用界面与交互。
- **Material 3**：主题、动态色、组件与动画。
- **llama.cpp + GGML**：GGUF 模型加载、tokenization、推理与采样。
- **JNI / C++17**：连接 Kotlin 与本地推理引擎。
- **DataStore**：本地设置、记忆等数据持久化。
- **OkHttp / SearXNG / Brave / Bing / 自定义 API**：可选联网搜索。
- **Tor Android**：可选 Tor 网络路由与 Bridge。

## 2.1.0 重点更新

### 硬件优化

- 识别 **MediaTek Dimensity** 平台，并在性能页面给出 MediaTek APU 能力提示。
- 识别 **Google Tensor** 平台；只有明确检测到 Google Tensor 时才显示 TPU 入口提示。其他 Android 芯片不会被误标为 TPU。
- Qualcomm Snapdragon、MediaTek Dimensity、Google Tensor 均优先走 Android/ARM 安全的 CPU 指令集检测，不强制使用可能导致旧设备崩溃的 `-march=native`。
- 当前 v2.1.0 的 llama.cpp Android Native bridge 仍以 CPU backend 为可靠基线；设备加速能力会被检测并展示，但不会把未实际接入的 APU/TPU 冒充成可用推理后端。

### GGUF / llama.cpp

Localis 不按模型名称维护小白名单，而是让内置 llama.cpp 的模型架构和 GGUF loader 决定兼容范围。实际支持取决于当前 llama.cpp 源码、GGUF 架构、量化、设备内存以及多模态组件。

### 记忆系统

2.1.0 将简单的字符串包含搜索升级为本地记忆检索：

1. 精确短语匹配
2. 中文二元词与英文词 token 重叠
3. 主题 / 标签匹配
4. 最近访问加权
5. 重要度加权
6. 访问频率加权
7. 置顶记忆优先

同时提供轻量预设：**全部 / 偏好 / 项目 / 设备 / 最近**。

记忆不会被发送到远程搜索服务；检索在设备本地完成。为了让重要记忆更持久，重复命中的记忆会提升访问计数和重要度，并保留创建、更新时间和最后访问时间。

> 这是“像 GPT 一样按当前问题主动召回相关记忆”的本地实现，而不是把所有历史内容无差别塞进 Context。它不依赖云端 embedding，因此更轻量、可控、隐私性更强。

### 搜索

搜索框支持更简单的一次调用模式，并在 Repository 层统一完成：

- 查询清理与空查询保护
- 去重
- 低质量来源过滤
- 标题 / 摘要相关性排序
- 权威域名轻量加权
- 最大结果数限制

同时修复了一个逻辑问题：搜索结果处理器已经计算出的排序结果此前没有真正返回给上层，现在会正确返回处理后的结果。

## 作者与开源

**作者：Shirakawa**

- GitHub：https://github.com/p8735489-prog
- 项目仓库：https://github.com/p8735489-prog/Localis

## 相关技术

- llama.cpp：https://github.com/ggml-org/llama.cpp
- GGML：https://github.com/ggml-org/ggml
- Tor Project：https://www.torproject.org/

## 隐私

Localis 的本地聊天、记忆与设置以 local-first 为设计目标。联网搜索、模型下载、Tor 等网络能力均属于可选功能。开启联网能力时，相应请求会发送给用户选择的服务或节点。

## 版本

**2.1.0**

Android `versionName` 已固定为 `2.1.0`，About 页面通过 `BuildConfig.VERSION_NAME` 显示版本，因此正式构建的关于页面应显示 **v2.1.0**。

## License

请以仓库中的 `LICENSE` 文件为准。
