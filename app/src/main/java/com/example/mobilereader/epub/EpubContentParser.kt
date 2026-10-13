package com.example.mobilereader.epub

import androidx.compose.ui.text.AnnotatedString
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.File

object EpubContentParser {
    fun parseContent(bookFile: File): EpubBookContent = EpubArchive(bookFile).use { epubArchive ->
        val packagePath = EpubParser.findPackagePath(epubArchive)
        val packageDirectory = packagePath.substringBeforeLast('/', "")
        val packageDocument = EpubParser.parsePackage(epubArchive, packagePath)
        val manifestItems = packageDocument.select("manifest > item[id][href]")
            .associateBy { it.id() }
        val readingItems = packageDocument.select("spine > itemref[idref]")
            .filterNot { it.attr("linear").equals("no", ignoreCase = true) }
        if (readingItems.isEmpty()) throw EpubReadException("EPUB has no reading order")

        val bookChapters = readingItems.map { readingItem ->
            val manifestItem = manifestItems[readingItem.attr("idref")]
                ?: throw EpubReadException("EPUB reading item is absent from manifest")
            val mediaType = manifestItem.attr("media-type")
            if (mediaType != "application/xhtml+xml" && mediaType != "text/html") {
                throw EpubReadException("EPUB reading item has unsupported media type")
            }
            val resourcePath = epubArchive.resolveFrom(
                packageDirectory,
                manifestItem.attr("href"),
            ) ?: throw EpubReadException("EPUB chapter path is unsafe")
            val chapterBytes = epubArchive.readRequired(
                resourcePath,
                EpubReadingLimits().mostChapterBytes,
            )
            readChapter(chapterBytes)
        }
        EpubBookContent(bookChapters)
    }

    private fun readChapter(chapterBytes: ByteArray): EpubChapter {
        val chapterDocument = ByteArrayInputStream(chapterBytes).use { chapterStream ->
            Jsoup.parse(chapterStream, null, "", Parser.xmlParser())
        }
        val chapterBody = chapterDocument.selectFirst("body")
            ?: throw EpubReadException("EPUB chapter body is missing")
        val contentElements = mutableListOf<EpubContentElement>()

        fun appendElements(parentElement: Element) {
            for (childElement in parentElement.children()) {
                val elementName = childElement.normalName().substringAfterLast(':').lowercase()
                val elementText = childElement.text().trim()
                when {
                    elementName == "p" && elementText.isNotEmpty() ->
                        contentElements += EpubContentElement.Paragraph(AnnotatedString(elementText))
                    elementName.matches(Regex("h[1-6]")) && elementText.isNotEmpty() ->
                        contentElements += EpubContentElement.Heading(
                            AnnotatedString(elementText),
                            elementName.substring(1).toInt(),
                        )
                    else -> appendElements(childElement)
                }
            }
        }
        appendElements(chapterBody)

        val titleFromHead = chapterDocument.selectFirst("head > title")
            ?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val titleFromHeading = contentElements.filterIsInstance<EpubContentElement.Heading>()
            .firstOrNull()?.text?.text
        return EpubChapter(titleFromHead ?: titleFromHeading, contentElements)
    }
}
