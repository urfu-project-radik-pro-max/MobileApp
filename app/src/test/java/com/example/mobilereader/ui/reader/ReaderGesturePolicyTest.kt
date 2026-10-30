package com.example.mobilereader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderGesturePolicyTest {
    @Test
    fun disabledEdgePagingLeavesCenterTapAvailable() {
        assertEquals(ReaderTapTarget.NONE, readerTapTarget(10f, 100f, false))
        assertEquals(ReaderTapTarget.NONE, readerTapTarget(90f, 100f, false))
        assertEquals(ReaderTapTarget.CENTER, readerTapTarget(50f, 100f, false))
    }

    @Test
    fun enabledEdgePagingKeepsBothTwentyTwoPercentZones() {
        assertEquals(ReaderTapTarget.LEFT, readerTapTarget(21f, 100f, true))
        assertEquals(ReaderTapTarget.CENTER, readerTapTarget(22f, 100f, true))
        assertEquals(ReaderTapTarget.CENTER, readerTapTarget(78f, 100f, true))
        assertEquals(ReaderTapTarget.RIGHT, readerTapTarget(79f, 100f, true))
    }

    @Test
    fun twoFingerTapAndDragCannotTurnPages() {
        val twoFingerTap = ReaderTapGesture(10f, 10f, 8f)
        twoFingerTap.observe(1, 10f, 10f, consumed = false)
        twoFingerTap.observe(2, 10f, 10f, consumed = false)
        twoFingerTap.observe(1, 10f, 10f, consumed = false)
        assertFalse(twoFingerTap.canTap)

        val drag = ReaderTapGesture(10f, 10f, 8f)
        drag.observe(1, 10f, 25f, consumed = false)
        assertFalse(drag.canTap)

        val shortTap = ReaderTapGesture(10f, 10f, 8f)
        shortTap.observe(1, 11f, 11f, consumed = false)
        assertTrue(shortTap.canTap)
    }

    @Test
    fun brightnessStartsOnlyAtEnabledLeftEdge() {
        assertTrue(canStartBrightnessGesture(12f, 100f, true))
        assertFalse(canStartBrightnessGesture(13f, 100f, true))
        assertFalse(canStartBrightnessGesture(5f, 100f, false))
    }
}
