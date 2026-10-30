package com.example.mobilereader.ui.reader

import kotlin.math.abs

internal enum class ReaderTapTarget {
    LEFT,
    RIGHT,
    CENTER,
    NONE,
}

internal fun readerTapTarget(
    x: Float,
    width: Float,
    edgeTapPagingEnabled: Boolean,
): ReaderTapTarget {
    if (x < width * 0.22f) {
        return if (edgeTapPagingEnabled) ReaderTapTarget.LEFT else ReaderTapTarget.NONE
    }
    if (x > width * 0.78f) {
        return if (edgeTapPagingEnabled) ReaderTapTarget.RIGHT else ReaderTapTarget.NONE
    }
    return ReaderTapTarget.CENTER
}

internal fun canStartBrightnessGesture(
    x: Float,
    width: Float,
    brightnessGestureEnabled: Boolean,
): Boolean {
    return brightnessGestureEnabled && x <= width * 0.12f
}

internal class ReaderTapGesture(
    private val startX: Float,
    private val startY: Float,
    private val touchSlop: Float,
) {
    var canTap: Boolean = true
        private set

    fun observe(pointerCount: Int, x: Float, y: Float, consumed: Boolean) {
        if (pointerCount != 1 || consumed) {
            canTap = false
        }
        if (abs(x - startX) > touchSlop || abs(y - startY) > touchSlop) {
            canTap = false
        }
    }

    fun cancel() {
        canTap = false
    }
}
