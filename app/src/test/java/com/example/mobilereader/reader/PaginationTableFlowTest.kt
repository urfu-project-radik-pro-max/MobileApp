package com.example.mobilereader.reader

import com.example.mobilereader.ui.reader.firstUnbreakableTableGroupEnd
import org.junit.Assert.assertEquals
import org.junit.Test

class PaginationTableFlowTest {
    @Test
    fun `table row group remains intact when it spans rows`() {
        assertEquals(
            3,
            firstUnbreakableTableGroupEnd(
                startRow = 0,
                rowCount = 5,
                noBreakAfterRow = booleanArrayOf(true, true, false, false, false),
            ),
        )
    }
}
