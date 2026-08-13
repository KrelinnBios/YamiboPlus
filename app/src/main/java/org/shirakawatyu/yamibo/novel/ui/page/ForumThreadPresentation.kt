package org.shirakawatyu.yamibo.novel.ui.page

import org.shirakawatyu.yamibo.novel.bean.forum.ForumThread

internal const val STICKY_THREADS_INITIAL_EXPANDED = false

internal data class ForumThreadGroups(
    val sticky: List<ForumThread>,
    val regular: List<ForumThread>
)

internal fun groupForumThreads(threads: List<ForumThread>) = ForumThreadGroups(
    sticky = threads.filter(ForumThread::isSticky),
    regular = threads.filterNot(ForumThread::isSticky)
)

internal fun forumThreadStats(thread: ForumThread): String =
    "${thread.viewCount} 查看 · ${thread.replyCount} 回复"

internal fun toggleExpandedForumId(currentForumId: String?, targetForumId: String): String? =
    targetForumId.takeUnless { it == currentForumId }
