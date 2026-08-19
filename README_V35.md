# Localis v35 — 推理后端路由与流畅手势重构

## 本次核心变化

### 推理引擎架构
- llama.cpp：GGUF 主力、兼容性优先。
- MNN：预留可选 Native Adapter；只有真正打包并通过能力检测才会启用。
- Cactus：预留独立 Native Adapter；不把非 GGUF runtime 伪装成 GGUF loader。
- `AUTO` 对 GGUF 默认选择 llama.cpp。

### 手势与转场
- 左边缘向右滑：页面向右退出。
- 右边缘向左滑：页面向左退出。
- 只在边缘区域接管手势，避免抢占中间区域的滚动、输入和模型详情手势。
- 使用 cubic-bezier 运动曲线 + 跟手位移 + 微缩放。
- 达到阈值后顺势退出，否则平滑回弹。
- NavHost 外层使用 Material 3 background，避免页面切出时露出黑色 Window background。
- Enter / pop transition 使用不同方向和缓动曲线。

## 真实性说明

本版本没有把 MNN/Cactus 的“适配层”冒充成已经打包的完整推理后端。当前 APK 的 GGUF 路径仍由 llama.cpp 负责；MNN/Cactus 只有在后续 Native runtime 实际加入并通过 capability probe 后才能进入自动路由。
