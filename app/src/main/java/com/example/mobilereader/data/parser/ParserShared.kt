package com.example.mobilereader.data.parser

import com.example.mobilereader.data.model.Chapter
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.NoteDocument
import com.example.mobilereader.data.model.slicePublisherBoxSpans
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser


internal fun looksLikeFont(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    val b2 = bytes[2].toInt() and 0xFF
    val b3 = bytes[3].toInt() and 0xFF
    return (b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00) ||
            (b0 == 'O'.code && b1 == 'T'.code && b2 == 'T'.code && b3 == 'O'.code) ||
            (b0 == 't'.code && b1 == 'r'.code && b2 == 'u'.code && b3 == 'e'.code) ||
            (b0 == 't'.code && b1 == 't'.code && b2 == 'c'.code && b3 == 'f'.code)
}


internal fun parseChapterDocument(bytes: ByteArray): Document? {
    val xmlDoc = runCatching {
        Jsoup.parse(bytes.inputStream(), null, "", Parser.xmlParser())
    }.getOrNull()


    val xmlReadingRoot = xmlDoc?.selectFirst("body, book")
    if (xmlReadingRoot != null &&
        (xmlReadingRoot.text().isNotBlank() || xmlReadingRoot.children().isNotEmpty())
    ) {
        return xmlDoc
    }

    return runCatching {
        Jsoup.parse(bytes.inputStream(), null, "")
    }.getOrNull() ?: xmlDoc
}


internal fun buildNotes(
    chapters: List<Chapter>,
    anchorLocations: Map<String, Pair<Int, Int>>,
    linkTargets: Set<String>,
    exactDocuments: Map<String, NoteDocument> = emptyMap(),
): Map<String, NoteDocument> {
    val notes = exactDocuments
        .filterKeys { it in linkTargets }
        .filterValues { it.elements.isNotEmpty() }
        .toMutableMap()


    val anchoredStartsByChapter = anchorLocations.values
        .groupBy { it.first }
        .mapValues { (_, rows) -> rows.map { it.second }.distinct().sorted() }

    for (key in linkTargets) {
        if (key in notes) continue
        val (chapterIndex, elementIndex) = anchorLocations[key] ?: continue
        val chapter = chapters.getOrNull(chapterIndex) ?: continue
        val elements = chapter.elements
        if (elementIndex !in elements.indices) continue
        val end = anchoredStartsByChapter[chapterIndex]
            .orEmpty()
            .firstOrNull { it > elementIndex }
            ?: elements.size
        if (end > elementIndex) {
            notes[key] = NoteDocument(
                elements = elements.subList(elementIndex, end).toList(),
                publisherBoxes = slicePublisherBoxSpans(
                    spans = chapter.publisherBoxes,
                    startElement = elementIndex,
                    endElementExclusive = end,
                ),
            )
        }
    }
    return notes
}
