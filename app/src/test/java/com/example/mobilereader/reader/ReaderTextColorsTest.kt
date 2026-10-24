package com.example.mobilereader.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.example.mobilereader.data.model.LINK_TAG
import com.example.mobilereader.ui.reader.withoutPublisherColors
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTextColorsTest {
    @Test
    fun `reader text removes author colors and keeps link annotations`() {
        val source = AnnotatedString.Builder("Open note").apply {
            addStyle(
                SpanStyle(color = Color.Red, background = Color.Yellow),
                0,
                4,
            )
            addStringAnnotation(LINK_TAG, "chapter.xhtml#note", 0, 4)
        }.toAnnotatedString()

        val readingText = source.withoutPublisherColors()

        assertEquals("Open note", readingText.text)
        assertEquals(Color.Unspecified, readingText.spanStyles.single().item.color)
        assertEquals(Color.Unspecified, readingText.spanStyles.single().item.background)
        assertEquals(
            "chapter.xhtml#note",
            readingText.getStringAnnotations(LINK_TAG, 0, 4).single().item,
        )
    }
}
