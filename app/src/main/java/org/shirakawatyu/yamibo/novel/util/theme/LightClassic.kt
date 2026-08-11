package org.shirakawatyu.yamibo.novel.util.theme

/**
 * 原色（浅色）模式下的最小化网页样式覆盖。
 *
 * 浅色模式整体保持论坛原样，这里只做一件事：把帖子正文里的链接颜色统一成站点默认的
 * 链接色 #6E2B19。楼主常把链接用 <font color> 或内联 color 上成五颜六色，浅色下同样会
 * 显得杂乱，这里强制统一（含链接内部的 font / 内联 color 子元素），与暗黑模式逻辑对应。
 * 只改文字颜色，绝不触碰 background-image。
 */
val LIGHT_MODE_CSS_RULES_CLASSIC = listOf(
    "/* === Unify post-content link colors to the site default link color === */",
    ".message a, .postmessage a, .t_f a, .t_msgfont a { color: #6E2B19 !important; }",
    ".message a font, .message a font[color], .message a [style*=\"color\"], .postmessage a font, .postmessage a font[color], .postmessage a [style*=\"color\"], .t_f a font, .t_f a font[color], .t_f a [style*=\"color\"], .t_msgfont a font, .t_msgfont a font[color], .t_msgfont a [style*=\"color\"] { color: #6E2B19 !important; }"
)
