# Localis

> **本地 AI，不只是聊天。** 在 Android 手机上运行 GGUF 模型，并把本地推理、视觉理解、联网搜索、模型管理、智能推荐、对话记录、记忆与隐私会话集中在一个应用中。

[![Build APK](https://github.com/p8735489-prog/Localis/actions/workflows/build-apk.yml/badge.svg)](https://github.com/p8735489-prog/Localis/actions/workflows/build-apk.yml)
[![GitHub](https://img.shields.io/badge/GitHub-Localis-181717?logo=github)](https://github.com/p8735489-prog/Localis)

## ✨ Localis 能做什么？

### 🧠 本地 GGUF AI

- 在 Android 设备本地运行 GGUF 模型
- 根据底层推理后端支持情况适配模型架构
- 支持不同 GGUF 量化版本
- CPU / GPU / NPU / Auto 后端选择（实际能力取决于设备和后端）
- 流式生成
- Context、Temperature、Top-P、Top-K、Repeat Penalty、Max Tokens 等参数调节
- Memory Mapping、Memory Lock、KV Cache、线程数和 Offload 等高级选项
- 自动加载、预热和卸载模型，降低长期运行的内存压力

### 👁️ 图片与多模态

- 直接上传图片进行分析
- 支持 Vision GGUF
- 支持 LLM + Vision Projector / mmproj 等多模块模型
- 自动检测模型组件和兼容性
- 图片输入与文字对话结合
- 内存不足时进行资源控制，降低图片推理导致 OOM 的风险

### 🌐 联网搜索

Localis 可以把本地模型与互联网搜索结合：

- 关闭搜索
- 智能搜索
- 始终搜索
- 深度搜索
- 自动生成搜索关键词
- 多轮搜索与结果整理
- 搜索结果去重、筛选和排序
- 保留真实来源、标题和 URL

支持多种搜索提供商：

- SearXNG
- Brave Search
- Bing
- 自定义搜索 API

### 📦 模型中心

不用手动寻找每一个模型文件：

- Hugging Face 模型搜索
- 清华镜像模型下载
- GGUF 文件发现
- 模型详情查看
- Q4 / Q5 / Q6 / Q8 等量化选择
- 暂停、继续、取消下载
- 断点续传
- 下载失败重试
- 模型完整性校验
- 本地模型导入、删除和管理
- 模型收藏与最近使用

多模块模型可以作为完整 Model Bundle 管理，避免用户手动寻找不匹配的组件。

### ⭐ 智能模型推荐

Localis 不只看“模型文件有多大”，而是结合当前设备进行推荐。

会综合考虑：

| 项目 | 参考因素 |
|---|---|
| 设备 | RAM、CPU、GPU、NPU、可用内存 |
| 模型 | 参数量、GGUF 大小、量化、架构 |
| 推理 | Context、KV Cache、Runtime 开销 |
| 多模态 | Vision Projector / mmproj |

推荐结果会显示：

- ★★★★★ 强烈推荐
- ★★★★☆ 推荐
- ★★★☆☆ 勉强
- ❌ 不推荐

评分和推荐应根据设备与模型实际情况动态计算，而不是固定给所有手机相同结果。

### 📊 Benchmark 与模型对比

可以测试已安装模型的实际表现，包括：

- Prompt Processing
- Generation Speed
- First Token Latency
- RAM 峰值
- 当前量化与推理后端

支持多个模型进行横向比较，帮助用户选择更适合自己设备的模型。

### 🤖 Localis Auto

开启 Auto 后，Localis 会根据当前任务选择已有能力：

```text
文字问题 → 文本模型
图片问题 → Vision 模型
需要实时信息 → 联网搜索
任务结束 → 自动释放闲置模型
```

高级用户也可以关闭 Auto，手动控制模型和推理参数。

### 💬 对话记录

普通会话支持本地历史记录：

- 新建会话
- 自动保存
- 继续历史会话
- 重命名
- 删除
- 置顶
- 搜索
- 导出
- 保存使用的模型、图片和搜索来源

切换模型不会导致历史会话消失。

### 🧠 记忆系统

记忆系统可以独立开启或关闭：

**开启：**
- 使用相关长期记忆
- 创建新的长期记忆
- 生成会话摘要

**关闭：**
- 不读取已有长期记忆
- 不创建新的长期记忆
- 不生成新的记忆摘要
- 不删除已有记忆

提供记忆中心，可以查看、编辑、删除和清空记忆。相关记忆才会进入当前 Context，不会无差别加载全部记忆。

### 🔒 隐私会话

需要临时、尽可能不留痕迹的对话时，可以开启：

**🔒 隐私会话**

隐私会话优先级高于普通记忆设置。开启后：

- 不保存对话记录
- 不读取长期记忆
- 不写入长期记忆
- 不生成会话摘要
- 不保存搜索历史
- 不保存搜索缓存
- 不保存图片缓存
- 会话结束后清理临时数据

退出时提示：

> 隐私会话已结束，本次会话数据已清理。

### 🗂️ 本地数据控制

Localis 采用 **local-first** 思路：聊天、记忆和设置默认保存在设备本地。

提供“删除所有本地数据”，方便用户主动清理应用产生的数据。

联网搜索属于可选能力；开启搜索时，搜索请求会发送给所选择的搜索服务。

## 🚀 快速开始

1. 从 [Releases](https://github.com/p8735489-prog/Localis/releases) 获取 APK。
2. 安装并打开 Localis。
3. 进入模型中心。
4. 搜索或导入兼容的 GGUF 模型。
5. 下载模型并开始本地对话。
6. 根据需要开启图片、联网搜索、记忆或 Auto Mode。

> 大模型需要较多存储空间和 RAM。实际运行效果取决于手机硬件、模型架构、量化方式和推理后端。

## 📱 模型兼容性

Localis 的目标是支持**底层推理后端能够运行的 GGUF 架构**，而不是把模型名称写死成少数几个品牌。

实际兼容性取决于：

- GGUF architecture
- tokenizer / chat template
- 推理后端
- Android 设备
- 多模态所需组件

Vision 模型可能需要对应的 Projector / mmproj。Localis 会进行兼容性检查；不支持的组合不会强行加载。

## ⚠️ 注意事项

- GPU/NPU 加速是否可用取决于设备和后端支持。
- 更大的模型通常需要更多 RAM 和存储空间。
- GGUF 文件大小不等于实际运行所需 RAM。
- Vision 模型通常需要额外组件和内存。
- 联网搜索需要网络连接。
- Benchmark 结果会因设备、温度、后台任务和推理参数而变化。

## 🔐 签名与安全

**发布签名密钥不会存放在 Git 仓库中。**

GitHub Actions 使用 GitHub Secrets 提供发布签名所需的 keystore 和密码。需要配置签名时，请参阅 [`SIGNING.md`](SIGNING.md)。

不要把 `.jks`、`.keystore`、密码或其他私密凭据提交到 Git。

## 📥 开源与反馈

- 项目主页：[github.com/p8735489-prog/Localis](https://github.com/p8735489-prog/Localis)
- 问题反馈：[Issues](https://github.com/p8735489-prog/Localis/issues)
- APK：[Releases](https://github.com/p8735489-prog/Localis/releases)

欢迎提交 Issue 和 Pull Request。

## 📄 License

请以仓库中的 `LICENSE` 文件为准。如果仓库尚未包含 LICENSE，请不要将项目默认视为采用某种开源许可证。

---

**Localis — 把 AI、模型、搜索和隐私控制带到你的设备上。**
