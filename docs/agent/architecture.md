# YamiboPlus · 页面、WebView、阅读器、数据库、网络与后台功能架构

本文件是本仓库的专项维护规则。根目录 [AGENTS.md](../../AGENTS.md) 的项目状态与全局边界继续有效。

## 架构速览

### 页面与导航

- 四个底栏主页面：`MangaHomePage`（原生列表）、`FavoritePage`（原生列表）、`BBSPage`（WebView）、`MinePage`（WebView）。
- BBS/Mine 使用常驻组合并通过 `isSelected` 切换；Manga/Favorite 走 NavHost 的 saveState/restoreState。
- 主要子页面包括 `ReaderPage`、`NativeMangaPage`、`MangaWebPage`、`ReaderWebPage`、`OtherWebPage`、`HistoryPage` 和 `MineHistoryPostPage`。
- 历史帖子详情复用 `MinePage` 的页面能力，但使用 route-scoped `MinePageVM` 和独立 WebView；不得把历史帖子状态写回底栏“我的”页面。
- 论坛、板块、帖子和空间内容新增原生能力时统一以**电脑版页面结构**为解析依据；原生页面不得回退展示手机版 WebView。需要网页表单兜底时也请求电脑版模板并解析其表单。
- 所有分页列表默认每页 **20 条**，帖子详情默认每页 **20 层**；板块、我的主题/回复、日志、记录等请求都应显式携带对应的 20 条分页参数，不能依赖账号 Cookie 中的论坛显示偏好。

### WebView

- `WebViewPool` 负责复用实例；页面切走后通过 `schedulePause` 延迟暂停。
- WebView 暂停期间 `evaluateJavascript` 可能被丢弃。任何“恢复页面”路径（如 `resumeBbsWebViewAfterChildPage`、`resumeMineWebViewAfterChildPage`）都必须重新注入主题和页面交互 JS。
- 从原生阅读器返回原帖时应恢复现有 WebView，不要无条件重新加载，否则会丢失滚动位置并重新出现加载遮罩。
- `BBSPageState` 管理论坛页加载、超时、错误和前后台恢复；修改恢复逻辑时不要绕过其状态机另起一套标志。

### 阅读器

- 小说：`ReaderPage` + `ReaderVM`，页面加载链为 `AuthenticatedThreadPageLoader` + `ThreadHtmlParser`，并由 `LocalCacheUtil`、`CacheUtil` 和内存预热组件维护缓存。
- 漫画：优先进入 `NativeMangaPage`，由 `MangaReaderManager`、`MangaImagePipeline` 和 `DirectoryRepository` 管理章节、图片和目录；`MangaWebPage` 负责 WebView 兜底。
- 漫画识别链固定为 `MangaProber.fastApiProbe` → `fastHtmlProbe` → WebView 兜底，不要在没有证据时改变顺序。
- `ReaderReturnBridge` 维护阅读器与原帖 WebView 的一次性返回状态。

### 数据与后台功能

- 新手引导：`OnboardingUtil` 按页面（`OnboardingUtil.Page`）记录是否已展示，存入 DataStore；`OnboardingOverlay` 是复用的 Compose 组件，登录态（`GlobalData.currentUid` 非空）下首次进入对应原生页面才查询展示。只覆盖原生页面（漫画发现/漫画阅读器/收藏/浏览历史/小说阅读器）和底栏，不覆盖 WebView 页面（论坛/我的），因为浮层引导在 WebView 上容易遮挡或对不齐。
- 收藏主状态在 `FavoriteVM`；本地数据、删除同步和墓碑队列分别由 `FavoriteUtil`、`FavoriteDeleteUtil`、`TombstoneQueueUtil` 管理。
- 收藏手动更新检查统一经过 `UpdateCheckEngine`；小说、漫画和其他帖子各有独立 profile/util。
- 收藏页支持的漫画版块固定为 fid 30「中文百合漫画区」和 fid 37「百合漫画图源区」；小说版块固定为 fid 49「文學區」、fid 55「轻小说/译文区」、fid 60「TXT小说区」。未识别收藏需允许用户手动选择小说、漫画或其他；选择其他后应从收藏页移出。
- 浏览历史由 `HistoryUtil` 管理；漫画目录由 `DirectoryRepository` 管理。
- 设置使用 DataStore（`SettingsUtil` / `DataStoreUtil`）；不要另建 SharedPreferences 保存同类全局设置。阅读器自身已有兼容存储除外。
- 应用更新统一走 `AppUpdateManager`；自动签到统一走 `AutoSignManager` / `AccountSyncManager`；缓存清理由 `CacheMaintenance` 统一协调。
- 论坛屏蔽：数据与持久化在 `ForumBlocklistManager`；页面注入逻辑在 `PageJsScripts.getForumBlockerJs`（向论坛/个人中心 WebView 注入「屏蔽」按钮与隐藏占位）；JS↔原生桥接是 `ForumBlocklistJSInterface`（`AndroidForumBlocklist`，含 `block` / `unblock` / `setUid`）；管理弹窗是 `ForumBlocklistDialog`。
- 当前登录 uid：`CurrentUserUtil` 持久化、`GlobalData.currentUid` 内存缓存，供屏蔽功能排除自己的内容。来源：收藏接口 `member_uid`（`FavoriteVM`）、桌面页 `discuz_uid`、手机版 `mycenter=1` 链接（页面脚本探测后经 `setUid` 回传）。
- 链接直达：`YamiboPostLinkUtil` 统一识别/归一化帖子链接；剪贴板与外部 deep link 经 `GlobalData.pendingClipboardUrl` / `pendingDeepLinkUrl` 传给 `BBSPage` 加载。
- 崩溃兜底：`CrashHandler` 在 `YamiboApplication.onCreate` 最先安装。

### 网络

- `YamiboRetrofit` 有两个主要客户端：`okHttpClient` 用于接口、HTML 代理和主框架，`threadOkHttpClient` 用于论坛图片。
- 两个客户端共享 `sharedConnectionPool` 与 `TtlDnsCache`；DNS 优化由 `DynamicDns` 在阿里/腾讯 DoH、手动 DoH 和系统 DNS 之间处理。
- WebView HTML 暗黑代理、静态资源代理、Cookie 同步和 Coil 图片缓存判断也集中在 `YamiboRetrofit`，修改时注意 WebView 与原生请求行为必须保持一致。
