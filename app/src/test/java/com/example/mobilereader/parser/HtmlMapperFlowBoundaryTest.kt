package com.example.mobilereader.parser

import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.LINK_TAG
import com.example.mobilereader.data.parser.CssResolver
import com.example.mobilereader.data.parser.HtmlMapper
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlMapperFlowBoundaryTest {
    @Test
    fun `styled block boundaries preserve text and links in source order`() {
        val mapper = HtmlMapper(
            resolveImage = { null },
            resolveLink = { href -> "chapter.xhtml$href" },
            css = CssResolver(listOf(CssResolver.Sheet(
                ".frame { float:left; border:2px solid; padding:1em; width:40%; }",
            ))),
        )
        val body = Jsoup.parse(
            "<div class='frame'>Before <a href='#next'>link</a>" +
                "<p>Middle</p>After</div><div class='frame' id='next'>Next</div>",
        ).body()

        val paragraphs = mapper.map(body).filterIsInstance<ContentElement.Paragraph>()
        assertEquals(listOf("Before link", "Middle", "After", "Next"),
            paragraphs.map { it.text.text.trim() })
        val link = paragraphs.first().text.getStringAnnotations(
            LINK_TAG, 0, paragraphs.first().text.length,
        ).single()
        assertEquals("chapter.xhtml#next", link.item)
    }
}
