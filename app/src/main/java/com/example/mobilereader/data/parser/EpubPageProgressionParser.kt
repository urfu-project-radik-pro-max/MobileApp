package com.example.mobilereader.data.parser

import com.example.mobilereader.data.model.PageProgression
import org.jsoup.nodes.Document
import java.util.Locale

internal object EpubPageProgressionParser {
    fun parsePageProgression(packageDocument: Document): PageProgression {
        val spine = packageDocument.getAllElements()
            .firstOrNull { it.normalName().substringAfterLast(':') == "spine" }
        return when (spine?.attr("page-progression-direction")?.trim()?.lowercase(Locale.ROOT)) {
            "ltr" -> PageProgression.LTR
            "rtl" -> PageProgression.RTL
            else -> PageProgression.DEFAULT
        }
    }
}
