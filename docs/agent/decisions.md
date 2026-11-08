# YamiboPlus · 历史兼容、网络、深色模式、阅读器、链接与崩溃恢复的已验证决策

本文件是本仓库的专项维护规则。根目录 [AGENTS.md](../../AGENTS.md) 的项目状态与全局边界继续有效。

## 已确立的决定

以下决定已有实机问题和现有实现作为依据。除非用户明确要求改变对应行为，不要仅凭通用最佳实践擅自推翻。

### 网络

- **禁止强制 HTTP/1.1**（`protocols(HTTP_1_1)`）。这曾导致整个 App 无法连接论坛，并与共享连接池中的 h2 连接冲突。
- 服务器偶发 `stream was reset: PROTOCOL_ERROR` 已在应用级拦截器 `proceedWithDnsRecovery` 处理：GET 遇瞬时流重置或建连失败（`UnknownHostException`/`ConnectException`/`SocketTimeoutException`/`SSLException`）最多重试 3 次、444 WAF 限流最多重试 2 次，每次重试前执行 `connectionPool.evictAll()` 清理坏连接并递增退避。不要再改协议协商层。
- `TtlDnsCache` 的 30 分钟 IP 缓存与“DNS 优化”开关无关。开关只决定使用阿里/腾讯 DoH、手动 DoH 或系统 DNS；解析结果一律进入同一缓存层。建连失败会在重试前主动令当前 host 缓存失效，不要拖到重试耗尽后才失效。
- 连接池 keepalive 固定为 **50 秒**，必须短于论坛服务器约 60～75 秒的空闲超时。不要改回分钟级，否则切回 App 时可能复用半死连接。

### 暗黑模式

- 只有一套深色主题：经典蓝黑 `DarkThemeColors.CLASSIC`，主色 `#4EA1FF`、背景 `#0D141D`、面板 `#182332`。不引入多套深色主题。
- 论坛网页深色规则全部位于 `util/theme/DarkClassic.kt`，加载时 HTML 代理注入（`proxyHtmlForDarkMode` + `injectThemeCssIntoHtml`）与运行时 JS 注入（`getThemeSetJs`）必须共用同一份 CSS。
- 原色（浅色）模式不再是零注入：`util/theme/LightClassic.kt` 的 `LIGHT_MODE_CSS_RULES_CLASSIC` 目前只统一正文链接颜色。同样走 `injectThemeCssIntoHtml` / `getThemeSetJs` 两条路径，不要加入与“链接统一”无关的规则，浅色模式整体应保持论坛原样。
- 电脑版空间/家园页的 viewport 由 `PageJsScripts.shouldUseResponsiveSpaceViewport` 决定：普通空间页用 `width=device-width`；`#ct.ct3_a`（spacecp / BLOG 个人主页等定宽多栏页）与 `pg_space+ct2_a+tl`（空间内主题列表）改用 `width=1200` 加按屏宽计算的 `initial-scale`。`ct3_a` 在 HTML 代理与运行时 JS（`yamiboUseResponsiveSpaceViewport`）两处都要排除 device-width。不要给 `ct3_a` 加 `float:none` 一类 reflow。
- CSS 规则字符串末尾会统一执行 `background:` → `background-color:` 重写。规则中可以写 `background:`，但绝不能覆盖站点的 `background-image`，轮播图、头像和会员自定义背景依赖它。
- CSS 规则字符串中不能出现单引号，否则会破坏 JS 注入字符串拼接。
- 只有自定义 DIY 会员空间才不启用暗黑模式；判定按页面内容：页面是 `body#space` 且使用 `data/attachment` 的自定义背景图。普通空间以及帖子、版块、手机版个人中心等页面照常启用暗黑。不要再用 URL 规则排除空间页。
- 底栏“我的”是手机版个人中心（`mobile=2` / `mobile=yes` / `mycenter=1`），不是 `body#space`，照常暗黑。
- 会员空间守卫统一由 `util/theme/MemberSpaceGuard` 提供：`isMemberSpaceHtml()` 给 HTML 代理判断、`jsExpression()` 给运行时 JS 判断，两者共用“`body#space` + `data/attachment` 背景”规则。修改判断逻辑时保持所有调用路径一致。
- 当前 DIY 检测认 `data/attachment` 自定义背景图。若遇到只用纯外链图且无 `data/attachment` 的真实样本，再基于样本扩展，不要凭空猜。
- 投票区 `#poll` 的彩条以及用户侧栏经验/积分彩条依赖内联颜色，必须保留原色；不要用大范围 `.plc div` / `.pls div` 规则覆盖 `.pbr`、`.pbg`、`.pbr2`、`.pbg2`。
- 深色链接统一使用浅蓝 `#7dbdf2`，不允许改成棕色；浅色模式正文链接统一为站点默认链接色 `#6E2B19`。
- 链接颜色统一要覆盖链接内部的 `font[color]` / 内联 `color` 子元素，但只改文字色，绝不动 `background-image`。
- 新页面未适配时，先让用户提供真实 HTML 片段，再按选择器精准补规则，不凭空猜测或添加大范围通配。
- 开屏（系统 SplashScreen + 窗口背景）固定使用浅色 `@color/splash_background`（`#FCF4CF`），不跟随暗黑模式。此前 SharedPreferences + `UiModeManager.setApplicationNightMode` + `values-night` 的开屏换色方案已因闪原色和漫画首页加载异常整体回退，不要恢复。

### 交互

- 底栏单击表示“切换板块”：跳到对应板块；已在本板块内时不做任何事。长按表示“回该板块主页”（论坛首页 / 个人资料 / 漫画首页 / 收藏首页），即 `returnToHome(notifyHome = true)` 发出的 `goHomeEvent`；单击走 `notifyHome = false`。不要把回主页改回单击。
- 刷新统一使用下拉手势，原生页用 `PullToRefreshBox`，WebView 页用 `SwipeRefreshLayout`。长按刷新已删除，不要恢复。
- 下拉刷新指示器必须跟随暗黑模式配色：深色背景 `#223247`、箭头 `#4EA1FF`。
- 切回 `MangaHomePage`（长按底栏漫画键）不触发网络刷新，只清空搜索词并回到顶部，避免网络波动破坏现有列表。
- 小说阅读器进度只显示页数 `当前/总数`，不显示百分比。
- 小说阅读器标题不常驻顶部，放在点击正文后弹出的菜单中间，与漫画阅读器保持一致。
- 从阅读器返回“原帖”的 URL 必须经过 `ReaderReturnBridge.forceMobileTemplate` 并带 `mobile=2`，否则论坛会渲染电脑版。

### 权限贴（readperm）

- API 返回 `readperm > 0` 只是帖子元数据。只要 API 已返回图片 URL，就说明当前账号有权限，不得在 `MangaProber.fastApiProbe` 中据此拦截。
- 访问拒绝检测只看 Discuz 错误元素：`#messagetext, .showmessage, .alert_error, .nfl .f_c`。
- 绝不回退到 body 全文匹配；“阅读权限: 50”等正常帖子属性会造成误判。

### 漫画目录（DirectoryRepository / MangaTitleCleaner）

- 目录归并默认只看作品名和汉化组，不自动记发布者（论坛账号）。只有标题标注为个人/非固定团队发布（`MangaTitleCleaner.isIndividualRelease`）时，才自动用发布者兜底区分；已写入的发布者设置不会被清空，用户始终可以在目录编辑弹窗手动填写“发布者”。
- 短篇集/合集/选集/总集/精选集等集合类后缀是书名本身的一部分，`getCleanBookName` 会在集合后缀词处优先截断，早于章节标记截断。
- 标题开头的括号段视为原作/出处标注；仅当括号段后仍有实质内容时，`getCleanBookName` 才剥掉它并使用后面的真实作品名，作品名本身写在括号里的整段标题不动。
- 目录归并用的组名走 `extractReleaseGroup`：优先显式汉化组名；不带“汉化”字样的制作组按论坛惯例取第一个【】段兜底，个人发布标注不算组名。该兜底只用于目录归并；组名比对做繁简归一（會/組/漢/譯）。
- 汉化组过滤是硬过滤（`DirectoryRepository.filterChaptersByDirectoryConstraints`）：设置汉化组后只保留匹配组章节，不按话数从其他组补缺。展示侧必须复用同一函数。
- 若首楼提供编号形式的跨作品链接列表，该列表视为权威目录：目录初始化优先使用这些链接，去掉当前汉化组约束，并避免自动搜索扩展到未列出的帖子。权威目录使用 `MangaDirectory.authoritativeLinks` 持久化标记；章节保持首楼列表顺序和标题。`manuallyUpdateDirectory` 对权威目录整体跳过，列表内容只在打开原帖时按首楼刷新。
- 目录已存组与当前打开帖子的组不一致时：若当前帖能识别出组名，优先切到当前组；只有当前帖识别不出组名时才沿用旧组。
- 已存目录名若是旧版清洗器残留，`initDirectoryForThread` 会按 `isStaleCleanBookName`、`isTruncatedCleanBookName`、`isParenResidueCleanBookName`、`isParodyResidueCleanBookName` 等规则迁移；这些规则要求新旧名字满足特定残渣关系，不要误还原用户手动修改的名字。
- 两个组对同一作品命名完全不同、导致搜索关键词对不上时，依赖用户手动修改目录名或搜索关键词，不要引入模糊匹配自动合并。

### 收藏、历史与缓存

- 收藏删除同时涉及论坛端、本地排序数据、缓存和 `TombstoneQueueUtil` 的延迟同步；不要只删除 UI 列表或只调用论坛接口。
- 历史帖子详情必须使用独立 WebView，退出后清理 route-scoped 状态，不能污染 `MinePage` 的常驻 WebView。
- 小说缓存以规范化 URL 及兼容别名为索引；清理单本缓存时必须覆盖同帖 URL 的兼容形式。
- 漫画缓存统计依赖实际图片 URL 集合；清理单项时优先精确淘汰对应 URL，不要无条件清空全局 Coil 缓存。

### 论坛屏蔽

- 只屏蔽别人的内容：自己发布的主题/楼层（含 1 楼）不显示「屏蔽」按钮。`view=me` 或自己的 `mod=space&do=thread/reply/favorite` 空间列表页整页跳过。
- uid 必须登录后尽早获取并由 `CurrentUserUtil` 本地持久化，注入屏蔽脚本时作为 `selfUid` 回传给页面。
- 列表页「屏蔽」按钮使用普通 `.threadlist_foot li` 容器，与浏览/回复数按钮对齐；帖子页按钮与用户名之间用四个不可断空格保持间距。
- 电脑版页面只隐藏、不注入按钮：列表行由 `syncPcListPage` 按黑名单隐藏/恢复，不加占位提示；取消屏蔽走黑名单弹窗或手机版页面。电脑版帖子楼层复用 `syncPostPage`。

### 链接直达

- 帖子链接识别与归一化统一走 `YamiboPostLinkUtil`：强制 `bbs.yamibo.com` + `mobile=2`，排除图片、首页等非帖子链接；剪贴板检测只在切回前台或启动时读取当前剪贴板。
- 跳转必须经 `BBSPage` 的 `startLoading`，不要使用裸 `webView.loadUrl`，否则会与首页初始加载竞争。

### 崩溃兜底

- `CrashHandler` 在 `YamiboApplication.onCreate` 最先安装。后台线程未捕获异常记录后吞掉以避免整体闪退；主线程异常仍交还系统默认处理器。崩溃日志写入 `getExternalFilesDir/crash`，仅保留最新若干份。

### CI / 发布

- workflow 只构建 release 签名包（`assembleRelease`），四个签名 secrets 缺一即失败。
- release 触发时版本号从 tag 推导：`APP_VERSION_NAME` 为 tag 去掉 `v` 前缀，`APP_VERSION_CODE` 为 `github.run_number`；tag 必须符合 `v数字.数字…` 格式，否则构建直接失败。发布包不得回落到 `build.gradle.kts` 默认版本号，否则会导致应用内更新循环。
- GitHub Actions 固定使用 Node 24 兼容版本：checkout@v5、setup-Java@v5、setup-gradle@v5、setup-android@v4、upload-artifact@v5，并设置 `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24`。不要降级。
- 发布产物路径和名称固定为 `app/build/outputs/apk/release/300-Plus.apk` / `300-Plus.apk`。
