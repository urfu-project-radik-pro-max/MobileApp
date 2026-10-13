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

    val charStart: Int = -1,
    val charEnd: Int = -1,

    val rowStart: Int = -1,
    val rowEnd: Int = -1,
    val tableLayout: TableLayout? = null,

    val headerRepeated: Boolean = false,

    val sideBox: SideBoxSpec? = null,

    val floatImagePath: String? = null,

    val publisherBoxes: List<ReaderPublisherBox> = emptyList(),

    val publisherStartsElement: Boolean = true,
    val publisherEndsElement: Boolean = true,

    val allowsVerticalScroll: Boolean = false,
)

class BookPage(
    val parts: List<PagePart>,

    val firstItemIndex: Int,


    val firstCharOffset: Int = 0,
)


const val LAYOUT_ENGINE_VERSION = 13

class PaginationSpec(
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val density: Density,
    val settings: ReaderSettings,
    val fontSize: Float,

    val bookFonts: Map<String, androidx.compose.ui.text.font.FontFamily> = emptyMap(),

    val embeddedFontSignature: String = "",

    val language: String? = null,
) {

    val key: String =
        "e$LAYOUT_ENGINE_VERSION | $contentWidthPx x $contentHeightPx | " +
            "$fontSize | ${settings.lineHeight} | " +
            "density=${density.density.toBits()}/${density.fontScale.toBits()} | " +
            "${settings.font} | ${settings.customFontPath} | " +
            "${settings.justify} | ${settings.hyphenation} | centerHeadings=${settings.centerHeadings} | " +
            "${settings.startChaptersOnNewPage} | ${settings.hideFootnotes} | " +
            "${settings.bookStyles} | ${settings.dropCaps} | lang=$language | " +
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
    var usedPx = 0
    var pageFirstIndex = 0
    var pageFirstChar = 0

    fun closePage() {
        if (parts.isNotEmpty()) {
            pages += BookPage(parts.toList(), pageFirstIndex, pageFirstChar)
            parts = mutableListOf()
        }
        usedPx = 0
    }

    fun addPart(part: PagePart, heightPx: Int, index: Int, charStart: Int = 0) {
        if (parts.isEmpty()) {
            pageFirstIndex = index
            pageFirstChar = charStart
        }
        parts += part
        usedPx += heightPx
    }

    with(spec.density) {
        val maxImageHeightPx = ReaderMetrics.maxImageHeight.roundToPx()
        val fontSizePx = spec.fontSize.sp.toPx()
        val publisherSideBoxPlans = HashMap<Int, SideBoxSpec?>()

        fun publisherSideBoxPlan(targetIndex: Int): SideBoxSpec? {
            if (publisherSideBoxPlans.containsKey(targetIndex)) {
                return publisherSideBoxPlans[targetIndex]
            }
            val target = items.getOrNull(targetIndex)
            val paragraph = target?.element as? ContentElement.Paragraph
            val publisherFloat = target?.publisherFloat
            val plan = if (!spec.settings.bookStyles || target == null || paragraph == null ||
                publisherFloat == null
            ) {
                null
            } else {
                val geometry = publisherBoxGeometry(
                    boxes = target.publisherBoxes,
                    columnWidthPx = spec.contentWidthPx,
                    fontSizePx = fontSizePx,
                    partStartsElement = true,
                    partEndsElement = true,
                    enabled = true,
                )
                val contentWidthDp = geometry.contentWidthPx.toDp()
                val (startInset, endInset) = ReaderMetrics.horizontalInsets(
                    paragraph,
                    contentWidthDp,
                    spec.fontSize,
                )
                val widthPx = (geometry.contentWidthPx -
                    startInset.roundToPx() - endInset.roundToPx()).coerceAtLeast(1)
                planSideBox(
                    paragraph = paragraph,
                    measurer = measurer,
                    density = spec.density,
                    settings = spec.settings,
                    fontSize = spec.fontSize,
                    bookFonts = spec.bookFonts,
                    language = spec.language,
                    widthPx = widthPx,
                    publisherFloat = publisherFloat,
                )
            }
            publisherSideBoxPlans[targetIndex] = plan
            return plan
        }


        val normalFlowFloatTargets = mutableSetOf<Int>()
        val normalFlowFloatSources = mutableSetOf<Int>()
        if (spec.settings.bookStyles) {
            items.forEachIndexed { targetIndex, target ->
                val publisherFloat = target.publisherFloat ?: return@forEachIndexed
                val plan = publisherSideBoxPlan(targetIndex)
                val geometry = publisherBoxGeometry(
                    boxes = target.publisherBoxes,
                    columnWidthPx = spec.contentWidthPx,
                    fontSizePx = fontSizePx,
                    partStartsElement = true,
                    partEndsElement = true,
                    enabled = true,
                )
                val (top, bottom) = ReaderMetrics.verticalPaddings(
                    target.element,
                    spec.fontSize,
                    bookStyles = true,
                )
                val verticalInsets = top.roundToPx() + bottom.roundToPx() +
                    geometry.topInsetPx + geometry.bottomInsetPx
                if (publisherFloatNeedsNormalFlow(
                        compositeHeightPx = plan?.compositeHeightPx,
                        verticalInsetsPx = verticalInsets,
                        pageHeightPx = spec.contentHeightPx,
                    )
                ) {
                    normalFlowFloatTargets += targetIndex
                    publisherFloat.contents.forEach { content ->
                        normalFlowFloatSources += content.sourceItemIndex
                    }
                }
            }
        }


        fun keepWithNextPx(headingIndex: Int): Int {
            var interPx = 0
            var j = headingIndex + 1
            val limit = min(headingIndex + 4, items.size)
            while (j < limit) {
                val next = items[j]
                if (next.suppressedByPublisherFloat && spec.settings.bookStyles &&
                    j !in normalFlowFloatSources
                ) {
                    j++
                    continue
                }
                if (spec.settings.startChaptersOnNewPage &&
                    next.chapterIndex != items[headingIndex].chapterIndex
                ) {
                    return 0
                }
                val (nTop, nBottom) = ReaderMetrics.verticalPaddings(
                    next.element,
                    spec.fontSize,
                    spec.settings.bookStyles,
                )
                val nextBoxes = publisherBoxGeometry(
                    boxes = next.publisherBoxes,
                    columnWidthPx = spec.contentWidthPx,
                    fontSizePx = fontSizePx,
                    partStartsElement = true,
                    partEndsElement = true,
                    enabled = spec.settings.bookStyles,
                )
                when (val el = next.element) {
                    is ContentElement.Table -> {

                        return interPx + nextBoxes.topInsetPx + nTop.roundToPx() +
                            (spec.fontSize * spec.settings.lineHeight * 2).dp.roundToPx()
                    }

                    is ContentElement.Spacer ->
                        interPx += ReaderMetrics.spacerHeight(el, spec.fontSize).roundToPx()

                    ContentElement.Divider ->
                        interPx += ReaderMetrics.dividerHeight.roundToPx() +
                            nTop.roundToPx() + nBottom.roundToPx()

                    is ContentElement.Image -> {
                        val imageTotal = imageHeightPx(
                            element = el,
                            contentWidthPx = nextBoxes.contentWidthPx,
                            fontSizePx = fontSizePx,
                            maxHeightPx = maxImageHeightPx,
                            publisherOwnsWidth = publisherBoxesOwnImageWidth(
                                next.publisherBoxes,
                                spec.settings.bookStyles,
                            ),
                        ) + nextBoxes.topInsetPx + nTop.roundToPx()


                        val twoLines =
                            (spec.fontSize * spec.settings.lineHeight * 2).dp.roundToPx()
                        return interPx + min(imageTotal, twoLines)
                    }

                    is ContentElement.Paragraph, is ContentElement.Heading -> {
                        val text = when (el) {
                            is ContentElement.Paragraph -> el.text
                            is ContentElement.Heading -> el.styledText
                            else -> return 0
                        }
                        if (text.text.isBlank()) {
                            j++
                            continue
                        }
                        val boxWidthDp = nextBoxes.contentWidthPx.toDp()
                        val (sInset, eInset) =
                            ReaderMetrics.horizontalInsets(el, boxWidthDp, spec.fontSize)
                        val w = (nextBoxes.contentWidthPx -
                            sInset.roundToPx() - eInset.roundToPx())
                            .coerceAtLeast(1)
                        val bidiText = BidiLayoutText.of(text)
                        val nextLayout = measurer.measure(
                            text = bidiText.display,
                            style = ReaderMetrics.textStyle(
                                el, spec.settings, spec.fontSize,
                                bookFonts = spec.bookFonts,
                                language = spec.language,
                            ),
                            constraints = Constraints(maxWidth = w),
                            placeholders = inlineImagePlaceholders(bidiText.display),
                        )


                        val keepLines = if (el is ContentElement.Heading) {
                            nextLayout.lineCount - 1
                        } else {
                            min(BreakRules.MIN_ORPHAN_LINES - 1, nextLayout.lineCount - 1)
                        }
                        return interPx + nextBoxes.topInsetPx + nTop.roundToPx() +
                            ceil(nextLayout.getLineBottom(keepLines)).toInt()
                    }
                }
                j++
            }
            return 0
        }

        for (index in fromIndex.coerceAtLeast(0) until toIndex.coerceAtMost(items.size)) {
            val item = items[index]
            if (index % 32 == 0) yield()


            if (spec.settings.startChaptersOnNewPage &&
                index > 0 &&
                item.chapterIndex != items[index - 1].chapterIndex &&
                parts.isNotEmpty()
            ) {
                closePage()
            }


            if (item.suppressedByPublisherFloat && spec.settings.bookStyles &&
                index !in normalFlowFloatSources
            ) {
                continue
            }


            if (parts.isNotEmpty() && item.publisherBoxes.any { box ->
                    box.startsAtElement && !box.endsAtElement &&
                        box.style.breakInsideAvoid
                }
            ) {
                closePage()
            }

            val element = item.element

            val elementBlock = when (element) {
                is ContentElement.Paragraph -> element.block
                is ContentElement.Heading -> element.block
                else -> null
            }
            if (elementBlock?.pageBreakBefore == true && parts.isNotEmpty()) closePage()

            val boxGeometry = publisherBoxGeometry(
                boxes = item.publisherBoxes,
                columnWidthPx = spec.contentWidthPx,
                fontSizePx = fontSizePx,
                partStartsElement = true,
                partEndsElement = true,
                enabled = spec.settings.bookStyles,
            )
            val boxContentWidthDp = boxGeometry.contentWidthPx.toDp()
            val (vTop, vBottom) = ReaderMetrics.verticalPaddings(
                element,
                spec.fontSize,
                spec.settings.bookStyles,
            )
            val baseVPaddingPx = vTop.roundToPx() + vBottom.roundToPx()

            when (element) {
                is ContentElement.Paragraph, is ContentElement.Heading -> {
                    val fullText = when (element) {
                        is ContentElement.Paragraph -> element.text
                        is ContentElement.Heading -> element.styledText
                    }
                    if (fullText.text.isBlank()) continue

                    val (startInset, endInset) =
                        ReaderMetrics.horizontalInsets(element, boxContentWidthDp, spec.fontSize)
                    val widthPx =
                        boxGeometry.contentWidthPx -
                            startInset.roundToPx() - endInset.roundToPx()
                    val fullBidi = BidiLayoutText.of(fullText)
                    val layout = measurer.measure(
                        text = fullBidi.display,
                        style = ReaderMetrics.textStyle(
                            element, spec.settings, spec.fontSize,
                            bookFonts = spec.bookFonts,
                            language = spec.language,
                        ),
                        constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)),
                        placeholders = inlineImagePlaceholders(fullBidi.display),
                    )


                    if (element is ContentElement.Heading && parts.isNotEmpty()) {
                        val vPaddingPx = baseVPaddingPx + boxGeometry.topInsetPx +
                            boxGeometry.bottomInsetPx
                        val ownHeight = ceil(layout.getLineBottom(layout.lineCount - 1)).toInt()
                        val fitsSomePage = ownHeight + vPaddingPx <= spec.contentHeightPx
                        val remainingHere = spec.contentHeightPx - usedPx - vPaddingPx
                        if (fitsSomePage &&
                            (
                                ownHeight > remainingHere ||
                                    !BreakRules.headingFits(
                                        remainingAfterHeadingPx = remainingHere - ownHeight,
                                        requiredNextPx = keepWithNextPx(index),
                                        pageHasOtherContent = true,
                                    )
                                )
                        ) {
                            closePage()
                        }
                    }

                    if (element is ContentElement.Paragraph) {
                        val avoidsBreak = spec.settings.bookStyles &&
                            item.publisherBoxes.any { box ->
                                box.startsAtElement && box.endsAtElement &&
                                    box.style.breakInsideAvoid
                            }
                        val wholeHeight = layout.size.height + baseVPaddingPx +
                            boxGeometry.topInsetPx + boxGeometry.bottomInsetPx
                        if (shouldMoveAvoidBlockToFreshPage(
                                breakInsideAvoid = avoidsBreak,
                                pageHasOtherContent = parts.isNotEmpty(),
                                wholeHeightPx = wholeHeight,
                                remainingHeightPx = spec.contentHeightPx - usedPx,
                                pageHeightPx = spec.contentHeightPx,
                            )
                        ) {
                            closePage()
                        }
                    }


                    var packedText = fullText
                    var packedBidi = fullBidi
                    var packLayout = layout
                    var packBase = 0
                    var firstPart = true

                    if (element is ContentElement.Paragraph) {
                        val floatImage = element.block?.floatImage
                        fun addFloatImageAsBlock() {
                            val image = floatImage ?: return
                            val aspect = imageAspectRatio(image.path) ?: 1.4f
                            val wantedHeight = min(
                                (boxGeometry.contentWidthPx * aspect).roundToInt(),
                                maxImageHeightPx,
                            )
                            val imagePadPx = 12.dp.roundToPx() * 2
                            val storedHeight = min(
                                wantedHeight,
                                (spec.contentHeightPx - imagePadPx).coerceAtLeast(1),
                            )
                            val total = storedHeight + imagePadPx
                            if (total > spec.contentHeightPx - usedPx && parts.isNotEmpty()) {
                                closePage()
                            }
                            addPart(
                                PagePart(
                                    itemIndex = index,
                                    element = element,
                                    imageHeightPx = storedHeight,
                                    floatImagePath = image.path,


                                    publisherBoxes = emptyList(),
                                    publisherStartsElement = true,
                                    publisherEndsElement = true,
                                ),
                                heightPx = total,
                                index = index,
                                charStart = 0,
                            )
                        }
                        if (floatImage != null && !spec.settings.bookStyles) {


                            addFloatImageAsBlock()
                        }
                        var sideBox = when {
                            item.publisherFloat == null -> planSideBox(
                                element, measurer, spec.density, spec.settings,
                                spec.fontSize, spec.bookFonts, spec.language, widthPx,
                            )

                            index in normalFlowFloatTargets -> null
                            else -> publisherSideBoxPlan(index)
                        }
                        if (sideBox != null) {
                            val compositePadding = baseVPaddingPx + boxGeometry.topInsetPx +
                                if (sideBox.besideEndChar >= fullText.length) {
                                    boxGeometry.bottomInsetPx
                                } else {
                                    0
                                }
                            if (sideBox.compositeHeightPx.toLong() + compositePadding >
                                spec.contentHeightPx
                            ) {


                                if (floatImage != null && spec.settings.bookStyles) {
                                    addFloatImageAsBlock()
                                }
                                sideBox = null
                            }
                        }
                        if (sideBox != null) {
                            val compositeAnchorIndex = item.publisherFloat
                                ?.contents
                                ?.minOfOrNull(ReaderPublisherFloatContent::sourceItemIndex)
                                ?: index
                            val sideBoxEndsElement = sideBox.besideEndChar >= fullText.length
                            val compositePadding = baseVPaddingPx + boxGeometry.topInsetPx +
                                if (sideBoxEndsElement) boxGeometry.bottomInsetPx else 0
                            val compositeTotal = sideBox.compositeHeightPx + compositePadding
                            if (compositeTotal > spec.contentHeightPx - usedPx &&
                                parts.isNotEmpty()
                            ) {
                                closePage()
                            }
                            val capLen = sideBox.capText?.length ?: 0
                            addPart(
                                PagePart(
                                    itemIndex = index,
                                    element = element,
                                    text = fullText.subSequence(
                                        capLen, sideBox.besideEndChar,
                                    ),
                                    isParagraphStart = true,
                                    charStart = capLen,
                                    charEnd = sideBox.besideEndChar,
                                    sideBox = sideBox,
                                    publisherBoxes = item.publisherBoxes,
                                    publisherStartsElement = true,
                                    publisherEndsElement = sideBoxEndsElement,
                                ),
                                heightPx = compositeTotal,
                                index = compositeAnchorIndex,
                                charStart = 0,
                            )
                            if (sideBox.besideEndChar >= fullText.length) continue
                            packBase = sideBox.besideEndChar
                            firstPart = false
                            packedText = fullText.subSequence(packBase, fullText.length)
                            packedBidi = BidiLayoutText.of(packedText)
                            packLayout = measurer.measure(
                                text = packedBidi.display,
                                style = ReaderMetrics.textStyle(
                                    element, spec.settings, spec.fontSize,
                                    isParagraphStart = false,
                                    bookFonts = spec.bookFonts,
                                    language = spec.language,
                                ),
                                constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)),
                                placeholders = inlineImagePlaceholders(packedBidi.display),
                            )
                        }
                    }

                    val raw = packedText.text
                    var cursor = 0
                    while (cursor < raw.length) {

                        while (cursor < raw.length && raw[cursor].isWhitespace()) cursor++
                        if (cursor >= raw.length) break

                        val startLine = packLayout.getLineForOffset(
                            packedBidi.layoutStart(cursor),
                        )
                        val topOffset = packLayout.getLineTop(startLine)
                        val elementCharStart = packBase + cursor
                        val publisherStartsElement = elementCharStart == 0
                        val leadingPadding = baseVPaddingPx +
                            if (publisherStartsElement) boxGeometry.topInsetPx else 0
                        val remaining = spec.contentHeightPx - usedPx - leadingPadding


                        var endLine = startLine
                        while (
                            endLine + 1 < packLayout.lineCount &&
                            packLayout.getLineBottom(endLine + 1) - topOffset <= remaining
                        ) {
                            endLine++
                        }


                        val fragmentStyle = ReaderMetrics.textStyle(
                            element, spec.settings, spec.fontSize,
                            isParagraphStart = firstPart,
                            bookFonts = spec.bookFonts,
                            language = spec.language,
                        )
                        var endChar: Int
                        var fragmentHeight: Int
                        while (true) {
                            val lastLine = endLine >= packLayout.lineCount - 1
                            endChar = if (lastLine) {
                                raw.length
                            } else {
                                packedBidi.sourceOffset(packLayout.getLineEnd(endLine))
                            }
                            if (!lastLine) {

                                endChar = retreatToWordBoundary(raw, cursor, endChar)
                                if (endChar <= cursor) {
                                    endChar = packedBidi.sourceOffset(
                                        packLayout.getLineEnd(endLine),
                                    )
                                }
                            }
                            fragmentHeight = if (cursor == 0 && lastLine) {

                                ceil(packLayout.getLineBottom(endLine)).toInt()
                            } else {
                                val fragment = packedText.subSequence(cursor, endChar)
                                val bidiFragment = BidiLayoutText.of(fragment)
                                measurer.measure(
                                    text = bidiFragment.display,
                                    style = fragmentStyle,
                                    constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)),
                                    placeholders = inlineImagePlaceholders(bidiFragment.display),
                                ).size.height
                            }
                            if (fragmentHeight <= remaining || endLine <= startLine) break
                            endLine--
                        }

                        if (fragmentHeight > remaining && parts.isNotEmpty()) {

                            closePage()
                            continue
                        }
                        if (endChar <= cursor) break


                        if (endLine < packLayout.lineCount - 1) {
                            val decision = BreakRules.splitDecision(
                                startLine = startLine,
                                endLine = endLine,
                                lineCount = packLayout.lineCount,
                                isFirstFragment = firstPart,
                                pageHasOtherContent = parts.isNotEmpty(),
                            )
                            when (decision) {
                                BreakRules.SplitDecision.MoveToNextPage -> {
                                    closePage()
                                    continue
                                }

                                is BreakRules.SplitDecision.PlaceFewer -> {
                                    endLine = decision.endLine
                                    endChar = packedBidi.sourceOffset(
                                        packLayout.getLineEnd(endLine),
                                    )
                                    retreatToWordBoundary(raw, cursor, endChar)
                                        .takeIf { it > cursor }
                                        ?.let { endChar = it }


                                    val fragment = packedText.subSequence(cursor, endChar)
                                    val bidiFragment = BidiLayoutText.of(fragment)
                                    fragmentHeight = measurer.measure(
                                        text = bidiFragment.display,
                                        style = fragmentStyle,
                                        constraints = Constraints(
                                            maxWidth = widthPx.coerceAtLeast(1),
                                        ),
                                        placeholders = inlineImagePlaceholders(
                                            bidiFragment.display,
                                        ),
                                    ).size.height
                                }

                                BreakRules.SplitDecision.Place -> Unit
                            }
                        }
                        if (endChar <= cursor) break

                        val elementCharEnd = packBase + endChar
                        val publisherEndsElement = elementCharEnd >= fullText.length
                        val fragmentPadding = leadingPadding +
                            if (publisherEndsElement) boxGeometry.bottomInsetPx else 0
                        if (fragmentHeight + fragmentPadding >
                            spec.contentHeightPx - usedPx && parts.isNotEmpty()
                        ) {
                            closePage()
                            continue
                        }

                        addPart(
                            PagePart(
                                itemIndex = index,
                                element = element,
                                text = packedText.subSequence(cursor, endChar),
                                isParagraphStart = firstPart,
                                charStart = packBase + cursor,
                                charEnd = packBase + endChar,
                                publisherBoxes = item.publisherBoxes,
                                publisherStartsElement = publisherStartsElement,
                                publisherEndsElement = publisherEndsElement,
                            ),
                            heightPx = fragmentHeight + fragmentPadding,
                            index = index,
                            charStart = packBase + cursor,
                        )
                        firstPart = false
                        cursor = endChar

                        if (cursor < raw.length) closePage()
                    }
                }

                is ContentElement.Image -> {
                    val heightPx = imageHeightPx(
                        element = element,
                        contentWidthPx = boxGeometry.contentWidthPx,
                        fontSizePx = fontSizePx,
                        maxHeightPx = maxImageHeightPx,
                        publisherOwnsWidth = publisherBoxesOwnImageWidth(
                            item.publisherBoxes,
                            spec.settings.bookStyles,
                        ),
                    )
                    val vPaddingPx = baseVPaddingPx + boxGeometry.topInsetPx +
                        boxGeometry.bottomInsetPx
                    val total = heightPx + vPaddingPx
                    if (total > spec.contentHeightPx - usedPx && parts.isNotEmpty()) closePage()
                    val storedImageHeight = min(
                        heightPx,
                        (spec.contentHeightPx - vPaddingPx).coerceAtLeast(1),
                    )
                    addPart(
                        PagePart(
                            itemIndex = index,
                            element = element,
                            imageHeightPx = storedImageHeight,
                            publisherBoxes = item.publisherBoxes,
                        ),
                        heightPx = storedImageHeight + vPaddingPx,
                        index = index,
                    )
                }

                ContentElement.Divider -> {
                    val vPaddingPx = baseVPaddingPx + boxGeometry.topInsetPx +
                        boxGeometry.bottomInsetPx
                    val total = ReaderMetrics.dividerHeight.roundToPx() + vPaddingPx
                    if (total > spec.contentHeightPx - usedPx && parts.isNotEmpty()) closePage()
                    addPart(
                        PagePart(
                            itemIndex = index,
                            element = element,
                            publisherBoxes = item.publisherBoxes,
                        ),
                        total,
                        index,
                    )
                }

                is ContentElement.Spacer -> {

                    val hasPublisherSurface = spec.settings.bookStyles &&
                        item.publisherBoxes.any { box ->
                            box.style.backgroundColorArgb != null ||
                                box.style.borderTop != null || box.style.borderRight != null ||
                                box.style.borderBottom != null || box.style.borderLeft != null
                        }
                    if (parts.isNotEmpty() || hasPublisherSurface) {
                        val total = ReaderMetrics.spacerHeight(element, spec.fontSize).roundToPx() +
                            boxGeometry.topInsetPx + boxGeometry.bottomInsetPx
                        if (total > spec.contentHeightPx - usedPx && parts.isNotEmpty()) {
                            closePage()
                        }


                        if (parts.isNotEmpty() || hasPublisherSurface) {
                            addPart(
                                PagePart(
                                    itemIndex = index,
                                    element = element,
                                    publisherBoxes = item.publisherBoxes,
                                ),
                                total,
                                index,
                            )
                        }
                    }
                }

                is ContentElement.Table -> {
                    if (element.rows.isEmpty()) continue
                    val layout = measureTableLayout(
                        element, measurer, spec.density, spec.settings,
                        spec.fontSize, boxGeometry.contentWidthPx,
                        spec.language, spec.bookFonts,
                    )
                    val avoidsBreak = spec.settings.bookStyles &&
                        item.publisherBoxes.any { box ->
                            box.startsAtElement && box.endsAtElement &&
                                box.style.breakInsideAvoid
                        }
                    val wholeTableHeight = layout.rowHeightsPx.sum() + baseVPaddingPx +
                        boxGeometry.topInsetPx + boxGeometry.bottomInsetPx
                    if (shouldMoveAvoidBlockToFreshPage(
                            breakInsideAvoid = avoidsBreak,
                            pageHasOtherContent = parts.isNotEmpty(),
                            wholeHeightPx = wholeTableHeight,
                            remainingHeightPx = spec.contentHeightPx - usedPx,
                            pageHeightPx = spec.contentHeightPx,
                        )
                    ) {
                        closePage()
                    }
                    val repeatHeader = element.rows.first().isHeader && element.rows.size > 1
                    var row = 0
                    while (row < element.rows.size) {
                        val headerPx = if (row > 0 && repeatHeader) layout.rowHeightsPx[0] else 0
                        val publisherStartsElement = row == 0
                        val leadingPadding = baseVPaddingPx +
                            if (publisherStartsElement) boxGeometry.topInsetPx else 0
                        val remaining =
                            spec.contentHeightPx - usedPx - leadingPadding - headerPx
                        var end = TableGrid.rowsThatFit(
                            layout.rowHeightsPx, layout.grid.noBreakAfterRow, row, remaining,
                        )
                        if (end == row) {
                            if (parts.isNotEmpty()) {

                                closePage()
                                continue
                            }


                            end = firstUnbreakableTableGroupEnd(
                                startRow = row,
                                rowCount = element.rows.size,
                                noBreakAfterRow = layout.grid.noBreakAfterRow,
                            )
                        }
                        val completionFit = fitTableCompletion(
                            startRow = row,
                            proposedEndExclusive = end,
                            rowHeightsPx = layout.rowHeightsPx,
                            noBreakAfterRow = layout.grid.noBreakAfterRow,
                            bottomInsetPx = boxGeometry.bottomInsetPx,
                            remainingHeightPx = remaining,
                            freshRemainingHeightPx = spec.contentHeightPx -
                                leadingPadding - headerPx,
                            pageHasOtherContent = parts.isNotEmpty(),
                        )
                        if (completionFit.moveToFreshPage) {


                            closePage()
                            continue
                        }
                        end = completionFit.endExclusive
                        val publisherEndsElement = end >= element.rows.size
                        var partHeight = headerPx + leadingPadding +
                            if (publisherEndsElement) boxGeometry.bottomInsetPx else 0
                        for (r in row until end) partHeight += layout.rowHeightsPx[r]
                        addPart(
                            PagePart(
                                itemIndex = index,
                                element = element,
                                rowStart = row,
                                rowEnd = end,
                                tableLayout = layout,
                                headerRepeated = row > 0 && repeatHeader,
                                charStart = row,
                                charEnd = end,
                                publisherBoxes = item.publisherBoxes,
                                publisherStartsElement = publisherStartsElement,
                                publisherEndsElement = publisherEndsElement,
                                allowsVerticalScroll = partHeight > spec.contentHeightPx,
                            ),
                            heightPx = partHeight,
                            index = index,
                            charStart = row,
                        )
                        row = end
                        if (row < element.rows.size) closePage()
                    }
                }
            }
        }
    }

    closePage()
    return pages
}


internal fun shouldMoveAvoidBlockToFreshPage(
    breakInsideAvoid: Boolean,
    pageHasOtherContent: Boolean,
    wholeHeightPx: Int,
    remainingHeightPx: Int,
    pageHeightPx: Int,
): Boolean = breakInsideAvoid && pageHasOtherContent &&
    wholeHeightPx <= pageHeightPx && wholeHeightPx > remainingHeightPx


internal fun publisherFloatNeedsNormalFlow(
    compositeHeightPx: Int?,
    verticalInsetsPx: Int,
    pageHeightPx: Int,
): Boolean = compositeHeightPx == null ||
    compositeHeightPx.toLong() + verticalInsetsPx.coerceAtLeast(0) > pageHeightPx

internal data class TableCompletionFit(
    val endExclusive: Int,
    val moveToFreshPage: Boolean,
)

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


internal fun fitTableCompletion(
    startRow: Int,
    proposedEndExclusive: Int,
    rowHeightsPx: IntArray,
    noBreakAfterRow: BooleanArray,
    bottomInsetPx: Int,
    remainingHeightPx: Int,
    freshRemainingHeightPx: Int,
    pageHasOtherContent: Boolean,
): TableCompletionFit {
    val rowCount = rowHeightsPx.size
    if (proposedEndExclusive < rowCount || bottomInsetPx <= 0) {
        return TableCompletionFit(proposedEndExclusive, false)
    }
    var rowsHeight = 0L
    for (row in startRow.coerceAtLeast(0) until rowCount) rowsHeight += rowHeightsPx[row]
    val completedHeight = rowsHeight + bottomInsetPx
    if (completedHeight <= remainingHeightPx) {
        return TableCompletionFit(proposedEndExclusive, false)
    }
    if (pageHasOtherContent && completedHeight <= freshRemainingHeightPx) {
        return TableCompletionFit(proposedEndExclusive, true)
    }


    var candidate = rowCount - 1
    while (candidate > startRow && noBreakAfterRow.getOrElse(candidate - 1) { false }) {
        candidate--
    }
    return TableCompletionFit(
        endExclusive = candidate.takeIf { it > startRow } ?: proposedEndExclusive,
        moveToFreshPage = false,
    )
}


internal fun imageHeightPx(
    element: ContentElement.Image,
    contentWidthPx: Int,
    fontSizePx: Float,
    maxHeightPx: Int,
    publisherOwnsWidth: Boolean = false,
): Int {
    val aspect = imageAspectRatio(element.path) ?: 1.4f
    element.heightEm?.let { heightEm ->
        val wanted = (heightEm * fontSizePx).roundToInt()

        val widthAtWanted = wanted / aspect
        val fitted = if (widthAtWanted > contentWidthPx) {
            (contentWidthPx * aspect).roundToInt()
        } else {
            wanted
        }
        return fitted.coerceIn(1, maxHeightPx)
    }
    val width = imageWidthPx(element, contentWidthPx, fontSizePx, publisherOwnsWidth)
    return min((width * aspect).roundToInt(), maxHeightPx).coerceAtLeast(1)
}


internal fun imageWidthPx(
    element: ContentElement.Image,
    contentWidthPx: Int,
    fontSizePx: Float,
    publisherOwnsWidth: Boolean = false,
): Int {
    val available = contentWidthPx.coerceAtLeast(1)
    if (publisherOwnsWidth) return available
    val requested = when {
        element.widthFrac != null -> available * element.widthFrac.coerceIn(0.01f, 1f)
        element.widthEm != null -> element.widthEm.coerceAtLeast(0.05f) * fontSizePx
        else -> available.toFloat()
    }
    return requested.roundToInt().coerceIn(1, available)
}

internal fun publisherBoxesOwnImageWidth(
    boxes: List<ReaderPublisherBox>,
    enabled: Boolean,
): Boolean = enabled && boxes.any { box ->
    box.style.widthFrac != null || box.style.widthEm != null
}

internal fun imageAspectRatio(path: String): Float? = runCatching {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    if (options.outWidth > 0 && options.outHeight > 0) {
        options.outHeight.toFloat() / options.outWidth
    } else {

        svgFileAspectRatio(path)
    }
}.getOrNull()

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
