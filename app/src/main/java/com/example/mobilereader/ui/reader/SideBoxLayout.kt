package com.example.mobilereader.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.ui.reader.selection.ReaderHighlights
import com.example.mobilereader.ui.reader.selection.readerHighlights
import com.example.mobilereader.ui.reader.selection.rememberTextFragment
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt


class SideBoxSpec(

    val capText: String?,

    val imagePath: String?,
    val leftSide: Boolean,
    val boxWidthPx: Int,
    val boxHeightPx: Int,

    val besideWidthPx: Int,

    val besideEndChar: Int,
    val compositeHeightPx: Int,

    val capFontSizeSp: Float = 0f,


    val capTopPx: Int = 0,

    val publisherFloat: ReaderPublisherFloat? = null,

    val publisherFloatItemHeightsPx: List<Int> = emptyList(),

    val publisherStacked: Boolean = false,
)


fun planSideBox(
    paragraph: ContentElement.Paragraph,
    measurer: TextMeasurer,
    density: Density,
    settings: ReaderSettings,
    fontSize: Float,
    bookFonts: Map<String, FontFamily>,
    language: String?,
    widthPx: Int,
    publisherFloat: ReaderPublisherFloat? = null,
): SideBoxSpec? = with(density) {


    if (!settings.bookStyles && !settings.dropCaps) return null
    val gapPx = (fontSize * 0.4f).dp.roundToPx()
    val minimumBesideWidthPx = (fontSize.sp.toPx() * 4f).roundToInt()

    if (settings.bookStyles && publisherFloat != null) {
        val measured = measurePublisherFloat(
            publisherFloat = publisherFloat,
            measurer = measurer,
            density = density,
            settings = settings,
            fontSize = fontSize,
            bookFonts = bookFonts,
            language = language,
            containingWidthPx = widthPx,
        ) ?: return null
        val beside = planBeside(
            paragraph, measurer, settings, fontSize, bookFonts, language,
            widthPx, capText = null, imagePath = null,
            leftSide = publisherFloat.side ==
                com.example.mobilereader.data.model.PublisherFloatSide.LEFT,
            boxW = measured.widthPx,
            boxH = measured.heightPx,
            gapPx = measured.gapPx,
            minimumBesideWidthPx = minimumBesideWidthPx,
            publisherFloat = publisherFloat,
            publisherFloatItemHeightsPx = measured.itemHeightsPx,
        )
        if (beside != null) return beside


        val paragraphBidi = BidiLayoutText.of(paragraph.text)
        val paragraphHeight = measurer.measure(
            text = paragraphBidi.display,
            style = ReaderMetrics.textStyle(
                paragraph,
                settings,
                fontSize,
                isParagraphStart = true,
                bookFonts = bookFonts,
                language = language,
            ),
            constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)),
            placeholders = inlineImagePlaceholders(paragraphBidi.display),
        ).size.height
        return SideBoxSpec(
            capText = null,
            imagePath = null,
            leftSide = publisherFloat.side ==
                com.example.mobilereader.data.model.PublisherFloatSide.LEFT,
            boxWidthPx = measured.widthPx,
            boxHeightPx = measured.heightPx,
            besideWidthPx = widthPx.coerceAtLeast(1),
            besideEndChar = paragraph.text.length,
            compositeHeightPx = measured.heightPx + paragraphHeight,
            publisherFloat = publisherFloat,
            publisherFloatItemHeightsPx = measured.itemHeightsPx,
            publisherStacked = true,
        )
    }

    val block = paragraph.block ?: return null

    if (settings.bookStyles) block.floatImage?.let { float ->
        val aspect = imageAspectRatio(float.path) ?: return null
        val boxW = (widthPx * float.widthFrac.coerceIn(0.1f, 0.45f)).roundToInt()
        val boxH = (boxW * aspect).roundToInt()
            .coerceAtMost((widthPx * 0.9f).roundToInt())
        return planBeside(
            paragraph, measurer, settings, fontSize, bookFonts, language,
            widthPx, capText = null, imagePath = float.path, leftSide = float.left,
            boxW = boxW, boxH = boxH, gapPx = gapPx,
            minimumBesideWidthPx = minimumBesideWidthPx,
        )
    }

    val cap = block.firstLetter?.takeIf { it.isDropCap } ?: return null
    val capText = SideBoxRules.capPrefix(
        paragraph.text.text,
        cap.sourceTextLength,
    ) ?: return null
    val besideStyle = ReaderMetrics.textStyle(
        paragraph, settings, fontSize,
        isParagraphStart = false, bookFonts = bookFonts, language = language,
    )
    val rest = paragraph.text.subSequence(capText.length, paragraph.text.length)
    if (rest.text.isBlank()) return null


    val referenceSp = fontSize * cap.scale.coerceIn(1.8f, 4f)
    val reference = measurer.measure(
        text = AnnotatedString(capText),
        style = ReaderMetrics.dropCapStyle(settings, referenceSp, cap, bookFonts, language),
        constraints = Constraints(),
    )
    if (reference.size.height <= 0 || reference.size.width <= 0) return null

    val probeWidth = (widthPx - reference.size.width - gapPx)
        .coerceAtLeast(minimumBesideWidthPx)
    val bidiRest = BidiLayoutText.of(rest)
    val probe = measurer.measure(
        text = bidiRest.display,
        style = besideStyle,
        constraints = Constraints(maxWidth = probeWidth),
        placeholders = inlineImagePlaceholders(bidiRest.display),
    )
    if (probe.lineCount == 0) return null
    val lines = Math.round(cap.scale).coerceIn(2, 4).coerceAtMost(probe.lineCount)

    val baselineFirst = probe.getLineBaseline(0)
    val baselineLast = probe.getLineBaseline(lines - 1)
    val boxH = kotlin.math.floor(baselineLast).toInt()
    if (boxH <= 0) return null


    val bodyFontPx = fontSize.sp.toPx()
    val lineOneCapTop = baselineFirst - CAP_HEIGHT_RATIO * bodyFontPx
    val targetVisualPx = baselineLast - lineOneCapTop
    if (targetVisualPx <= 0f) return null
    val capSp = (fontSize * targetVisualPx / (CAP_HEIGHT_RATIO * bodyFontPx))
        .coerceIn(fontSize, fontSize * 8f)
    val capLayout = measurer.measure(
        text = AnnotatedString(capText),
        style = ReaderMetrics.dropCapStyle(settings, capSp, cap, bookFonts, language),
        constraints = Constraints(),
    )
    if (capLayout.size.width <= 0) return null

    val capTopPx = (baselineLast - capLayout.firstBaseline).roundToInt()
    return planBeside(
        paragraph, measurer, settings, fontSize, bookFonts, language,
        widthPx, capText = capText, imagePath = null, leftSide = cap.leftSide,
        boxW = capLayout.size.width, boxH = boxH, gapPx = gapPx,
        minimumBesideWidthPx = minimumBesideWidthPx,
        capFontSizeSp = capSp, capTopPx = capTopPx,
    )
}

private data class MeasuredPublisherFloat(
    val widthPx: Int,
    val heightPx: Int,
    val gapPx: Int,
    val itemHeightsPx: List<Int>,
)


private fun measurePublisherFloat(
    publisherFloat: ReaderPublisherFloat,
    measurer: TextMeasurer,
    density: Density,
    settings: ReaderSettings,
    fontSize: Float,
    bookFonts: Map<String, FontFamily>,
    language: String?,
    containingWidthPx: Int,
): MeasuredPublisherFloat? = with(density) {
    if (publisherFloat.contents.isEmpty() || containingWidthPx <= 0) return null
    val style = publisherFloat.style
    val fontPx = fontSize.sp.toPx()
    val horizontalFrameExtra =
        (style.paddingLeftEm + style.paddingRightEm).coerceAtLeast(0f) * fontPx +
            (style.paddingLeftFrac + style.paddingRightFrac)
                .coerceIn(0f, 0.45f) * containingWidthPx +
            listOf(style.borderLeft, style.borderRight).sumOf { side ->
                side?.widthEm?.coerceAtLeast(0f)?.times(fontPx)?.toDouble() ?: 0.0
            }.toFloat()
    val requestedOuterWidth = when {
        style.widthFrac != null ->
            containingWidthPx * style.widthFrac.coerceIn(0.05f, 1f) + horizontalFrameExtra
        style.widthEm != null -> style.widthEm.coerceAtLeast(0.05f) * fontPx +
            horizontalFrameExtra
        else -> containingWidthPx * DEFAULT_FLOAT_WIDTH_FRACTION
    }
    val maximumOuterWidth = (containingWidthPx * 0.48f).roundToInt().coerceAtLeast(1)
    val minimumOuterWidth = (fontPx * 5f).roundToInt()
        .coerceAtMost(maximumOuterWidth)
        .coerceAtLeast(1)
    val outerWidth = requestedOuterWidth.roundToInt()
        .coerceIn(minimumOuterWidth, maximumOuterWidth)
    if (outerWidth >= containingWidthPx) return null

    val frameGeometry = publisherFloatFrameGeometry(publisherFloat, outerWidth, fontPx)
    val innerWidth = frameGeometry.contentWidthPx.coerceAtLeast(1)
    val maxImageHeight = ReaderMetrics.maxImageHeight.roundToPx()
    val itemHeights = publisherFloat.contents.map { content ->
        val geometry = publisherBoxGeometry(
            boxes = content.publisherBoxes,
            columnWidthPx = innerWidth,
            fontSizePx = fontPx,
            partStartsElement = true,
            partEndsElement = true,
            enabled = true,
        )
        val element = content.element
        val (vTop, vBottom) = ReaderMetrics.verticalPaddings(
            element,
            fontSize,
            settings.bookStyles,
        )
        val vertical = geometry.topInsetPx + geometry.bottomInsetPx +
            vTop.roundToPx() + vBottom.roundToPx()
        val height = when (element) {
            is ContentElement.Paragraph, is ContentElement.Heading -> {
                val text = when (element) {
                    is ContentElement.Paragraph -> element.text
                    is ContentElement.Heading -> element.styledText
                    else -> AnnotatedString("")
                }
                val contentWidthDp = geometry.contentWidthPx.toDp()
                val (start, end) = ReaderMetrics.horizontalInsets(
                    element,
                    contentWidthDp,
                    fontSize,
                )
                val textWidth = (geometry.contentWidthPx -
                    start.roundToPx() - end.roundToPx()).coerceAtLeast(1)
                val bidi = BidiLayoutText.of(text)
                measurer.measure(
                    text = bidi.display,
                    style = ReaderMetrics.textStyle(
                        element,
                        settings,
                        fontSize,
                        bookFonts = bookFonts,
                        language = language,
                    ),
                    constraints = Constraints(maxWidth = textWidth),
                    placeholders = inlineImagePlaceholders(bidi.display),
                ).size.height + vertical
            }

            is ContentElement.Image -> imageHeightPx(
                element = element,
                contentWidthPx = geometry.contentWidthPx,
                fontSizePx = fontPx,
                maxHeightPx = maxImageHeight,
                publisherOwnsWidth = publisherBoxesOwnImageWidth(
                    content.publisherBoxes,
                    enabled = true,
                ) || publisherFloatOwnsDirectImageWidth(publisherFloat, content),
            ) + vertical

            is ContentElement.Spacer ->
                ReaderMetrics.spacerHeight(element, fontSize).roundToPx() + vertical

            ContentElement.Divider -> ReaderMetrics.dividerHeight.roundToPx() + vertical
            is ContentElement.Table -> 0
        }
        height.coerceAtLeast(1)
    }
    if (itemHeights.isEmpty()) return null
    val height = frameGeometry.topInsetPx + frameGeometry.bottomInsetPx + itemHeights.sum()
    if (height <= 0) return null

    val inlineMarginEm = if (publisherFloat.side ==
        com.example.mobilereader.data.model.PublisherFloatSide.LEFT
    ) {
        style.marginRightEm
    } else {
        style.marginLeftEm
    }
    val inlineMarginFrac = if (publisherFloat.side ==
        com.example.mobilereader.data.model.PublisherFloatSide.LEFT
    ) {
        style.marginRightFrac
    } else {
        style.marginLeftFrac
    }
    val authoredGap = inlineMarginEm.coerceAtLeast(0f) * fontPx +
        inlineMarginFrac.coerceIn(0f, 0.25f) * containingWidthPx
    val gap = maxOf((fontSize * 0.4f).dp.roundToPx().toFloat(), authoredGap)
        .roundToInt()

    MeasuredPublisherFloat(outerWidth, height, gap, itemHeights)
}

private fun publisherFloatFrameGeometry(
    float: ReaderPublisherFloat,
    widthPx: Int,
    fontSizePx: Float,
): PublisherBoxGeometry {
    val frame = ReaderPublisherBox(
        id = "publisher-float-frame",
        parentId = null,
        style = float.style.copy(
            marginLeftEm = 0f,
            marginLeftFrac = 0f,
            marginRightEm = 0f,
            marginRightFrac = 0f,
            widthFrac = null,
            widthEm = null,
        ),
        floatSide = float.side,
        startsAtElement = true,
        endsAtElement = true,
    )
    return publisherBoxGeometry(
        boxes = listOf(frame),
        columnWidthPx = widthPx,
        fontSizePx = fontSizePx,
        partStartsElement = true,
        partEndsElement = true,
        enabled = true,
    )
}


private const val CAP_HEIGHT_RATIO = 0.72f
private const val DEFAULT_FLOAT_WIDTH_FRACTION = 0.34f

private fun planBeside(
    paragraph: ContentElement.Paragraph,
    measurer: TextMeasurer,
    settings: ReaderSettings,
    fontSize: Float,
    bookFonts: Map<String, FontFamily>,
    language: String?,
    widthPx: Int,
    capText: String?,
    imagePath: String?,
    leftSide: Boolean,
    boxW: Int,
    boxH: Int,
    gapPx: Int,
    minimumBesideWidthPx: Int,
    capFontSizeSp: Float = 0f,
    capTopPx: Int = 0,
    publisherFloat: ReaderPublisherFloat? = null,
    publisherFloatItemHeightsPx: List<Int> = emptyList(),
): SideBoxSpec? {
    if (boxW <= 0 || boxH <= 0) return null
    val besideW = widthPx - boxW - gapPx

    if (besideW < minimumBesideWidthPx) return null

    val capLen = capText?.length ?: 0
    val rest = paragraph.text.subSequence(capLen, paragraph.text.length)
    if (rest.text.isBlank()) return null
    val besideStartsParagraph = capText == null
    val besideStyle = ReaderMetrics.textStyle(
        paragraph, settings, fontSize,
        isParagraphStart = besideStartsParagraph,
        bookFonts = bookFonts,
        language = language,
    )
    val bidiRest = BidiLayoutText.of(rest)
    val layout = measurer.measure(
        text = bidiRest.display,
        style = besideStyle,
        constraints = Constraints(maxWidth = besideW.coerceAtLeast(1)),
        placeholders = inlineImagePlaceholders(bidiRest.display),
    )
    if (layout.lineCount == 0) return null
    val bottoms = FloatArray(layout.lineCount) { layout.getLineBottom(it) }
    val lines = SideBoxRules.besideLineCount(bottoms, boxH)
    if (lines <= 0) return null

    val besideEndRelative: Int
    val compositeH: Int
    if (lines >= layout.lineCount) {

        besideEndRelative = rest.length
        compositeH = maxOf(boxH, ceil(layout.getLineBottom(layout.lineCount - 1)).toInt())
    } else {
        var end = bidiRest.sourceOffset(layout.getLineEnd(lines - 1))
        retreatToWordBoundary(rest.text, 0, end)
            .takeIf { it > 0 }
            ?.let { end = it }
        if (end <= 0) return null


        val fragment = rest.subSequence(0, end)
        val bidiFragment = BidiLayoutText.of(fragment)
        val fragmentHeight = measurer.measure(
            text = bidiFragment.display,
            style = besideStyle,
            constraints = Constraints(maxWidth = besideW.coerceAtLeast(1)),
            placeholders = inlineImagePlaceholders(bidiFragment.display),
        ).size.height
        besideEndRelative = end
        compositeH = maxOf(boxH, fragmentHeight)
    }
    return SideBoxSpec(
        capText = capText,
        imagePath = imagePath,
        leftSide = leftSide,
        boxWidthPx = boxW,
        boxHeightPx = boxH,
        besideWidthPx = besideW,
        besideEndChar = capLen + besideEndRelative,
        compositeHeightPx = compositeH,
        capFontSizeSp = capFontSizeSp,
        capTopPx = capTopPx,
        publisherFloat = publisherFloat,
        publisherFloatItemHeightsPx = publisherFloatItemHeightsPx,
    )
}


@Composable
fun SideBoxComposite(
    element: ContentElement.Paragraph,
    sideBox: SideBoxSpec,
    besideText: AnnotatedString,
    settings: ReaderSettings,
    fontSize: Float,
    bookFonts: Map<String, FontFamily>,
    language: String?,
    colors: ReaderColors,
    totalWidthPx: Int,
    surroundingBackground: Color = colors.background,
    publisherBackgroundAlreadyApplied: Boolean = false,
    invertImages: Boolean = false,
    highlights: ReaderHighlights? = null,
    itemIndex: Int = -1,
    footnotes: FootnoteHandler? = null,
    searchHighlight: String? = null,
    modifier: Modifier = Modifier,
) {
    val blockColors = publisherColorPair(
        element.block,
        settings.bookStyles,
        colors.text,
        surroundingBackground,
        backgroundAlreadyApplied = publisherBackgroundAlreadyApplied,
    )
    val besideStyle = ReaderMetrics.textStyle(
        element, settings, fontSize,
        isParagraphStart = sideBox.capText == null,
        bookFonts = bookFonts,
        language = language,
    ).copy(color = blockColors.foreground)


    val capLength = sideBox.capText?.length ?: 0
    val capFragment = if (sideBox.capText != null) {
        rememberTextFragment(highlights, itemIndex, 0, capLength)
    } else {
        null
    }
    val bidiBeside = remember(besideText) { BidiLayoutText.of(besideText) }
    val besideFragment = rememberTextFragment(
        highlights, itemIndex, capLength, besideText.length, bidiBeside,
    )

    Layout(
        modifier = modifier,
        content = {
            if (sideBox.capText != null) {
                val cap = element.block?.firstLetter
                val capColors = publisherColorPair(
                    foregroundArgb = cap?.foregroundColorArgb
                        ?: element.block?.foregroundColorArgb,
                    backgroundArgb = cap?.backgroundColorArgb,
                    enabled = settings.bookStyles,
                    defaultForeground = blockColors.foreground,
                    surroundingBackground = blockColors.effectiveBackground,
                )
                val capSize = sideBox.capFontSizeSp.takeIf { it > 0f }
                    ?: (fontSize * (cap?.scale ?: 2.6f).coerceIn(1.8f, 4f))
                Text(
                    text = sideBox.capText,
                    style = ReaderMetrics
                        .dropCapStyle(settings, capSize, cap, bookFonts, language)
                        .copy(
                            color = capColors.foreground,
                            background = capColors.background ?: androidx.compose.ui.graphics.Color.Unspecified,
                        ),
                    softWrap = false,
                    onTextLayout = { capFragment?.layout = it },
                    modifier = Modifier.readerHighlights(capFragment, highlights),
                )
            } else if (sideBox.publisherFloat != null) {
                PublisherFloatColumn(
                    sideBox = sideBox,
                    settings = settings,
                    fontSize = fontSize,
                    bookFonts = bookFonts,
                    language = language,
                    colors = colors,
                    surroundingBackground = blockColors.effectiveBackground,
                    invertImages = invertImages,
                    highlights = highlights,
                    footnotes = footnotes,
                    searchHighlight = searchHighlight,
                )
            } else {
                AsyncImage(
                    model = sideBox.imagePath?.let { File(it) },
                    contentDescription = element.block?.floatImage?.altText,
                    contentScale = ContentScale.Fit,
                    colorFilter = imageColorFilter(invertImages),
                )
            }
            Text(
                text = bidiBeside.display,
                style = besideStyle,
                inlineContent = inlineImageContent(bidiBeside.display, invertImages),
                onTextLayout = { besideFragment?.layout = it },
                modifier = Modifier.readerHighlights(besideFragment, highlights),
            )
        },
    ) { measurables, _ ->
        val boxPlaceable = measurables[0].measure(
            if (sideBox.capText != null) {
                Constraints()
            } else {
                Constraints.fixed(
                    sideBox.boxWidthPx.coerceAtLeast(1),
                    sideBox.boxHeightPx.coerceAtLeast(1),
                )
            },
        )
        val besideWidth = sideBox.besideWidthPx.coerceAtLeast(1)
        val besidePlaceable = measurables[1].measure(
            Constraints(
                minWidth = besideWidth,
                maxWidth = besideWidth,
                maxHeight = sideBox.compositeHeightPx.coerceAtLeast(1),
            ),
        )
        layout(totalWidthPx, sideBox.compositeHeightPx) {
            if (sideBox.publisherStacked) {
                boxPlaceable.place((totalWidthPx - sideBox.boxWidthPx).coerceAtLeast(0) / 2, 0)
                besidePlaceable.place(0, sideBox.boxHeightPx)
                return@layout
            }


            val boxTop = if (sideBox.capText != null) sideBox.capTopPx else 0
            if (sideBox.leftSide) {
                boxPlaceable.place(0, boxTop)
                besidePlaceable.place(totalWidthPx - besideWidth, 0)
            } else {
                boxPlaceable.place(totalWidthPx - sideBox.boxWidthPx, boxTop)
                besidePlaceable.place(0, 0)
            }
        }
    }
}


@Composable
private fun PublisherFloatColumn(
    sideBox: SideBoxSpec,
    settings: ReaderSettings,
    fontSize: Float,
    bookFonts: Map<String, FontFamily>,
    language: String?,
    colors: ReaderColors,
    surroundingBackground: Color,
    invertImages: Boolean,
    highlights: ReaderHighlights?,
    footnotes: FootnoteHandler?,
    searchHighlight: String?,
) {
    val publisherFloat = sideBox.publisherFloat ?: return
    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSize.sp.toPx() }
    val frameGeometry = remember(publisherFloat, sideBox.boxWidthPx, fontSizePx) {
        publisherFloatFrameGeometry(publisherFloat, sideBox.boxWidthPx, fontSizePx)
    }
    val width = with(density) { sideBox.boxWidthPx.toDp() }
    val height = with(density) { sideBox.boxHeightPx.toDp() }
    val contentWidth = with(density) { frameGeometry.contentWidthPx.toDp() }
    val left = with(density) { frameGeometry.contentLeftPx.toDp() }
    val top = with(density) { frameGeometry.topInsetPx.toDp() }
    val frameBackground = publisherBoxEffectiveBackground(
        boxes = frameGeometry.boxes.map(PublisherDrawBox::source),
        surroundingBackground = surroundingBackground,
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .drawPublisherBoxes(frameGeometry, 0f, fontSizePx),
    ) {
        Column(
            modifier = Modifier
                .absoluteOffset(x = left, y = top)
                .width(contentWidth),
        ) {
            publisherFloat.contents.forEachIndexed { index, content ->
                PublisherFloatLeaf(
                    content = content,
                    fixedHeightPx = sideBox.publisherFloatItemHeightsPx
                        .getOrElse(index) { 1 },
                    availableWidthPx = frameGeometry.contentWidthPx,
                    settings = settings,
                    fontSize = fontSize,
                    bookFonts = bookFonts,
                    language = language,
                    colors = colors,
                    surroundingBackground = frameBackground,
                    publisherFloatOwnsImageWidth =
                        publisherFloatOwnsDirectImageWidth(publisherFloat, content),
                    invertImages = invertImages,
                    highlights = highlights,
                    footnotes = footnotes,
                    searchHighlight = searchHighlight,
                )
            }
        }
    }
}

@Composable
private fun PublisherFloatLeaf(
    content: ReaderPublisherFloatContent,
    fixedHeightPx: Int,
    availableWidthPx: Int,
    settings: ReaderSettings,
    fontSize: Float,
    bookFonts: Map<String, FontFamily>,
    language: String?,
    colors: ReaderColors,
    surroundingBackground: Color,
    publisherFloatOwnsImageWidth: Boolean,
    invertImages: Boolean,
    highlights: ReaderHighlights?,
    footnotes: FootnoteHandler?,
    searchHighlight: String?,
) {
    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSize.sp.toPx() }
    val geometry = remember(content.publisherBoxes, availableWidthPx, fontSizePx) {
        publisherBoxGeometry(
            boxes = content.publisherBoxes,
            columnWidthPx = availableWidthPx,
            fontSizePx = fontSizePx,
            partStartsElement = true,
            partEndsElement = true,
            enabled = true,
        )
    }
    val element = content.element
    val (vTop, vBottom) = ReaderMetrics.verticalPaddings(
        element,
        fontSize,
        settings.bookStyles,
    )
    val top = vTop + with(density) { geometry.topInsetPx.toDp() }
    val bottom = vBottom + with(density) { geometry.bottomInsetPx.toDp() }
    val contentWidthDp = with(density) { geometry.contentWidthPx.toDp() }
    val (startInset, endInset) = ReaderMetrics.horizontalInsets(
        element,
        contentWidthDp,
        fontSize,
    )
    val (leftInset, _) = ReaderMetrics.physicalHorizontalInsets(
        element,
        contentWidthDp,
        fontSize,
    )
    val left = with(density) { geometry.contentLeftPx.toDp() } + leftInset
    val exactWidth = with(density) {
        (geometry.contentWidthPx - startInset.roundToPx() - endInset.roundToPx())
            .coerceAtLeast(1)
            .toDp()
    }
    val leafBackground = publisherBoxEffectiveBackground(
        boxes = content.publisherBoxes,
        surroundingBackground = surroundingBackground,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { fixedHeightPx.coerceAtLeast(1).toDp() })
            .drawPublisherBoxes(geometry, 0f, fontSizePx),
    ) {
        when (element) {
            is ContentElement.Paragraph, is ContentElement.Heading -> {
                val raw = when (element) {
                    is ContentElement.Paragraph -> element.text
                    is ContentElement.Heading -> element.styledText
                    else -> AnnotatedString("")
                }
                val block = when (element) {
                    is ContentElement.Paragraph -> element.block
                    is ContentElement.Heading -> element.block
                    else -> null
                }
                val defaultColor = if ((element as? ContentElement.Paragraph)?.style ==
                    com.example.mobilereader.data.model.ParagraphStyle.QUOTE
                ) {
                    colors.secondaryText
                } else {
                    colors.text
                }
                val pair = publisherColorPair(
                    block,
                    settings.bookStyles,
                    defaultColor,
                    leafBackground,
                    backgroundAlreadyApplied = settings.bookStyles &&
                        hasPublisherBoxBackground(content.publisherBoxes),
                )
                val linkColor = readableReaderForeground(colors.accent, pair.effectiveBackground)
                val decorated = remember(
                    raw, settings.bookStyles, pair, linkColor,
                    footnotes, searchHighlight, colors.accent,
                ) {
                    raw.withPublisherColors(settings.bookStyles, pair)
                        .withFootnoteLinks(linkColor, footnotes)
                        .withSearchHighlight(searchHighlight, colors.accent.copy(alpha = 0.3f))
                }
                val bidi = remember(decorated) { BidiLayoutText.of(decorated) }
                val fragment = rememberTextFragment(
                    highlights,
                    content.sourceItemIndex,
                    charStart = 0,
                    length = raw.length,
                    bidi = bidi,
                )
                Text(
                    text = bidi.display,
                    style = ReaderMetrics.textStyle(
                        element,
                        settings,
                        fontSize,
                        bookFonts = bookFonts,
                        language = language,
                    ).copy(color = pair.foreground),
                    inlineContent = inlineImageContent(bidi.display, invertImages),
                    onTextLayout = { fragment?.layout = it },
                    modifier = Modifier
                        .absoluteOffset(x = left, y = top)
                        .width(exactWidth)
                        .readerHighlights(fragment, highlights),
                )
            }

            is ContentElement.Image -> {
                val imageHeightPx = (fixedHeightPx - with(density) {
                    (top + bottom).roundToPx()
                }).coerceAtLeast(1)
                val displayImageWidthPx = imageWidthPx(
                    element = element,
                    contentWidthPx = with(density) { exactWidth.roundToPx() },
                    fontSizePx = fontSizePx,
                    publisherOwnsWidth = publisherBoxesOwnImageWidth(
                        content.publisherBoxes,
                        enabled = true,
                    ) || publisherFloatOwnsImageWidth,
                )
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .absoluteOffset(x = left, y = top)
                        .width(exactWidth)
                        .height(with(density) { imageHeightPx.toDp() }),
                ) {
                    AsyncImage(
                        model = File(element.path),
                        contentDescription = element.altText,
                        contentScale = ContentScale.Fit,
                        colorFilter = imageColorFilter(invertImages),
                        modifier = Modifier
                            .width(with(density) { displayImageWidthPx.toDp() })
                            .height(with(density) { imageHeightPx.toDp() }),
                    )
                }
            }

            ContentElement.Divider -> HorizontalDivider(
                color = colors.secondaryText.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(exactWidth),
            )

            is ContentElement.Spacer -> Unit
            is ContentElement.Table -> Unit
        }
    }
}


internal fun publisherFloatOwnsDirectImageWidth(
    publisherFloat: ReaderPublisherFloat,
    content: ReaderPublisherFloatContent,
): Boolean = publisherFloat.contents.size == 1 &&
    content.element is ContentElement.Image &&
    (publisherFloat.style.widthFrac != null || publisherFloat.style.widthEm != null) &&
    content.publisherBoxes.none { box ->
        box.style.widthFrac != null || box.style.widthEm != null
    }
