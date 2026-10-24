package com.example.mobilereader.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.mobilereader.data.ReaderFont
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.model.BlockStyle
import com.example.mobilereader.data.model.BlockAlign
import com.example.mobilereader.data.model.BookTextDirection
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.ui.reader.ReaderMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMetricsTest {
    private val settings = ReaderSettings(font = ReaderFont.SERIF)

    @Test
    fun `enlarged paragraph uses author scale and line height`() {
        val paragraph = ContentElement.Paragraph(
            AnnotatedString("Long paragraph"),
            block = BlockStyle(fontScale = 2f, lineHeightMult = 3f),
        )
        for (size in listOf(14f, 24f)) {
            val style = ReaderMetrics.textStyle(paragraph, settings.copy(lineHeight = 1.5f), size)
            assertEquals(size * 2f, style.fontSize.value, 0.001f)
            assertEquals(size * 2f * 3f, style.lineHeight.value, 0.001f)
            assertEquals(0f, ReaderMetrics.horizontalInsets(paragraph, 300.dp, size).first.value, 0f)
        }
    }

    @Test
    fun `headings preserve semantic hierarchy and user centering`() {
        val sizes = (1..6).map { level ->
            val heading = ContentElement.Heading("Heading", level)
            val style = ReaderMetrics.textStyle(heading, settings, 20f)
            assertEquals(20f * ReaderMetrics.headingScale(level), style.fontSize.value, 0.001f)
            assertEquals(TextAlign.Start, style.textAlign)
            assertEquals(TextAlign.Center,
                ReaderMetrics.textStyle(heading, settings.copy(centerHeadings = true), 20f).textAlign)
            style.fontSize.value
        }
        sizes.zipWithNext().forEach { (larger, smaller) -> assertTrue(larger > smaller) }
    }

    @Test
    fun `author heading scale replaces default scale and alignment is respected`() {
        val heading = ContentElement.Heading("Subtitle", 2,
            BlockStyle(fontScale = 1.1f, align = BlockAlign.RIGHT))
        val style = ReaderMetrics.textStyle(heading, settings, 20f)
        assertEquals(22f, style.fontSize.value, 0.001f)
        assertEquals(27.5f, style.lineHeight.value, 0.001f)
        assertEquals(TextAlign.Right, style.textAlign)
    }

    @Test
    fun `ordinary paragraph remains at reader size while author alignment applies`() {
        val ordinary = ContentElement.Paragraph(AnnotatedString("Ordinary"))
        val enlarged = ContentElement.Paragraph(AnnotatedString("Large"),
            block = BlockStyle(fontScale = 1.5f, align = BlockAlign.CENTER))
        val ordinaryStyle = ReaderMetrics.textStyle(ordinary, settings, 20f)
        val enlargedStyle = ReaderMetrics.textStyle(enlarged, settings, 20f)
        assertEquals(20f, ordinaryStyle.fontSize.value, 0.001f)
        assertEquals(30f, ordinaryStyle.lineHeight.value, 0.001f)
        assertEquals(30f, enlargedStyle.fontSize.value, 0.001f)
        assertEquals(45f, enlargedStyle.lineHeight.value, 0.001f)
        assertEquals(TextAlign.Center, enlargedStyle.textAlign)
    }

    @Test
    fun `reader gaps apply to paragraphs and images`() {
        val paragraph = ContentElement.Paragraph(
            AnnotatedString("Text"),
            block = BlockStyle(fontScale = 2f),
        )
        val image = ContentElement.Image(path = "/missing/image.png")
        assertEquals(3f, ReaderMetrics.verticalPaddings(paragraph, 20f).first.value, 0f)
        assertEquals(12f, ReaderMetrics.verticalPaddings(image, 20f).first.value, 0f)
    }

    @Test
    fun `table cells keep reader typography and RTL direction`() {
        val block = BlockStyle(
            fontScale = 2f,
            lineHeightMult = 3f,
            language = "ar",
            direction = BookTextDirection.RTL,
        )
        val style = ReaderMetrics.tableCellStyle(
            settings = settings.copy(lineHeight = 1.4f),
            fontSize = 20f,
            scale = 1f,
            header = true,
            language = "en",
            tableBlock = block,
        )
        assertEquals(18.4f, style.fontSize.value, 0.001f)
        assertEquals(18.4f * 1.4f, style.lineHeight.value, 0.001f)
        assertEquals(TextDirection.Rtl, style.textDirection)
        assertEquals("ar", style.localeList?.firstOrNull()?.toLanguageTag())
    }
}
