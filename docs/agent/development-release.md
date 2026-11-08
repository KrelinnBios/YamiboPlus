# YamiboPlus · 本地构建、运行、CI、签名和发布

本文件是本仓库的专项维护规则。根目录 [AGENTS.md](../../AGENTS.md) 的项目状态与全局边界继续有效。

## 本地运行与验证

Windows 常用命令：

```powershell
.\gradlew.bat compileDebugKotlin   # Kotlin 改动后的最低检查，必须执行
.\gradlew.bat testDebugUnitTest    # 运行本地单元测试
.\gradlew.bat assembleDebug        # 生成 app\build\outputs\apk\debug\300-Plus.apk
.\gradlew.bat clean assembleDebug  # 增量构建损坏时使用
```

- 修改 Kotlin 后至少运行 `compileDebugKotlin`。
- 修改 URL 归一化、Cookie、会话、HTML 解析、阅读器返回链接、图片加载策略、应用更新解析、帖子链接识别（`YamiboPostLinkUtil`）或屏蔽数据（`ForumBlocklistManager`）时，应补充或更新 `app/src/test` 下的对应单元测试，并运行 `testDebugUnitTest`。
- 纯文档、图片或资源说明修改无需运行 Gradle 构建。
- 增量构建出现 `TaskOutputsBackup`、`IOException` 等缓存损坏时再使用 `clean assembleDebug`，不要把 `clean` 当默认步骤。
- 本地通常没有连接设备或模拟器，无法完整运行 instrumented test 或 adb；UI、WebView 生命周期、网络恢复和系统安装行为仍需要实机确认。
- 间歇性或网络相关 bug 无法在本机稳定复现时，修改后明确说明需要用户实测，不直接宣称“已修复”。
