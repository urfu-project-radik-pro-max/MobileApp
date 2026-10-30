package com.example.mobilereader.ui.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.model.BlockAlign
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.ui.reader.selection.ReaderHighlights
import com.example.mobilereader.ui.reader.selection.SelectionText
import com.example.mobilereader.ui.reader.selection.readerHighlights
import com.example.mobilereader.ui.reader.selection.rememberTextFragment

private data class TableCellPaddingPx(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

private fun tableCellPaddingPx(defaultPx: Int): TableCellPaddingPx =
    TableCellPaddingPx(defaultPx, defaultPx, defaultPx, defaultPx)


class TableLayout(
    val grid: TableGrid.Grid,
    val colWidthsPx: IntArray,
    val rowHeightsPx: IntArray,

    val fontScale: Float,
) {
    val tableWidthPx: Int get() = colWidthsPx.sum()
}


fun measureTableLayout(
    table: ContentElement.Table,
    measurer: TextMeasurer,
    density: Density,
    settings: ReaderSettings,
    fontSize: Float,
    contentWidthPx: Int,
    language: String?,
    bookFonts: Map<String, FontFamily> = emptyMap(),
): TableLayout = with(density) {
    val grid = TableGrid.build(table.rows)
    val columnCount = grid.columnCount.coerceAtLeast(1)
    val defaultCellPadPx = ReaderMetrics.tableCellPadding.roundToPx()
    val availablePx = contentWidthPx.coerceAtLeast(1)

    class SpanIntrinsic(val col: Int, val span: Int, val minPx: Int, val maxPx: Int)

    var chosenScale = 1f
    var widths = IntArray(columnCount)
    for (scale in floatArrayOf(1f, 0.85f, 0.7f)) {
        chosenScale = scale
        val minW = IntArray(columnCount)
        val maxW = IntArray(columnCount)
        val spans = mutableListOf<SpanIntrinsic>()
        for ((r, row) in table.rows.withIndex()) {
            for ((ci, cell) in row.cells.withIndex()) {
                val col = grid.cellColumns[r][ci].coerceAtMost(columnCount - 1)
                val span = cell.colSpan.coerceIn(1, 10).coerceAtMost(columnCount - col)
                val padding = tableCellPaddingPx(defaultCellPadPx)
                val style = ReaderMetrics.tableCellStyle(
                    settings, fontSize, scale, cell.header, language,
                    table.block, cell.block, bookFonts,
                )
                val bidiCell = BidiLayoutText.of(cell.text)
                val maxIntrinsic = measurer
                    .measure(
                        bidiCell.display,
                        style,
                        placeholders = tableCellPlaceholders(
                            text = bidiCell.display,
                            availableWidthPx = (availablePx - padding.left - padding.right)
                                .coerceAtLeast(1),
                            fontSizePx = style.fontSize.toPx(),
                        ),
                        constraints = Constraints(),
                    )
                    .size.width + padding.left + padding.right
                val minIntrinsic = tableCellMinIntrinsicWidthPx(
                    text = cell.text,
                    availableWidthPx = (availablePx - padding.left - padding.right).coerceAtLeast(1),
                    fontSizePx = style.fontSize.toPx(),
                ) { run, placeholders ->
                    val bidiRun = BidiLayoutText.of(run)
                    measurer.measure(
                        text = bidiRun.display,
                        style = style,
                        placeholders = if (bidiRun.hasControls) {
                            tableCellPlaceholders(
                                text = bidiRun.display,
                                availableWidthPx = (availablePx - padding.left - padding.right)
                                    .coerceAtLeast(1),
                                fontSizePx = style.fontSize.toPx(),
                            )
                        } else {
                            placeholders
                        },
                        constraints = Constraints(),
                    ).size.width
                } + padding.left + padding.right
                if (span <= 1) {
                    if (minIntrinsic > minW[col]) minW[col] = minIntrinsic
                    if (maxIntrinsic > maxW[col]) maxW[col] = maxIntrinsic
                } else {
                    spans += SpanIntrinsic(col, span, minIntrinsic, maxIntrinsic)
                }
            }
        }

        for (span in spans) {
            TableGrid.spreadSpan(minW, span.col, span.span, span.minPx)
            TableGrid.spreadSpan(maxW, span.col, span.span, span.maxPx)
        }
        for (i in 0 until columnCount) if (maxW[i] < minW[i]) maxW[i] = minW[i]
        widths = TableGrid.distributeColumns(minW, maxW, availablePx)
        var sumMin = 0L
        for (w in minW) sumMin += w
        if (sumMin <= availablePx) break
    }


    class SpanCell(val row: Int, val rowSpan: Int, val heightPx: Int)

    val rowHeights = IntArray(table.rows.size)
    val rowSpanCells = mutableListOf<SpanCell>()
    for ((r, row) in table.rows.withIndex()) {
        var height = 0
        for ((ci, cell) in row.cells.withIndex()) {
            val col = grid.cellColumns[r][ci].coerceAtMost(columnCount - 1)
            val span = cell.colSpan.coerceIn(1, 10).coerceAtMost(columnCount - col)
            var cellWidth = 0
            for (c in col until col + span) cellWidth += widths[c]
            val padding = tableCellPaddingPx(defaultCellPadPx)
            val style = ReaderMetrics.tableCellStyle(
                settings, fontSize, chosenScale, cell.header, language,
                table.block, cell.block, bookFonts,
            )
            val bidiCell = BidiLayoutText.of(cell.text)
            val textHeight = measurer.measure(
                text = bidiCell.display,
                style = style,
                placeholders = tableCellPlaceholders(
                    text = bidiCell.display,
                    availableWidthPx = (cellWidth - padding.left - padding.right).coerceAtLeast(1),
                    fontSizePx = style.fontSize.toPx(),
                ),
                constraints = Constraints(
                    maxWidth = (cellWidth - padding.left - padding.right).coerceAtLeast(1),
                ),
            ).size.height
            val total = textHeight + padding.top + padding.bottom
            val rowSpan = cell.rowSpan.coerceIn(1, 20).coerceAtMost(table.rows.size - r)
            if (rowSpan <= 1) {
                if (total > height) height = total
            } else {
                rowSpanCells += SpanCell(r, rowSpan, total)
            }
        }
        rowHeights[r] = height
    }


    for (spanCell in rowSpanCells) {
        val end = spanCell.row + spanCell.rowSpan
        var sum = 0
        for (r in spanCell.row until end) sum += rowHeights[r]
        if (spanCell.heightPx > sum) rowHeights[end - 1] += spanCell.heightPx - sum
    }

    TableLayout(grid, widths, rowHeights, chosenScale)
}

internal fun tableCellMinIntrinsicWidthPx(
    text: AnnotatedString,
    availableWidthPx: Int,
    fontSizePx: Float,
    measure: (AnnotatedString, List<AnnotatedString.Range<Placeholder>>) -> Int,
): Int {
    if (text.isEmpty()) return 0

    val runs = NON_WHITESPACE_RUN.findAll(text.text).toList()
    if (runs.isEmpty()) return 0
    val imageMarks = tableCellPlaceholders(text, availableWidthPx, fontSizePx)
    val candidateRanges = linkedSetOf<IntRange>()

    runs.sortedByDescending { it.value.length }
        .take(3)
        .forEach { candidateRanges += it.range }


    if (imageMarks.isNotEmpty()) {
        val orderedMarks = imageMarks.sortedBy { it.start }
        var markIndex = 0
        for (run in runs) {
            val runStart = run.range.first
            val runEnd = run.range.last + 1
            while (markIndex < orderedMarks.size && orderedMarks[markIndex].end <= runStart) {
                markIndex++
            }
            if (markIndex < orderedMarks.size &&
                orderedMarks[markIndex].start < runEnd &&
                orderedMarks[markIndex].end > runStart
            ) {
                candidateRanges += run.range
            }
        }
    }

    var widest = 0
    for (range in candidateRanges) {
        val fragment = text.subSequence(range.first, range.last + 1)
        val width = measure(fragment, tableCellPlaceholders(fragment, availableWidthPx, fontSizePx))
        if (width > widest) widest = width
    }
    return widest
}

private val NON_WHITESPACE_RUN = Regex("\\S+")


@Composable
fun TableBlock(
    table: ContentElement.Table,
    layout: TableLayout,
    rowStart: Int,
    rowEnd: Int,
    headerRepeated: Boolean,
    settings: ReaderSettings,
    fontSize: Float,
    language: String?,
    bookFonts: Map<String, FontFamily> = emptyMap(),
    invertImages: Boolean = false,
    footnotes: FootnoteHandler? = null,
    searchHighlight: String? = null,
    colors: ReaderColors,

    highlights: ReaderHighlights? = null,
    itemIndex: Int = -1,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cellSpans = remember(table) { SelectionText.tableCellSpans(table) }

    class CellPlacement(
        val row: Int,
        val cellIndex: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    val placements = remember(table, layout, rowStart, rowEnd, headerRepeated) {
        val columnCount = layout.grid.columnCount.coerceAtLeast(1)
        val colOffsets = IntArray(columnCount + 1)
        for (c in 1..columnCount) colOffsets[c] = colOffsets[c - 1] + layout.colWidthsPx[c - 1]
        val rtlColumns = ReaderMetrics.isRtl(table)

        val drawRows = buildList {
            if (headerRepeated && rowStart > 0) add(0)
            for (r in rowStart.coerceAtLeast(0) until rowEnd.coerceAtMost(table.rows.size)) add(r)
        }
        val rowOffsets = IntArray(drawRows.size)
        var acc = 0
        for (i in drawRows.indices) {
            rowOffsets[i] = acc
            acc += layout.rowHeightsPx[drawRows[i]]
        }

        val list = mutableListOf<CellPlacement>()
        for ((visual, r) in drawRows.withIndex()) {
            val row = table.rows[r]
            for ((ci, cell) in row.cells.withIndex()) {
                val col = layout.grid.cellColumns[r][ci].coerceAtMost(columnCount - 1)
                val span = cell.colSpan.coerceIn(1, 10).coerceAtMost(columnCount - col)
                val rowSpan = cell.rowSpan.coerceIn(1, 20)

                val spanEnd = if (r == 0 && headerRepeated && rowStart > 0) {
                    1
                } else {
                    (r + rowSpan).coerceAtMost(rowEnd)
                }
                var height = 0
                for (rr in r until spanEnd) height += layout.rowHeightsPx[rr]
                if (height == 0) height = layout.rowHeightsPx[r]
                list += CellPlacement(
                    row = r,
                    cellIndex = ci,
                    x = tableCellPhysicalX(colOffsets, col, span, rtlColumns),
                    y = rowOffsets[visual],
                    width = colOffsets[col + span] - colOffsets[col],
                    height = height,
                )
            }
        }
        list
    }

    val borderColor = colors.secondaryText.copy(alpha = 0.35f)
    val defaultCellPaddingPx = with(density) { ReaderMetrics.tableCellPadding.roundToPx() }
    val placementPaddings = remember(
        table,
        placements,
        defaultCellPaddingPx,
    ) {
        placements.map {
            tableCellPaddingPx(defaultCellPaddingPx)
        }
    }
    Layout(
        modifier = modifier.drawBehind {
            val stroke = Stroke(width = 1f)
            for (p in placements) {
                drawRect(
                    color = borderColor,
                    topLeft = Offset(p.x.toFloat(), p.y.toFloat()),
                    size = Size(p.width.toFloat(), p.height.toFloat()),
                    style = stroke,
                )
            }
        },
        content = {
            for ((placementIndex, p) in placements.withIndex()) {
                val cell = table.rows[p.row].cells[p.cellIndex]
                val cellPadding = placementPaddings[placementIndex]
                val align = cell.align ?: if (cell.header) BlockAlign.CENTER else null
                val decorated = remember(
                    cell.text, footnotes, searchHighlight, colors.accent,
                ) {
                    cell.text.withoutPublisherColors()
                        .withFootnoteLinks(colors.accent, footnotes)
                        .withSearchHighlight(searchHighlight, colors.accent.copy(alpha = 0.3f))
                }
                val bidiDisplay = remember(decorated) { BidiLayoutText.of(decorated) }
                val display = bidiDisplay.display


                val fragment = rememberTextFragment(
                    highlights = highlights,
                    itemIndex = itemIndex,
                    charStart = cellSpans.getOrNull(p.row)?.getOrNull(p.cellIndex)?.start ?: 0,
                    length = cell.text.length,
                    bidi = bidiDisplay,
                )
                val textStyle = ReaderMetrics.tableCellStyle(
                    settings,
                    fontSize,
                    layout.fontScale,
                    cell.header,
                    language,
                    table.block,
                    cell.block,
                    bookFonts,
                ).copy(
                    color = colors.text,
                    textAlign = when (align) {
                        BlockAlign.CENTER -> TextAlign.Center
                        BlockAlign.END -> TextAlign.End
                        BlockAlign.LEFT -> TextAlign.Left
                        BlockAlign.RIGHT -> TextAlign.Right
                        else -> TextAlign.Start
                    },
                )
                val availableTextWidthPx = (p.width - cellPadding.left - cellPadding.right)
                    .coerceAtLeast(1)
                val textFontSizePx = with(density) { textStyle.fontSize.toPx() }
                Text(
                    text = display,
                    inlineContent = tableCellInlineContent(
                        text = display,
                        availableWidthPx = availableTextWidthPx,
                        fontSizePx = textFontSizePx,
                        invertImages = invertImages,
                    ),
                    style = textStyle,
                    overflow = TextOverflow.Clip,
                    onTextLayout = { fragment?.layout = it },
                    modifier = Modifier.readerHighlights(fragment, highlights),
                )
            }
        },
    ) { measurables, _ ->
        var totalWidth = 0
        for (w in layout.colWidthsPx) totalWidth += w
        var totalHeight = 0
        if (headerRepeated && rowStart > 0) totalHeight += layout.rowHeightsPx[0]
        for (r in rowStart.coerceAtLeast(0) until rowEnd.coerceAtMost(table.rows.size)) {
            totalHeight += layout.rowHeightsPx[r]
        }

        val placeables = measurables.mapIndexed { i, measurable ->
            val p = placements[i]
            val padding = placementPaddings[i]
            measurable.measure(
                Constraints(
                    minWidth = (p.width - padding.left - padding.right).coerceAtLeast(1),
                    maxWidth = (p.width - padding.left - padding.right).coerceAtLeast(1),
                    maxHeight = (p.height - padding.top - padding.bottom).coerceAtLeast(1),
                ),
            )
        }
        layout(totalWidth, totalHeight) {
            placeables.forEachIndexed { i, placeable ->
                val p = placements[i]
                val padding = placementPaddings[i]
                placeable.place(p.x + padding.left, p.y + padding.top)
            }
        }
    }
}


internal fun tableCellPhysicalX(
    columnOffsets: IntArray,
    logicalColumn: Int,
    span: Int,
    rtl: Boolean,
): Int {
    val start = logicalColumn.coerceIn(0, columnOffsets.lastIndex)
    val end = (start + span.coerceAtLeast(1)).coerceIn(start, columnOffsets.lastIndex)
    return if (rtl) columnOffsets.last() - columnOffsets[end] else columnOffsets[start]
}


internal fun tableCellPlaceholders(
    text: AnnotatedString,
    availableWidthPx: Int,
    fontSizePx: Float,
) = inlineImagePlaceholders(text, availableWidthPx, fontSizePx)


internal fun tableCellInlineContent(
    text: AnnotatedString,
    availableWidthPx: Int,
    fontSizePx: Float,
    invertImages: Boolean,
) = inlineImageContent(text, availableWidthPx, fontSizePx, invertImages)
