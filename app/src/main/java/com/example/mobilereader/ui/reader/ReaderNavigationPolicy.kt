package com.example.mobilereader.ui.reader

import kotlin.math.abs
import kotlin.math.roundToInt


internal object ReaderNavigationPolicy {


    fun pageIndexForLocation(pages: List<BookPage>, location: ReaderReturnLocation.Main): Int? {
        if (pages.isEmpty()) return null
        val item = location.flatItemIndex
        val char = location.charOffset ?: 0
        return pages.indexOfLast { page ->
            page.firstItemIndex < item ||
                (page.firstItemIndex == item && page.firstCharOffset <= char)
        }.coerceAtLeast(0)
    }


    fun isLargeScrollJump(travelledPx: Float, viewportPx: Int): Boolean =
        travelledPx.isFinite() && viewportPx > 0 &&
            abs(travelledPx) >= viewportPx * LARGE_SCROLL_VIEWPORTS


    fun partialHolderContainsTarget(
        chapterStarts: List<Int>,
        itemCount: Int,
        partialFirstItem: Int?,
        targetItem: Int,
    ): Boolean {
        if (partialFirstItem == null || itemCount <= 0 || chapterStarts.isEmpty()) return false
        val chapter = chapterStarts.indexOfLast { it <= partialFirstItem }
        if (chapter < 0) return false
        val from = chapterStarts[chapter]
        val to = chapterStarts.getOrNull(chapter + 1)?.minus(1) ?: (itemCount - 1)
        return targetItem in from..to
    }


    fun canConsumeBookFractionSeek(partialPagination: Boolean): Boolean =
        !partialPagination


    fun pendingBookFractionSeek(
        currentFraction: Float,
        requestedFraction: Float,
    ): Float? {
        if (!requestedFraction.isFinite()) return null
        val requested = requestedFraction.coerceIn(0f, 1f)
        val current = currentFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
        return requested.takeUnless { current != null && abs(it - current) <= FRACTION_NO_OP_EPSILON }
    }


    fun progressTargetIndex(
        fraction: Float,
        firstIndex: Int,
        lastIndexInclusive: Int,
    ): Int {
        val span = (lastIndexInclusive - firstIndex).coerceAtLeast(0)
        val safeFraction = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        return firstIndex + (safeFraction * span).roundToInt()
    }

    private const val LARGE_SCROLL_VIEWPORTS = 2.25f
    private const val FRACTION_NO_OP_EPSILON = 0.0005f
}
