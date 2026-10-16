package com.example.mobilereader.ui.reader

import com.example.mobilereader.data.translation.ContextualTranslationException
import com.example.mobilereader.data.translation.ContextualTranslationProblem
import com.example.mobilereader.ui.reader.selection.BookSelection
import com.example.mobilereader.ui.reader.selection.SelectionText

data class ContextualTranslationContext(
    val selectedText: String,
    val surroundingParagraph: String,
    val context: String,
)

object ContextualTranslationContextBuilder {
    private const val maximumSelectedTextLength = 4_000
    private const val maximumContextLength = 12_000
    private const val approximatePageLength = 1_800

    fun build(
        items: List<ReaderItem>,
        selection: BookSelection,
        contextPageCount: Int,
        pages: List<BookPage>?,
    ): ContextualTranslationContext {
        if (items.isEmpty()) {
            throw ContextualTranslationException(ContextualTranslationProblem.EMPTY_BOOK)
        }
        if (selection.start.itemIndex !in items.indices || selection.end.itemIndex !in items.indices) {
            throw ContextualTranslationException(ContextualTranslationProblem.INVALID_SELECTION)
        }
        val selectedText = SelectionText.extract(selection, items.size) { index -> items[index].element }
        if (selectedText.isBlank()) {
            throw ContextualTranslationException(ContextualTranslationProblem.EMPTY_SELECTION)
        }
        if (selectedText.length > maximumSelectedTextLength) {
            throw ContextualTranslationException(ContextualTranslationProblem.SELECTED_TEXT_TOO_LONG)
        }

        val selectedIndices = (selection.start.itemIndex..selection.end.itemIndex).filter { index ->
            val textLength = SelectionText.elementText(items[index].element)?.length ?: return@filter false
            val startOffset = if (index == selection.start.itemIndex) selection.start.charOffset else 0
            val endOffset = if (index == selection.end.itemIndex) selection.end.charOffset else textLength
            endOffset.coerceAtMost(textLength) > startOffset.coerceAtLeast(0)
        }
        val surroundingParagraph = selectedIndices.mapNotNull { index ->
            SelectionText.elementText(items[index].element)?.trim()?.takeIf(String::isNotEmpty)
        }.joinToString("\n\n")
        if (surroundingParagraph.length > maximumContextLength) {
            throw ContextualTranslationException(ContextualTranslationProblem.PARAGRAPH_TOO_LONG)
        }

        val lastSelectedIndex = selectedIndices.lastOrNull() ?: selection.start.itemIndex
        val lastSelectedOffset = if (lastSelectedIndex == selection.end.itemIndex) {
            (selection.end.charOffset - 1).coerceAtLeast(0)
        } else {
            (SelectionText.elementText(items[lastSelectedIndex].element)?.length ?: 1) - 1
        }
        val candidateIndices = if (pages.isNullOrEmpty()) {
            collectScrollContext(items, selection.start.itemIndex, contextPageCount)
        } else {
            collectPageContext(pages, lastSelectedIndex, lastSelectedOffset, contextPageCount)
        }
        val remainingLength = maximumContextLength - surroundingParagraph.length
        var additionalLength = 0
        val precedingIndices = candidateIndices.asReversed().filter { index ->
            if (index >= selection.start.itemIndex || index in selectedIndices) return@filter false
            val length = SelectionText.elementText(items[index].element)?.length ?: return@filter false
            if (additionalLength + length > remainingLength) return@filter false
            additionalLength += length
            true
        }
        val followingIndices = candidateIndices.filter { index ->
            if (index <= selection.end.itemIndex || index in selectedIndices) return@filter false
            val length = SelectionText.elementText(items[index].element)?.length ?: return@filter false
            if (additionalLength + length > remainingLength) return@filter false
            additionalLength += length
            true
        }
        val contextIndices = (precedingIndices + selectedIndices + followingIndices).distinct().sorted()
        val context = contextIndices.mapNotNull { index ->
            SelectionText.elementText(items[index].element)?.trim()?.takeIf(String::isNotEmpty)
        }.joinToString("\n\n")

        return ContextualTranslationContext(
            selectedText = selectedText,
            surroundingParagraph = surroundingParagraph,
            context = context,
        )
    }

    private fun collectPageContext(
        pages: List<BookPage>,
        selectedItemIndex: Int,
        selectedCharacterOffset: Int,
        contextPageCount: Int,
    ): List<Int> {
        val selectedPageIndex = pages.indexOfLast { page ->
            page.firstItemIndex < selectedItemIndex ||
                (page.firstItemIndex == selectedItemIndex &&
                    page.firstCharOffset <= selectedCharacterOffset)
        }.coerceAtLeast(0)
        val firstPageIndex = (selectedPageIndex - contextPageCount.coerceIn(1, 5) + 1).coerceAtLeast(0)
        return pages.subList(firstPageIndex, selectedPageIndex + 1)
            .flatMap { page -> page.parts.map(PagePart::itemIndex) }
            .distinct()
    }

    private fun collectScrollContext(
        items: List<ReaderItem>,
        selectedItemIndex: Int,
        contextPageCount: Int,
    ): List<Int> {
        val targetLength = approximatePageLength * contextPageCount.coerceIn(1, 5)
        val indices = mutableListOf<Int>()
        var collectedLength = 0
        for (index in selectedItemIndex downTo 0) {
            val length = SelectionText.elementText(items[index].element)?.length ?: continue
            indices.add(index)
            collectedLength += length
            if (collectedLength >= targetLength) break
        }
        return indices.asReversed()
    }
}
