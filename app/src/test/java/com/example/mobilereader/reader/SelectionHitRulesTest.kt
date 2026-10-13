package com.example.mobilereader.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.mobilereader.ui.reader.selection.PillSide
import com.example.mobilereader.ui.reader.selection.SelectionEdge
import com.example.mobilereader.ui.reader.selection.SelectionHitRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionHitRulesTest {


    private val paragraphs = listOf(
        Rect(60f, 0f, 340f, 40f),
        Rect(60f, 60f, 340f, 100f),
        Rect(60f, 120f, 340f, 160f),
    )

    @Test
    fun `a point inside a fragment picks it`() {
        assertEquals(0, SelectionHitRules.pick(paragraphs, Offset(100f, 20f)))
        assertEquals(2, SelectionHitRules.pick(paragraphs, Offset(300f, 130f)))
    }

    @Test
    fun `a finger out in the page margin stays on its own line`() {

        assertEquals(1, SelectionHitRules.pick(paragraphs, Offset(10f, 80f)))
        assertEquals(2, SelectionHitRules.pick(paragraphs, Offset(390f, 140f)))
    }

    @Test
    fun `a point in the gap between paragraphs takes the nearer one`() {
        assertEquals(0, SelectionHitRules.pick(paragraphs, Offset(200f, 45f)))
        assertEquals(1, SelectionHitRules.pick(paragraphs, Offset(200f, 55f)))
    }

    @Test
    fun `vertical closeness beats horizontal closeness`() {


        val rects = listOf(
            Rect(60f, 0f, 340f, 40f),
            Rect(240f, 60f, 340f, 100f),
        )
        assertEquals(1, SelectionHitRules.pick(rects, Offset(80f, 90f)))
    }

    @Test
    fun `empty rects are ignored and nothing means nothing`() {

        val rects = listOf(Rect.Zero, paragraphs[1], Rect.Zero)
        assertEquals(1, SelectionHitRules.pick(rects, Offset(100f, 500f)))
        assertEquals(-1, SelectionHitRules.pick(listOf(Rect.Zero), Offset(1f, 1f)))
        assertEquals(-1, SelectionHitRules.pick(emptyList(), Offset(1f, 1f)))
    }

    @Test
    fun `ties keep the first entry so callers can order by preference`() {
        val twins = listOf(paragraphs[0], paragraphs[0])
        assertEquals(0, SelectionHitRules.pick(twins, Offset(100f, 20f)))
    }

    @Test
    fun `paged mode advances at the left and right edges`() {
        val width = 400f
        val height = 800f
        fun advance(x: Float, y: Float) =
            SelectionHitRules.edgeAdvance(Offset(x, y), width, height, paged = true, bandPx = 50f)

        assertEquals(-1, advance(20f, 400f))
        assertEquals(1, advance(390f, 400f))
        assertEquals(0, advance(200f, 400f))

        assertEquals(0, advance(200f, 10f))
        assertEquals(0, advance(200f, 790f))
    }

    @Test
    fun `scroll mode advances at the top and bottom edges`() {
        val width = 400f
        val height = 800f
        fun advance(x: Float, y: Float) =
            SelectionHitRules.edgeAdvance(Offset(x, y), width, height, paged = false, bandPx = 50f)

        assertEquals(-1, advance(200f, 20f))
        assertEquals(1, advance(200f, 790f))
        assertEquals(0, advance(200f, 400f))
        assertEquals(0, advance(10f, 400f))
    }

    @Test
    fun `auto scroll ramps with depth and stops in the middle`() {
        val height = 800f
        val band = 50f
        val max = 14f
        assertEquals(0f, SelectionHitRules.autoScrollPx(400f, height, band, max), 0.001f)

        val shallow = SelectionHitRules.autoScrollPx(770f, height, band, max)
        val deep = SelectionHitRules.autoScrollPx(800f, height, band, max)
        assertTrue(shallow > 0f)
        assertTrue(deep > shallow)
        assertEquals(max, deep, 0.001f)


        assertEquals(-max, SelectionHitRules.autoScrollPx(0f, height, band, max), 0.001f)

        assertTrue(SelectionHitRules.autoScrollPx(49f, height, band, max) < -0.1f)
    }

    @Test
    fun `a long press far from any text starts nothing`() {


        val rects = listOf(Rect(60f, 0f, 340f, 40f), Rect(60f, 600f, 340f, 640f))
        val deepInPicture = Offset(200f, 300f)
        assertEquals(-1, SelectionHitRules.pick(rects, deepInPicture, maxVerticalGap = 40f))


        assertEquals(0, SelectionHitRules.pick(rects, deepInPicture))
    }

    @Test
    fun `a long press just under the last line still catches it`() {
        val rects = listOf(Rect(60f, 600f, 340f, 640f))
        assertEquals(0, SelectionHitRules.pick(rects, Offset(200f, 660f), maxVerticalGap = 40f))
    }

    private fun side(bounds: Rect, insetTop: Float = 8f) = SelectionHitRules.toolbarSide(
        bounds = bounds,
        pillHeight = 48f,
        screenHeight = 800f,
        gapPx = 12f,
        handleReachPx = 20f,
        insetTop = insetTop,
        insetBottom = 8f,
    )

    private fun offset(
        bounds: Rect,
        side: PillSide,
        insetTop: Float = 8f,
    ) = SelectionHitRules.toolbarOffset(
        bounds = bounds,
        pillWidth = 240f, pillHeight = 48f,
        screenWidth = 400f,
        gapPx = 12f,
        handleReachPx = 20f,
        insetTop = insetTop, insetLeft = 8f, insetRight = 8f,
        side = side,
    )

    @Test
    fun `the action pill sits above the selection, centered on it`() {
        val bounds = Rect(100f, 300f, 300f, 340f)
        assertEquals(PillSide.ABOVE, side(bounds))
        val where = offset(bounds, PillSide.ABOVE)
        assertEquals(300f - 12f - 48f, where.y, 0.001f)
        assertEquals(200f - 120f, where.x, 0.001f)
    }

    @Test
    fun `the pill flips below a selection on the first line`() {
        val bounds = Rect(100f, 0f, 300f, 40f)
        assertEquals(PillSide.BELOW, side(bounds))
        assertEquals(72f, offset(bounds, PillSide.BELOW).y, 0.001f)
    }

    @Test
    fun `the pill clears the camera cutout by flipping below`() {


        val bounds = Rect(100f, 150f, 300f, 190f)
        assertEquals(PillSide.BELOW, side(bounds, insetTop = 120f))
        val where = offset(bounds, PillSide.BELOW, insetTop = 120f)
        assertEquals(222f, where.y, 0.001f)
    }

    @Test
    fun `a pill placed below clears the handle hanging there`() {


        val bounds = Rect(100f, 300f, 300f, 340f)
        val y = offset(bounds, PillSide.BELOW).y
        assertTrue("pill must start below the handle", y >= 340f + 20f)
    }

    @Test
    fun `the pill stays on screen next to the margins`() {
        assertEquals(8f, offset(Rect(0f, 300f, 40f, 340f), PillSide.ABOVE).x, 0.001f)

        assertEquals(152f, offset(Rect(360f, 300f, 400f, 340f), PillSide.ABOVE).x, 0.001f)
    }

    @Test
    fun `column grouping separates the two pages of a turn`() {


        val leaving = Rect(-260f, 300f, 140f, 340f)
        val arriving = Rect(140f, 300f, 540f, 340f)
        assertFalse(SelectionHitRules.sharesColumn(leaving, arriving))


        val sameColumnLower = Rect(-260f, 900f, 140f, 940f)
        assertTrue(SelectionHitRules.sharesColumn(leaving, sameColumnLower))

        assertTrue(SelectionHitRules.sharesColumn(leaving, Rect(-100f, 400f, 140f, 440f)))
    }

    @Test
    fun `a selection filling the screen still gets a pill`() {
        val bounds = Rect(0f, 0f, 400f, 800f)
        assertEquals(PillSide.ABOVE, side(bounds))
        assertEquals(8f, offset(bounds, PillSide.ABOVE).y, 0.001f)
    }

    @Test
    fun `handles hang outwards from the caret so they cover no text`() {
        val caret = Rect(120f, 40f, 120f, 68f)
        val start = SelectionHitRules.handleCenter(caret, SelectionEdge.START, radiusPx = 12f)
        val end = SelectionHitRules.handleCenter(caret, SelectionEdge.END, radiusPx = 12f)
        assertEquals(Offset(108f, 80f), start)
        assertEquals(Offset(132f, 80f), end)
    }

    @Test
    fun `handles are grabbed within the radius, nearest first`() {
        val start = Offset(100f, 100f)
        val end = Offset(300f, 300f)
        assertEquals(
            SelectionEdge.START,
            SelectionHitRules.grabbedHandle(start, end, Offset(110f, 105f), radiusPx = 40f),
        )
        assertEquals(
            SelectionEdge.END,
            SelectionHitRules.grabbedHandle(start, end, Offset(290f, 300f), radiusPx = 40f),
        )
        assertNull(SelectionHitRules.grabbedHandle(start, end, Offset(200f, 200f), radiusPx = 40f))

        assertEquals(
            SelectionEdge.END,
            SelectionHitRules.grabbedHandle(null, end, Offset(305f, 305f), radiusPx = 40f),
        )
        assertNull(SelectionHitRules.grabbedHandle(null, null, Offset(0f, 0f), radiusPx = 40f))
    }
}
