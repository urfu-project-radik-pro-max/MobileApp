package com.example.mobilereader.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.File

object EpubParser {
    fun parseMetadata(bookFile: File): EpubMetadata = EpubArchive(bookFile).use { epubArchive ->
        val packagePath = findPackagePath(epubArchive)
        val packageDirectory = packagePath.substringBeforeLast('/', "")
        val packageDocument = parsePackage(epubArchive, packagePath)
        val metadataElement = packageDocument.selectFirst("metadata")
            ?: throw EpubReadException("EPUB metadata is missing")

        val allAuthors = mutableListOf<String>()
        val translators = mutableListOf<String>()
        val refinements = metadataElement.select("meta[refines][property]")
        fun refinedValue(identifier: String, propertyName: String): String? =
            refinements.firstOrNull { refinement ->
                refinement.attr("refines").removePrefix("#") == identifier &&
                    refinement.attr("property").substringAfterLast(':') == propertyName
            }?.text()?.trim()?.takeIf { it.isNotEmpty() }

        for (creatorElement in metadataElement.select("dc|creator, dc|contributor")) {
            val personName = creatorElement.text().trim().takeIf { it.isNotEmpty() } ?: continue
            val personRole = creatorElement.attr("opf:role")
                .ifEmpty { refinedValue(creatorElement.id(), "role").orEmpty() }
            when {
                personRole.equals("trl", ignoreCase = true) -> translators += personName
                creatorElement.normalName().substringAfterLast(':') == "creator" &&
                    (personRole.isEmpty() || personRole.equals("aut", ignoreCase = true)) ->
                    allAuthors += personName
            }
        }

        val manifestById = packageDocument.select("manifest > item[id][href]")
            .associateBy { it.id() }
        val coverItem = packageDocument.selectFirst("manifest > item[properties~=cover-image]")
            ?: metadataElement.selectFirst("meta[name=cover]")
                ?.attr("content")?.let(manifestById::get)
            ?: packageDocument.selectFirst("guide > reference[type=cover]")
        val coverLink = coverItem?.attr("href")
        val coverBytes = coverLink
            ?.let { epubArchive.resolveFrom(packageDirectory, it) }
            ?.let { epubArchive.readOptional(it, EpubReadingLimits().mostCoverBytes) }

        var seriesName = metadataElement.selectFirst("meta[name=calibre:series]")
            ?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
        var seriesNumber = metadataElement.selectFirst("meta[name=calibre:series_index]")
            ?.attr("content")?.toFloatOrNull()
        if (seriesName == null) {
            for (seriesElement in metadataElement.select("meta[property=belongs-to-collection]")) {
                val seriesType = refinedValue(seriesElement.id(), "collection-type")
                if (seriesType != null && !seriesType.equals("series", ignoreCase = true)) continue
                seriesName = seriesElement.text().trim().takeIf { it.isNotEmpty() }
                seriesNumber = refinedValue(seriesElement.id(), "group-position")?.toFloatOrNull()
                if (seriesName != null) break
            }
        }

        EpubMetadata(
            title = metadataElement.selectFirst("dc|title")?.text()?.trim()?.takeIf { it.isNotEmpty() },
            author = allAuthors.firstOrNull(),
            allAuthors = allAuthors,
            language = metadataElement.selectFirst("dc|language")?.text()?.trim(),
            description = metadataElement.selectFirst("dc|description")?.text()
                ?.let { Jsoup.parse(it).text().trim() }?.takeIf { it.isNotEmpty() },
            coverBytes = coverBytes,
            genres = metadataElement.select("dc|subject").map { it.text().trim() }
                .filter { it.isNotEmpty() },
            publisher = metadataElement.selectFirst("dc|publisher")?.text()?.trim(),
            publicationYear = metadataElement.selectFirst("dc|date")?.text()
                ?.let { Regex("""\d{4}""").find(it)?.value },
            isbn = findIsbn(metadataElement),
            series = seriesName,
            seriesNumber = seriesNumber,
            translators = translators,
        )
    }

    internal fun findPackagePath(epubArchive: EpubArchive): String {
        val containerBytes = epubArchive.readRequired(
            "META-INF/container.xml",
            EpubReadingLimits().mostPackageBytes,
        )
        val containerDocument = parseXml(containerBytes)
        val packagePath = containerDocument.selectFirst("rootfile[full-path]")
            ?.attr("full-path") ?: throw EpubReadException("EPUB package is missing")
        return epubArchive.resolveFrom("", packagePath)
            ?: throw EpubReadException("EPUB package path is unsafe")
    }

    internal fun parsePackage(epubArchive: EpubArchive, packagePath: String): Document =
        parseXml(epubArchive.readRequired(packagePath, EpubReadingLimits().mostPackageBytes))

    private fun parseXml(xmlBytes: ByteArray): Document =
        ByteArrayInputStream(xmlBytes).use { xmlStream ->
            Jsoup.parse(xmlStream, null, "", Parser.xmlParser())
        }

    private fun findIsbn(metadataElement: Element): String? {
        for (identifierElement in metadataElement.select("dc|identifier")) {
            val identifierText = identifierElement.text().trim()
            if (identifierElement.attr("opf:scheme").equals("ISBN", ignoreCase = true)) {
                return identifierText
            }
            if (identifierText.startsWith("urn:isbn:", ignoreCase = true)) {
                return identifierText.substringAfterLast(':')
            }
            val simpleNumber = identifierText.filterNot { it == ' ' || it == '-' }
            if ((simpleNumber.length == 10 || simpleNumber.length == 13) &&
                simpleNumber.dropLast(1).all(Char::isDigit) &&
                (simpleNumber.last().isDigit() || simpleNumber.last().equals('X', true))
            ) {
                return identifierText
            }
        }
        return null
    }
}
