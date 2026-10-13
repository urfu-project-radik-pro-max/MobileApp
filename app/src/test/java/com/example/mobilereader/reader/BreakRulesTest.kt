package com.example.mobilereader.reader

import com.example.mobilereader.ui.reader.BreakRules
import com.example.mobilereader.ui.reader.BreakRules.SplitDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakRulesTest {

    private fun decide(
        startLine: Int,
        endLine: Int,
        lineCount: Int,
        isFirstFragment: Boolean = true,
        pageHasOtherContent: Boolean = true,
    ) = BreakRules.splitDecision(startLine, endLine, lineCount, isFirstFragment, pageHasOtherContent)

    @Test
    fun `plenty of lines on both sides - place as computed`() {
        assertEquals(SplitDecision.Place, decide(startLine = 0, endLine = 4, lineCount = 10))
    }

    @Test
    fun `single-line widow - cut one line earlier`() {

        assertEquals(SplitDecision.PlaceFewer(2), decide(startLine = 0, endLine = 3, lineCount = 5))
    }

    @Test
    fun `orphan after widow adjustment - move paragraph to next page`() {

        assertEquals(SplitDecision.MoveToNextPage, decide(startLine = 0, endLine = 1, lineCount = 3))
    }

    @Test
    fun `single fitting line of a new paragraph - move to next page`() {
        assertEquals(SplitDecision.MoveToNextPage, decide(startLine = 0, endLine = 0, lineCount = 4))
    }

    @Test
    fun `empty page never moves - degenerate placement allowed`() {
        assertEquals(
            SplitDecision.Place,
            decide(startLine = 0, endLine = 1, lineCount = 3, pageHasOtherContent = false),
        )
        assertEquals(
            SplitDecision.Place,
            decide(startLine = 0, endLine = 0, lineCount = 4, pageHasOtherContent = false),
        )
    }

    @Test
    fun `continuation fragment ignores the orphan rule but honors widows`() {

        assertEquals(
            SplitDecision.PlaceFewer(7),
            decide(startLine = 0, endLine = 8, lineCount = 10, isFirstFragment = false, pageHasOtherContent = false),
        )

        assertEquals(
            SplitDecision.PlaceFewer(5),
            decide(startLine = 3, endLine = 6, lineCount = 8, isFirstFragment = false, pageHasOtherContent = false),
        )
    }

    @Test
    fun `widow adjustment eating the whole fragment on a used page - move`() {

        assertEquals(
            SplitDecision.MoveToNextPage,
            decide(startLine = 0, endLine = 0, lineCount = 2, isFirstFragment = true),
        )
    }

    @Test
    fun `continuation with nothing left after widow rule on empty page - place`() {
        assertEquals(
            SplitDecision.Place,
            decide(startLine = 5, endLine = 5, lineCount = 7, isFirstFragment = false, pageHasOtherContent = false),
        )
    }

    @Test
    fun `heading keep-with-next matrix`() {

        assertTrue(BreakRules.headingFits(0, 500, pageHasOtherContent = false))

        assertTrue(BreakRules.headingFits(300, 200, pageHasOtherContent = true))

        assertFalse(BreakRules.headingFits(100, 200, pageHasOtherContent = true))
    }
}
