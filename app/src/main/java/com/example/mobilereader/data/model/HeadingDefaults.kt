package com.example.mobilereader.data.model


object HeadingDefaults {
    fun scale(level: Int): Float = when (level.coerceIn(1, 6)) {
        1 -> 1.50f
        2 -> 1.32f
        3 -> 1.18f
        4 -> 1.10f
        5 -> 1.04f
        else -> 1.00f
    }


    fun isOrnament(text: String): Boolean {
        val marks = text.filterNot { it.isWhitespace() }
        return marks.length in 1..12 && marks.all { it in "*⁂⁎✦✧❦❧◆◇" }
    }


    fun spaceBeforeEm(level: Int): Float = when (level.coerceIn(1, 6)) {
        1 -> 1.4f
        2 -> 1.2f
        3 -> 1.0f
        else -> 0.85f
    }

    fun spaceAfterEm(level: Int): Float = when (level.coerceIn(1, 6)) {
        1 -> 0.55f
        2 -> 0.50f
        3 -> 0.45f
        else -> 0.40f
    }
}
