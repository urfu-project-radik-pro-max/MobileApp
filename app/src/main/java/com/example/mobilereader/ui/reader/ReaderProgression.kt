package com.example.mobilereader.ui.reader

import com.example.mobilereader.data.model.PageProgression
import com.example.mobilereader.data.parser.LanguageTag


internal object ReaderProgression {

    fun resolve(declared: PageProgression, language: String?): PageProgression = when (declared) {
        PageProgression.LTR -> PageProgression.LTR
        PageProgression.RTL -> PageProgression.RTL
        PageProgression.DEFAULT -> if (isRtlLanguage(language)) {
            PageProgression.RTL
        } else {
            PageProgression.LTR
        }
    }


    fun horizontalTapDelta(leftEdge: Boolean, progression: PageProgression): Int {
        val rtl = progression == PageProgression.RTL
        return when {
            leftEdge && rtl -> 1
            leftEdge -> -1
            rtl -> -1
            else -> 1
        }
    }


    fun selectionPageDelta(physicalDirection: Int, progression: PageProgression): Int {
        require(physicalDirection in -1..1)
        return if (progression == PageProgression.RTL) {
            -physicalDirection
        } else {
            physicalDirection
        }
    }


    fun usesReversePagerLayout(
        progression: PageProgression,
        uiLayoutIsRtl: Boolean = false,
    ): Boolean = (progression == PageProgression.RTL) xor uiLayoutIsRtl

    internal fun isRtlLanguage(language: String?): Boolean = LanguageTag.isRtl(language)
}
