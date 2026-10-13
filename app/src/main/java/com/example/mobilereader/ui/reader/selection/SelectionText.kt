package com.example.mobilereader.ui.reader.selection

import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.INLINE_IMAGE_CHAR
import java.text.BreakIterator


object SelectionText {


    private const val ROW_SEPARATOR = "\n"
    private const val CELL_SEPARATOR = "  "


    private const val PARAGRAPH_SEPARATOR = "\n\n"


    fun elementText(element: ContentElement): String? = when (element) {
        is ContentElement.Paragraph -> element.text.text
        is ContentElement.Heading -> element.text
        is ContentElement.Table -> element.flatText()
        is ContentElement.Image, is ContentElement.Spacer, ContentElement.Divider -> null
    }


    private val IMAGE_PLACEHOLDER = INLINE_IMAGE_CHAR.single()


    private fun Char.isWordish() = !isWhitespace() && this != IMAGE_PLACEHOLDER


    fun wordAt(text: String, offset: Int): CharSpan {
        if (text.isEmpty()) return CharSpan(0, 0)
        val at = offset.coerceIn(0, text.length)


        var probe = when {
            at < text.length && text[at].isWordish() -> at
            at > 0 && text[at - 1].isWordish() -> at - 1
            else -> at.coerceAtMost(text.length - 1)
        }
        if (text[probe] == IMAGE_PLACEHOLDER) {


            probe = nearestWordish(text, probe) ?: return CharSpan(probe, probe + 1)
        }
        if (text[probe].isWhitespace()) {


            return CharSpan(probe, probe + 1)
        }

        val iterator = BreakIterator.getWordInstance()
        iterator.setText(text)
        val start = if (iterator.isBoundary(probe)) probe else iterator.preceding(probe)
        val end = iterator.following(probe)
        return CharSpan(
            start = if (start == BreakIterator.DONE) 0 else start,
            end = if (end == BreakIterator.DONE) text.length else end,
        )
    }


    private fun nearestWordish(text: String, from: Int): Int? {
        for (i in from + 1 until text.length) if (text[i].isWordish()) return i
        for (i in from - 1 downTo 0) if (text[i].isWordish()) return i
        return null
    }


    fun extract(
        selection: BookSelection,
        count: Int,
        elementAt: (Int) -> ContentElement,
    ): String {
        if (selection.isEmpty) return ""
        val builder = StringBuilder()
        val from = selection.start.itemIndex.coerceAtLeast(0)
        val to = selection.end.itemIndex.coerceAtMost(count - 1)
        for (index in from..to) {
            val text = elementText(elementAt(index)) ?: continue
            val start = if (index == selection.start.itemIndex) selection.start.charOffset else 0
            val end = if (index == selection.end.itemIndex) selection.end.charOffset else text.length
            val low = start.coerceIn(0, text.length)
            val high = end.coerceIn(low, text.length)
            if (high <= low) continue
            val piece = text.substring(low, high)
                .replace(INLINE_IMAGE_CHAR, "")
                .trim()
            if (piece.isEmpty()) continue
            if (builder.isNotEmpty()) builder.append(PARAGRAPH_SEPARATOR)
            builder.append(piece)
        }
        return builder.toString()
    }


    fun tableCellSpans(table: ContentElement.Table): List<List<CharSpan>> {
        var offset = 0
        return table.rows.mapIndexed { rowIndex, row ->
            if (rowIndex > 0) offset += ROW_SEPARATOR.length
            row.cells.mapIndexed { cellIndex, cell ->
                if (cellIndex > 0) offset += CELL_SEPARATOR.length
                val length = cell.text.text.length
                CharSpan(offset, offset + length).also { offset += length }
            }
        }
    }
}
