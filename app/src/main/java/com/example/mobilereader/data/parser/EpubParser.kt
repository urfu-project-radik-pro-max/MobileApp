package com.example.mobilereader.data.parser

import com.example.mobilereader.data.model.BookContent
import com.example.mobilereader.data.model.BookFont
import com.example.mobilereader.data.model.BookMetadata
import com.example.mobilereader.data.model.BookNavigationEntry
import com.example.mobilereader.data.model.BookNavigationTarget
import com.example.mobilereader.data.model.Chapter
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.LinkedDocument
import com.example.mobilereader.data.model.LinkedDocumentTarget
import com.example.mobilereader.data.model.NoteDocument
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.parser.Tag
import java.io.DataInputStream
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile


object EpubParser {


    private const val FONT_PIPELINE_VERSION = 2
    private const val DTBOOK_MEDIA_TYPE = "application/x-dtbook+xml"

    private class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String,
        val fallback: String,
    )


    private class SpineItem(
        val item: ManifestItem,
        val linear: Boolean,
    )


    private class EpubCssBudget(private val limits: ReaderResourceLimits) {
        private var expandedBytes = 0L
        private var expandedSheets = 0
        private var expansionOperations = 0
        private val acceptedResolvers = mutableSetOf<String>()
        private val rejectedResolvers = mutableSetOf<String>()

        fun acceptResolver(signature: String, sheets: List<CssResolver.Sheet>): Boolean {
            if (signature in acceptedResolvers) return true
            if (signature in rejectedResolvers) return false
            var bytes = 0L
            for (sheet in sheets) {
                val next = sheet.text.length.toLong() * 2L
                if (bytes > limits.maxEpubCssExpandedBytes - next) {
                    rejectedResolvers += signature
                    return false
                }
                bytes += next
            }
            if (expandedBytes > limits.maxEpubCssExpandedBytes - bytes) {
                rejectedResolvers += signature
                return false
            }
            expandedBytes += bytes
            acceptedResolvers += signature
            return true
        }

        fun enterSheet(): Boolean {
            if (expandedSheets >= limits.maxEpubCssExpandedSheets ||
                expansionOperations >= limits.maxEpubCssExpansionOperations
            ) {
                return false
            }
            expandedSheets++
            expansionOperations++
            return true
        }

        fun traverseImport(): Boolean {
            if (expansionOperations >= limits.maxEpubCssExpansionOperations) return false
            expansionOperations++
            return true
        }
    }


    fun parseMetadata(file: File): BookMetadata =
        parseMetadata(file, ReaderResourceLimits.DEFAULT)

    internal fun parseMetadata(file: File, limits: ReaderResourceLimits): BookMetadata {
        ZipFile(file).use { epubArchive ->
            val budget = ArchiveResourceBudget(epubArchive, limits)
            val packagePath = findOpfPath(epubArchive, budget, limits)
                ?: throw IllegalArgumentException("Not a valid EPUB: missing OPF")
            val packageDirectory = packagePath.substringBeforeLast('/', "")
            val packageDocument = parseXml(epubArchive, packagePath, budget, limits)

            val title = packageDocument.selectFirst("metadata > dc|title")?.text()?.trim()


            val refinements = packageDocument.select("metadata > meta[refines][property]")
            fun refined(id: String, property: String): String? {
                if (id.isEmpty()) return null
                return refinements.firstOrNull {
                    it.attr("refines").removePrefix("#") == id &&
                            it.attr("property").substringAfterLast(':') == property
                }?.text()?.trim()?.takeIf { it.isNotEmpty() }
            }

            val authors = mutableListOf<String>()
            val translators = mutableListOf<String>()
            for (creator in packageDocument.select("metadata > dc|creator")) {
                val name = creator.text().trim().takeIf { it.isNotEmpty() } ?: continue
                val role =
                    creator.attr("opf:role").ifEmpty { refined(creator.id(), "role").orEmpty() }
                when {
                    role.isEmpty() || role.equals("aut", ignoreCase = true) -> authors += name
                    role.equals("trl", ignoreCase = true) -> translators += name
                    else -> Unit
                }
            }
            for (contributor in packageDocument.select("metadata > dc|contributor")) {
                val name = contributor.text().trim().takeIf { it.isNotEmpty() } ?: continue
                val role = contributor.attr("opf:role")
                    .ifEmpty { refined(contributor.id(), "role").orEmpty() }
                if (role.equals("trl", ignoreCase = true)) translators += name
            }

            var isbn: String? = null
            for (identifier in packageDocument.select("metadata > dc|identifier")) {
                val text = identifier.text().trim().takeIf { it.isNotEmpty() } ?: continue
                isbn = when {
                    identifier.attr("opf:scheme").equals("ISBN", ignoreCase = true) -> text
                    text.startsWith("urn:isbn:", ignoreCase = true) ->
                        text.substringAfterLast(':').trim()

                    looksLikeIsbn(text) -> text
                    else -> null
                } ?: continue
                break
            }

            var series: String? = null
            var seriesNumber: Float? = null
            packageDocument.selectFirst("metadata > meta[name=calibre:series]")
                ?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { name ->
                    series = name
                    seriesNumber =
                        packageDocument.selectFirst("metadata > meta[name=calibre:series_index]")
                            ?.attr("content")?.trim()?.toFloatOrNull()
                }
            if (series == null) {
                for (meta in packageDocument.select("metadata > meta[property=belongs-to-collection]")) {
                    val type = refined(meta.id(), "collection-type")
                    if (type != null && !type.equals("series", ignoreCase = true)) continue
                    val name = meta.text().trim().takeIf { it.isNotEmpty() } ?: continue
                    series = name
                    seriesNumber = refined(meta.id(), "group-position")?.toFloatOrNull()
                    break
                }
            }

            val items = manifestItems(packageDocument)
            val coverHref = findCoverHref(packageDocument, items)
            val coverBytes = coverHref
                ?.let { zipEntry(epubArchive, resolvePath(packageDirectory, it)) }
                ?.let { budget.readOptional(it, limits.maxCoverBytes, "EPUB cover") }

            return BookMetadata(
                title = title,
                author = authors.firstOrNull(),
                coverBytes = coverBytes,
                authors = authors,
                genres = packageDocument.select("metadata > dc|subject")
                    .map { it.text().trim() }.filter { it.isNotEmpty() },
                series = series,
                seriesNumber = seriesNumber,
                publisher = packageDocument.selectFirst("metadata > dc|publisher")?.text()
                    ?.trim()?.takeIf { it.isNotEmpty() },
                year = packageDocument.selectFirst("metadata > dc|date")?.text()
                    ?.let { Regex("""\d{4}""").find(it)?.value },
                isbn = isbn,
                translators = translators,

                description = packageDocument.selectFirst("metadata > dc|description")?.text()
                    ?.let { Jsoup.parse(it).text().trim() }?.takeIf { it.isNotEmpty() },
                language = packageDocument.selectFirst("metadata > dc|language")?.text()
                    ?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }


    private fun looksLikeIsbn(text: String): Boolean {
        if (!text.all { it.isDigit() || it == '-' || it == ' ' || it == 'X' || it == 'x' }) {
            return false
        }
        val digits = text.count { it.isDigit() || it == 'X' || it == 'x' }
        return digits == 10 || digits == 13
    }


    fun parseContent(file: File, imagesDir: File): BookContent =
        parseContent(file, imagesDir, ReaderResourceLimits.DEFAULT)

    internal fun parseContent(
        file: File,
        imagesDir: File,
        limits: ReaderResourceLimits,
    ): BookContent {
        ZipFile(file).use { epubArchive ->
            val budget = ArchiveResourceBudget(epubArchive, limits)
            val packagePath = findOpfPath(epubArchive, budget, limits)
                ?: throw IllegalArgumentException("Not a valid EPUB: missing OPF")
            val packageDirectory = packagePath.substringBeforeLast('/', "")
            val packageDocument = parseXml(epubArchive, packagePath, budget, limits)

            val items = manifestItems(packageDocument)
            val toc =
                chapterTitles(epubArchive, packageDocument, items, packageDirectory, budget, limits)
            val titlesByPath = toc.byPath
            val pageProgression = EpubPageProgressionParser.parsePageProgression(packageDocument)
            val metadataLanguage = LanguageTag.normalize(
                packageDocument.selectFirst("metadata > dc|language")?.text(),
            )
            val fb2EpubConversion = packageDocument.select("metadata > meta[name]").any {
                it.attr("name").equals("FB2EPUB.version", ignoreCase = true)
            }

            val chapters = mutableListOf<Chapter>()
            val linkedDocuments = linkedMapOf<String, LinkedDocument>()
            val extractedImages = mutableMapOf<String, String>()
            val inlineSvgs = mutableMapOf<String, String>()

            val anchorLocations = mutableMapOf<String, Pair<Int, Int>>()

            val fileLocations = mutableMapOf<String, Pair<Int, Int>>()

            val linkedLocations = mutableMapOf<String, LinkedDocumentTarget>()
            val linkTargets = mutableSetOf<String>()
            val noteTargets = mutableSetOf<String>()
            val exactNoteDocuments = mutableMapOf<String, NoteDocument>()
            val cssCache = mutableMapOf<String, String?>()
            val resolverCache = mutableMapOf<String, CssResolver>()
            val cssBudget = EpubCssBudget(limits)
            val htmlExpansionBudget = HtmlExpansionBudget(
                maxGeneratedRunChars = limits.maxHtmlGeneratedRunChars,
                maxGeneratedTotalChars = limits.maxHtmlGeneratedTotalChars,
            )
            val fonts = mutableMapOf<String, BookFont>()


            val encryption = parseEncryption(epubArchive, budget, limits)

            val spine = spineItems(packageDocument, items)


            val linearPaths = spine.asSequence()
                .filter { it.linear }
                .map { resolvePath(packageDirectory, it.item.href) }
                .toSet()
            val processedLinkedPaths = mutableSetOf<String>()
            for (spineItem in spine) {
                val item = spineItem.item
                val chapterPath = resolvePath(packageDirectory, item.href)


                if (!spineItem.linear && chapterPath in linearPaths) continue
                val linear = spineItem.linear
                if (!linear && !processedLinkedPaths.add(chapterPath)) continue
                val entry = zipEntry(epubArchive, chapterPath)
                if (entry == null) {
                    if (linear) throw IllegalArgumentException(
                        "EPUB required chapter '$chapterPath' is missing from the archive",
                    )
                    continue
                }

                if (item.mediaType.equals("image/svg+xml", ignoreCase = true)) {
                    val imagePath = extractImage(
                        epubArchive,
                        chapterPath,
                        imagesDir,
                        extractedImages,
                        budget,
                        limits,
                        required = linear,
                    ) ?: if (linear) throw IllegalArgumentException(
                        "EPUB required image page '$chapterPath' cannot be read",
                    ) else continue
                    val svgDocument = parseOptionalXml(
                        epubArchive,
                        chapterPath,
                        budget,
                        limits.maxImageBytes,
                        "EPUB SVG description",
                    )
                    val svgRoot = svgDocument?.getAllElements()
                        ?.firstOrNull { it.localName() == "svg" }
                    val svgAlt = svgRoot
                        ?.let { svg ->
                            svg.attr("aria-label").ifBlank {
                                svg.children().firstOrNull { it.normalName() == "title" }
                                    ?.text().orEmpty()
                            }.trim().takeIf(String::isNotEmpty)
                        }
                    val tocEntry = titlesByPath[chapterPath]?.firstOrNull()
                    val image = ContentElement.Image(path = imagePath, altText = svgAlt)
                    if (linear) {
                        val chapterIndex = chapters.size
                        chapters += Chapter(
                            title = tocEntry?.title,
                            elements = listOf(image),
                            depth = tocEntry?.depth ?: 0,
                        )
                        fileLocations.putIfAbsent(chapterPath, chapterIndex to 0)
                    } else {
                        linkedDocuments[chapterPath] = LinkedDocument(
                            id = chapterPath,
                            title = tocEntry?.title,
                            elements = listOf(image),
                        )
                        linkedLocations.putIfAbsent(
                            chapterPath,
                            LinkedDocumentTarget(chapterPath, 0),
                        )
                    }
                    continue
                }
                val dtbook = item.mediaType.equals(DTBOOK_MEDIA_TYPE, ignoreCase = true)
                if (!item.mediaType.contains("html", ignoreCase = true) && !dtbook) {
                    if (linear) throw IllegalArgumentException(
                        "EPUB required chapter '$chapterPath' has unsupported media type '${item.mediaType}'",
                    )
                    continue
                }

                val chapterDir = chapterPath.substringBeforeLast('/', "")

                val doc = parseChapterDocument(
                    epubArchive,
                    entry,
                    budget,
                    limits,
                    required = linear,
                ) ?: if (linear) throw IllegalArgumentException(
                    "EPUB required chapter '$chapterPath' cannot be parsed",
                ) else continue
                val resolver = cssResolverFor(
                    doc,
                    epubArchive,
                    chapterDir,
                    cssCache,
                    resolverCache,
                    budget,
                    limits,
                    cssBudget,
                )
                val originalBody = if (dtbook) {
                    doc.selectFirst("book")
                } else {
                    doc.selectFirst("body")
                }
                val body = originalBody ?: recoverReadableBody(doc, resolver)
                if (body == null) {
                    if (linear) throw IllegalArgumentException(
                        "EPUB required chapter '$chapterPath' has no readable body or book",
                    )
                    continue
                }
                if (fb2EpubConversion) {
                    promoteFb2EpubNoteLinks(body, chapterPath, chapterDir)
                }
                if (resolver != null) {
                    extractFonts(
                        resolver,
                        epubArchive,
                        imagesDir,
                        fonts,
                        encryption,
                        budget,
                        limits,
                    )
                }
                fun newMapper() = HtmlMapper(
                    resolveImage = { src ->
                        extractImage(
                            epubArchive,
                            resolvePath(chapterDir, src),
                            imagesDir,
                            extractedImages,
                            budget,
                            limits,
                        )
                    },
                    resolveLink = { href -> resolveLinkKey(href, chapterPath, chapterDir) },
                    css = resolver,
                    resolveInlineSvg = { markup -> writeInlineSvg(markup, imagesDir, inlineSvgs) },
                    expansionBudget = htmlExpansionBudget,
                )
                var mapper = newMapper()
                var elements = mapper.map(body)
                if (elements.isEmpty() && originalBody != null) {
                    val recoveredBody = recoverReadableBody(originalBody, resolver)
                    if (recoveredBody != null) {
                        mapper = newMapper()
                        elements = mapper.map(recoveredBody)
                    }
                }
                mapper.noteDocuments.forEach { (id, note) ->
                    exactNoteDocuments.putIfAbsent("$chapterPath#$id", note)
                }


                resolver?.clearCache()
                if (elements.isEmpty()) {
                    if (linear) throw IllegalArgumentException(
                        "EPUB required chapter '$chapterPath' has no readable content",
                    )
                    continue
                }
                linkTargets += mapper.linkTargets
                noteTargets += mapper.noteTargets

                if (!linear) {
                    val tocEntry = titlesByPath[chapterPath]?.firstOrNull()
                    val title = tocEntry?.title
                        ?: elements.firstOrNull { it is ContentElement.Heading }
                            ?.let { (it as ContentElement.Heading).text }
                    linkedDocuments[chapterPath] = LinkedDocument(
                        id = chapterPath,
                        title = title,
                        elements = elements,
                    )
                    linkedLocations.putIfAbsent(
                        chapterPath,
                        LinkedDocumentTarget(chapterPath, 0),
                    )
                    mapper.anchors.forEach { (id, index) ->
                        linkedLocations.putIfAbsent(
                            "$chapterPath#$id",
                            LinkedDocumentTarget(
                                documentId = chapterPath,
                                elementIndex = index.coerceIn(0, elements.lastIndex),
                            ),
                        )
                    }
                    continue
                }


                data class AddedSegment(
                    val localStart: Int,
                    val localEnd: Int,
                    val chapterIndex: Int,
                    val elementBase: Int,
                )

                val tocEntries = titlesByPath[chapterPath].orEmpty()
                val points = linkedMapOf<Int, TocEntry>()
                for (tocEntry in tocEntries) {
                    val index = tocEntry.fragment
                        ?.let(mapper.anchors::get)
                        ?: if (tocEntry.fragment == null) 0 else null
                    if (index != null && index in elements.indices) {
                        points.putIfAbsent(index, tocEntry)
                    }
                }
                val orderedPoints = points.entries.sortedBy { it.key }
                val boundaries = buildList {
                    if (orderedPoints.firstOrNull()?.key != 0) add(0 to null)
                    orderedPoints.forEach { add(it.key to it.value) }
                    if (isEmpty()) add(0 to tocEntries.firstOrNull())
                }

                val added = mutableListOf<AddedSegment>()
                for ((position, boundary) in boundaries.withIndex()) {
                    val start = boundary.first
                    val end = boundaries.getOrNull(position + 1)?.first ?: elements.size
                    if (start >= end) continue
                    val tocEntry = boundary.second
                    val merge =
                        tocEntry == null && titlesByPath.isNotEmpty() && chapters.isNotEmpty()
                    val chapterIndex: Int
                    val elementBase: Int
                    if (merge) {
                        val previous = chapters.removeAt(chapters.lastIndex)
                        elementBase = previous.elements.size
                        chapters += Chapter(
                            title = previous.title,
                            elements = previous.elements + elements.subList(start, end),
                            depth = previous.depth,
                        )
                        chapterIndex = chapters.lastIndex
                    } else {
                        val segment = elements.subList(start, end)
                        val title = tocEntry?.title
                            ?: segment.firstOrNull { it is ContentElement.Heading }
                                ?.let { (it as ContentElement.Heading).text }
                        chapterIndex = chapters.size
                        elementBase = 0
                        chapters += Chapter(
                            title = title,
                            elements = segment,
                            depth = tocEntry?.depth ?: 0,
                        )
                    }
                    added += AddedSegment(start, end, chapterIndex, elementBase)
                }

                mapper.anchors.forEach { (id, index) ->
                    val segment = added.lastOrNull { index >= it.localStart } ?: return@forEach
                    val localIndex = (index - segment.localStart)
                        .coerceIn(0, segment.localEnd - segment.localStart - 1)
                    anchorLocations.putIfAbsent(
                        "$chapterPath#$id",
                        segment.chapterIndex to (segment.elementBase + localIndex),
                    )
                }


                added.firstOrNull()?.let { first ->
                    fileLocations.putIfAbsent(
                        chapterPath,
                        first.chapterIndex to first.elementBase,
                    )
                }
            }


            val navTargets = fileLocations + anchorLocations

            val notes = buildNotes(
                chapters = chapters,
                anchorLocations = anchorLocations,
                linkTargets = noteTargets,
                exactDocuments = exactNoteDocuments,
            ).toMutableMap()


            for ((documentId, document) in linkedDocuments) {
                val localAnchors = linkedLocations.mapNotNull { (key, target) ->
                    if (target.documentId == documentId) key to (0 to target.elementIndex) else null
                }.toMap()
                notes += buildNotes(
                    chapters = listOf(
                        Chapter(
                            title = document.title,
                            elements = document.elements,
                        ),
                    ),
                    anchorLocations = localAnchors,
                    linkTargets = noteTargets,
                    exactDocuments = exactNoteDocuments,
                )
            }

            val navigation = toc.entries.mapNotNull { entry ->
                val key = entry.targetKey
                val main = navTargets[key]
                val linked = linkedLocations[key]
                val target = when {
                    main != null -> BookNavigationTarget.ReadingOrder(
                        chapterIndex = main.first,
                        elementIndex = main.second,
                    )

                    linked != null -> BookNavigationTarget.Linked(
                        documentId = linked.documentId,
                        elementIndex = linked.elementIndex,
                    )

                    else -> null
                } ?: return@mapNotNull null
                BookNavigationEntry(entry.title, entry.depth, target)
            }

            return BookContent(
                chapters = chapters,
                notes = notes,
                linkTargets = navTargets.filterKeys { it in linkTargets },
                fonts = fonts.values.toList(),
                language = metadataLanguage ?: LanguageTag.detectFromChapters(chapters),
                linkedDocuments = linkedDocuments,
                linkedDocumentTargets = linkedLocations.filterKeys { it in linkTargets },
                navigation = navigation,
                pageProgression = pageProgression,
            )
        }
    }


    private fun resolveLinkKey(href: String, chapterPath: String, chapterDir: String): String? {
        val lower = href.lowercase()
        if (lower.startsWith("http:") || lower.startsWith("https:") ||
            lower.startsWith("mailto:") || lower.startsWith("tel:")
        ) {
            return null
        }
        val path = href.substringBefore('#')
        val rawFragment = if ('#' in href) href.substringAfter('#') else ""
        val fragment = decodeUrlPath(rawFragment) ?: rawFragment
        if (fragment.isEmpty()) {

            if (path.isEmpty()) return null
            val extension = path.substringAfterLast('.').lowercase()
            if (!extension.startsWith("htm") && !extension.startsWith("xhtm") &&
                extension != "svg"
            ) {
                return null
            }
            return resolvePath(chapterDir, path)
        }
        val target = if (path.isEmpty()) chapterPath else resolvePath(chapterDir, path)
        return "$target#$fragment"
    }


    private fun promoteFb2EpubNoteLinks(
        body: Element,
        chapterPath: String,
        chapterDir: String,
    ) {
        for (link in body.select("a[href]")) {
            if (!FB2_EPUB_NOTE_MARKER.matches(link.text().trim())) continue
            val key = resolveLinkKey(link.attr("href"), chapterPath, chapterDir) ?: continue
            val fragment = key.substringAfter('#', "")
            if (fragment.isEmpty()) continue
            val basename = key.substringBefore('#').substringAfterLast('/')
            if (!FB2_EPUB_NOTE_FILE.matches(basename)) continue
            link.attr("type", "note")
        }
    }


    private fun parseChapterDocument(
        epubArchive: ZipFile,
        entry: ZipEntry,
        budget: ArchiveResourceBudget,
        limits: ReaderResourceLimits,
        required: Boolean,
    ): Document? {
        val bytes = if (required) {
            budget.readRequired(
                entry,
                limits.maxChapterBytes,
                "EPUB spine document ${entry.name}",
            )
        } else {
            budget.readOptional(
                entry,
                limits.maxChapterBytes,
                "EPUB linked document ${entry.name}",
            ) ?: return null
        }
        return parseChapterDocument(bytes)
    }

    private fun recoverReadableBody(source: Element, resolver: CssResolver?): Element? {
        val recoveredBody = Element(Tag.valueOf("body"), "")
        var examinedNodes = 0
        var recoveredBlocks = 0
        val excludedTags = setOf(
            "head", "title", "meta", "metadata", "link", "script", "style", "template",
            "noscript", "nav", "toc", "ncx", "header", "footer", "form", "button",
            "input", "select", "textarea", "aside",
        )
        val readableTags = setOf(
            "p", "pre", "li", "dt", "dd", "ul", "ol", "list", "h1", "h2", "h3",
            "h4", "h5", "h6", "levelhd", "hd", "bridgehead", "doctitle", "covertitle",
            "math", "img", "image", "svg", "a", "table", "line",
        )

        fun excluded(element: Element): Boolean =
            element.normalName() in excludedTags || element.hasAttr("hidden") ||
                resolver?.computed(element)?.hidden == true

        fun visit(element: Element, depth: Int) {
            if (depth >= HtmlMapper.DEFAULT_HTML_STRUCTURE_DEPTH) {
                throw ResourceLimitException(
                    ResourceLimitKind.STRUCTURAL_DEPTH,
                    "EPUB chapter recovery exceeds the HTML structure depth limit",
                )
            }
            examinedNodes++
            if (examinedNodes > 50_000) {
                throw IllegalArgumentException("EPUB chapter recovery has too many document nodes")
            }
            if (excluded(element)) return
            if (element.normalName() in readableTags) {
                val sourceElements = element.getAllElements().toList()
                if (examinedNodes + sourceElements.size > 50_000) {
                    throw IllegalArgumentException("EPUB chapter recovery has too many document nodes")
                }
                sourceElements.forEach { descendant ->
                    if (descendant.parents().size + depth - element.parents().size >=
                        HtmlMapper.DEFAULT_HTML_STRUCTURE_DEPTH
                    ) {
                        throw ResourceLimitException(
                            ResourceLimitKind.STRUCTURAL_DEPTH,
                            "EPUB chapter recovery exceeds the HTML structure depth limit",
                        )
                    }
                }
                examinedNodes += sourceElements.size - 1
                val copy = element.clone()
                val copiedElements = copy.getAllElements().toList()
                for (index in sourceElements.indices.reversed()) {
                    val original = sourceElements[index]
                    if (index > 0 && excluded(original)) copiedElements[index].remove()
                }
                recoveredBody.appendChild(copy)
                recoveredBlocks++
                return
            }
            element.children().forEach { child -> visit(child, depth + 1) }
        }

        visit(source, 0)
        return recoveredBody.takeIf { recoveredBlocks > 0 }
    }


    private fun spineItems(packageDocument: Document, items: List<ManifestItem>): List<SpineItem> {
        val itemsById = items.associateBy { it.id }

        fun renderable(item: ManifestItem): ManifestItem? {
            var candidate: ManifestItem? = item
            val seen = mutableSetOf<String>()
            while (candidate != null && seen.add(candidate.id)) {
                if (candidate.mediaType.contains("html", ignoreCase = true) ||
                    candidate.mediaType.equals("image/svg+xml", ignoreCase = true) ||
                    candidate.mediaType.equals(DTBOOK_MEDIA_TYPE, ignoreCase = true)
                ) {
                    return candidate
                }
                candidate = candidate.fallback.takeIf { it.isNotEmpty() }?.let(itemsById::get)
            }
            return null
        }

        val refs = packageDocument.select("spine > itemref")
        if (refs.isNotEmpty()) {
            val resolved = refs.mapNotNull { ref ->
                val idref = ref.attr("idref")
                val linear = !ref.attr("linear").equals("no", ignoreCase = true)
                val item = itemsById[idref]
                val rendered = item?.let(::renderable)
                if (rendered == null) {
                    if (linear) throw IllegalArgumentException(
                        "EPUB required spine item '$idref' has no readable manifest resource",
                    )
                    null
                } else {
                    SpineItem(item = rendered, linear = linear)
                }
            }
            return resolved
        }
        return items.mapNotNull(::renderable)
            .distinctBy { it.id }
            .map { item -> SpineItem(item = item, linear = true) }
    }


    private fun cssResolverFor(
        doc: Document,
        epubArchive: ZipFile,
        chapterDir: String,
        cssCache: MutableMap<String, String?>,
        resolverCache: MutableMap<String, CssResolver>,
        budget: ArchiveResourceBudget,
        limits: ReaderResourceLimits,
        cssBudget: EpubCssBudget,
    ): CssResolver? {
        val sheets = mutableListOf<CssResolver.Sheet>()

        data class Source(
            val key: String,
            val text: String,
            val baseDir: String,
            val imports: List<String>,
        )

        data class Frame(
            val source: Source,
            var nextImport: Int = 0,
        )

        val sourceCache = mutableMapOf<String, Source>()
        val missingSources = mutableSetOf<String>()

        fun importPaths(text: String, baseDir: String): List<String> {
            return topLevelCssImports(text).mapNotNull { (href, media) ->
                if (!screenMediaApplies(media)) return@mapNotNull null
                if (href.startsWith("data:", ignoreCase = true) ||
                    href.startsWith("http:", ignoreCase = true) ||
                    href.startsWith("https:", ignoreCase = true)
                ) {
                    return@mapNotNull null
                }
                resolvePath(baseDir, href)
            }.toList()
        }

        fun externalSource(path: String): Source? {
            sourceCache[path]?.let { return it }
            if (path in missingSources) return null
            val text = if (cssCache.containsKey(path)) {
                cssCache[path]
            } else {
                zipEntry(epubArchive, path)?.let { entry ->
                    budget.readOptional(
                        entry,
                        limits.maxStylesheetBytes,
                        "EPUB stylesheet $path",
                    )?.decodeToString()
                }.also { cssCache[path] = it }
            }
            if (text == null) {
                missingSources += path
                return null
            }
            return Source(
                key = path,
                text = text,
                baseDir = path.substringBeforeLast('/', ""),
                imports = importPaths(text, path.substringBeforeLast('/', "")),
            ).also { sourceCache[path] = it }
        }


        fun appendRoot(root: Source) {
            val activePath = mutableSetOf<String>()
            val stack = java.util.ArrayDeque<Frame>()

            fun push(source: Source) {
                if (source.key in activePath || !cssBudget.enterSheet()) return
                activePath += source.key
                stack.addLast(Frame(source))
            }

            push(root)
            while (stack.isNotEmpty()) {
                val frame = stack.peekLast() ?: break
                if (frame.nextImport < frame.source.imports.size &&
                    cssBudget.traverseImport()
                ) {
                    val path = frame.source.imports[frame.nextImport++]
                    externalSource(path)?.let(::push)
                    continue
                }

                frame.nextImport = frame.source.imports.size
                stack.removeLast()
                activePath -= frame.source.key
                sheets += CssResolver.Sheet(frame.source.text, frame.source.baseDir)
            }
        }

        val roots = mutableListOf<Source>()
        var inlineIndex = 0
        for (node in doc.select("link[href], style")) {
            if (!screenMediaApplies(node.attr("media"))) continue
            if (node.normalName() == "link") {
                val rel = node.attr("rel").split(Regex("""\s+"""))
                val type = node.attr("type")
                if (rel.none { it.equals("stylesheet", true) } &&
                    !type.contains("css", true)
                ) {
                    continue
                }
                val path = resolvePath(chapterDir, node.attr("href"))
                externalSource(path)?.let(roots::add)
            } else {
                val index = inlineIndex++
                val text = node.data().ifEmpty { node.text() }
                if (text.isNotBlank() && text.length.toLong() <= limits.maxStylesheetBytes) {
                    roots +=
                        Source(
                            key = "inline:$chapterDir:$index:${resourceDigest(text)}",
                            text = text,
                            baseDir = chapterDir,
                            imports = importPaths(text, chapterDir),
                        )
                }
            }
        }


        if (roots.isEmpty()) return CssResolver(emptyList())
        val rootSignature = roots.joinToString(separator = "") { "${it.key.length}:${it.key}" }
        val cacheKey = resourceDigest(rootSignature)
        resolverCache[cacheKey]?.let { return it }
        roots.forEach(::appendRoot)
        if (sheets.isEmpty()) return null
        if (!cssBudget.acceptResolver(cacheKey, sheets)) return null
        return CssResolver(sheets).also { resolverCache[cacheKey] = it }
    }


    private fun screenMediaApplies(media: String): Boolean {
        return media.split(',').any { branch ->
            var query = branch.trim().lowercase()
            if (query.isEmpty()) return@any true
            var negated = false
            if (query.startsWith("only ")) query = query.removePrefix("only ").trimStart()
            if (query.startsWith("not ")) {
                negated = true
                query = query.removePrefix("not ").trimStart()
            }
            val type = query.split(Regex("""\s+and\b"""), limit = 2).first().trim()
                .takeUnless { it.startsWith("(") }
                ?.substringBefore(' ')
                .orEmpty()
            val applies = when (type) {
                "", "all", "screen" -> true
                else -> false
            }
            if (negated) !applies else applies
        }
    }


    private fun topLevelCssImports(css: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var cursor = 0
        while (cursor < css.length) {
            while (cursor < css.length &&
                (css[cursor].isWhitespace() || css[cursor] == '\uFEFF')
            ) {
                cursor++
            }
            if (cursor + 1 < css.length && css[cursor] == '/' && css[cursor + 1] == '*') {
                val end = css.indexOf("*/", cursor + 2)
                if (end < 0) break
                cursor = end + 2
                continue
            }
            if (css.regionMatches(cursor, "@charset", 0, 8, ignoreCase = true) ||
                css.regionMatches(cursor, "@layer", 0, 6, ignoreCase = true)
            ) {
                val semicolon = css.indexOf(';', cursor)
                val brace = css.indexOf('{', cursor)
                if (semicolon < 0 || brace in cursor until semicolon) break
                cursor = semicolon + 1
                continue
            }
            if (!css.regionMatches(cursor, "@import", 0, 7, ignoreCase = true)) break
            val match = CSS_IMPORT_REGEX.find(css, cursor)
                ?.takeIf { it.range.first == cursor }
                ?: break
            val href = match.groups[1]?.value?.takeIf { it.isNotBlank() }
                ?: match.groups[2]?.value?.takeIf { it.isNotBlank() }
                ?: break
            result += href to match.groups[3]?.value.orEmpty().trim()
            cursor = match.range.last + 1
        }
        return result
    }


    private fun parseEncryption(
        epubArchive: ZipFile,
        budget: ArchiveResourceBudget,
        limits: ReaderResourceLimits,
    ): Set<String> {
        val entry = zipEntry(epubArchive, "META-INF/encryption.xml") ?: return emptySet()
        val bytes = budget.readOptional(
            entry,
            limits.maxPackageXmlBytes,
            "EPUB encryption metadata",
        ) ?: return emptySet()
        val doc = runCatching {
            Jsoup.parse(bytes.inputStream(), "UTF-8", "", Parser.xmlParser())
        }.getOrNull() ?: return emptySet()

        fun Element.localName() = tagName().substringAfter(':')

        val encryptedPaths = mutableSetOf<String>()
        for (data in doc.getAllElements()) {
            if (!data.localName().equals("EncryptedData", ignoreCase = true)) continue
            val uri = data.getAllElements()
                .firstOrNull { it.localName().equals("CipherReference", ignoreCase = true) }
                ?.attr("URI")
            if (uri.isNullOrEmpty()) continue

            encryptedPaths += resolvePath("", uri)
        }
        return encryptedPaths
    }


    private fun fontFileName(entryPath: String): String =
        resourceCacheFileName(
            prefix = "font_v$FONT_PIPELINE_VERSION",
            canonical = entryPath,
            displayName = entryPath,
        )

    private fun cachedFontHasValidSignature(file: File, maxFontBytes: Long): Boolean {
        if (file.length() !in 4L..maxFontBytes) return false
        return runCatching {
            DataInputStream(file.inputStream()).use { stream ->
                val signature = ByteArray(4)
                stream.readFully(signature)
                looksLikeFont(signature)
            }
        }.getOrDefault(false)
    }

    private fun extractFonts(
        resolver: CssResolver,
        epubArchive: ZipFile,
        imagesDir: File,
        out: MutableMap<String, BookFont>,
        encryption: Set<String>,
        budget: ArchiveResourceBudget,
        limits: ReaderResourceLimits,
    ) {
        for (face in resolver.fontFaces) {
            val entryPath = resolvePath(face.baseDir, face.src)
            if (entryPath in encryption) continue
            val sourcePath = face.src.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
            if (sourcePath.endsWith(".woff") || sourcePath.endsWith(".woff2")) continue
            val family = face.family.trim().lowercase()
            val key =
                "${entryPath.length}:$entryPath${family.length}:$family:${face.bold}:${face.italic}"
            if (key in out) continue
            val target = File(imagesDir, fontFileName(entryPath))


            if (cachedFontHasValidSignature(target, limits.maxFontBytes)) {
                out[key] = BookFont(
                    family = family,
                    path = target.absolutePath,
                    bold = face.bold,
                    italic = face.italic,
                )
                continue
            }
            if (target.exists() && !target.delete()) continue

            val entry = zipEntry(epubArchive, entryPath) ?: continue
            val bytes = budget.readOptional(
                entry,
                limits.maxFontBytes,
                "EPUB font $entryPath",
            ) ?: continue

            if (bytes.size.toLong() > limits.maxFontBytes) continue
            if (!looksLikeFont(bytes)) continue
            imagesDir.mkdirs()


            val partial = File(target.parentFile, target.name + ".tmp")
            partial.writeBytes(bytes)
            if (!partial.renameTo(target)) {
                partial.delete()
                continue
            }
            out[key] = BookFont(
                family = family,
                path = target.absolutePath,
                bold = face.bold,
                italic = face.italic,
            )
        }
    }

    private fun findOpfPath(
        epubArchive: ZipFile,
        budget: ArchiveResourceBudget,
        limits: ReaderResourceLimits,
    ): String? {
        val container = zipEntry(epubArchive, "META-INF/container.xml")
        if (container != null) {
            val bytes = budget.readRequired(
                container,
                limits.maxPackageXmlBytes,
                "EPUB container metadata",
            )
            val doc = Jsoup.parse(bytes.inputStream(), "UTF-8", "", Parser.xmlParser())
            doc.selectFirst("rootfile")?.attr("full-path")
                ?.takeIf { it.isNotEmpty() && isSafeArchivePath(it) }
                ?.let { return it }
        }

        return epubArchive.entries().asSequence()
            .firstOrNull {
                !it.isDirectory && isSafeArchivePath(it.name) &&
                        it.name.endsWith(".opf", ignoreCase = true)
            }
            ?.name
    }

    private fun parseXml(
        epubArchive: ZipFile,
        path: String,
        budget: ArchiveResourceBudget,
        limits: ReaderResourceLimits,
    ): Document {
        val entry =
            zipEntry(epubArchive, path) ?: throw IllegalArgumentException("Missing $path in EPUB")
        val bytes = budget.readRequired(entry, limits.maxPackageXmlBytes, "EPUB package $path")
        return Jsoup.parse(bytes.inputStream(), "UTF-8", "", Parser.xmlParser())
    }

    private fun parseOptionalXml(
        epubArchive: ZipFile,
        path: String,
        budget: ArchiveResourceBudget,
        maxBytes: Long,
        label: String,
    ): Document? {
        val entry = zipEntry(epubArchive, path) ?: return null
        val bytes = budget.readOptional(entry, maxBytes, label) ?: return null
        return runCatching {
            Jsoup.parse(bytes.inputStream(), "UTF-8", "", Parser.xmlParser())
        }.getOrNull()
    }

    private fun manifestItems(packageDocument: Document): List<ManifestItem> =
        packageDocument.select("manifest > item").map {
            ManifestItem(
                id = it.attr("id"),
                href = it.attr("href"),
                mediaType = it.attr("media-type"),
                properties = it.attr("properties"),
                fallback = it.attr("fallback"),
            )
        }

    private fun findCoverHref(packageDocument: Document, items: List<ManifestItem>): String? {

        items.firstOrNull { it.hasProperty("cover-image") }?.let { return it.href }

        val coverId = packageDocument.selectFirst("metadata > meta[name=cover]")?.attr("content")
        if (!coverId.isNullOrEmpty()) {
            items.firstOrNull { it.id == coverId }?.let { return it.href }
        }

        return items.firstOrNull {
            it.mediaType.startsWith("image/") && it.id.contains("cover", ignoreCase = true)
        }?.href
    }


    private class TocEntry(
        val title: String,
        val depth: Int,
        val path: String,
        val fragment: String?,
    ) {
        val targetKey: String get() = fragment?.let { "$path#$it" } ?: path
    }

    private class TocIndex(
        val byPath: Map<String, List<TocEntry>>,
        val entries: List<TocEntry>,
    )


    private fun chapterTitles(
        epubArchive: ZipFile,
        packageDocument: Document,
        items: List<ManifestItem>,
        packageDirectory: String,
        budget: ArchiveResourceBudget,
        limits: ReaderResourceLimits,
    ): TocIndex {
        val titles = linkedMapOf<String, MutableList<TocEntry>>()
        val ordered = mutableListOf<TocEntry>()

        fun addEntry(target: String, text: String, depth: Int, fragment: String?) {
            val entries = titles.getOrPut(target) { mutableListOf() }


            TocEntry(text, depth, target, fragment).let { entry ->
                entries += entry
                ordered += entry
            }
        }

        items.firstOrNull { it.hasProperty("nav") }?.let { navItem ->
            val navPath = resolvePath(packageDirectory, navItem.href)
            val navDir = navPath.substringBeforeLast('/', "")
            zipEntry(epubArchive, navPath)?.let { entry ->
                val bytes = budget.readOptional(
                    entry,
                    limits.maxPackageXmlBytes,
                    "EPUB navigation document",
                ) ?: return@let
                val doc = runCatching {
                    Jsoup.parse(bytes.inputStream(), null, "", Parser.xmlParser())
                }.getOrNull()?.takeIf { it.selectFirst("nav") != null }
                    ?: Jsoup.parse(bytes.inputStream(), null, "")
                val toc = doc.getElementsByAttribute("epub:type").firstOrNull { element ->
                    element.attr("epub:type").split(Regex("""\s+""")).any { it == "toc" }
                }
                    ?: doc.selectFirst("nav")
                toc?.select("a[href]")?.forEach { a ->
                    val href = a.attr("href")
                    val target = resolvePath(navDir, href.substringBefore('#'))
                    val rawFragment = href.substringAfter('#', "")
                    val fragment = rawFragment
                        .takeIf { '#' in href && it.isNotEmpty() }
                        ?.let { decodeUrlPath(it) ?: it }
                    var text = a.text().trim()
                    if (text.isEmpty() || target.isEmpty()) return@forEach


                    var depth = 0
                    var unlinkedLabel: String? = null
                    a.parents()
                        .filter { it.tagName() == "li" }
                        .drop(1)
                        .forEach { li ->
                            val link = li.children().firstOrNull { it.tagName() == "a" }
                            if (link != null) {
                                depth++
                            } else if (unlinkedLabel == null) {
                                unlinkedLabel = li.children()
                                    .firstOrNull { it.tagName() == "span" }
                                    ?.text()?.trim()?.takeIf { it.isNotEmpty() }
                            }
                        }
                    unlinkedLabel?.let { text = composeNestedChapterTitle(it, text) }
                    addEntry(target, text, depth, fragment)
                }
            }
        }

        if (ordered.isEmpty()) {
            val declaredNcx = packageDocument.selectFirst("spine")?.attr("toc")
                ?.takeIf { it.isNotEmpty() }
                ?.let { id -> items.firstOrNull { it.id == id } }
            val ncxItem = declaredNcx
                ?.takeIf { it.mediaType == "application/x-dtbncx+xml" }
                ?: items.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
            if (ncxItem != null) {
                val navigationPath = resolvePath(packageDirectory, ncxItem.href)
                val ncxDir = navigationPath.substringBeforeLast('/', "")
                zipEntry(epubArchive, navigationPath)?.let { entry ->
                    val bytes = budget.readOptional(
                        entry,
                        limits.maxPackageXmlBytes,
                        "EPUB NCX navigation",
                    ) ?: return@let
                    val doc = Jsoup.parse(
                        bytes.inputStream(),
                        "UTF-8",
                        "",
                        Parser.xmlParser(),
                    )
                    doc.select("navPoint").forEach { navPoint ->
                        val text = navPoint.selectFirst("navLabel > text")?.text()?.trim()
                        val src = navPoint.selectFirst("content")?.attr("src")
                        val path = src?.substringBefore('#')
                        if (!text.isNullOrEmpty() && !path.isNullOrEmpty()) {
                            val depth = navPoint.parents().count { it.tagName() == "navPoint" }
                            val target = resolvePath(ncxDir, path)
                            val rawFragment = src.substringAfter('#', "")
                            val fragment = rawFragment
                                .takeIf { '#' in src && it.isNotEmpty() }
                                ?.let { decodeUrlPath(it) ?: it }
                            addEntry(target, text, depth, fragment)
                        }
                    }
                }
            }
        }
        return TocIndex(
            byPath = titles.mapValues { it.value.toList() },
            entries = ordered.toList(),
        )
    }


    private fun ManifestItem.hasProperty(value: String): Boolean =
        properties.split(Regex("""\s+""")).any { it == value }


    private fun Element.localName(): String = normalName().substringAfterLast(':')


    internal fun writeInlineSvg(
        markup: String,
        imagesDir: File,
        cache: MutableMap<String, String>,
    ): String? {
        cache[markup]?.let { return it }
        imagesDir.mkdirs()
        val target = File(
            imagesDir,
            resourceCacheFileName("svg_inline", markup, "inline.svg"),
        )
        val path = runCatching {
            target.writeText(markup)
            target.absolutePath
        }.getOrNull() ?: return null
        cache[markup] = path
        return path
    }

    private fun extractImage(
        epubArchive: ZipFile,
        entryPath: String,
        imagesDir: File,
        cache: MutableMap<String, String>,
        budget: ArchiveResourceBudget,
        limits: ReaderResourceLimits,
        required: Boolean = false,
    ): String? {
        cache[entryPath]?.let { return it }
        val entry = zipEntry(epubArchive, entryPath) ?: return null
        val target = File(
            imagesDir,
            resourceCacheFileName("epub", entryPath, entryPath),
        )
        if (target.exists()) {
            if (target.length() in 1..limits.maxImageBytes) {
                cache[entryPath] = target.absolutePath
                return target.absolutePath
            }
            if (required) {
                throw ResourceLimitException(
                    ResourceLimitKind.ENTRY_SIZE,
                    "Cached EPUB spine image exceeds ${limits.maxImageBytes} bytes",
                )
            }
            return null
        }
        val copied = if (required) {
            budget.copyRequired(entry, target, limits.maxImageBytes, "EPUB spine image $entryPath")
            true
        } else {
            budget.copyOptional(entry, target, limits.maxImageBytes, "EPUB image $entryPath")
        }
        if (!copied) return null
        cache[entryPath] = target.absolutePath
        return target.absolutePath
    }


    private fun zipEntry(epubArchive: ZipFile, path: String): ZipEntry? {
        if (!isSafeArchivePath(path)) return null
        epubArchive.getEntry(path)?.let { return it }
        val decoded = decodeUrlPath(path)
        if (decoded != null && decoded != path && isSafeArchivePath(decoded)) {
            epubArchive.getEntry(decoded)?.let { return it }
        }
        return null
    }


    private fun resolvePath(baseDir: String, href: String): String {


        val path = href.substringBefore('#').substringBefore('?')
        val raw = decodeUrlPath(path) ?: path
        val combined = if (baseDir.isEmpty()) raw else "$baseDir/$raw"
        val parts = mutableListOf<String>()
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    private fun decodeUrlPath(path: String): String? = runCatching {
        URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
    }.getOrNull()

    private val CSS_IMPORT_REGEX = Regex(
        """@import\s+(?:url\(\s*['\"]?([^'\")\s]+)['\"]?\s*\)|['\"]([^'\"]+)['\"])\s*([^;]*);""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val FB2_EPUB_NOTE_MARKER = Regex("""\[(?:\d+|[*†‡]+)]""")
    private val FB2_EPUB_NOTE_FILE = Regex("""ch2-\d+\.xhtml""", RegexOption.IGNORE_CASE)
}
