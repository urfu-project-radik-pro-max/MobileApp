package com.example.mobilereader.ui.reader.selection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import kotlin.math.abs


enum class PillSide { ABOVE, BELOW }


object SelectionHitRules {


    val HANDLE_RADIUS = 10.dp


    val HANDLE_REACH = HANDLE_RADIUS * 2


    val HANDLE_GRAB_RADIUS = 28.dp


    val EDGE_BAND = 56.dp


    val AUTO_SCROLL_MAX_PER_FRAME = 14.dp


    private const val VERTICAL_WEIGHT = 6f


    val START_MAX_GAP = 40.dp


    fun pick(
        rects: List<Rect>,
        point: Offset,
        maxVerticalGap: Float = Float.MAX_VALUE,
    ): Int {
        var containing = -1
        var bandBest = -1
        var bandDistance = Float.MAX_VALUE
        var nearest = -1
        var nearestDistance = Float.MAX_VALUE
        var nearestVerticalGap = Float.MAX_VALUE

        for (index in rects.indices) {
            val rect = rects[index]
            if (rect.isEmpty) continue
            if (rect.contains(point)) {
                if (containing < 0) containing = index
                continue
            }
            val dx = horizontalGap(rect, point.x)
            val dy = verticalGap(rect, point.y)
            if (dy == 0f && dx < bandDistance) {
                bandDistance = dx
                bandBest = index
            }
            val weighted = dy * VERTICAL_WEIGHT + dx
            if (weighted < nearestDistance) {
                nearestDistance = weighted
                nearestVerticalGap = dy
                nearest = index
            }
        }
        return when {
            containing >= 0 -> containing
            bandBest >= 0 -> bandBest
            nearestVerticalGap <= maxVerticalGap -> nearest
            else -> -1
        }
    }


    fun edgeAdvance(
        point: Offset,
        width: Float,
        height: Float,
        paged: Boolean,
        bandPx: Float,
    ): Int {
        val position = if (paged) point.x else point.y
        val extent = if (paged) width else height
        return when {
            position < bandPx -> -1
            position > extent - bandPx -> 1
            else -> 0
        }
    }


    fun autoScrollPx(y: Float, height: Float, bandPx: Float, maxPerFrame: Float): Float {
        if (bandPx <= 0f) return 0f
        val aboveTop = bandPx - y
        val belowBottom = y - (height - bandPx)
        val depth = when {
            aboveTop > 0f -> -aboveTop
            belowBottom > 0f -> belowBottom
            else -> return 0f
        }
        val ramp = (abs(depth) / bandPx).coerceIn(0.2f, 1f)
        return if (depth < 0f) -maxPerFrame * ramp else maxPerFrame * ramp
    }


    fun sharesColumn(a: Rect, b: Rect): Boolean = a.left < b.right && b.left < a.right


    fun handleCenter(caret: Rect, edge: SelectionEdge, radiusPx: Float): Offset = when (edge) {
        SelectionEdge.START -> Offset(caret.left - radiusPx, caret.bottom + radiusPx)
        SelectionEdge.END -> Offset(caret.right + radiusPx, caret.bottom + radiusPx)
    }


    fun grabbedHandle(
        start: Offset?,
        end: Offset?,
        point: Offset,
        radiusPx: Float,
    ): SelectionEdge? {
        val toStart = start?.let { (it - point).getDistance() } ?: Float.MAX_VALUE
        val toEnd = end?.let { (it - point).getDistance() } ?: Float.MAX_VALUE
        if (minOf(toStart, toEnd) > radiusPx) return null
        return if (toStart <= toEnd) SelectionEdge.START else SelectionEdge.END
    }


    fun toolbarSide(
        bounds: Rect,
        pillHeight: Float,
        screenHeight: Float,
        gapPx: Float,
        handleReachPx: Float,
        insetTop: Float,
        insetBottom: Float,
    ): PillSide {
        if (bounds.top - gapPx - pillHeight >= insetTop) return PillSide.ABOVE
        val below = belowY(bounds, gapPx, handleReachPx)
        if (below + pillHeight <= screenHeight - insetBottom) return PillSide.BELOW
        return PillSide.ABOVE
    }


    private fun belowY(bounds: Rect, gapPx: Float, handleReachPx: Float): Float =
        bounds.bottom + handleReachPx + gapPx


    fun toolbarOffset(
        bounds: Rect,
        pillWidth: Float,
        pillHeight: Float,
        screenWidth: Float,
        gapPx: Float,
        handleReachPx: Float,
        insetTop: Float,
        insetLeft: Float,
        insetRight: Float,
        side: PillSide,
    ): Offset {
        val y = when (side) {
            PillSide.ABOVE -> bounds.top - gapPx - pillHeight
            PillSide.BELOW -> belowY(bounds, gapPx, handleReachPx)
        }
        val x = bounds.center.x - pillWidth / 2f
        val maxX = (screenWidth - insetRight - pillWidth).coerceAtLeast(insetLeft)
        return Offset(x.coerceIn(insetLeft, maxX), y.coerceAtLeast(insetTop))
    }

    private fun horizontalGap(rect: Rect, x: Float): Float = when {
        x < rect.left -> rect.left - x
        x > rect.right -> x - rect.right
        else -> 0f
    }

    private fun verticalGap(rect: Rect, y: Float): Float = when {
        y < rect.top -> rect.top - y
        y > rect.bottom -> y - rect.bottom
        else -> 0f
    }
}
