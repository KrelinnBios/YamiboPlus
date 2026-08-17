package org.shirakawatyu.yamibo.novel.ui.page

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListItem
import org.shirakawatyu.yamibo.novel.bean.space.SpaceListRequest
import org.shirakawatyu.yamibo.novel.bean.space.SpacePageKind
import org.shirakawatyu.yamibo.novel.bean.space.SpaceTabSpec

/**
 * 消息中心：私信 / 提醒。
 */
@Composable
fun NativeMessageCenterPage(
    navController: NavController,
    uid: String = "",
    onOpenConversation: (SpaceListItem.PrivateMessage) -> Unit,
    onOpenNotice: (SpaceListItem.Notice) -> Unit
) {
    NativeSpaceListPage(
        title = "消息中心",
        tabs = listOf(
            SpaceTabSpec("私信", SpaceListRequest(SpacePageKind.PRIVATE_MESSAGE)),
            SpaceTabSpec("提醒", SpaceListRequest(SpacePageKind.NOTICE))
        ),
        navController = navController,
        uid = uid,
        // 空间列表页是普通内容页（参照 QQ 联系人/消息列表），底栏常驻；
        // 列表需要为底栏预留底部滚动空间。
        showBottomNavBar = true,
        onItemClick = { item ->
            when (item) {
                is SpaceListItem.PrivateMessage -> onOpenConversation(item)
                is SpaceListItem.Notice -> onOpenNotice(item)
                else -> Unit
            }
        }
    )
}

/**
 * 好友：我的好友 / 在线成员 / 我的访客 / 我的足迹。
 */
@Composable
fun NativeFriendPage(
    navController: NavController,
    uid: String = "",
    onOpenFriendSpace: (SpaceListItem.Friend) -> Unit,
    onOpenPm: (SpaceListItem.Friend) -> Unit
) {
    NativeSpaceListPage(
        title = "好友",
        tabs = listOf(
            SpaceTabSpec("我的好友", SpaceListRequest(SpacePageKind.FRIEND)),
            SpaceTabSpec(
                "在线成员",
                SpaceListRequest(SpacePageKind.FRIEND, view = "online", type = "member")
            ),
            SpaceTabSpec(
                "我的访客",
                SpaceListRequest(SpacePageKind.FRIEND, view = "visitor")
            ),
            SpaceTabSpec(
                "我的足迹",
                SpaceListRequest(SpacePageKind.FRIEND, view = "trace")
            )
        ),
        navController = navController,
        uid = uid,
        showBottomNavBar = true,
        onItemClick = { item ->
            when (item) {
                is SpaceListItem.Friend -> onOpenFriendSpace(item)
                else -> Unit
            }
        }
    )
}

/**
 * 记录：我和好友的记录 / 我的记录 / 随便看看。
 */
@Composable
fun NativeDoingPage(
    navController: NavController,
    uid: String = ""
) {
    NativeSpaceListPage(
        title = "记录",
        tabs = listOf(
            SpaceTabSpec("我和好友的记录", SpaceListRequest(SpacePageKind.DOING, view = "we")),
            SpaceTabSpec("我的记录", SpaceListRequest(SpacePageKind.DOING, view = "me")),
            SpaceTabSpec("随便看看", SpaceListRequest(SpacePageKind.DOING, view = "all"))
        ),
        navController = navController,
        uid = uid,
        initialTabIndex = 1,
        showBottomNavBar = true,
        onItemClick = {}
    )
}

/**
 * 日志：好友的日志 / 我的日志 / 随便看看。
 */
@Composable
fun NativeBlogPage(
    navController: NavController,
    uid: String = "",
    onOpenBlog: (SpaceListItem.Blog) -> Unit,
    onOpenBlogAction: (String) -> Unit = {}
) {
    NativeSpaceListPage(
        title = "日志",
        tabs = listOf(
            SpaceTabSpec("好友的日志", SpaceListRequest(SpacePageKind.BLOG, view = "we")),
            SpaceTabSpec("我的日志", SpaceListRequest(SpacePageKind.BLOG, view = "me")),
            SpaceTabSpec("随便看看", SpaceListRequest(SpacePageKind.BLOG, view = "all"))
        ),
        navController = navController,
        uid = uid,
        initialTabIndex = 1,
        showBottomNavBar = true,
        showCategories = true,
        onTopBarAction = {
            onOpenBlogAction(
                "https://bbs.yamibo.com/home.php?mod=spacecp&ac=blog&mobile=2"
            )
        },
        onActionClick = onOpenBlogAction,
        onItemClick = { item ->
            when (item) {
                is SpaceListItem.Blog -> onOpenBlog(item)
                else -> Unit
            }
        }
    )
}

/**
 * 我的主题 / 回复。
 */
@Composable
fun NativeUserThreadsPage(
    navController: NavController,
    uid: String = "",
    initialTab: String = "thread",
    onOpenThread: (SpaceListItem.UserThread) -> Unit
) {
    NativeSpaceListPage(
        title = "我的主题",
        tabs = listOf(
            SpaceTabSpec("主题", SpaceListRequest(SpacePageKind.USER_THREAD)),
            SpaceTabSpec(
                "回复",
                SpaceListRequest(SpacePageKind.USER_THREAD, type = "reply")
            )
        ),
        navController = navController,
        uid = uid,
        showBottomNavBar = true,
        initialTabIndex = if (initialTab == "reply") 1 else 0,
        onItemClick = { item ->
            when (item) {
                is SpaceListItem.UserThread -> onOpenThread(item)
                else -> Unit
            }
        }
    )
}
