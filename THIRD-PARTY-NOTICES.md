# 第三方软件、内容与服务说明

本文件补充 [LICENSE](./LICENSE) 和 [NOTICE](./NOTICE)，用于说明 YamiboPlus 所使用、但不由本项目 AGPL-3.0 重新授权的上游代码、第三方软件、内容、数据和外部服务。具体组件、文件或内容旁如有更明确的权利声明，以该声明为准。

## 上游代码与归属

本项目包含来自下列上游项目及其贡献者、并按 GNU AGPL-3.0 发布的代码：

- [prprbell/YamiboReaderPro](https://github.com/prprbell/YamiboReaderPro)
- [flben233/YamiboReader](https://github.com/flben233/YamiboReader)

著作权归属、本项目修改范围和许可证指引见 [NOTICE](./NOTICE)。保留上游归属不表示上游作者参与维护、认可或背书 YamiboPlus。

## 第三方软件与资源

当前发布版本直接使用的主要组件包括：

- [AndroidX 与 Jetpack Compose](https://github.com/androidx/androidx)：Activity、Core、Lifecycle、Navigation、DataStore、Compose UI、Material、SwipeRefreshLayout 和动画等组件，主要为 `Apache-2.0`。
- [Retrofit 2.11.0](https://github.com/square/retrofit)及 Gson converter：`Apache-2.0`。
- [OkHttp 4.12.0](https://github.com/square/okhttp)：包括 Brotli、DNS-over-HTTPS 等模块，`Apache-2.0`；其传递依赖继续适用各自许可证。
- [Gson](https://github.com/google/gson)：由 Retrofit Gson converter 使用，`Apache-2.0`。
- [jsoup 1.17.2](https://github.com/jhy/jsoup)：`MIT`。
- [Fastjson2 2.0.51.android5](https://github.com/alibaba/fastjson2)：`Apache-2.0`。
- [Coil 2.6.0](https://github.com/coil-kt/coil)：用于图片加载和 Compose 集成，`Apache-2.0`。
- [Telephoto 0.6.2](https://github.com/saket/telephoto)：用于图片缩放交互，`Apache-2.0`。
- [android-opencc 1.2.0](https://github.com/qichuan/android-opencc)：Android 封装为 `MIT`；其包含的 [OpenCC](https://github.com/BYVoid/OpenCC) 代码和词典等材料另适用 `Apache-2.0` 及相关上游声明。
- [Kotlin](https://github.com/JetBrains/kotlin)及其运行时依赖：`Apache-2.0`。

上述组件及其传递依赖保留各自的上游许可证和权利声明；本项目整体依照 AGPL-3.0 分发，不会取消或替代这些第三方义务。Apache License 2.0 的完整文本见 <https://www.apache.org/licenses/LICENSE-2.0>。

## 论坛内容与账户数据

YamiboPlus 是面向百合会论坛的非官方客户端，与论坛运营方无隶属关系。

- 论坛帖子、小说、漫画、图片、头像、用户资料及其他站点内容来自 `yamibo.com`、`bbs.yamibo.com` 或内容发布者，不属于本项目自身代码，也不纳入 AGPL-3.0。
- 登录凭据、Cookie、收藏、历史、阅读进度和其他账户数据分别受论坛规则、隐私政策、内容权利及适用法律约束。
- 本项目许可证不授予百合会、Yamibo、论坛标识、用户名称或第三方内容的商标及其他使用权。

用户在访问、保存或分享论坛内容时，应遵守论坛规则及相应权利人的要求。

## 外部服务

应用可访问 GitHub API 和 GitHub Releases 检查、展示及下载新版本。APK 下载默认使用 GitHub 直链；连接失败时，当前实现可能依次尝试以下第三方前缀代理：

- `https://ghproxy.net/`
- `https://gh-proxy.com/`
- `https://gh.llkk.cc/`

GitHub、下载代理及论坛均为独立外部服务，不受本项目控制。它们的可用性、日志处理、隐私政策和服务条款由各自运营方负责；使用代理下载时，请求会经过对应服务。

## 版本与反馈

直接依赖的版本以 [Gradle 构建配置](./app/build.gradle.kts)为准，完整依赖集合以相应版本构建时解析出的依赖图为准。构建、调试和测试专用工具不一定随正式 APK 分发，其许可证仍以各自上游声明为准。

完整许可证文本及上游版权声明以各项目发布内容为准；本文中的许可证标识和链接仅用于定位，不替代相应许可证正文。

本项目使用或展示第三方材料，不代表 YamiboPlus 有权对其重新许可，也不代表相关权利人对本项目作出认可或背书。如发现版本、来源或权利标注不完整，请通过仓库反馈渠道指出相应组件、页面或内容。
