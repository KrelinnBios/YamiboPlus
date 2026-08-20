package org.shirakawatyu.yamibo.novel.bean.space

/**
 * 原生「我的空间」列表页条目。
 * 覆盖消息中心（私信/提醒）、好友、记录、日志、我的主题/回复。
 */
sealed interface SpaceListItem {
    /** 私信会话条目 */
    data class PrivateMessage(
        val touid: String,
        val name: String,
        val time: String,
        val summary: String,
        val avatarUrl: String?,
        val url: String
    ) : SpaceListItem

    /** 提醒条目 */
    data class Notice(
        val title: String,
        val time: String,
        val summary: String,
        val avatarUrl: String?,
        val url: String
    ) : SpaceListItem

    /** 好友条目 */
    data class Friend(
        val uid: String,
        val name: String,
        val avatarUrl: String?,
        val statusText: String,
        val spaceUrl: String,
        val pmUrl: String
    ) : SpaceListItem

    /** 记录（doing）条目 */
    data class Doing(
        val uid: String,
        val name: String,
        val avatarUrl: String?,
        val time: String,
        val content: String,
        val comments: List<DoingComment>,
        val spaceUrl: String
    ) : SpaceListItem

    /** 日志条目 */
    data class Blog(
        val blogId: String,
        val category: String,
        val title: String,
        val time: String,
        val summary: String,
        val authorName: String,
        val url: String,
        val editUrl: String = "",
        val deleteUrl: String = "",
        val stickUrl: String = "",
        val tags: List<String> = emptyList()
    ) : SpaceListItem

    /** 我的主题/回复条目 */
    data class UserThread(
        val tid: String,
        val title: String,
        val time: String,
        val forumName: String,
        val viewCount: String,
        val replyCount: String,
        val isClosed: Boolean,
        val url: String
    ) : SpaceListItem
}

data class DoingComment(
    val authorName: String,
    val time: String,
    val content: String
)

enum class SpacePageKind {
    PRIVATE_MESSAGE,
    NOTICE,
    FRIEND,
    DOING,
    BLOG,
    USER_THREAD
}

data class SpaceListRequest(
    val kind: SpacePageKind,
    val uid: String = "",
    val view: String = "",
    val type: String = "",
    val categoryId: String = "",
    val fuid: String = ""
)

data class SpaceTabSpec(
    val label: String,
    val request: SpaceListRequest
)

data class SpaceListPage(
    val items: List<SpaceListItem>,
    val previousUrl: String? = null,
    val nextUrl: String? = null,
    val categories: List<SpaceCategory> = emptyList(),
    val friendFilters: List<SpaceFriendFilter> = emptyList()
)

data class SpaceCategory(
    val id: String,
    val name: String
)

data class SpaceFriendFilter(
    val uid: String,
    val name: String
)

sealed interface BlogContentBlock {
    data class Text(val value: String) : BlogContentBlock
    data class Image(val url: String) : BlogContentBlock
    data class Quote(val value: String) : BlogContentBlock
}

data class BlogComment(
    val id: String,
    val authorName: String,
    val authorUid: String,
    val avatarUrl: String?,
    val time: String,
    val content: String,
    val replyUrl: String = "",
    val editUrl: String = "",
    val deleteUrl: String = ""
)

data class BlogDetail(
    val blogId: String,
    val ownerUid: String,
    val category: String,
    val title: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val time: String,
    val viewCount: String,
    val commentCount: String,
    val blocks: List<BlogContentBlock>,
    val comments: List<BlogComment>,
    val favoriteUrl: String = "",
    val shareUrl: String = "",
    val inviteUrl: String = "",
    val editUrl: String = "",
    val deleteUrl: String = "",
    val reportUrl: String = "",
    val commentFormUrl: String = "",
    val commentFormHash: String = "",
    val commentReferer: String = ""
)

data class PrivateMessageBubble(
    val isSelf: Boolean,
    val authorName: String,
    val avatarUrl: String?,
    val content: String,
    val time: String
)

data class PrivateMessageConversation(
    val touid: String,
    val title: String,
    val pmid: String,
    val formHash: String,
    val messages: List<PrivateMessageBubble>,
    val previousUrl: String? = null,
    val nextUrl: String? = null
)
