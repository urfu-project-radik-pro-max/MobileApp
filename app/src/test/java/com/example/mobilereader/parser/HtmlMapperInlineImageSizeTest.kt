package com.example.mobilereader.parser

import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.INLINE_IMAGE_CHAR
import com.example.mobilereader.data.model.INLINE_IMAGE_ID_TAG
import com.example.mobilereader.data.model.INLINE_IMAGE_SIZE_TAG
import com.example.mobilereader.data.model.INLINE_IMAGE_TAG
import com.example.mobilereader.data.model.InlineImageSize
import com.example.mobilereader.data.parser.CssResolver
import com.example.mobilereader.data.parser.HtmlMapper
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlMapperInlineImageSizeTest {
    @Test
    fun `html dimensions keep a small image between adjacent text`() {
        val elements = map("<p>Before <img src='icon.svg' width='32' height='16'> after</p>")

        assertEquals(1, elements.size)
        val paragraph = elements.single() as ContentElement.Paragraph
        assertEquals("Before ${INLINE_IMAGE_CHAR} after", paragraph.text.text)
        assertEquals(1, paragraph.text.getStringAnnotations(INLINE_IMAGE_TAG, 0, paragraph.text.length).size)
        assertEquals(
            InlineImageSize(widthEm = 2f, heightEm = 1f),
            sizes(paragraph).single(),
        )
    }

    @Test
    fun `css em and percent dimensions are stored on their image occurrences`() {
        val css = CssResolver(
            listOf(
                CssResolver.Sheet("img.em-size { width: 2em; height: 1em; } img.percent-size { width: 25%; }"),
            ),
        )
        val elements = map(
            "<p>Start <img class='em-size' src='one.svg'> middle " +
                "<img class='percent-size' src='two.svg'> end</p>",
            css,
        )

        assertEquals(1, elements.size)
        val paragraph = elements.single() as ContentElement.Paragraph
        assertEquals(
            listOf(
                InlineImageSize(widthEm = 2f, heightEm = 1f),
                InlineImageSize(widthFrac = 0.25f),
            ),
            sizes(paragraph),
        )
    }

    @Test
    fun `two occurrences of one file keep separate dimensions and identifiers`() {
        val elements = map(
            "<p>First <img src='shared.svg' width='16' height='16'> then " +
                "<img src='shared.svg' width='48' height='24'> last</p>",
        )

        assertEquals(1, elements.size)
        val paragraph = elements.single() as ContentElement.Paragraph
        assertEquals("First ${INLINE_IMAGE_CHAR} then ${INLINE_IMAGE_CHAR} last", paragraph.text.text)
        assertEquals(
            listOf(
                InlineImageSize(widthEm = 1f, heightEm = 1f),
                InlineImageSize(widthEm = 3f, heightEm = 1.5f),
            ),
            sizes(paragraph),
        )
        val ids = paragraph.text.getStringAnnotations(INLINE_IMAGE_ID_TAG, 0, paragraph.text.length)
        assertEquals(2, ids.map { it.item }.toSet().size)
        val paths = paragraph.text.getStringAnnotations(INLINE_IMAGE_TAG, 0, paragraph.text.length)
        assertEquals(listOf("/tmp/shared.svg", "/tmp/shared.svg"), paths.map { it.item })
    }

    @Test
    fun `image without dimensions has no size annotation`() {
        val paragraph = map("<p>Before <img src='plain.svg'> after</p>")
            .single() as ContentElement.Paragraph

        assertEquals("Before ${INLINE_IMAGE_CHAR} after", paragraph.text.text)
        assertNull(sizes(paragraph).singleOrNull())
    }

    private fun map(html: String, css: CssResolver? = null): List<ContentElement> {
        val body = Jsoup.parse(html).body()
        return HtmlMapper(
            resolveImage = { source -> "/tmp/$source" },
            css = css,
        ).map(body)
    }

    private fun sizes(paragraph: ContentElement.Paragraph): List<InlineImageSize> {
        return paragraph.text.getStringAnnotations(
            INLINE_IMAGE_SIZE_TAG,
            0,
            paragraph.text.length,
        ).mapNotNull { annotation ->
            InlineImageSize.fromAnnotationValue(annotation.item)
        }
    }
}
