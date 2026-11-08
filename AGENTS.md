# AGENTS.md

本文件适用于整个仓库，作为 AI 编码代理修改本项目时的项目规范。除非用户另有要求，使用中文回复和说明。

## 项目概览

- **YamiboPlus（300 Plus）**：面向百合会论坛（bbs.yamibo.com）的非官方 Android 阅读客户端，基于 [prprbell/YamiboReaderPro](https://github.com/prprbell/YamiboReaderPro) 修改而来。
- Kotlin + Jetpack Compose + Material 3；minSdk 24 / targetSdk 34 / compileSdk 34 / JDK 17。
- 包名 `org.shirakawatyu.yamibo.novel`，applicationId `com.krelinnbios.yamiboplus`。
- 仅构建 `arm64-v8a` 和 `armeabi-v7a`；APK 固定命名为 `300-Plus.apk`。
- 应用内更新绑定 GitHub Releases（KrelinnBios/YamiboPlus）。

## 开始任务前

- 先读取与任务直接相关的实现、调用链和已有测试，不根据文件名或界面表象猜测根因。
- 优先沿用现有类、状态流、Repository 和工具函数，以最小 diff 完成明确需求。
- 修 UI、WebView 注入或 CSS 前先获取真实页面样本；没有对应 HTML 时不要凭空猜选择器。
- 涉及网络、解析、缓存、目录、更新或论坛行为时，先检查本文件“已确立的决定”中是否已有历史约束。
- 先检查工作区已有修改；无关修改不要清理、回滚或格式化。
- 不把一次局部修复扩展成架构清理、依赖升级、全局格式化或无关功能调整。

## 修改原则

- 优先沿用现有类、状态流和工具函数，不在 Composable 中复制网络、缓存或持久化逻辑。
- 修复问题时先确认实际调用链和已有测试，不根据文件名或界面表现直接猜根因。
- 不顺手升级 Gradle、AGP、Kotlin、Compose 或网络依赖；依赖升级必须是明确任务，并单独验证兼容性。
- 不提交构建产物、签名材料、`.env`、`local.properties` 或临时抓取页面。
- 文档和中文源文件统一使用 UTF-8，避免 PowerShell 默认编码造成乱码。

## 专项规范导航

原有项目特有约束已按主题迁移到以下**仓库内部**文件，相关任务必须先阅读对应专题；根目录的项目状态、安全边界和提交规则仍对整个仓库有效。目录级 `AGENTS.md` 只作局部导航，不覆盖根规则。

- [features ](docs/agent/features.md)：功能范围和受支持的平台行为。
- [architecture ](docs/agent/architecture.md)：页面、WebView、阅读器、数据库、网络与后台功能架构。
- [decisions ](docs/agent/decisions.md)：历史兼容、网络、深色模式、阅读器、链接与崩溃恢复的已验证决策。
- [development-release ](docs/agent/development-release.md)：本地构建、运行、CI、签名和发布。

**跨目录注意：** 修改网络、缓存、WebView、暗色模式或阅读器交互前，必须核对 [历史决定](docs/agent/decisions.md)，不得用常规重构覆盖经过兼容性验证的行为。

## 文档同步

- `README.md` 面向项目使用者；`AGENTS.md` 只记录编码代理修改代码、验证行为和发布时需要遵守的项目规则。
- README 结构固定为：项目简介 → 功能概览 → 界面预览 → 使用方式 → 数据与安全 → 内容边界 → 许可协议 → 反馈与贡献，与 NeoDBLite 保持一致；不设“技术信息”小节。
- README 功能列表优先使用简短标签的扁平格式，不加粗标签，不强制四字。
- README 简介只保留项目定位和必要范围；不添加默认成立的功能范围套话，也不重复数据处理说明。
- 顶部图标统一使用仓库根目录下的 `icon/icon.svg`。
- 修改公开功能、数据处理、发布方式、APK 名称或安装要求时，检查 README 是否需要同步。

## 提交约定

- 一个提交聚焦一个明确主题，不把功能修改、重构、依赖升级和文档清理混在一起。
- 提交信息沿用简短中文风格，例如 `更新 XXX.kt`。
- 不提交构建产物、签名材料、本地环境文件、临时抓取页面或与任务无关的生成物。
- 工作区已有其他修改时，只处理并提交本次任务涉及的内容。