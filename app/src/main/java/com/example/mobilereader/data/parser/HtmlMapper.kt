package com.example.mobilereader.data.parser

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.example.mobilereader.data.model.BlockAlign
import com.example.mobilereader.data.model.BlockStyle
import com.example.mobilereader.data.model.BookTextDirection
import com.example.mobilereader.data.model.BIDI_TAG
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.EXTERNAL_LINK_TAG
import com.example.mobilereader.data.model.FOOTNOTE_TAG
import com.example.mobilereader.data.model.FirstLetterStyle
import com.example.mobilereader.data.model.FloatImage
import com.example.mobilereader.data.model.LINK_TAG
import com.example.mobilereader.data.model.InlineBidiMode
import com.example.mobilereader.data.model.NoteDocument
import com.example.mobilereader.data.model.ParagraphStyle
import com.example.mobilereader.data.model.PublisherBoxSpan
import com.example.mobilereader.data.model.PublisherBoxStyle
import com.example.mobilereader.data.model.PublisherClear
import com.example.mobilereader.data.model.PublisherFloatSide
import com.example.mobilereader.data.model.TableCell
import com.example.mobilereader.data.model.TableRow
import com.example.mobilereader.data.model.slicePublisherBoxSpans
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.File
import java.util.Base64


class HtmlMapper(
    private val resolveImage: (String) -> String?,
    private val resolveLink: (String) -> String? = { null },
    css: CssResolver? = null,

    private val resolveInlineSvg: (String) -> String? = { null },

    private val maxStructureDepth: Int = DEFAULT_HTML_STRUCTURE_DEPTH,

    private val expansionBudget: HtmlExpansionBudget = HtmlExpansionBudget(),

    private val publisherBoxIdPrefix: String = "document",
) {

    init {
        require(maxStructureDepth > 0)
    }


    private var css: CssResolver? = css

    private val out = mutableListOf<ContentElement>()
    private var pendingInline: MutableList<Node> = mutableListOf()
    private var pendingInlineOwner: Element? = null


    val publisherBoxes = mutableListOf<PublisherBoxSpan>()

    private data class OpenPublisherBox(
        val id: String,
        val parentId: String?,
        val startElement: Int,
        val style: PublisherBoxStyle,
        val floatSide: PublisherFloatSide?,
        val clear: PublisherClear,
    )

    private val publisherBoxStack = mutableListOf<String>()
    private var publisherBoxSequence = 0


    private var headingLevel: Int? = null


    private class ListContext(
        val ordered: Boolean,
        var nextIndex: Int,
        val styleType: String,
    )

    private val listStack = mutableListOf<ListContext>()


    val anchors = mutableMapOf<String, Int>()


    val linkTargets = mutableSetOf<String>()


    val noteTargets = mutableSetOf<String>()

    private val mathFormatter by lazy {
        MathMlFormatter(
            maxDepth = maxStructureDepth,
            resolveInternalLink = resolveLink,
            acceptExternalLink = ::safeExternalHref,
            onInternalLink = linkTargets::add,
            resolveAuthorStyle = ::inlineStyleFor,
        )
    }


    val noteDocuments = mutableMapOf<String, NoteDocument>()

    fun map(body: Element): List<ContentElement> {
        if (css == null && (body.hasAttr("style") || body.selectFirst("[style]") != null)) {
            css = CssResolver(emptyList())
        }
        val bodyBox = beginPublisherBox(body, allowRoot = true)
        try {
            walk(body, quote = false)
            flushPending(quote = false)
        } finally {
            endPublisherBox(bodyBox)
        }
        return out.toList()
    }

    private fun registerAnchor(id: String) {
        if (id.isNotEmpty()) anchors.putIfAbsent(id, out.size)
    }

    private fun walk(container: Element, quote: Boolean, depth: Int = 0) {
        if (depth >= maxStructureDepth) {


            container.childNodes()
                .filterIsInstance<TextNode>()
                .filterNot(TextNode::isBlank)
                .forEach { queuePending(it, container) }
            return
        }
        for (node in container.childNodes()) {
            when (node) {
                is TextNode -> if (!node.isBlank) queuePending(node, container)

                is Element -> {


                    val richNoteContainer = isSemanticNoteContainer(node)
                    if (richNoteContainer) flushPending(quote)
                    registerAnchor(node.attr("id"))
                    val noteStart = out.size
                    walkElement(node, quote, depth + 1)
                    if (richNoteContainer) {
                        flushPending(quote)
                        if (out.size > noteStart) {
                            val document = NoteDocument(
                                elements = out.subList(noteStart, out.size).toList(),
                                publisherBoxes = slicePublisherBoxSpans(
                                    spans = publisherBoxes,
                                    startElement = noteStart,
                                    endElementExclusive = out.size,
                                ),
                            )
                            buildList {
                                node.attr("id").takeIf(String::isNotEmpty)?.let(::add)
                                node.select("[id]").forEach { descendant ->
                                    descendant.attr("id").takeIf(String::isNotEmpty)?.let(::add)
                                }


                                addAll(anchors.filterValues { it == noteStart }.keys)
                            }.forEach { id -> noteDocuments.putIfAbsent(id, document) }
                        }
                    }
                }
            }
        }
    }


    private fun isSemanticNoteContainer(node: Element): Boolean {
        val ownTokens = buildString {
            append(node.attr("epub:type"))
            append(' ')
            append(node.attr("role"))
            append(' ')
            append(node.attr("type"))
            append(' ')
            append(node.className())
        }.lowercase().split(Regex("[^a-z0-9_-]+"))
        if (node.normalName() in NOTE_ITEM_TAGS && ownTokens.any(::isNoteContainerToken)) {
            return true
        }
        if (node.normalName() == "note" || node.normalName() == "prodnote") return true


        if (node.attr("id").isEmpty() || node.normalName() !in NOTE_ITEM_TAGS) return false
        return node.parents().take(3).any { ancestor ->
            val tokens = "${ancestor.attr("epub:type")} ${ancestor.attr("role")} " +
                    "${ancestor.attr("type")} ${ancestor.className()}"
            tokens.lowercase().split(Regex("[^a-z0-9_-]+")).any(::isNoteCollectionToken)
        }
    }

    private fun isNoteContainerToken(token: String): Boolean = token in NOTE_CONTAINER_TOKENS

    private fun isNoteCollectionToken(token: String): Boolean =
        token in NOTE_CONTAINER_TOKENS || token in NOTE_COLLECTION_TOKENS

    private fun walkElement(node: Element, quote: Boolean, depth: Int) {


        val opensFlowBox = node.normalName() !in IMAGE_TAGS &&
                isPublisherBoxElement(node, allowRoot = false)


        if (opensFlowBox) flushPending(quote)
        val openBox = if (opensFlowBox) beginPublisherBox(node) else null
        try {
            walkElementContent(node, quote, depth)
        } finally {
            endPublisherBox(openBox)
        }
    }

    private fun walkElementContent(node: Element, quote: Boolean, depth: Int) {


        if (css?.computed(node)?.hidden == true) return


        if (isMathElement(node)) {
            if (isDisplayMath(node)) {
                flushPending(quote)
                emitMathBlock(node, quote)
            } else {
                queuePending(node, node.parent())
            }
            return
        }

        when (node.normalName()) {
            "p", "li", "dt", "dd", "pre" -> {
                flushPending(quote)
                emitParagraph(node, quote, depth)
            }

            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flushPending(quote)
                emitHeading(node, node.normalName()[1].digitToInt())
            }


            "levelhd", "hd", "bridgehead" -> {
                flushPending(quote)
                emitHeading(node, dtbookHeadingLevel(node))
            }

            "doctitle", "covertitle" -> {
                flushPending(quote)
                emitHeading(node, 1)
            }

            "blockquote", "cite", "poem", "epigraph" -> {
                flushPending(quote)
                walk(node, quote = true, depth = depth)
                flushPending(quote = true)
            }

            "ul", "ol" -> {
                flushPending(quote)
                val ordered = node.normalName() == "ol"


                val styleType = ListMarkers.cssTypeFor(node.attr("type").takeIf { it.isNotEmpty() })
                    ?: ListMarkers.applicableCssType(css?.computed(node)?.listStyleType, ordered)
                    ?: ListMarkers.defaultType(ordered, listStack.size + 1)
                val start = if (ordered) node.attr("start").toIntOrNull() ?: 1 else 1
                listStack.add(ListContext(ordered, start, styleType))
                walk(node, quote, depth)
                flushPending(quote)
                listStack.removeAt(listStack.lastIndex)
            }

            "list" -> {
                flushPending(quote)
                val kind = node.attr("type").lowercase()
                val ordered = kind == "ol" || node.attr("enum").isNotEmpty()
                val styleType = when (kind) {
                    "pl" -> "none"
                    else -> ListMarkers.cssTypeFor(node.attr("enum").takeIf(String::isNotEmpty))
                        ?: ListMarkers.applicableCssType(
                            css?.computed(node)?.listStyleType,
                            ordered
                        )
                        ?: ListMarkers.defaultType(ordered, listStack.size + 1)
                }
                val start = if (ordered) node.attr("start").toIntOrNull() ?: 1 else 1
                listStack.add(ListContext(ordered, start, styleType))
                walk(node, quote, depth)
                flushPending(quote)
                listStack.removeAt(listStack.lastIndex)
            }

            "figcaption", "caption" -> {
                flushPending(quote)
                if (node.selectFirst(BLOCK_PROBE) != null) {
                    walk(node, quote, depth)
                    flushPending(quote)
                } else {
                    emitCaptionLike(node, quote)
                }
            }

            "summary" -> {
                flushPending(quote)
                if (node.selectFirst(BLOCK_PROBE) != null) {
                    walk(node, quote, depth)
                    flushPending(quote)
                } else {
                    emitSummary(node, quote)
                }
            }

            "audio", "video" -> {
                flushPending(quote)
                emitMediaFallback(node, quote, depth)
            }

            "div", "section", "article", "aside", "main", "header",
            "footer", "dl", "figure", "center",
            "nav", "details",


            "dtbook", "book", "frontmatter", "bodymatter", "rearmatter",
            "level", "level1", "level2", "level3", "level4", "level5", "level6",
            "sidebar", "note", "prodnote", "annotation", "linegroup", "imggroup",
                -> {
                flushPending(quote)
                if (isEmptyLineBlock(node)) {
                    out += ContentElement.Spacer(1f)
                    return
                }

                emitGeneratedParagraph(node, after = false, quote)
                val titleLevel = titleClassLevel(node)
                if (titleLevel != null && headingLevel == null) {
                    headingLevel = titleLevel
                    walk(node, quote, depth)
                    flushPending(quote)
                    headingLevel = null
                } else {
                    walk(node, quote, depth)
                    flushPending(quote)
                }
                emitGeneratedParagraph(node, after = true, quote)
            }

            "img" -> {
                flushPending(quote)
                emitImage(node.attr("src"), node)
            }

            "image" -> {

                flushPending(quote)
                emitImage(node.attr("xlink:href").ifEmpty { node.attr("href") }, node)
            }

            "svg" -> {
                flushPending(quote)
                emitSvg(node)
            }

            "hr" -> {
                flushPending(quote)
                out += ContentElement.Divider
            }

            "table" -> {
                flushPending(quote)
                emitTable(node)
            }


            "line", "byline", "dateline", "docauthor", "address" -> {
                flushPending(quote)
                emitParagraph(node, quote, depth)
            }

            "br" -> queuePending(node, node.parent())

            "style", "script", "head", "title", "link", "meta", "source", "track" -> Unit

            else -> {


                if (node.selectFirst(BLOCK_PROBE) != null) {
                    flushPending(quote)
                    walk(node, quote, depth)
                    flushPending(quote)
                } else {
                    queuePending(node, node.parent())
                }
            }
        }
    }


    private fun queuePending(node: Node, owner: Element?) {
        if (pendingInline.isEmpty()) pendingInlineOwner = owner
        pendingInline += node
    }


    private fun beginPublisherBox(
        element: Element,
        allowRoot: Boolean = false,
    ): OpenPublisherBox? {
        if (!isPublisherBoxElement(element, allowRoot)) return null
        val resolver = css ?: return null
        val computed = resolver.computed(element)
        if (computed.hidden) return null
        val style = resolver.publisherBoxStyle(element) ?: PublisherBoxStyle.DEFAULT
        val floatSide = when (computed.floatSide) {
            "left" -> PublisherFloatSide.LEFT
            "right" -> PublisherFloatSide.RIGHT
            else -> null
        }
        val clear = when (computed.clear) {
            "left" -> PublisherClear.LEFT
            "right" -> PublisherClear.RIGHT
            "both" -> PublisherClear.BOTH
            else -> PublisherClear.NONE
        }
        if (style.isDefault && floatSide == null && clear == PublisherClear.NONE) return null

        val prefix = publisherBoxIdPrefix.ifBlank { "document" }
        val open = OpenPublisherBox(
            id = "$prefix:publisher-box-${publisherBoxSequence++}",
            parentId = publisherBoxStack.lastOrNull(),
            startElement = out.size,
            style = style,
            floatSide = floatSide,
            clear = clear,
        )
        publisherBoxStack += open.id
        return open
    }

    private fun endPublisherBox(open: OpenPublisherBox?) {
        if (open == null) return
        check(publisherBoxStack.removeAt(publisherBoxStack.lastIndex) == open.id) {
            "Publisher box stack is not properly nested"
        }
        val end = out.size
        if (end == open.startElement) {


            if (open.clear != PublisherClear.NONE) {
                publisherBoxes += PublisherBoxSpan(
                    id = open.id,
                    parentId = open.parentId,
                    startElement = end,
                    endElementExclusive = end,
                    clear = open.clear,
                )
            } else {


                for (index in publisherBoxes.indices) {
                    val span = publisherBoxes[index]
                    if (span.parentId == open.id) {
                        publisherBoxes[index] = span.copy(parentId = open.parentId)
                    }
                }
            }
            return
        }
        publisherBoxes += PublisherBoxSpan(
            id = open.id,
            parentId = open.parentId,
            startElement = open.startElement,
            endElementExclusive = end,
            style = open.style,
            floatSide = open.floatSide,
            clear = open.clear,
        )
    }

    private fun isPublisherBoxElement(element: Element, allowRoot: Boolean): Boolean {
        val tag = element.normalName()
        if ((!allowRoot && tag in ROOT_TAGS) ||
            (tag in NON_BLOCK_TAGS && tag !in IMAGE_TAGS)
        ) {
            return false
        }
        return (allowRoot && tag in ROOT_TAGS) ||
                tag in PUBLISHER_BOX_TAGS ||
                (isMathElement(element) && isDisplayMath(element)) ||
                element.selectFirst(BLOCK_PROBE) != null
    }


    private fun flushPending(quote: Boolean) {
        if (pendingInline.isEmpty()) return
        val nodes = pendingInline
        val owner = pendingInlineOwner
        pendingInline = mutableListOf()
        pendingInlineOwner = null
        val builder = InlineTextBuilder()
        nodes.forEach { appendInline(it, builder) }
        val hasText = !builder.isBlank
        if (hasText) {
            val level = headingLevel ?: owner?.let(::titleClassLevel)
            if (level != null) {
                out += ContentElement.Heading(
                    styledText = builder.build().trimmed(),
                    level = level.coerceIn(1, 6),
                    block = looseBlockTextStyle(owner),
                )
            } else {
                out += ContentElement.Paragraph(
                    builder.build(),
                    if (quote) ParagraphStyle.QUOTE else ParagraphStyle.NORMAL,
                    looseBlockTextStyle(owner, withFirstLetter = true),
                )
            }
        }
    }


    private fun looseBlockTextStyle(
        owner: Element?,
        withFirstLetter: Boolean = false,
    ): BlockStyle {
        val authored = owner?.let { blockStyleFor(it, withFirstLetter) }
        val base = authored ?: BlockStyle()
        return base.copy(
            firstLineIndent = base.firstLineIndent ?: false,
            spaceBeforeSpecified = true,
            spaceAfterSpecified = true,
        )
    }

    private fun emitParagraph(element: Element, quote: Boolean, depth: Int) {

        element.select("[id]").forEach { registerAnchor(it.attr("id")) }

        val ownTitleLevel = titleClassLevel(element)
        val level = headingLevel ?: ownTitleLevel
        if (level != null) {
            emitHeading(element, level)
            return
        }
        if (isEmptyLineBlock(element)) {
            out += ContentElement.Spacer(1f)
            return
        }

        val builder = InlineTextBuilder()
        val preformatted = element.normalName() == "pre"
        if (preformatted) builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
        var isListItem = false
        if (element.normalName() == "li") {
            val context = listStack.lastOrNull()
            if (context != null) {
                isListItem = true
                if (context.ordered) {
                    element.attr("value").toIntOrNull()?.let { context.nextIndex = it }
                }
                builder.text(ListMarkers.marker(context.styleType, context.nextIndex))
                if (context.ordered) context.nextIndex++
            } else {
                builder.text("• ")
            }
        }
        val explicitInitial = if (!preformatted && !isListItem) {
            explicitFloatedInitial(element)
        } else {
            null
        }

        appendGeneratedRun(builder, generated(element, after = false))


        for (child in element.childNodes()) {
            if (child is Element && child.normalName() in NESTED_LIST_TAGS) continue
            appendInline(
                child,
                builder,
                preserveWhitespace = preformatted,
                suppressedStyleElement = explicitInitial?.element,
            )
        }
        appendGeneratedRun(builder, generated(element, after = true))
        if (preformatted) builder.pop()


        var floatImage: FloatImage? = null
        var floatElement: Element? = null
        val resolver = css
        if (resolver != null) {
            for (img in element.select("img")) {
                val computed = resolver.computed(img)
                val side = computed.floatSide ?: continue
                val frac = computed.widthFrac
                    ?: computed.widthEm?.let { it / 30f }
                    ?: 0.33f
                if (frac > 0.45f) continue
                val path = resolveImage(img.attr("src")) ?: continue
                floatImage = FloatImage(
                    path = path,
                    widthFrac = frac.coerceIn(0.1f, 0.45f),
                    left = side == "left",
                    altText = imageAlt(img),
                )
                floatElement = img
                break
            }
        }
        val hasText = !builder.isBlank
        if (hasText) {
            var block = blockStyleFor(element, withFirstLetter = true)
            if (isListItem) {


                val base = block ?: BlockStyle()
                block = base.copy(
                    indentStartEm = base.indentStartEm + 1.2f * (listStack.size - 1),
                    firstLineIndent = false,
                )
            }
            if (floatImage != null) {
                block = (block ?: BlockStyle()).copy(floatImage = floatImage)
            }
            if (explicitInitial != null) {
                block = (block ?: BlockStyle()).copy(firstLetter = explicitInitial.style)
            }
            if (preformatted) {
                block = (block ?: BlockStyle()).copy(
                    fontFamily = "monospace",
                    firstLineIndent = false,
                )
            }
            out += ContentElement.Paragraph(
                builder.build(),
                if (quote) ParagraphStyle.QUOTE else ParagraphStyle.NORMAL,
                block,
            )
        } else {
            floatElement = null
        }


        val inlineRefs = builder.imageRefs.toSet()
        element.select("img").forEach { image ->
            val path = resolveImage(image.attr("src"))
            if (image !== floatElement &&
                (!hasText || isBlockLikeMedia(image) || path != null && path !in inlineRefs)
            ) {
                emitImage(image.attr("src"), image)
            }
        }
        element.select("svg").forEach { svg ->
            val path = resolveSvg(svg)
            if (!hasText || path !in inlineRefs) path?.let { out += ContentElement.Image(it) }
        }
        element.children()
            .filter { it.normalName() in NESTED_LIST_TAGS }
            .forEach { nested ->
                if (depth < maxStructureDepth) walkElement(nested, quote, depth + 1)
            }
    }

    private fun emitHeading(element: Element, level: Int) {
        element.select("[id]").forEach { registerAnchor(it.attr("id")) }
        val builder = InlineTextBuilder()
        appendGeneratedRun(builder, generated(element, after = false))
        appendChildrenInline(element, builder)
        appendGeneratedRun(builder, generated(element, after = true))
        val hasText = !builder.isBlank
        if (hasText) {
            out += ContentElement.Heading(
                styledText = builder.build().trimmed(),
                level = level.coerceIn(1, 6),
                block = blockStyleFor(element),
            )
        }
        val inlineRefs = builder.imageRefs.toSet()
        element.select("img").forEach { image ->
            val path = resolveImage(image.attr("src"))
            if (!hasText || isBlockLikeMedia(image) || path != null && path !in inlineRefs) {
                emitImage(image.attr("src"), image)
            }
        }
        element.select("svg").forEach { svg ->
            val path = resolveSvg(svg)
            if (!hasText || path !in inlineRefs) path?.let { out += ContentElement.Image(it) }
        }
    }

    private fun emitMathBlock(element: Element, quote: Boolean) {
        registerMathAnchors(element)
        val builder = InlineTextBuilder()


        mathFormatter.append(element, builder, applyRootAuthorStyle = false)
        if (builder.isBlank) return
        val base = blockStyleFor(element) ?: BlockStyle()
        out += ContentElement.Paragraph(
            builder.build(),
            if (quote) ParagraphStyle.QUOTE else ParagraphStyle.NORMAL,
            base.copy(
                align = base.align ?: BlockAlign.CENTER,
                firstLineIndent = false,
            ),
        )
    }


    private fun emitCaptionLike(node: Element, quote: Boolean) {
        node.select("[id]").forEach { registerAnchor(it.attr("id")) }
        val builder = InlineTextBuilder()
        appendChildrenInline(node, builder)
        val hasText = !builder.isBlank
        if (hasText) {
            val base = blockStyleFor(node) ?: BlockStyle()
            out += ContentElement.Paragraph(
                builder.build(),
                if (quote) ParagraphStyle.QUOTE else ParagraphStyle.NORMAL,
                base.copy(
                    align = base.align ?: BlockAlign.CENTER,
                    italic = base.italic ?: true,
                    fontScale = (base.fontScale ?: 1f).let {
                        if (it in 0.99f..1.01f) 0.9f else it
                    },
                    firstLineIndent = false,
                ),
            )
        }
        val inlineRefs = builder.imageRefs.toSet()
        node.select("img").forEach { image ->
            val path = resolveImage(image.attr("src"))
            if (!hasText || isBlockLikeMedia(image) || path != null && path !in inlineRefs) {
                emitImage(image.attr("src"), image)
            }
        }
        node.select("svg").forEach { svg ->
            val path = resolveSvg(svg)
            if (!hasText || path !in inlineRefs) path?.let { out += ContentElement.Image(it) }
        }
    }


    private fun emitSummary(node: Element, quote: Boolean) {
        node.select("[id]").forEach { registerAnchor(it.attr("id")) }
        val builder = InlineTextBuilder()
        appendChildrenInline(node, builder)
        if (builder.isBlank) return
        val base = blockStyleFor(node) ?: BlockStyle()
        out += ContentElement.Paragraph(
            builder.build(),
            if (quote) ParagraphStyle.QUOTE else ParagraphStyle.NORMAL,
            base.copy(bold = base.bold ?: true, firstLineIndent = false),
        )
    }


    private fun emitMediaFallback(node: Element, quote: Boolean, depth: Int) {
        val poster = node.attr("poster")
        if (poster.isNotEmpty() && resolveImage(poster) != null) {
            emitImage(poster)
            return
        }
        if (node.selectFirst(BLOCK_PROBE) != null || node.text().isNotBlank()) {


            walk(node, quote, depth)
            flushPending(quote)
            return
        }
        val label = if (node.normalName() == "audio") AUDIO_PLACEHOLDER else VIDEO_PLACEHOLDER
        out += ContentElement.Paragraph(
            AnnotatedString(label),
            ParagraphStyle.NORMAL,
            BlockStyle(
                align = BlockAlign.CENTER,
                italic = true,
                fontScale = 0.85f,
                firstLineIndent = false,
            ),
        )
    }


    private fun emitTable(table: Element) {


        table.select("[id]").forEach { registerAnchor(it.attr("id")) }
        val tableBlock = blockStyleFor(table)

        table.selectFirst("caption")?.let(::emitTableCaption)


        val ownRows = table.select("tr").filter { row ->
            row.parents().firstOrNull { it.normalName() == "table" } === table
        }
        if (ownRows.isEmpty()) {
            val text = table.text().trim()
            if (text.isNotEmpty()) out += ContentElement.Paragraph(AnnotatedString(text))
            return
        }

        val rows = mutableListOf<TableRow>()
        for (rowElement in ownRows) {
            val cellElements = rowElement.children()
                .filter { it.normalName() == "td" || it.normalName() == "th" }
            if (cellElements.isEmpty()) continue
            val inHead = rowElement.parents().any { it.normalName() == "thead" }
            val cells = cellElements.map { cellElement ->
                val builder = InlineTextBuilder()
                appendCellContent(cellElement, builder)
                val header = cellElement.normalName() == "th"
                val cellComputed = css?.computed(cellElement)
                val cellPublisherBox = css?.publisherBoxStyle(cellElement)
                    ?: PublisherBoxStyle.DEFAULT.takeIf {
                        cellComputed?.paddingSpecified == true ||
                                cellComputed?.borderSpecified == true
                    }
                TableCell(
                    text = builder.build(),
                    colSpan = cellElement.attr("colspan").toIntOrNull()?.coerceIn(1, 10) ?: 1,
                    rowSpan = cellElement.attr("rowspan").toIntOrNull()?.coerceIn(1, 20) ?: 1,
                    align = cellAlign(cellElement, header),
                    header = header,
                    block = tableCellBlockStyle(cellElement, table),
                    publisherBox = cellPublisherBox,
                    publisherPaddingSpecified = cellComputed?.paddingSpecified == true,
                    publisherBorderSpecified = cellComputed?.borderSpecified == true,
                )
            }
            rows += TableRow(cells, isHeader = inHead || cells.all { it.header })
        }

        val columnCount = rows.maxOfOrNull { row -> row.cells.sumOf { it.colSpan } } ?: 0
        when {
            rows.isEmpty() || columnCount == 0 -> Unit

            columnCount == 1 -> {

                for (row in rows) {
                    for (cell in row.cells) {
                        if (cell.text.text.isBlank()) continue
                        out += ContentElement.Paragraph(
                            cell.text,
                            ParagraphStyle.NORMAL,
                            mergedTableCellBlock(tableBlock, cell),
                        )
                    }
                }
            }

            columnCount > 12 || rows.size > 400 -> {
                for (row in rows) {
                    val text = row.cells.joinToString("    ") { it.text.text.trim() }
                        .trim()
                    if (text.isEmpty()) continue
                    out += ContentElement.Paragraph(
                        AnnotatedString(text),
                        ParagraphStyle.NORMAL,
                        BlockStyle(firstLineIndent = false),
                    )
                }
            }

            else -> out += ContentElement.Table(rows, tableBlock)
        }
    }

    private fun emitTableCaption(caption: Element) {
        val openBox = beginPublisherBox(caption)
        try {
            val builder = InlineTextBuilder()
            appendChildrenInline(caption, builder)
            if (!builder.isBlank) {
                out += ContentElement.Paragraph(
                    builder.build(),
                    ParagraphStyle.NORMAL,
                    (blockStyleFor(caption) ?: BlockStyle.DEFAULT).copy(
                        align = BlockAlign.CENTER,
                        italic = true,
                        firstLineIndent = false,
                    ),
                )
            }
        } finally {
            endPublisherBox(openBox)
        }
    }


    private fun appendCellContent(cell: Element, builder: InlineTextBuilder) {
        val wholeCellStyle = tableCellInlineStyle(cell)
        if (wholeCellStyle != null) builder.pushStyle(wholeCellStyle)
        for (child in cell.childNodes()) {
            if (child is Element && child.normalName() in CELL_BLOCK_TAGS) {
                if (!builder.isBlank) builder.lineBreak()


                appendInline(child, builder)
            } else {
                appendInline(child, builder)
            }
        }
        if (wholeCellStyle != null) builder.pop()
    }


    private fun tableCellBlockStyle(cell: Element, table: Element): BlockStyle? {
        val resolver = css
        val language = languageFor(cell)
        val tableLanguage = languageFor(table)
        if (resolver == null) {
            val direction = attributeDirection(cell)
            val style = BlockStyle(
                language = language.takeIf { it != tableLanguage },
                direction = direction.takeIf { it != attributeDirection(table) },
                foregroundColorArgb = legacyForeground(cell)
                    ?.takeIf { it != legacyForeground(table) },
                backgroundColorArgb = legacyVisualBackground(cell, table),
            )
            return style.takeUnless { it.isDefault }
        }

        val own = resolver.computed(cell)
        val base = resolver.computed(table)
        val ownDirection = when (own.direction) {
            "ltr" -> BookTextDirection.LTR
            "rtl" -> BookTextDirection.RTL
            "auto" -> BookTextDirection.AUTO
            else -> attributeDirection(cell)
        }
        val baseDirection = when (base.direction) {
            "ltr" -> BookTextDirection.LTR
            "rtl" -> BookTextDirection.RTL
            "auto" -> BookTextDirection.AUTO
            else -> attributeDirection(table)
        }
        val style = BlockStyle(
            italic = own.italic.takeIf { it != base.italic },
            bold = own.bold.takeIf { it != base.bold },


            fontScale = own.fontSizeEm.coerceIn(0.5f, 3f).takeIf {
                kotlin.math.abs(own.fontSizeEm - base.fontSizeEm) > 0.001f
            },
            fontFamily = own.fontFamilyName.takeIf { it != base.fontFamilyName },
            lineHeightMult = when {
                own.lineHeightMult == null && base.lineHeightMult != null -> 1.3f
                own.lineHeightMult != null && (
                        base.lineHeightMult == null ||
                                kotlin.math.abs(own.lineHeightMult - base.lineHeightMult) > 0.001f
                        ) -> own.lineHeightMult

                else -> null
            },
            language = language.takeIf { it != tableLanguage },
            direction = ownDirection.takeIf { it != baseDirection },
            foregroundColorArgb = own.foregroundColorArgb
                .takeIf { it != base.foregroundColorArgb },
            backgroundColorArgb = resolver.visualBackground(cell, table),
        )
        return style.takeUnless { it.isDefault }
    }


    private fun tableCellInlineStyle(cell: Element): SpanStyle? {
        val computed = css?.computed(cell) ?: return null
        var style = SpanStyle()
        var any = false
        val decorations = buildList {
            if (computed.underline) add(TextDecoration.Underline)
            if (computed.strike) add(TextDecoration.LineThrough)
        }
        if (decorations.isNotEmpty()) {
            style = style.copy(textDecoration = TextDecoration.combine(decorations))
            any = true
        }
        if (computed.superScript || computed.subScript) {
            style = style.copy(
                baselineShift = if (computed.superScript) {
                    BaselineShift.Superscript
                } else {
                    BaselineShift.Subscript
                },
                fontSize = TextUnit(0.75f, TextUnitType.Em),
            )
            any = true
        }
        return style.takeIf { any }
    }


    private fun mergedTableCellBlock(
        table: BlockStyle?,
        cell: TableCell,
    ): BlockStyle {
        val base = table ?: BlockStyle.DEFAULT
        val own = cell.block
        return base.copy(
            align = cell.align ?: base.align,
            italic = own?.italic ?: base.italic,
            bold = own?.bold ?: base.bold,
            fontScale = own?.fontScale ?: base.fontScale,
            fontFamily = own?.fontFamily ?: base.fontFamily,
            lineHeightMult = own?.lineHeightMult ?: base.lineHeightMult,
            language = own?.language ?: base.language,
            direction = own?.direction ?: base.direction,
            foregroundColorArgb = own?.foregroundColorArgb ?: base.foregroundColorArgb,
            backgroundColorArgb = own?.backgroundColorArgb ?: base.backgroundColorArgb,
            firstLineIndent = false,
        )
    }


    private fun attributeAlign(element: Element): BlockAlign? {
        var node: Element? = element
        while (node != null && node.normalName() !in ROOT_TAGS) {
            if (node.normalName() == "center") return BlockAlign.CENTER
            when (node.attr("align").lowercase()) {
                "center" -> return BlockAlign.CENTER
                "right" -> return BlockAlign.RIGHT
                "left" -> return BlockAlign.LEFT
                "justify" -> return BlockAlign.JUSTIFY
            }
            node = node.parent()
        }
        return null
    }

    private fun cellAlign(cell: Element, header: Boolean): BlockAlign? {
        val cssAlign = css?.let {


            when (it.computed(cell).textAlign) {
                "center" -> BlockAlign.CENTER
                "end" -> BlockAlign.END
                "start" -> BlockAlign.START
                "right" -> BlockAlign.RIGHT
                "left" -> BlockAlign.LEFT
                else -> null
            }
        }
        if (cssAlign != null) return cssAlign
        return when (cell.attr("align").lowercase()) {
            "center" -> BlockAlign.CENTER
            "right" -> BlockAlign.RIGHT
            "left" -> BlockAlign.LEFT
            else -> if (header) BlockAlign.CENTER else null
        }
    }

    private fun emitImage(src: String, node: Element? = null) {
        val openBox = node?.let { beginPublisherBox(it) }
        try {
            emitImageContent(src, node)
        } finally {
            endPublisherBox(openBox)
        }
    }

    private fun emitImageContent(src: String, node: Element?) {
        val alt = node?.let(::imageAlt)
        if (src.isEmpty()) {
            emitMissingImageAlt(alt)
            return
        }
        val path = resolveImage(src)
        if (path == null) {
            emitMissingImageAlt(alt)
            return
        }


        val computed = node?.let { css?.computed(it) }
        out += ContentElement.Image(
            path = path,
            widthFrac = computed?.widthFrac,
            widthEm = computed?.widthEm,
            heightEm = computed?.heightEm,
            altText = alt,
            spaceBeforeSpecified = computed?.marginTopSpecified == true,
            spaceAfterSpecified = computed?.marginBottomSpecified == true,
        )
    }

    private fun imageAlt(node: Element): String? =
        node.attr("alt").ifBlank { node.attr("aria-label") }
            .ifBlank { node.attr("title") }
            .trim()
            .takeIf { it.isNotEmpty() }

    private fun isBlockLikeMedia(node: Element): Boolean {
        val computed = css?.computed(node) ?: return false
        return computed.floatSide != null ||
                (computed.widthFrac ?: 0f) > 0.45f ||
                (computed.widthEm ?: 0f) > 12f ||
                (computed.heightEm ?: 0f) > 3f
    }

    private class ExplicitFloatedInitial(
        val element: Element,
        val style: FirstLetterStyle,
    )


    private fun explicitFloatedInitial(paragraph: Element): ExplicitFloatedInitial? {
        val resolver = css ?: return null


        if (resolver.generated(paragraph, after = false) != null) return null

        val candidate = paragraph.childNodes().firstNotNullOfOrNull { node ->
            when (node) {
                is TextNode -> if (node.isBlank) null else return null
                is Element -> {
                    if (resolver.computed(node).hidden) null else node
                }

                else -> null
            }
        } ?: return null
        if (candidate.normalName() !in EXPLICIT_INITIAL_TAGS) return null
        if (candidate.childNodes().any { it !is TextNode }) return null
        if (resolver.generated(candidate, after = false) != null ||
            resolver.generated(candidate, after = true) != null
        ) {
            return null
        }

        val raw = candidate.childNodes()
            .filterIsInstance<TextNode>()
            .joinToString(separator = "") { it.wholeText }
        val visible = raw.replace(INLINE_WHITESPACE, " ").trim()
        if (visible.isEmpty() || ' ' in visible) return null
        val codePoints = visible.codePointCount(0, visible.length)
        if (codePoints !in 1..3 || visible.codePoints().noneMatch(Character::isLetter)) return null

        val style = resolver.floatedTextInitial(candidate, paragraph, visible.length)
            ?.let { resolved ->
                resolved.copy(
                    language = languageFor(candidate),
                    direction = resolved.direction ?: attributeDirection(candidate),
                )
            }
            ?: return null
        return ExplicitFloatedInitial(candidate, style)
    }

    private fun emitMissingImageAlt(alt: String?) {
        if (alt == null) return
        out += ContentElement.Paragraph(
            AnnotatedString(alt),
            ParagraphStyle.NORMAL,
            BlockStyle(italic = true, firstLineIndent = false),
        )
    }


    private fun emitSvg(source: Element) {
        resolveSvg(source)?.let {
            val computed = css?.computed(source)
            out += ContentElement.Image(
                path = it,
                altText = source.attr("aria-label").ifBlank {
                    source.selectFirst("title")?.text().orEmpty()
                }.trim().takeIf(String::isNotEmpty),
                spaceBeforeSpecified = computed?.marginTopSpecified == true,
                spaceAfterSpecified = computed?.marginBottomSpecified == true,
            )
        }
    }

    private fun resolveSvg(source: Element): String? {
        val svg = source.clone()
        if (svg.attr("xmlns").isEmpty()) {
            svg.attr("xmlns", "http://www.w3.org/2000/svg")
        }
        for (image in svg.select("image")) {
            val attr = if (image.hasAttr("xlink:href")) "xlink:href" else "href"
            val ref = image.attr(attr)
            if (ref.isEmpty() || ref.startsWith("data:", ignoreCase = true)) continue
            val path = resolveImage(ref) ?: continue
            val file = File(path)
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
            if (bytes.isEmpty()) continue
            image.attr(
                attr,
                "data:${imageMime(file.name, bytes)};base64,${
                    Base64.getEncoder().encodeToString(bytes)
                }"
            )
        }
        return resolveInlineSvg(svg.outerHtml())
    }

    private fun imageMime(name: String, bytes: ByteArray): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".svg") || bytes.take(256).toByteArray()
                .decodeToString().contains("<svg", ignoreCase = true) -> "image/svg+xml"

            lower.endsWith(".gif") || bytes.size >= 3 &&
                    bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                    bytes[2] == 'F'.code.toByte() -> "image/gif"

            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            else -> "image/png"
        }
    }


    private fun blockStyleFor(element: Element, withFirstLetter: Boolean = false): BlockStyle? {
        val resolver = css
        if (resolver == null) {
            val style = BlockStyle(
                align = attributeAlign(element),
                language = languageFor(element),
                direction = attributeDirection(element),
                foregroundColorArgb = legacyForeground(element),


                backgroundColorArgb = legacyVisualBackground(element),
            )
            return if (style.isDefault) null else style
        }
        val computed = resolver.computed(element)

        var pageBreak = computed.pageBreakBefore


        var child: Element = element
        var ancestor = element.parent()
        while (ancestor != null && ancestor.normalName() !in ROOT_TAGS) {
            val box = resolver.computed(ancestor)
            if (firstBlockChild(ancestor) === child) {
                pageBreak = pageBreak || box.pageBreakBefore
            }
            child = ancestor
            ancestor = ancestor.parent()
        }


        val align = when {
            computed.textAlign == "center" -> BlockAlign.CENTER
            computed.textAlign == "end" -> BlockAlign.END
            computed.textAlign == "start" -> BlockAlign.START
            computed.textAlign == "right" -> BlockAlign.RIGHT
            computed.textAlign == "left" -> BlockAlign.LEFT
            computed.textAlign == "justify" -> BlockAlign.JUSTIFY
            else -> attributeAlign(element)
        }

        val computedFontScale = computed.fontSizeEm.coerceIn(0.6f, 2.6f)
        val style = BlockStyle(
            align = align,
            italic = computed.italic,
            bold = computed.bold,


            fontScale = computedFontScale.takeIf {
                it !in 0.999f..1.001f || element.normalName() in HEADING_TAGS
            },
            firstLineIndent = computed.textIndentEm?.let { it > 0.05f },
            firstLineIndentEm = computed.textIndentEm,
            fontFamily = computed.fontFamilyName,
            lineHeightMult = computed.lineHeightMult,
            hyphens = computed.hyphensAuto,


            spaceBeforeSpecified = computed.marginTopSpecified,
            spaceAfterSpecified = computed.marginBottomSpecified,
            pageBreakBefore = pageBreak,
            firstLetter = if (withFirstLetter) resolver.firstLetter(element) else null,
            language = languageFor(element),
            direction = when (computed.direction) {
                "ltr" -> BookTextDirection.LTR
                "rtl" -> BookTextDirection.RTL
                "auto" -> BookTextDirection.AUTO
                else -> attributeDirection(element)
            },
            foregroundColorArgb = computed.foregroundColorArgb,


            backgroundColorArgb = computed.backgroundColorArgb,
        )
        return if (style.isDefault) null else style
    }


    private fun appendGeneratedRun(builder: InlineTextBuilder, run: CssResolver.GeneratedRun?) {
        if (run == null) return
        val style = generatedSpanStyle(run)
        if (style != null) builder.pushStyle(style)
        builder.text(run.text)
        if (style != null) builder.pop()
    }

    private fun generatedSpanStyle(run: CssResolver.GeneratedRun): SpanStyle? {
        var style = SpanStyle()
        var any = false
        if (run.italic == true) {
            style = style.copy(fontStyle = FontStyle.Italic)
            any = true
        }
        if (run.bold == true) {
            style = style.copy(fontWeight = FontWeight.Bold)
            any = true
        }
        if (run.scale !in 0.99f..1.01f) {
            style = style.copy(fontSize = TextUnit(run.scale, TextUnitType.Em))
            any = true
        }
        run.foregroundColorArgb?.let {
            style = style.copy(color = Color(it))
            any = true
        }
        run.backgroundColorArgb?.takeIf { (it ushr 24) != 0 }?.let {
            style = style.copy(background = Color(it))
            any = true
        }
        return if (any) style else null
    }


    private fun emitGeneratedParagraph(element: Element, after: Boolean, quote: Boolean) {
        val run = generated(element, after) ?: return
        val builder = InlineTextBuilder()
        appendGeneratedRun(builder, run)
        if (builder.isBlank) return
        val block = blockStyleFor(element)?.copy(firstLineIndent = false)
            ?: BlockStyle(firstLineIndent = false)
        out += ContentElement.Paragraph(
            builder.build(),
            if (quote) ParagraphStyle.QUOTE else ParagraphStyle.NORMAL,
            block,
        )
    }


    private fun generated(element: Element, after: Boolean): CssResolver.GeneratedRun? {
        val run = css?.generated(element, after) ?: return null
        return run.takeIf { expansionBudget.acceptGenerated(it.text.length) }
    }

    private fun firstBlockChild(container: Element): Element? =
        container.children().firstOrNull { it.normalName() !in NON_BLOCK_TAGS }


    private fun titleClassLevel(element: Element): Int? {
        for (name in element.classNames()) {
            val match = TITLE_CLASS.matchEntire(name) ?: continue
            val digit = match.groupValues[1]
            return if (digit.isEmpty()) 1 else (digit.toInt() + 1).coerceAtMost(6)
        }
        return null
    }


    private fun dtbookHeadingLevel(element: Element): Int {
        var nestedGenericLevels = 0
        var parent = element.parent()
        while (parent != null) {
            val name = parent.normalName()
            if (name == "level") {
                nestedGenericLevels++
            } else if (name.length == 6 && name.startsWith("level")) {
                val explicit = name.last().digitToIntOrNull()
                if (explicit != null) {
                    return (explicit + nestedGenericLevels).coerceIn(1, 6)
                }
            }
            parent = parent.parent()
        }
        return nestedGenericLevels.coerceIn(1, 6)
    }


    private fun isEmptyLineBlock(element: Element): Boolean =
        element.classNames().any { it.equals("empty-line", ignoreCase = true) } &&
                element.text().isBlank()


    private fun appendChildrenInline(
        element: Element,
        out: InlineTextBuilder,
        preserveWhitespace: Boolean = false,
        depth: Int = 0,
        suppressedStyleElement: Element? = null,
    ) {
        for (child in element.childNodes()) {
            appendInline(
                child,
                out,
                preserveWhitespace,
                depth + 1,
                suppressedStyleElement,
            )
        }
    }


    private fun AnnotatedString.trimmed(): AnnotatedString {
        var start = 0
        var end = length
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        return if (start == 0 && end == length) this else subSequence(start, end)
    }

    private fun appendInline(
        node: Node,
        out: InlineTextBuilder,
        preserveWhitespace: Boolean = false,
        depth: Int = 0,
        suppressedStyleElement: Element? = null,
    ) {
        when (node) {
            is TextNode -> if (preserveWhitespace) {
                out.preformattedText(node.wholeText)
            } else {
                out.text(node.wholeText)
            }

            is Element -> {
                val bidiMode = inlineBidiMode(node)
                if (bidiMode != null) out.pushAnnotation(BIDI_TAG, bidiMode.name)
                try {
                    if (depth >= maxStructureDepth) {
                        node.childNodes()
                            .filterIsInstance<TextNode>()
                            .forEach { child ->
                                if (preserveWhitespace) out.preformattedText(child.wholeText)
                                else out.text(child.wholeText)
                            }
                        return
                    }
                    if (css?.computed(node)?.hidden == true) return
                    if (isMathElement(node)) {
                        registerMathAnchors(node)
                        mathFormatter.append(node, out)
                        return
                    }
                    when (node.normalName()) {
                        "br" -> out.lineBreak()
                        "wbr" -> out.wordBreakOpportunity()
                        "img", "image" -> {
                            if (!isBlockLikeMedia(node)) {
                                val src = if (node.normalName() == "img") {
                                    node.attr("src")
                                } else {
                                    node.attr("xlink:href").ifEmpty { node.attr("href") }
                                }
                                val path = resolveImage(src)
                                if (path != null) {
                                    out.inlineImage(path, imageAlt(node))
                                } else {
                                    imageAlt(node)?.let(out::text)
                                }
                            }
                        }

                        "svg" -> {
                            val computed = css?.computed(node)
                            val blockLike = (computed?.widthFrac ?: 0f) > 0.45f ||
                                    (computed?.widthEm ?: 0f) > 12f ||
                                    (computed?.heightEm ?: 0f) > 3f
                            if (!blockLike) {
                                resolveSvg(node)?.let { path ->
                                    val alt = node.attr("aria-label").ifBlank {
                                        node.selectFirst("title")?.text().orEmpty()
                                    }
                                    out.inlineImage(path, alt)
                                }
                            }
                        }


                        "ruby" -> appendChildrenInline(
                            node, out, preserveWhitespace, depth, suppressedStyleElement,
                        )

                        "rp" -> Unit
                        "rt" -> {
                            out.pushStyle(
                                SpanStyle(
                                    fontSize = TextUnit(0.6f, TextUnitType.Em),
                                    baselineShift = BaselineShift(0.35f),
                                ),
                            )
                            appendChildrenInline(
                                node, out, preserveWhitespace, depth, suppressedStyleElement,
                            )
                            out.pop()
                        }

                        "q" -> {
                            val generatedBefore = generated(node, after = false)
                            val generatedAfter = generated(node, after = true)
                            val (open, close) = quoteMarksFor(node)
                            if (generatedBefore != null) {
                                appendGeneratedRun(out, generatedBefore)
                            } else {
                                out.text(open)
                            }
                            appendChildrenInline(
                                node, out, preserveWhitespace, depth, suppressedStyleElement,
                            )
                            if (generatedAfter != null) {
                                appendGeneratedRun(out, generatedAfter)
                            } else {
                                out.text(close)
                            }
                        }

                        "mark" -> {
                            out.pushStyle(SpanStyle(background = Color(0x66FFD54F)))
                            val style = inlineStyleFor(node)
                            if (style != null) out.pushStyle(style)
                            appendChildrenInline(
                                node, out, preserveWhitespace, depth, suppressedStyleElement,
                            )
                            if (style != null) out.pop()
                            out.pop()
                        }

                        "a" -> {
                            val href = node.attr("href").trim()
                            val key = href.takeIf { it.isNotEmpty() }?.let(resolveLink)
                            if (key != null) {
                                linkTargets += key
                                val note = isNoteReference(node, key)
                                if (note) noteTargets += key
                                out.pushAnnotation(
                                    if (note) FOOTNOTE_TAG else LINK_TAG,
                                    key,
                                )
                                appendChildrenInline(
                                    node, out, preserveWhitespace, depth, suppressedStyleElement,
                                )
                                out.pop()
                            } else if (safeExternalHref(href)) {
                                out.pushAnnotation(EXTERNAL_LINK_TAG, href)
                                appendChildrenInline(
                                    node, out, preserveWhitespace, depth, suppressedStyleElement,
                                )
                                out.pop()
                            } else {
                                appendChildrenInline(
                                    node, out, preserveWhitespace, depth, suppressedStyleElement,
                                )
                            }
                        }

                        else -> {
                            val style = if (node === suppressedStyleElement) {
                                null
                            } else {
                                inlineStyleFor(node)
                            }
                            if (style != null) out.pushStyle(style)
                            appendGeneratedRun(out, generated(node, after = false))
                            appendChildrenInline(
                                node, out, preserveWhitespace, depth, suppressedStyleElement,
                            )
                            appendGeneratedRun(out, generated(node, after = true))
                            if (style != null) out.pop()
                        }
                    }
                } finally {
                    if (bidiMode != null) out.pop()
                }
            }
        }
    }


    private fun inlineStyleFor(element: Element): SpanStyle? {
        val languageStyle = inlineLanguageStyle(element)
        val resolver = css
            ?: return mergeSpanStyles(
                mergeSpanStyles(tagStyleFor(element.normalName()), legacyInlineStyle(element)),
                languageStyle,
            )
        val parent = element.parent() ?: return tagStyleFor(element.normalName())
        val own = resolver.computed(element)
        val base = resolver.computed(parent)

        var style = SpanStyle()
        var any = false
        if (own.italic != null && own.italic != base.italic) {
            style = style.copy(fontStyle = if (own.italic) FontStyle.Italic else FontStyle.Normal)
            any = true
        }
        if (own.bold != null && own.bold != base.bold) {
            style = style.copy(fontWeight = if (own.bold) FontWeight.Bold else FontWeight.Normal)
            any = true
        }
        val sizeRatio = own.fontSizeEm / base.fontSizeEm
        if (sizeRatio !in 0.99f..1.01f) {
            style = style.copy(
                fontSize = TextUnit(sizeRatio.coerceIn(0.5f, 2f), TextUnitType.Em),
            )
            any = true
        }
        if (own.superScript) {
            style = style.copy(baselineShift = BaselineShift.Superscript)
            if (style.fontSize == TextUnit.Unspecified) {
                style = style.copy(fontSize = TextUnit(0.75f, TextUnitType.Em))
            }
            any = true
        } else if (own.subScript) {
            style = style.copy(baselineShift = BaselineShift.Subscript)
            if (style.fontSize == TextUnit.Unspecified) {
                style = style.copy(fontSize = TextUnit(0.75f, TextUnitType.Em))
            }
            any = true
        }
        val decorations = mutableListOf<TextDecoration>()
        if (own.underline && !base.underline) decorations += TextDecoration.Underline
        if (own.strike && !base.strike) decorations += TextDecoration.LineThrough
        if (decorations.isNotEmpty()) {
            style = style.copy(textDecoration = TextDecoration.combine(decorations))
            any = true
        }
        if (own.monospace && !base.monospace) {
            style = style.copy(fontFamily = FontFamily.Monospace)
            any = true
        }
        if (own.fontFamilyName != base.fontFamilyName) {
            val family = when (own.fontFamilyName) {
                "serif" -> FontFamily.Serif
                "sans-serif", "sans" -> FontFamily.SansSerif
                "monospace" -> FontFamily.Monospace
                "cursive" -> FontFamily.Cursive
                else -> null
            }
            if (family != null) {
                style = style.copy(fontFamily = family)
                any = true
            }
        }
        if (own.foregroundColorArgb != base.foregroundColorArgb) {
            own.foregroundColorArgb?.let {
                style = style.copy(color = Color(it))
                any = true
            }
        }
        own.backgroundColorArgb?.takeIf { (it ushr 24) != 0 }?.let {
            style = style.copy(background = Color(it))
            any = true
        }
        return mergeSpanStyles(if (any) style else null, languageStyle)
    }


    private fun legacyInlineStyle(element: Element): SpanStyle? {
        var style = SpanStyle()
        var any = false
        element.attr("color").takeIf { it.isNotBlank() }?.let(CssColor::parse)?.let {
            style = style.copy(color = Color(it))
            any = true
        }
        element.attr("bgcolor").takeIf { it.isNotBlank() }?.let(CssColor::parse)
            ?.takeIf { (it ushr 24) != 0 }
            ?.let {
                style = style.copy(background = Color(it))
                any = true
            }
        return style.takeIf { any }
    }

    private fun legacyForeground(element: Element): Int? {
        var node: Element? = element
        while (node != null && node.normalName() != "#root") {
            val raw = node.attr("color").ifBlank {
                node.attr("text").takeIf { node.normalName() == "body" }.orEmpty()
            }
            if (raw.isNotBlank()) CssColor.parse(raw)?.let { return it }
            node = node.parent()
        }
        return null
    }

    private fun legacyVisualBackground(
        element: Element,
        stopExclusive: Element? = null,
    ): Int? {
        val layers = ArrayList<Int>(3)
        var node: Element? = element
        while (node != null && node !== stopExclusive && node.normalName() != "#root") {
            node.attr("bgcolor").takeIf { it.isNotBlank() }?.let(CssColor::parse)?.let {
                layers += it
            }
            node = node.parent()
        }
        var result = 0
        for (index in layers.lastIndex downTo 0) {
            result = compositeArgb(layers[index], result)
        }
        return result.takeIf { (it ushr 24) != 0 }
    }

    private fun compositeArgb(foreground: Int, background: Int): Int {
        val fa = (foreground ushr 24) and 0xFF
        if (fa == 0) return background
        if (fa == 0xFF) return foreground
        val ba = (background ushr 24) and 0xFF
        val outA = fa + (ba * (255 - fa) + 127) / 255
        if (outA == 0) return 0
        fun channel(shift: Int): Int {
            val fc = (foreground ushr shift) and 0xFF
            val bc = (background ushr shift) and 0xFF
            val premul = fc * fa + (bc * ba * (255 - fa) + 127) / 255
            return (premul + outA / 2) / outA
        }
        return (outA shl 24) or (channel(16) shl 16) or
                (channel(8) shl 8) or channel(0)
    }

    private fun mergeSpanStyles(first: SpanStyle?, second: SpanStyle?): SpanStyle? = when {
        first == null -> second
        second == null -> first
        else -> first.merge(second)
    }

    private fun inlineLanguageStyle(element: Element): SpanStyle? {
        val own = languageFor(element)
        val inherited = element.parent()?.let(::languageFor)
        return own.takeIf { it != null && it != inherited }
            ?.let { SpanStyle(localeList = LocaleList(it)) }
    }

    private fun languageFor(element: Element): String? {
        var node: Element? = element
        while (node != null) {
            val raw = node.attr("lang").ifBlank { node.attr("xml:lang") }.trim()
            if (raw.isNotEmpty()) return LanguageTag.normalize(raw) ?: raw.lowercase()
            node = node.parent()
        }
        return null
    }

    private fun attributeDirection(element: Element): BookTextDirection? {
        var node: Element? = element
        while (node != null) {
            when (node.attr("dir").lowercase()) {
                "ltr" -> return BookTextDirection.LTR
                "rtl" -> return BookTextDirection.RTL
                "auto" -> return BookTextDirection.AUTO
            }
            node = node.parent()
        }
        return null
    }


    private fun inlineBidiMode(element: Element): InlineBidiMode? {
        val computed = css?.computed(element)
        val rawDir = element.attr("dir").trim().lowercase()
        val parentDirection = element.parent()?.let { css?.computed(it)?.direction }
        val direction = when {


            rawDir in setOf("ltr", "rtl", "auto") -> computed?.direction ?: rawDir


            element.normalName() == "bdi" -> computed?.direction
                ?.takeIf { it != parentDirection }
                ?: "auto"

            else -> computed?.direction ?: when (attributeDirection(element)) {
                BookTextDirection.LTR -> "ltr"
                BookTextDirection.RTL -> "rtl"
                BookTextDirection.AUTO -> "auto"
                null -> "auto"
            }
        }
        val unicodeBidi = when (element.normalName()) {
            "bdi" -> computed?.unicodeBidi ?: "isolate"
            "bdo" -> computed?.unicodeBidi ?: "bidi-override"
            else -> computed?.unicodeBidi ?: if (rawDir.isNotEmpty()) "isolate" else "normal"
        }

        fun isolate() = when (direction) {
            "ltr" -> InlineBidiMode.ISOLATE_LTR
            "rtl" -> InlineBidiMode.ISOLATE_RTL
            else -> InlineBidiMode.ISOLATE_AUTO
        }

        fun embed() = when (direction) {
            "rtl" -> InlineBidiMode.EMBED_RTL
            "ltr" -> InlineBidiMode.EMBED_LTR
            else -> InlineBidiMode.ISOLATE_AUTO
        }

        fun override() = when (direction) {
            "rtl" -> InlineBidiMode.OVERRIDE_RTL
            else -> InlineBidiMode.OVERRIDE_LTR
        }

        fun isolateOverride() = when (direction) {
            "rtl" -> InlineBidiMode.ISOLATE_OVERRIDE_RTL
            else -> InlineBidiMode.ISOLATE_OVERRIDE_LTR
        }
        return when (unicodeBidi) {
            "isolate" -> isolate()
            "embed" -> embed()
            "bidi-override" -> override()
            "isolate-override" -> isolateOverride()
            "plaintext" -> InlineBidiMode.PLAINTEXT
            else -> null
        }
    }

    private fun tagStyleFor(tag: String): SpanStyle? = when (tag) {
        "i", "em", "dfn", "var" -> SpanStyle(fontStyle = FontStyle.Italic)
        "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
        "u", "ins" -> SpanStyle(textDecoration = TextDecoration.Underline)
        "s", "strike", "del" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        "code", "kbd", "samp", "tt" -> SpanStyle(fontFamily = FontFamily.Monospace)
        "sup" -> SpanStyle(
            baselineShift = BaselineShift.Superscript,
            fontSize = TextUnit(0.75f, TextUnitType.Em),
        )

        "sub" -> SpanStyle(
            baselineShift = BaselineShift.Subscript,
            fontSize = TextUnit(0.75f, TextUnitType.Em),
        )

        "small" -> SpanStyle(fontSize = TextUnit(0.85f, TextUnitType.Em))
        else -> null
    }


    private fun isNoteReference(link: Element, key: String): Boolean {
        if ('#' !in key) return false
        val epubTypes = link.attr("epub:type").split(Regex("\\s+"))
        if (epubTypes.any { it.equals("noteref", ignoreCase = true) }) return true
        if (link.attr("role").split(Regex("\\s+")).any {
                it.equals("doc-noteref", ignoreCase = true)
            }
        ) {
            return true
        }
        if (link.attr("type").equals("note", ignoreCase = true)) return true

        val hint = (link.className() + " " + link.id() + " " +
                link.attr("rel") + " " + key.substringBefore('#').substringAfterLast('/'))
            .lowercase()
        if (NOTE_HINT.containsMatchIn(hint)) return true


        val marker = link.text().trim().trim('[', ']', '(', ')')
        return (link.normalName() == "sup" || link.parents().any { it.normalName() == "sup" } ||
                link.selectFirst("sup") != null) &&
                marker.isNotEmpty() && marker.all { it.isDigit() || it in "*†‡" }
    }

    private fun safeExternalHref(href: String): Boolean {
        val scheme = href.substringBefore(':', "").lowercase()
        return scheme in SAFE_EXTERNAL_SCHEMES
    }

    private fun isMathElement(element: Element): Boolean =
        element.normalName().substringAfterLast(':').equals("math", ignoreCase = true)

    private fun isDisplayMath(element: Element): Boolean {
        if (element.attr("display").equals("block", ignoreCase = true)) return true
        return element.attr("style").split(';').any { declaration ->
            val (property, value) = declaration.split(':', limit = 2)
                .map(String::trim)
                .let { parts ->
                    if (parts.size == 2) parts[0] to parts[1] else "" to ""
                }
            property.equals("display", ignoreCase = true) &&
                    value.substringBefore('!').trim().equals("block", ignoreCase = true)
        }
    }

    private fun registerMathAnchors(element: Element) {
        registerAnchor(element.attr("id"))
        element.select("[id]").forEach { registerAnchor(it.attr("id")) }
    }

    private fun quoteMarksFor(element: Element): Pair<String, String> {
        val nested = element.parents().count { it.normalName() == "q" } % 2 == 1
        val language = generateSequence(element as Element?) { it.parent() }
            .mapNotNull { node ->
                node.attr("lang").ifBlank { node.attr("xml:lang") }
                    .substringBefore('-').lowercase().takeIf(String::isNotEmpty)
            }
            .firstOrNull()
        return when (language) {
            "ru", "uk", "be", "fr" -> if (nested) "„" to "“" else "«" to "»"
            "de" -> if (nested) "‚" to "‘" else "„" to "“"
            else -> if (nested) "‘" to "’" else "“" to "”"
        }
    }

    private companion object {
        val EXPLICIT_INITIAL_TAGS = setOf("span", "font", "b", "strong", "i", "em")
        val INLINE_WHITESPACE = Regex("[\\s\\u00A0]+")
        const val DEFAULT_HTML_STRUCTURE_DEPTH = 256
        val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        const val BLOCK_PROBE =
            "p, div, h1, h2, h3, h4, h5, h6, blockquote, img, ul, ol, table, math[display=block]"
        val ROOT_TAGS = setOf("body", "html", "#root")
        val NON_BLOCK_TAGS = setOf(
            "span", "a", "em", "i", "b", "strong", "u", "s", "sup", "sub",
            "small", "big", "code", "br", "img", "ruby", "rt", "rp", "rb",
        )
        val IMAGE_TAGS = setOf("img", "image")
        val PUBLISHER_BOX_TAGS = setOf(
            "p", "li", "dt", "dd", "pre",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "levelhd", "hd", "bridgehead", "doctitle", "covertitle",
            "blockquote", "cite", "poem", "epigraph",
            "ul", "ol", "list", "figcaption", "caption", "summary",
            "audio", "video",
            "div", "section", "article", "aside", "main", "header", "footer",
            "dl", "figure", "center", "nav", "details",
            "dtbook", "book", "frontmatter", "bodymatter", "rearmatter",
            "level", "level1", "level2", "level3", "level4", "level5", "level6",
            "sidebar", "note", "prodnote", "annotation", "linegroup", "imggroup",
            "svg", "hr", "table", "line", "byline", "dateline", "docauthor",
            "address", "img", "image",
        )
        val TITLE_CLASS = Regex("""(?i)title(\d*)""")
        val NESTED_LIST_TAGS = setOf("ul", "ol", "list")
        val CELL_BLOCK_TAGS = setOf("p", "div")
        val NOTE_HINT = Regex("(?:^|[-_\\s])(footnote|endnote|noteref|note)(?:$|[-_\\s])")
        val NOTE_ITEM_TAGS = setOf("li", "p", "div", "section", "aside", "note", "prodnote")
        val NOTE_CONTAINER_TOKENS = setOf(
            "footnote", "endnote", "rearnote", "doc-footnote", "doc-endnote",
        )
        val NOTE_COLLECTION_TOKENS = setOf(
            "footnotes", "endnotes", "rearnotes", "notes", "doc-endnotes",
        )
        val SAFE_EXTERNAL_SCHEMES = setOf("http", "https", "mailto", "tel")


        const val AUDIO_PLACEHOLDER = "♪ Audio is not supported"
        const val VIDEO_PLACEHOLDER = "▶ Video is not supported"
    }
}
