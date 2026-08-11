package org.shirakawatyu.yamibo.novel.bean

/**
 * 内容数据类，用于表示小说章节中的内容
 *
 * @param data 内容数据，可以是文本内容或图片
 * @param type 内容类型，标识是图片还是文本
 * @param chapterTitle 章节标题，可为空
 * @param chapterStart 是否为「硬分章」起点：即便与上一章标题相同也强制新建目录项
 *   （用于作者把两个同名「第N章」分楼层发布的情况，避免被连续同名去重合并成一章）
 */
data class Content(
    val data: String,
    val type: ContentType,
    val chapterTitle: String? = null,
    val isParagraphEnd: Boolean = false,
    val chapterStart: Boolean = false
)

enum class ContentType {
    IMG, TEXT
}


