package com.example.mobilereader.parser

import com.example.mobilereader.data.model.PageProgression
import com.example.mobilereader.data.parser.EpubPageProgressionParser
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubPageProgressionParserTest {
    @Test
    fun `page progression follows OPF spine direction`() {
        for ((direction, expected) in listOf(
            "rtl" to PageProgression.RTL,
            "ltr" to PageProgression.LTR,
            "" to PageProgression.DEFAULT,
        )) {
            val document = Jsoup.parse(
                "<package><spine page-progression-direction='$direction'/></package>",
                "",
                Parser.xmlParser(),
            )
            assertEquals(expected, EpubPageProgressionParser.parsePageProgression(document))
        }
    }
}
