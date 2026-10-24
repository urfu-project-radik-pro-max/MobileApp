package com.example.mobilereader.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.model.ContentElement
import kotlinx.coroutines.yield
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt


class PagePart(
    val itemIndex: Int,
    val element: ContentElement,

    val text: AnnotatedString? = null,

    val isParagraphStart: Boolean = true,

    val imageHeightPx: Int? = null,
    val imageWidthPx: Int? = null,

    val charStart: Int = -1,
    val charEnd: Int = -1,

    val rowStart: Int = -1,
    val rowEnd: Int = -1,
    val tableLayout: TableLayout? = null,

    val headerRepeated: Boolean = false,

    val allowsVerticalScroll: Boolean = false,
)

class BookPage(
    val parts: List<PagePart>,

    val firstItemIndex: Int,


    val firstCharOffset: Int = 0,
)


const val LAYOUT_ENGINE_VERSION = 15

class PaginationSpec(
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val density: Density,
    settings: ReaderSettings,
    val fontSize: Float,

    val bookFonts: Map<String, androidx.compose.ui.text.font.FontFamily> = emptyMap(),

    val embeddedFontSignature: String = "",

    val language: String? = null,
) {
    val settings: ReaderSettings = settings

    val key: String =
        "e$LAYOUT_ENGINE_VERSION | $contentWidthPx x $contentHeightPx | " +
            "$fontSize | ${settings.lineHeight} | " +
            "density=${density.density.toBits()}/${density.fontScale.toBits()} | " +
            "${settings.font} | ${settings.customFontPath} | " +
            "${settings.justify} | ${settings.hyphenation} | centerHeadings=${settings.centerHeadings} | " +
            "${settings.startChaptersOnNewPage} | ${settings.hideFootnotes} | " +
            "lang=$language | " +
            "embeddedFonts=$embeddedFontSignature"
}


suspend fun paginateBook(
    items: List<ReaderItem>,
    measurer: TextMeasurer,
    spec: PaginationSpec,
    fromIndex: Int = 0,
    toIndex: Int = items.size,
): List<BookPage> {
    val pages = mutableListOf<BookPage>()
    var parts = mutableListOf<PagePart>()
    var usedHeightPx = 0
    var firstItemIndex = 0
    var firstCharOffset = 0

    fun closePage() {
        if (parts.isNotEmpty()) {
            pages += BookPage(parts.toList(), firstItemIndex, firstCharOffset)
            parts = mutableListOf()
        }
        usedHeightPx = 0
    }

    fun addPart(part: PagePart, heightPx: Int, itemIndex: Int, charOffset: Int = 0) {
        if (parts.isEmpty()) {
            firstItemIndex = itemIndex
            firstCharOffset = charOffset
        }
        parts += part
        usedHeightPx += heightPx
    }

    with(spec.density) {
        val fontSizePx = spec.fontSize.sp.toPx()
        val maxImageHeightPx = ReaderMetrics.maxImageHeight.roundToPx()
        val contentWidthPx = spec.contentWidthPx.coerceAtLeast(1)

        for (index in fromIndex.coerceAtLeast(0) until toIndex.coerceAtMost(items.size)) {
            if (index % 32 == 0) yield()
            val item = items[index]
            if (spec.settings.startChaptersOnNewPage &&
                index > 0 &&
                item.chapterIndex != items[index - 1].chapterIndex &&
                parts.isNotEmpty()
            ) {
                closePage()
            }

            val element = item.element
            val (topPadding, bottomPadding) = ReaderMetrics.verticalPaddings(
                element,
                spec.fontSize,
            )
            val topPaddingPx = topPadding.roundToPx()
            val bottomPaddingPx = bottomPadding.roundToPx()

            when (element) {
                is ContentElement.Paragraph, is ContentElement.Heading -> {
                    val fullText = when (element) {
                        is ContentElement.Paragraph -> element.text
                        is ContentElement.Heading -> element.styledText
                    }
                    if (fullText.isEmpty()) {
                        val emptyHeightPx = topPaddingPx + bottomPaddingPx
                        if (emptyHeightPx > spec.contentHeightPx - usedHeightPx &&
                            parts.isNotEmpty()
                        ) {
                            closePage()
                        }
                        addPart(PagePart(index, element, text = fullText, charStart = 0, charEnd = 0),
                            emptyHeightPx, index)
                        continue
                    }
                    val (startInset, endInset) = ReaderMetrics.horizontalInsets(
                        element,
                        contentWidthPx.toDp(),
                        spec.fontSize,
                    )
                    val textWidthPx = (contentWidthPx -
                        startInset.roundToPx() - endInset.roundToPx()).coerceAtLeast(1)
                    var charOffset = 0
                    while (charOffset < fullText.length) {
                        val fragment = fullText.subSequence(charOffset, fullText.length)
                        val bidiText = BidiLayoutText.of(fragment)
                        val paragraphStart = charOffset == 0
                        val textStyle = ReaderMetrics.textStyle(
                            element,
                            spec.settings,
                            spec.fontSize,
                            isParagraphStart = paragraphStart,
                            bookFonts = spec.bookFonts,
                            language = spec.language,
                        )
                        val layout = measurer.measure(
                            text = bidiText.display,
                            style = textStyle,
                            constraints = Constraints(maxWidth = textWidthPx),
                            placeholders = inlineImagePlaceholders(bidiText.display),
                        )
                        val firstPaddingPx = if (paragraphStart) topPaddingPx else 0
                        val availableHeightPx = spec.contentHeightPx -
                            usedHeightPx - firstPaddingPx
                        if (layout.getLineBottom(0) > availableHeightPx &&
                            parts.isNotEmpty()
                        ) {
                            closePage()
                            continue
                        }
                        var endLine = 0
                        while (endLine + 1 < layout.lineCount &&
                            layout.getLineBottom(endLine + 1) <= availableHeightPx
                        ) {
                            endLine++
                        }
                        val lastLine = endLine == layout.lineCount - 1
                        var fragmentEnd = if (lastLine) {
                            fullText.length
                        } else {
                            charOffset + bidiText.sourceOffset(layout.getLineEnd(endLine))
                        }
                        if (fragmentEnd <= charOffset) {
                            val nextCodePoint = fullText.text.codePointAt(charOffset)
                            fragmentEnd = (charOffset + Character.charCount(nextCodePoint))
                                .coerceAtMost(fullText.length)
                        }
                        val completesElement = fragmentEnd == fullText.length
                        val endingPaddingPx = if (completesElement) bottomPaddingPx else 0
                        val measuredHeightPx = ceil(layout.getLineBottom(endLine)).toInt()
                        val totalHeightPx = firstPaddingPx + measuredHeightPx + endingPaddingPx
                        if (totalHeightPx > spec.contentHeightPx - usedHeightPx &&
                            parts.isNotEmpty()
                        ) {
                            closePage()
                            continue
                        }
                        addPart(
                            PagePart(
                                itemIndex = index,
                                element = element,
                                text = fullText.subSequence(charOffset, fragmentEnd),
                                isParagraphStart = paragraphStart,
                                charStart = charOffset,
                                charEnd = fragmentEnd,
                                allowsVerticalScroll = totalHeightPx > spec.contentHeightPx,
                            ),
                            totalHeightPx,
                            index,
                            charOffset,
                        )
                        charOffset = fragmentEnd
                        if (charOffset < fullText.length) closePage()
                    }
                }

                is ContentElement.Image -> {
                    val paddingPx = topPaddingPx + bottomPaddingPx
                    val imageSize = flowImageSizePx(
                        element = element,
                        contentWidthPx = contentWidthPx,
                        fontSizePx = fontSizePx,
                        maxHeightPx = min(maxImageHeightPx,
                            (spec.contentHeightPx - paddingPx).coerceAtLeast(1)),
                    )
                    if (imageSize.heightPx + paddingPx > spec.contentHeightPx - usedHeightPx &&
                        parts.isNotEmpty()
                    ) {
                        closePage()
                    }
                    addPart(
                        PagePart(
                            itemIndex = index,
                            element = element,
                            imageHeightPx = imageSize.heightPx,
                            imageWidthPx = imageSize.widthPx,
                        ),
                        imageSize.heightPx + paddingPx,
                        index,
                    )
                }

                is ContentElement.Table -> {
                    if (element.rows.isEmpty()) {
                        addPart(PagePart(index, element, rowStart = 0, rowEnd = 0),
                            topPaddingPx + bottomPaddingPx, index)
                        continue
                    }
                    val tableLayout = measureTableLayout(
                        element,
                        measurer,
                        spec.density,
                        spec.settings,
                        spec.fontSize,
                        contentWidthPx,
                        spec.language,
                        spec.bookFonts,
                    )
                    var startRow = 0
                    while (startRow < element.rows.size) {
                        val firstPaddingPx = if (startRow == 0) topPaddingPx else 0
                        val remainingHeightPx = spec.contentHeightPx -
                            usedHeightPx - firstPaddingPx
                        var endRow = TableGrid.rowsThatFit(
                            tableLayout.rowHeightsPx,
                            tableLayout.grid.noBreakAfterRow,
                            startRow,
                            remainingHeightPx,
                        )
                        if (endRow == startRow && parts.isNotEmpty()) {
                            closePage()
                            continue
                        }
                        if (endRow == startRow) {
                            endRow = firstUnbreakableTableGroupEnd(
                                startRow,
                                element.rows.size,
                                tableLayout.grid.noBreakAfterRow,
                            )
                        }
                        val finalPaddingPx =
                            if (endRow == element.rows.size) bottomPaddingPx else 0
                        val rowHeightPx = (startRow until endRow)
                            .sumOf { row -> tableLayout.rowHeightsPx[row] }
                        val partHeightPx = firstPaddingPx + rowHeightPx + finalPaddingPx
                        if (partHeightPx > spec.contentHeightPx - usedHeightPx &&
                            parts.isNotEmpty()
                        ) {
                            closePage()
                            continue
                        }
                        addPart(
                            PagePart(
                                itemIndex = index,
                                element = element,
                                rowStart = startRow,
                                rowEnd = endRow,
                                tableLayout = tableLayout,
                                charStart = startRow,
                                charEnd = endRow,
                                allowsVerticalScroll = partHeightPx > spec.contentHeightPx,
                            ),
                            partHeightPx,
                            index,
                            startRow,
                        )
                        startRow = endRow
                        if (startRow < element.rows.size) closePage()
                    }
                }

                ContentElement.Divider, is ContentElement.Spacer -> {
                    val contentHeightPx = when (element) {
                        ContentElement.Divider -> ReaderMetrics.dividerHeight.roundToPx()
                        is ContentElement.Spacer ->
                            ReaderMetrics.spacerHeight(element, spec.fontSize).roundToPx()
                    }
                    val partHeightPx = contentHeightPx + topPaddingPx + bottomPaddingPx
                    if (partHeightPx > spec.contentHeightPx - usedHeightPx &&
                        parts.isNotEmpty()
                    ) {
                        closePage()
                    }
                    addPart(PagePart(index, element), partHeightPx, index)
                }
            }
        }
    }

    closePage()
    return pages
}

internal fun firstUnbreakableTableGroupEnd(
    startRow: Int,
    rowCount: Int,
    noBreakAfterRow: BooleanArray,
): Int {
    if (rowCount <= 0) return 0
    var end = (startRow.coerceIn(0, rowCount - 1) + 1).coerceAtMost(rowCount)
    while (end < rowCount && noBreakAfterRow.getOrElse(end - 1) { false }) end++
    return end
}


internal data class FlowImageSize(val widthPx: Int, val heightPx: Int)

internal fun flowImageSizePx(
    element: ContentElement.Image,
    contentWidthPx: Int,
    fontSizePx: Float,
    maxHeightPx: Int,
): FlowImageSize {
    val availableWidth = contentWidthPx.coerceAtLeast(1)
    val availableHeight = maxHeightPx.coerceAtLeast(1)
    val heightPerWidth = imageAspectRatio(element.path) ?: 1.4f
    val requestedWidth = when {
        element.widthFrac != null -> availableWidth * element.widthFrac.coerceIn(0.01f, 1f)
        element.widthEm != null -> element.widthEm.coerceAtLeast(0.05f) * fontSizePx
        element.heightEm != null -> element.heightEm.coerceAtLeast(0.05f) * fontSizePx / heightPerWidth
        else -> availableWidth.toFloat()
    }
    val requestedHeight = element.heightEm?.let { it.coerceAtLeast(0.05f) * fontSizePx }
        ?: requestedWidth * heightPerWidth
    val fit = min(1f, min(availableWidth / requestedWidth, availableHeight / requestedHeight))
    return FlowImageSize(
        widthPx = (requestedWidth * fit).roundToInt().coerceIn(1, availableWidth),
        heightPx = (requestedHeight * fit).roundToInt().coerceIn(1, availableHeight),
    )
}

internal fun imageAspectRatio(path: String): Float? {
    val rasterAspectRatio = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.outHeight.toFloat() / options.outWidth
        } else {
            null
        }
    }.getOrNull()
    return rasterAspectRatio ?: svgFileAspectRatio(path)
}

private fun svgFileAspectRatio(path: String): Float? {
    if (!path.endsWith(".svg", ignoreCase = true)) return null
    val head = runCatching { java.io.File(path).readText().take(4096) }.getOrNull()
        ?: return null
    return svgAspectRatio(head)
}


internal fun svgAspectRatio(markup: String): Float? {
    fun length(name: String): Float? {
        val raw = Regex("""(?i)\b$name\s*=\s*["']([^"']+)["']""")
            .find(markup)?.groupValues?.get(1)?.trim() ?: return null
        if ('%' in raw) return null
        return Regex("""\d+(\.\d+)?""").find(raw)?.value?.toFloatOrNull()
            ?.takeIf { it > 0f }
    }

    val width = length("width")
    val height = length("height")
    if (width != null && height != null) return height / width

    val viewBox = Regex("""(?i)viewBox\s*=\s*["']([^"']+)["']""")
        .find(markup)?.groupValues?.get(1)
        ?.trim()?.split(Regex("""[\s,]+"""))
        ?.mapNotNull { it.toFloatOrNull() }
    if (viewBox != null && viewBox.size == 4 && viewBox[2] > 0f && viewBox[3] > 0f) {
        return viewBox[3] / viewBox[2]
    }
    return null
}


internal fun retreatToWordBoundary(text: String, start: Int, end: Int): Int {
    if (end >= text.length) return end
    if (text[end - 1].isWhitespace() || text[end].isWhitespace()) return end
    var i = end - 1
    while (i > start && !text[i].isWhitespace()) i--
    return if (i <= start) end else i + 1
}
