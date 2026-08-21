package org.shirakawatyu.yamibo.novel.util.theme

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class DarkClassicCssTest {
    private val css = DARK_MODE_CSS_RULES_CLASSIC.joinToString("\n")

    @Test
    fun keepsDesktopDarkroomRules() {
        assertTrue(css.contains("#darkroomtable"))
        assertTrue(css.contains("#darkroomtable a"))
    }

    @Test
    fun keepsDesktopPaginationJumpRules() {
        assertTrue(css.contains(".pg label"))
        assertTrue(css.contains("input.px"))
    }

    @Test
    fun mobileBlogPaginationWrapperStaysTransparent() {
        assertTrue(
            css.contains(
                "body.pg_space .threadlist_box .pgs { background-color: transparent !important; }"
            )
        )
    }

    @Test
    fun keepsDesktopRanklistSelectedRules() {
        assertTrue(css.contains(".pg_ranklist .tbn li.a"))
    }

    @Test
    fun themesDesktopRateFormPopup() {
        assertTrue(css.contains("#fwin_rate .tm_c"))
        assertTrue(css.contains("#rateform .dt th"))
        assertTrue(css.contains("#rateform .reasonselect"))
        assertTrue(css.contains("#rateform ul[id^=scoreoption]"))
        assertTrue(css.contains("#rateform .dpbtn"))
    }

    @Test
    fun themesDesktopBlogInvitePage() {
        assertTrue(css.contains("#nv_misc.pg_invite #ct .usd"))
        assertTrue(css.contains("#nv_misc.pg_invite #ct .tbx span.a"))
        assertTrue(css.contains("#nv_misc.pg_invite #friends li"))
        assertTrue(css.contains("#nv_misc.pg_invite #inviteform"))
        assertTrue(css.contains("#nv_misc.pg_invite #ct .pn.pnc"))
    }

    @Test
    fun themesMobileSignPageFromRealPageStructure() {
        assertTrue(css.contains("#calendar .table-wrapper"))
        assertTrue(css.contains("#calendar #tablehead"))
        assertTrue(css.contains("#calendar .fl-table thead th span"))
        assertTrue(css.contains("#tblist .hui-media-content p"))
        assertTrue(css.contains("#tblist .page a"))
    }

    @Test
    fun privateMessageSpacerUsesPageBackgroundInsteadOfComposerPanel() {
        assertTrue(css.contains("#pmform { background-color: transparent !important"))
        assertTrue(css.contains("#pmform .foot_height { background-color: #0d141d !important; }"))
        assertFalse(css.contains("#pmform, #pmform .tedt"))
    }
}
