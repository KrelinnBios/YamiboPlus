package org.shirakawatyu.yamibo.novel.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignPageParserTest {
    @Test
    fun parsesRealSignPageStructure() {
        val html = """
            <html><body>
              <div class="signbtn"><a href="plugin.php?id=zqlj_sign&amp;sign=abc">今日已打卡</a></div>
              <div class="hui-common-title"><div class="hui-common-title-txt">打卡公告</div></div>
              <div class="hui-content">随便玩玩~ 看心情随时可能修改规则</div>
              <table id="tablehead"><tbody><tr><th>上个月 2026年8月 下个月</th></tr></tbody></table>
              <table id="tablebody"><tbody><tr>
                <td></td>
                <td><div class="day on">1</div></td>
                <td><div class="day on today">2</div><div class="holiday">七夕</div></td>
              </tr></tbody></table>
              <div id="tblist">
                <div class="hui-media-content">
                  <p>用户名：<a href="home.php?mod=space&amp;uid=634729">测试用户</a><span>今日已打卡</span></p>
                  <p>打卡等级：百合入道</p>
                  <p>总天数：6</p><p>月天数：6</p><p>总奖励：6对象</p>
                  <p>上次奖励：2对象</p><p>上次打卡时间：2026-08-21 19:52:01</p>
                </div>
              </div>
              <div class="hui-common-title"><div class="hui-common-title-txt">我的打卡动态</div></div>
              <div class="hui-list"><div class="hui-list-text">已连续打卡 3 天</div></div>
            </body></html>
        """.trimIndent()

        val page = SignPageParser.parse(html)

        assertEquals(2026, page.year)
        assertEquals(8, page.month)
        assertTrue(page.signedToday)
        assertEquals("随便玩玩~ 看心情随时可能修改规则", page.announcement)
        assertEquals(3, page.calendar.size)
        assertTrue(page.calendar[1]?.signed == true)
        assertTrue(page.calendar[2]?.today == true)
        assertEquals("七夕", page.calendar[2]?.holiday)
        assertEquals("634729", page.records.single().uid)
        assertEquals("6", page.records.single().totalDays)
        assertEquals(listOf("已连续打卡 3 天"), page.myStats)
    }
}
