package com.example.mobilereader.ui.reader

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix


private val invertMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)


internal fun imageColorFilter(invert: Boolean): ColorFilter? =
    if (invert) ColorFilter.colorMatrix(invertMatrix) else null
