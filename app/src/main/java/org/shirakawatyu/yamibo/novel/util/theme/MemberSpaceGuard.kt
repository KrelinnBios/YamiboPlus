package org.shirakawatyu.yamibo.novel.util.theme

object MemberSpaceGuard {
    private val bodySpaceRegex = Regex(
        """<body\b[^>]*\bid\s*=\s*(["'])space\1""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // 自定义 DIY 空间的标志：用 data/attachment 的相册图片当背景。
    // 普通空间（如「xxx的空间」）只有 static/image 的默认图，没有这种自定义背景。
    private val customDiyBgRegex = Regex(
        """background(?:-image)?\s*:[^;}]*data/attachment""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 是否为「自定义 DIY 会员空间」——只有这种页面排除暗黑，以保留作者亲自设计的版面。
     * 判定：页面是 body#space，且用了 data/attachment 的自定义背景图。
     * 普通空间（无自定义背景）以及其它所有页面都照常启用暗黑。
     */
    fun isMemberSpaceHtml(html: String): Boolean {
        if (!bodySpaceRegex.containsMatchIn(html)) return false
        return customDiyBgRegex.containsMatchIn(html)
    }

    /** 运行时 JS 判断：当前页是否为自定义 DIY 会员空间（body#space 且含 data/attachment 背景图）。 */
    fun jsExpression(): String {
        return """
            (function(){
                if (!document.body || document.body.id !== 'space') return false;
                try {
                    return /background(?:-image)?\s*:[^;}]*data\/attachment/i
                        .test(document.documentElement.innerHTML);
                } catch (e) { return false; }
            })()
        """.trimIndent()
    }
}
