package com.example.mobilereader.ui.reader

import java.text.BreakIterator


object SideBoxRules {


    private const val OPENING_PUNCTUATION = "«“„\"'‘—–‒("


    const val MIN_REMAINDER_CHARS = 20


    fun besideLineCount(lineBottoms: FloatArray, boxHeightPx: Int): Int {
        for (i in lineBottoms.indices) {
            if (lineBottoms[i] >= boxHeightPx) return i + 1
        }
        return lineBottoms.size
    }


    fun capPrefix(text: String, explicitLength: Int? = null): String? {
        if (text.length < MIN_REMAINDER_CHARS) return null
        if (explicitLength != null) {
            if (explicitLength !in 1..text.length) return null
            if (text.length - explicitLength < MIN_REMAINDER_CHARS) return null
            val prefix = text.substring(0, explicitLength)
            if (prefix.codePoints().noneMatch(Character::isLetter)) return null
            return prefix
        }
        var i = 0
        while (i < text.length && i < 2 && text[i] in OPENING_PUNCTUATION) i++

        if (i >= text.length || !Character.isLetter(text.codePointAt(i))) return null
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        val end = iterator.following(i)
        if (end <= i) return null
        if (text.length - end < MIN_REMAINDER_CHARS) return null
        return text.substring(0, end)
    }
}
