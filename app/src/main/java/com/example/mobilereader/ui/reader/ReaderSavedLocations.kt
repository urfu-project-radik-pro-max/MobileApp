package com.example.mobilereader.ui.reader

import com.example.mobilereader.data.model.BookTextPosition
import com.example.mobilereader.data.model.Bookmark
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.Quote
import com.example.mobilereader.ui.reader.selection.BookAnchor
import com.example.mobilereader.ui.reader.selection.BookSelection
import com.example.mobilereader.ui.reader.selection.SelectionText
import java.security.MessageDigest

internal fun ReaderState.Ready.savedPosition(flatIndex: Int, charOffset: Int): BookTextPosition? {
    val item = items.getOrNull(flatIndex) ?: return null
    val chapterIndex = chapterAt(flatIndex)
    val chapterStart = chapterStarts.getOrNull(chapterIndex) ?: return null
    val elementIndex = flatIndex - chapterStart
    if (elementIndex < 0) return null
    val text = SelectionText.elementText(item.element)
    if (charOffset < 0 || charOffset > (text?.length ?: 0)) return null
    return BookTextPosition(
        chapterIndex = chapterIndex,
        elementIndex = elementIndex,
        charOffset = charOffset,
        elementFingerprint = elementFingerprint(item.element),
    )
}

internal fun ReaderState.Ready.resolvePosition(position: BookTextPosition?): BookAnchor? {
    position ?: return null
    val chapterStart = chapterStarts.getOrNull(position.chapterIndex) ?: return null
    val chapterEnd = chapterStarts.getOrNull(position.chapterIndex + 1) ?: items.size
    val flatIndex = chapterStart + position.elementIndex
    if (flatIndex !in chapterStart until chapterEnd) return null
    val element = items[flatIndex].element
    val textLength = SelectionText.elementText(element)?.length ?: 0
    if (position.charOffset !in 0..textLength) return null
    if (elementFingerprint(element) != position.elementFingerprint) return null
    return BookAnchor(flatIndex, position.charOffset)
}

internal fun ReaderState.Ready.bookmarkAnchor(bookmark: Bookmark): BookAnchor? =
    resolvePosition(bookmark.textPosition)

internal fun ReaderState.Ready.quoteRange(quote: Quote): BookSelection? {
    val start = resolvePosition(quote.startPosition) ?: return null
    val end = resolvePosition(quote.endPosition) ?: return null
    if (start >= end) return null
    val range = BookSelection(start, end)
    val actualText = SelectionText.extract(range, items.size) { items[it].element }
    return range.takeIf { actualText == quote.text }
}

private fun elementFingerprint(element: ContentElement): String {
    val identity = when (element) {
        is ContentElement.Paragraph -> "paragraph:${element.text.text}"
        is ContentElement.Heading -> "heading:${element.text}"
        is ContentElement.Table -> "table:${element.flatText()}"
        is ContentElement.Image -> "image:${element.path.substringAfterLast('/').substringAfterLast('\\')}:${element.altText.orEmpty()}"
        is ContentElement.Spacer -> "spacer"
        ContentElement.Divider -> "divider"
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
