package org.shirakawatyu.yamibo.novel.ui.page

import org.junit.Assert.assertEquals
import org.junit.Test
import org.shirakawatyu.yamibo.novel.bean.HistoryEntry
import java.util.Calendar

class HistoryPageTest {
    @Test
    fun historyMonthsContainsOnlyRecordedMonthsInChronologicalOrder() {
        val entries = listOf(
            historyEntry(2026, Calendar.FEBRUARY, 1),
            historyEntry(2025, Calendar.MARCH, 20),
            historyEntry(2025, Calendar.JANUARY, 5),
            historyEntry(2025, Calendar.MARCH, 8)
        )

        assertEquals(
            listOf(
                HistoryMonth(2025, Calendar.JANUARY),
                HistoryMonth(2025, Calendar.MARCH),
                HistoryMonth(2026, Calendar.FEBRUARY)
            ),
            historyMonths(entries)
        )
    }

    private fun historyEntry(year: Int, month0: Int, day: Int): HistoryEntry =
        HistoryEntry(
            url = "https://bbs.yamibo.com/thread-$year-$month0-$day-1.html",
            title = "",
            author = "",
            section = "",
            timestamp = Calendar.getInstance().apply {
                clear()
                set(year, month0, day)
            }.timeInMillis
        )
}
