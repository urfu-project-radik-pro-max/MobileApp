package com.example.mobilereader.reader

import com.example.mobilereader.data.model.InlineImageSize
import com.example.mobilereader.data.parser.InlineTextBuilder
import com.example.mobilereader.ui.reader.inlineImageContent
import com.example.mobilereader.ui.reader.inlineImagePlaceholder
import com.example.mobilereader.ui.reader.inlineImagePlaceholders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InlineImagesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `width and height use their author dimensions`() {
        val image = imageFile()
        val placeholder = inlineImagePlaceholder(
            path = image.absolutePath,
            size = InlineImageSize(widthEm = 2f, heightEm = 1f),
            availableWidthPx = 200,
            fontSizePx = 20f,
        )

        assertEquals(2f, placeholder.width.value)
        assertEquals(1f, placeholder.height.value)
    }

    @Test
    fun `one dimension preserves image proportions and available width caps it`() {
        val image = imageFile()
        val widthOnly = inlineImagePlaceholder(
            path = image.absolutePath,
            size = InlineImageSize(widthEm = 10f),
            availableWidthPx = 100,
            fontSizePx = 20f,
        )
        val heightOnly = inlineImagePlaceholder(
            path = image.absolutePath,
            size = InlineImageSize(heightEm = 2f),
            availableWidthPx = 100,
            fontSizePx = 20f,
        )
        val percentage = inlineImagePlaceholder(
            path = image.absolutePath,
            size = InlineImageSize(widthFrac = 0.25f),
            availableWidthPx = 100,
            fontSizePx = 20f,
        )

        assertEquals(5f, widthOnly.width.value)
        assertEquals(2.5f, widthOnly.height.value)
        assertEquals(4f, heightOnly.width.value)
        assertEquals(2f, heightOnly.height.value)
        assertEquals(1.25f, percentage.width.value)
        assertEquals(0.625f, percentage.height.value)
    }

    @Test
    fun `unsized image keeps the original compact placeholder height`() {
        val placeholder = inlineImagePlaceholder(
            path = imageFile().absolutePath,
            size = InlineImageSize(),
            availableWidthPx = 200,
            fontSizePx = 20f,
        )

        assertEquals(0.9f, placeholder.height.value)
        assertEquals(1.8f, placeholder.width.value)
    }

    @Test
    fun `two uses of one file have separate screen content matching pagination`() {
        val image = imageFile()
        val builder = InlineTextBuilder()
        builder.text("Before ")
        builder.inlineImage(image.absolutePath, size = InlineImageSize(widthEm = 1f))
        builder.text(" between ")
        builder.inlineImage(image.absolutePath, size = InlineImageSize(widthEm = 3f))
        builder.text(" after")
        val text = builder.build()

        val placeholders = inlineImagePlaceholders(text, availableWidthPx = 200, fontSizePx = 20f)
        val content = inlineImageContent(text, availableWidthPx = 200, fontSizePx = 20f)

        assertEquals(2, placeholders.size)
        assertEquals(2, content.size)
        assertEquals(1f, placeholders[0].item.width.value)
        assertEquals(3f, placeholders[1].item.width.value)
        assertTrue(content.values.any { it.placeholder == placeholders[0].item })
        assertTrue(content.values.any { it.placeholder == placeholders[1].item })
    }

    private fun imageFile() = temporaryFolder.newFile("image.svg").apply {
        writeText("<svg viewBox='0 0 200 100'></svg>")
    }
}
