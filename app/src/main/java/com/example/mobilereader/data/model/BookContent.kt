package com.example.mobilereader.data.model

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType


class BookMetadata(
    val title: String?,
    val author: String?,
    val coverBytes: ByteArray?,

    val authors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val series: String? = null,
    val seriesNumber: Float? = null,
    val publisher: String? = null,
    val year: String? = null,
    val isbn: String? = null,
    val translators: List<String> = emptyList(),

    val description: String? = null,
    val language: String? = null,
)


const val FOOTNOTE_TAG = "footnote"


const val LINK_TAG = "internalLink"


const val EXTERNAL_LINK_TAG = "externalLink"


const val INLINE_IMAGE_TAG = "inlineImage"


const val INLINE_IMAGE_ALT_TAG = "inlineImageAlt"


const val INLINE_IMAGE_CHAR = "\uFFFC"


class BookContent(
    val chapters: List<Chapter>,


    val notes: Map<String, NoteDocument> = emptyMap(),

    val linkTargets: Map<String, Pair<Int, Int>> = emptyMap(),

    val fonts: List<BookFont> = emptyList(),


    val language: String? = null,


    val linkedDocuments: Map<String, LinkedDocument> = emptyMap(),

    val linkedDocumentTargets: Map<String, LinkedDocumentTarget> = emptyMap(),


    val navigation: List<BookNavigationEntry> = emptyList(),


    val publisherPublication: PublisherPublication? = null,


    val pageProgression: PageProgression = PageProgression.DEFAULT,
) {
    val totalElements: Int = chapters.sumOf { it.elements.size }
}


data class NoteDocument(
    val elements: List<ContentElement>,

    val publisherBoxes: List<PublisherBoxSpan> = emptyList(),
) {

    val text: String
        get() = elements.mapNotNull { element ->
            when (element) {
                is ContentElement.Paragraph -> element.text.text
                is ContentElement.Heading -> element.text
                is ContentElement.Table -> element.flatText()
                is ContentElement.Image -> element.altText
                ContentElement.Divider -> null
                is ContentElement.Spacer -> null
            }
        }.joinToString("\n\n")
}


class LinkedDocument(

    val id: String,
    val title: String?,
    val elements: List<ContentElement>,

    val publisherBoxes: List<PublisherBoxSpan> = emptyList(),
)


data class LinkedDocumentTarget(
    val documentId: String,
    val elementIndex: Int,
)


class BookNavigationEntry(
    val title: String,
    val depth: Int,
    val target: BookNavigationTarget,
)


sealed interface BookNavigationTarget {
    data class ReadingOrder(
        val chapterIndex: Int,
        val elementIndex: Int,
    ) : BookNavigationTarget

    data class Linked(
        val documentId: String,
        val elementIndex: Int,
    ) : BookNavigationTarget
}


data class BookFont(

    val family: String,
    val path: String,
    val bold: Boolean,
    val italic: Boolean,
)

class Chapter(
    val title: String?,
    val elements: List<ContentElement>,


    val depth: Int = 0,

    val publisherBoxes: List<PublisherBoxSpan> = emptyList(),
)

sealed interface ContentElement {

    data class Paragraph(
        val text: AnnotatedString,
        val style: ParagraphStyle = ParagraphStyle.NORMAL,
        val block: BlockStyle? = null,
    ) : ContentElement

    data class Heading(

        val styledText: AnnotatedString,
        val level: Int,
        val block: BlockStyle? = null,
    ) : ContentElement {
        constructor(
            text: String,
            level: Int,
            block: BlockStyle? = null,
        ) : this(AnnotatedString(text), level, block)


        val text: String get() = styledText.text
    }


    data class Image(
        val path: String,
        val widthFrac: Float? = null,

        val widthEm: Float? = null,
        val heightEm: Float? = null,

        val altText: String? = null,

        val spaceBeforeSpecified: Boolean = false,
        val spaceAfterSpecified: Boolean = false,
    ) : ContentElement

    data object Divider : ContentElement


    data class Spacer(val heightEm: Float = 1f) : ContentElement


    data class Table(
        val rows: List<TableRow>,
        val block: BlockStyle? = null,
    ) : ContentElement {

        fun flatText(): String = rows.joinToString("\n") { row ->
            row.cells.joinToString("  ") { it.text.text }
        }
    }
}


const val BOOKMARK_PREVIEW_CHARS = 90


fun ContentElement.previewText(): String? = when (this) {
    is ContentElement.Paragraph -> text.text.take(BOOKMARK_PREVIEW_CHARS)
    is ContentElement.Heading -> text.take(BOOKMARK_PREVIEW_CHARS)
    is ContentElement.Table -> flatText().take(BOOKMARK_PREVIEW_CHARS)
    else -> null
}

class TableRow(
    val cells: List<TableCell>,

    val isHeader: Boolean,
)

class TableCell(
    val text: AnnotatedString,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,

    val align: BlockAlign? = null,
    val header: Boolean = false,


    val block: BlockStyle? = null,

    val publisherBox: PublisherBoxStyle? = null,

    val publisherPaddingSpecified: Boolean = false,

    val publisherBorderSpecified: Boolean = false,
)

enum class ParagraphStyle { NORMAL, QUOTE, POEM }


enum class BlockAlign { START, CENTER, END, JUSTIFY, LEFT, RIGHT }


enum class BookTextDirection { LTR, RTL, AUTO }


enum class InlineBidiMode {
    ISOLATE_AUTO,
    ISOLATE_LTR,
    ISOLATE_RTL,
    EMBED_LTR,
    EMBED_RTL,
    OVERRIDE_LTR,
    OVERRIDE_RTL,
    ISOLATE_OVERRIDE_LTR,
    ISOLATE_OVERRIDE_RTL,
    PLAINTEXT,
}

const val BIDI_TAG = "mobilereader:bidi"


data class BlockStyle(
    val align: BlockAlign? = null,
    val italic: Boolean? = null,
    val bold: Boolean? = null,


    val fontScale: Float? = null,

    val indentStartFrac: Float = 0f,
    val indentStartEm: Float = 0f,
    val indentEndFrac: Float = 0f,
    val indentEndEm: Float = 0f,

    val indentLeftFrac: Float = 0f,
    val indentLeftEm: Float = 0f,
    val indentRightFrac: Float = 0f,
    val indentRightEm: Float = 0f,

    val firstLineIndent: Boolean? = null,

    val firstLineIndentEm: Float? = null,

    val spaceBeforeEm: Float = 0f,
    val spaceAfterEm: Float = 0f,


    val spaceBeforeSpecified: Boolean = false,
    val spaceAfterSpecified: Boolean = false,

    val fontFamily: String? = null,

    val lineHeightMult: Float? = null,

    val hyphens: Boolean? = null,

    val pageBreakBefore: Boolean = false,

    val firstLetter: FirstLetterStyle? = null,

    val floatImage: FloatImage? = null,

    val language: String? = null,

    val direction: BookTextDirection? = null,

    val foregroundColorArgb: Int? = null,

    val backgroundColorArgb: Int? = null,
) {
    val isDefault: Boolean
        get() = this == DEFAULT

    companion object {
        val DEFAULT = BlockStyle()
    }
}


data class FloatImage(

    val path: String,

    val widthFrac: Float,
    val left: Boolean,
    val altText: String? = null,
)


data class FirstLetterStyle(

    val scale: Float,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val isDropCap: Boolean,

    val fontFamily: String? = null,

    val foregroundColorArgb: Int? = null,
    val backgroundColorArgb: Int? = null,

    val leftSide: Boolean = true,

    val direction: BookTextDirection? = null,
    val language: String? = null,


    val sourceTextLength: Int? = null,
)


fun AnnotatedString.withFootnoteRefStyle(): AnnotatedString {
    val refs = getStringAnnotations(FOOTNOTE_TAG, 0, length)
    if (refs.isEmpty()) return this
    val builder = AnnotatedString.Builder(this)
    for (ref in refs) {
        builder.addStyle(
            SpanStyle(
                fontSize = TextUnit(0.72f, TextUnitType.Em),
                baselineShift = BaselineShift(0.35f),
            ),
            ref.start,
            ref.end,
        )
    }
    return builder.toAnnotatedString()
}


fun AnnotatedString.withoutFootnotes(): AnnotatedString {
    val refs = getStringAnnotations(FOOTNOTE_TAG, 0, length).sortedBy { it.start }
    if (refs.isEmpty()) return this
    val builder = AnnotatedString.Builder()
    var position = 0
    for (ref in refs) {
        if (ref.start > position) builder.append(subSequence(position, ref.start))
        position = maxOf(position, ref.end)
    }
    if (position < length) builder.append(subSequence(position, length))
    return builder.toAnnotatedString()
}
