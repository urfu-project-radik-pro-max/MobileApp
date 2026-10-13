package com.example.mobilereader.ui.reader


object BreakRules {


    const val MIN_ORPHAN_LINES = 2


    const val MIN_WIDOW_LINES = 2

    sealed interface SplitDecision {

        data object Place : SplitDecision


        data class PlaceFewer(val endLine: Int) : SplitDecision


        data object MoveToNextPage : SplitDecision
    }


    fun splitDecision(
        startLine: Int,
        endLine: Int,
        lineCount: Int,
        isFirstFragment: Boolean,
        pageHasOtherContent: Boolean,
    ): SplitDecision {
        var end = endLine

        val linesAfter = lineCount - 1 - end
        if (linesAfter in 1 until MIN_WIDOW_LINES) {
            end -= MIN_WIDOW_LINES - linesAfter
        }

        val linesHere = end - startLine + 1
        if (isFirstFragment && linesHere < MIN_ORPHAN_LINES) {
            return if (pageHasOtherContent) SplitDecision.MoveToNextPage else SplitDecision.Place
        }
        if (end < startLine) {
            return if (pageHasOtherContent) SplitDecision.MoveToNextPage else SplitDecision.Place
        }
        return if (end == endLine) SplitDecision.Place else SplitDecision.PlaceFewer(end)
    }


    fun headingFits(
        remainingAfterHeadingPx: Int,
        requiredNextPx: Int,
        pageHasOtherContent: Boolean,
    ): Boolean = !pageHasOtherContent || remainingAfterHeadingPx >= requiredNextPx
}
