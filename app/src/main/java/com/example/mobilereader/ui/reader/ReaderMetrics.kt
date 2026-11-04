package com.example.mobilereader.ui.reader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobilereader.data.PageMargins
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.model.BlockAlign
import com.example.mobilereader.data.model.BlockStyle
import com.example.mobilereader.data.model.BookTextDirection
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.HeadingDefaults
import com.example.mobilereader.data.model.ParagraphStyle
import com.example.mobilereader.data.parser.LanguageTag
import com.example.mobilereader.ui.theme.fontFamilyFor


object ReaderMetrics {


    fun horizontalPadding(margins: PageMargins): Dp = margins.dp.dp

    fun contentWidth(viewportWidth: Dp, margins: PageMargins): Dp =
        (viewportWidth - horizontalPadding(margins) * 2).coerceAtLeast(0.dp)

    val maxImageHeight = 520.dp

    private fun blockOf(element: ContentElement): BlockStyle? = when (element) {
        is ContentElement.Paragraph -> element.block
        is ContentElement.Heading -> element.block
        is ContentElement.Table -> element.block
        else -> null
    }


    fun horizontalInsets(
        element: ContentElement,
        contentWidth: Dp,
        fontSize: Float,
    ): Pair<Dp, Dp> {
        val kindInset = when ((element as? ContentElement.Paragraph)?.style) {
            ParagraphStyle.QUOTE -> 20.dp
            ParagraphStyle.POEM -> 24.dp
            else -> 0.dp
        }
        return kindInset to 0.dp
    }


    fun physicalHorizontalInsets(
        element: ContentElement,
        contentWidth: Dp,
        fontSize: Float,
    ): Pair<Dp, Dp> {
        val (start, end) = horizontalInsets(element, contentWidth, fontSize)
        return if (isRtl(element)) end to start else start to end
    }


    fun isRtl(element: ContentElement): Boolean {
        val block = blockOf(element)
        when (block?.direction) {
            BookTextDirection.LTR -> return false
            BookTextDirection.RTL -> return true
            BookTextDirection.AUTO, null -> Unit
        }
        val text = when (element) {
            is ContentElement.Paragraph -> element.text.text
            is ContentElement.Heading -> element.text
            is ContentElement.Table -> element.flatText()
            else -> ""
        }
        firstStrongRtl(text)?.let { return it }
        return LanguageTag.isRtl(block?.language)
    }

    private fun firstStrongRtl(text: String): Boolean? {
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            when (Character.getDirectionality(codePoint)) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            }
            offset += Character.charCount(codePoint)
        }
        return null
    }


    fun verticalPaddings(
        element: ContentElement,
        fontSize: Float,
    ): Pair<Dp, Dp> {
        val base = when (element) {
            is ContentElement.Paragraph -> when (element.style) {
                ParagraphStyle.NORMAL -> 3.dp
                else -> 6.dp
            }

            is ContentElement.Heading -> 0.dp
            is ContentElement.Image -> 12.dp
            ContentElement.Divider -> 20.dp
            is ContentElement.Spacer -> 0.dp
            is ContentElement.Table -> 12.dp
        }
        val defaultTop = if (element is ContentElement.Heading) {
            (fontSize * HeadingDefaults.spaceBeforeEm(element.level)).dp
        } else base
        val defaultBottom = if (element is ContentElement.Heading) {
            (fontSize * HeadingDefaults.spaceAfterEm(element.level)).dp
        } else base
        return defaultTop to defaultBottom
    }


    fun spacerHeight(element: ContentElement.Spacer, fontSize: Float): Dp =
        (fontSize * element.heightEm.coerceIn(0.3f, 3f)).dp


    fun textStyle(
        element: ContentElement,
        settings: ReaderSettings,
        fontSize: Float,
        isParagraphStart: Boolean = true,
        bookFonts: Map<String, FontFamily> = emptyMap(),
        language: String? = null,
    ): TextStyle {
        val block = blockOf(element)
        val resolvedLanguage = block?.language ?: language
        val fontFamily = block?.fontFamily?.let { family -> bookFonts[family] }
            ?: fontFamilyFor(settings.font)
        val base = TextStyle(
            fontSize = fontSize.sp,
            lineHeight = (fontSize * settings.lineHeight).sp,


            fontFamily = fontFamily,
            hyphens = Hyphens.Auto,
            lineBreak = LineBreak.Paragraph,


            localeList = resolvedLanguage?.let { LocaleList(it) },
            textDirection = when (block?.direction) {
                BookTextDirection.LTR -> TextDirection.Ltr
                BookTextDirection.RTL -> TextDirection.Rtl
                BookTextDirection.AUTO, null -> autoTextDirection(resolvedLanguage)
            },
        )
        return when (element) {
            is ContentElement.Paragraph -> paragraphStyle(
                element, settings, fontSize, isParagraphStart, base,
            )

            is ContentElement.Heading -> headingStyle(element, settings, fontSize, base)

            else -> base
        }
    }


    private fun paragraphStyle(
        element: ContentElement.Paragraph,
        settings: ReaderSettings,
        fontSize: Float,
        isParagraphStart: Boolean,
        base: TextStyle,
    ): TextStyle {
        val kind = element.style
        val normal = kind == ParagraphStyle.NORMAL
        val block = element.block
        val scale = block?.fontScale?.coerceIn(0.6f, 2.6f) ?: 1f
        val defaultAlign = if (normal) TextAlign.Justify else TextAlign.Start
        val align = textAlign(block?.align) ?: defaultAlign
        return base.copy(
            fontSize = (fontSize * scale).sp,
            lineHeight = (fontSize * scale * (block?.lineHeightMult ?: settings.lineHeight)).sp,
            fontStyle = if (block?.italic ?: !normal) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (block?.bold == true) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
            textIndent = if (normal && isParagraphStart &&
                align != TextAlign.Center && align != TextAlign.End && block?.firstLineIndent != false
            ) {
                TextIndent(firstLine = (fontSize * 1.4f).sp)
            } else {
                TextIndent.None
            },
        )
    }

    private fun headingStyle(
        element: ContentElement.Heading,
        settings: ReaderSettings,
        fontSize: Float,
        base: TextStyle,
    ): TextStyle {
        val block = element.block
        val scale = block?.fontScale?.coerceIn(0.6f, 2.6f) ?: headingScale(element.level)
        val defaultAlign = if (HeadingDefaults.isOrnament(element.text)) {
            TextAlign.Center
        } else {
            TextAlign.Start
        }
        return base.copy(
            fontSize = (fontSize * scale).sp,
            lineHeight = (fontSize * scale * (block?.lineHeightMult ?: 1.25f)).sp,
            fontWeight = if (block?.bold == false) FontWeight.Normal else FontWeight.Bold,
            fontStyle = if (block?.italic == true) FontStyle.Italic else FontStyle.Normal,
            textAlign = if (settings.centerHeadings) {
                TextAlign.Center
            } else {
                textAlign(block?.align) ?: defaultAlign
            },
            hyphens = Hyphens.None,
        )
    }

    private fun textAlign(align: BlockAlign?): TextAlign? = when (align) {
        BlockAlign.START -> TextAlign.Start
        BlockAlign.END -> TextAlign.End
        BlockAlign.LEFT -> TextAlign.Left
        BlockAlign.RIGHT -> TextAlign.Right
        BlockAlign.CENTER -> TextAlign.Center
        BlockAlign.JUSTIFY -> TextAlign.Justify
        null -> null
    }


    fun headingScale(level: Int): Float = HeadingDefaults.scale(level)


    val dividerHeight = 1.dp


    val tableCellPadding = 6.dp



    fun tableCellStyle(
        settings: ReaderSettings,
        fontSize: Float,
        scale: Float,
        header: Boolean,
        language: String?,
        tableBlock: BlockStyle? = null,
        cellBlock: BlockStyle? = null,
        bookFonts: Map<String, FontFamily> = emptyMap(),
    ): TextStyle {


        val resolvedSize = fontSize * 0.92f * scale
        val resolvedLanguage = cellBlock?.language ?: tableBlock?.language ?: language
        val resolvedDirection = cellBlock?.direction ?: tableBlock?.direction
        val authoredFontFamily = cellBlock?.fontFamily ?: tableBlock?.fontFamily
        val fontFamily = authoredFontFamily?.let { family -> bookFonts[family] }
            ?: fontFamilyFor(settings.font)
        return TextStyle(
            fontSize = resolvedSize.sp,
            lineHeight = (resolvedSize * settings.lineHeight).sp,
            fontFamily = fontFamily,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            hyphens = Hyphens.None,
            lineBreak = LineBreak.Paragraph,
            localeList = resolvedLanguage?.let { LocaleList(it) },
            textDirection = when (resolvedDirection) {
                BookTextDirection.LTR -> TextDirection.Ltr
                BookTextDirection.RTL -> TextDirection.Rtl
                BookTextDirection.AUTO, null -> autoTextDirection(resolvedLanguage)
            },
        )
    }

    private fun autoTextDirection(language: String?): TextDirection =
        if (LanguageTag.isRtl(language)) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr
}
