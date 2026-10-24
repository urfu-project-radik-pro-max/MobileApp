package com.example.mobilereader.data.parser

import com.example.mobilereader.data.model.HeadingDefaults
import org.jsoup.nodes.Element
import java.util.IdentityHashMap


class CssResolver(sheets: List<Sheet>) {


    class Sheet(val text: String, val baseDir: String = "")


    class FontFace(
        val family: String,
        val src: String,
        val baseDir: String,
        val bold: Boolean,
        val italic: Boolean,
    )


    private enum class FontPart { STYLE, WEIGHT, SIZE, LINE_HEIGHT, FAMILY }
    private class Declaration(
        val value: String,
        val important: Boolean,

        val fontPart: FontPart? = null,

        val backgroundShorthand: Boolean = false,
    )

    private class Rule(

        val chain: List<Compound>,
        val specificity: Int,
        val order: Int,
        val declarations: Map<String, Declaration>,
    )

    private enum class Combinator { DESCENDANT, CHILD, NEXT_SIBLING, SUBSEQUENT_SIBLING }

    private enum class AttrOp { EXISTS, EQUALS, INCLUDES, DASH, PREFIX, SUFFIX, SUBSTRING }

    private class AttrSelector(val name: String, val op: AttrOp, val value: String?) {
        fun matches(element: Element): Boolean {
            if (!element.hasAttr(name)) return false
            val actual = element.attr(name)
            val expected = value ?: return true
            return when (op) {
                AttrOp.EXISTS -> true
                AttrOp.EQUALS -> actual == expected
                AttrOp.INCLUDES ->
                    expected.isNotEmpty() && actual.split(whitespaceRegex).contains(expected)
                AttrOp.DASH -> actual == expected || actual.startsWith("$expected-")
                AttrOp.PREFIX -> expected.isNotEmpty() && actual.startsWith(expected)
                AttrOp.SUFFIX -> expected.isNotEmpty() && actual.endsWith(expected)
                AttrOp.SUBSTRING -> expected.isNotEmpty() && actual.contains(expected)
            }
        }
    }

    private sealed interface PseudoClass {
        fun matches(element: Element, context: SelectorMatchContext): Boolean

        object FirstChild : PseudoClass {
            override fun matches(element: Element, context: SelectorMatchContext) =
                element.previousElementSibling() == null
        }

        object LastChild : PseudoClass {
            override fun matches(element: Element, context: SelectorMatchContext) =
                element.nextElementSibling() == null
        }

        object OnlyChild : PseudoClass {
            override fun matches(element: Element, context: SelectorMatchContext) =
                element.previousElementSibling() == null && element.nextElementSibling() == null
        }

        object FirstOfType : PseudoClass {
            override fun matches(element: Element, context: SelectorMatchContext) =
                context.siblingIndex(element, ofType = true) == 1
        }


        class Nth(private val a: Int, private val b: Int, private val ofType: Boolean) : PseudoClass {
            override fun matches(element: Element, context: SelectorMatchContext): Boolean {
                val index = context.siblingIndex(element, ofType) ?: return false
                val diff = index - b
                return if (a == 0) diff == 0 else diff % a == 0 && diff / a >= 0
            }
        }


        class Not(private val inner: Simple) : PseudoClass {
            override fun matches(element: Element, context: SelectorMatchContext) =
                !inner.matches(element, context)
        }
    }

    private class Simple(
        val tag: String?,
        val id: String?,
        val classes: List<String>,
        val attrs: List<AttrSelector>,
        val pseudos: List<PseudoClass>,
    ) {
        fun matches(element: Element, context: SelectorMatchContext): Boolean {
            if (!context.consume(1 + classes.size + attrs.size + pseudos.size)) return false
            if (tag != null && tag != "*" && element.normalName() != tag) return false
            if (id != null && element.id() != id) return false
            if (classes.isNotEmpty()) {
                val names = element.classNames()
                if (classes.any { it !in names }) return false
            }
            if (attrs.any { !it.matches(element) }) return false
            if (pseudos.any { !it.matches(element, context) }) return false
            return true
        }
    }


    private class Compound(val combinator: Combinator, val simple: Simple)


    private class SelectorMatchContext(maxOperations: Int) {
        private data class SiblingPosition(val child: Int, val ofType: Int)

        private var remaining = maxOperations
        private val positionsByParent =
            IdentityHashMap<Element, IdentityHashMap<Element, SiblingPosition>>()

        val exhausted: Boolean get() = remaining <= 0

        fun consume(cost: Int = 1): Boolean {
            if (cost <= 0) return true
            if (remaining < cost) {
                remaining = 0
                return false
            }
            remaining -= cost
            return true
        }

        fun siblingIndex(element: Element, ofType: Boolean): Int? {
            val parent = element.parent() ?: return 1
            var positions = positionsByParent[parent]
            if (positions == null) {
                val children = parent.children()
                if (!consume(children.size)) return null
                positions = IdentityHashMap(children.size)
                val typeCounts = HashMap<String, Int>()
                children.forEachIndexed { index, child ->
                    val tag = child.normalName()
                    val typeIndex = (typeCounts[tag] ?: 0) + 1
                    typeCounts[tag] = typeIndex
                    positions[child] = SiblingPosition(index + 1, typeIndex)
                }
                positionsByParent[parent] = positions
            }
            val position = positions[element] ?: return null
            return if (ofType) position.ofType else position.child
        }
    }


    private class RuleIndex {
        private val universal = mutableListOf<Rule>()
        private val byTag = mutableMapOf<String, MutableList<Rule>>()
        private val byClass = mutableMapOf<String, MutableList<Rule>>()
        private val byId = mutableMapOf<String, MutableList<Rule>>()
        var size: Int = 0
            private set

        fun add(rule: Rule) {
            val subject = rule.chain.last().simple
            val id = subject.id
            val tag = subject.tag
            val bucket = when {
                id != null -> byId.getOrPut(id) { mutableListOf() }
                subject.classes.isNotEmpty() ->
                    byClass.getOrPut(subject.classes.first()) { mutableListOf() }
                tag != null && tag != "*" ->
                    byTag.getOrPut(tag) { mutableListOf() }
                else -> universal
            }
            val cap = if (bucket === universal) MAX_UNIVERSAL_RULES else MAX_RULES_PER_INDEX_BUCKET
            if (bucket.size >= cap) return
            bucket.add(rule)
            size++
        }

        fun visitCandidates(element: Element, visitor: (Rule) -> Boolean) {
            fun visit(bucket: List<Rule>?) {
                if (bucket == null) return
                for (rule in bucket) if (!visitor(rule)) return
            }

            for (rule in universal) if (!visitor(rule)) return
            val tag = byTag[element.normalName()]
            if (tag != null) for (rule in tag) if (!visitor(rule)) return
            for (name in element.classNames()) {
                val classes = byClass[name] ?: continue
                for (rule in classes) if (!visitor(rule)) return
            }
            val id = element.id()
            if (id.isNotEmpty()) visit(byId[id])
        }
    }


    class Computed(
        val italic: Boolean?,
        val bold: Boolean?,
        val fontSizeEm: Float,

        val textAlign: String?,

        val hidden: Boolean,

        val display: String?,


        val underline: Boolean,
        val strike: Boolean,
        val superScript: Boolean,
        val subScript: Boolean,
        val monospace: Boolean,

        val fontFamilyName: String?,

        val lineHeightMult: Float?,


        internal val lineHeightNumber: Float?,
        internal val lineHeightAbsoluteEm: Float?,

        val hyphensAuto: Boolean?,

        val direction: String?,

        val unicodeBidi: String?,

        val listStyleType: String?,

        val foregroundColorArgb: Int?,

        val backgroundColorArgb: Int?,

        val widthFrac: Float?,
        val widthEm: Float?,
        val heightEm: Float?,

        val customProps: Map<String, String> = emptyMap(),
    )

    private val mutableFontFaces = mutableListOf<FontFace>()
    private val fontFaceKeys = mutableSetOf<String>()
    private val rules = RuleIndex()
    private val beforeRules = RuleIndex()
    private val afterRules = RuleIndex()
    private var orderCounter = 0
    private var ruleCount = 0
    private var stylesheetDeclarationCount = 0
    private val selectorMatchContext = SelectorMatchContext(MAX_SELECTOR_MATCH_OPERATIONS)
    private val cache = HashMap<Element, Computed>()

    init {
        for (sheet in sheets) parseSheetText(sheet.text, sheet.baseDir)
    }


    val fontFaces: List<FontFace> get() = mutableFontFaces


    fun computed(element: Element): Computed {
        cache[element]?.let { return it }
        val unresolved = mutableListOf<Element>()
        var cursor: Element? = element
        var inherited: Computed? = null
        while (true) {
            val current = cursor ?: break
            val cached = cache[current]
            if (cached != null) {
                inherited = cached
                break
            }
            unresolved.add(current)
            cursor = current.parent()?.takeIf { it.normalName() != "#root" }
        }
        for (index in unresolved.lastIndex downTo 0) {
            val current = unresolved[index]
            inherited = resolve(current, inherited).also { cache[current] = it }
        }
        return checkNotNull(cache[element])
    }


    fun clearCache() = cache.clear()

    fun visualBackground(element: Element, stopExclusive: Element? = null): Int? {
        val layers = ArrayList<Int>(4)
        var cursor: Element? = element
        while (cursor != null && cursor !== stopExclusive && cursor.normalName() != "#root") {
            computed(cursor).backgroundColorArgb?.let(layers::add)
            cursor = cursor.parent()
        }
        var result = 0
        for (index in layers.lastIndex downTo 0) {
            result = compositeArgb(layers[index], result)
        }
        return result.takeIf { (it ushr 24) != 0 }
    }


    class GeneratedRun(
        val text: String,
        val italic: Boolean? = null,
        val bold: Boolean? = null,

        val scale: Float = 1f,
        val foregroundColorArgb: Int? = null,
        val backgroundColorArgb: Int? = null,
    )


    fun generated(element: Element, after: Boolean): GeneratedRun? {
        val pool = if (after) afterRules else beforeRules
        if (pool.size == 0) return null
        val merged = cascade(pool, element, inline = null) ?: return null
        val text = merged["content"]?.value?.let { parseContentValue(it) } ?: return null
        if (text.isBlank()) return null

        var italic: Boolean? = null
        var bold: Boolean? = null
        var scale = 1f
        val inherited = computed(element)
        var foreground = merged["color"]?.value?.let {
            resolveForeground(it, inherited.foregroundColorArgb)
        } ?: inherited.foregroundColorArgb
        var background = merged["background-color"]?.value?.let {
            resolveBackground(it, inherited.backgroundColorArgb, foreground)
        }
        for ((property, declaration) in merged) {
            val value = declaration.value.trim().lowercase()
            when (property) {
                "font-style" -> when {
                    value.startsWith("italic") || value.startsWith("oblique") -> italic = true
                    value == "normal" -> italic = false
                }

                "font-weight" -> {
                    val number = value.toIntOrNull()
                    when {
                        value == "bold" || value == "bolder" -> bold = true
                        number != null -> bold = number >= 600
                        value == "normal" || value == "lighter" -> bold = false
                    }
                }

                "font-size" -> fontSizeFactor(value, 1f)?.let { scale = it }
                "color", "background-color" -> Unit
            }
        }
        return GeneratedRun(
            text,
            italic,
            bold,
            scale.coerceIn(0.5f, 2.5f),
            foreground.takeIf { it != inherited.foregroundColorArgb },
            background,
        )
    }


    private fun parseContentValue(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val lower = value.lowercase()
        if (lower == "none" || lower == "normal") return null
        if ("counter(" in lower || "attr(" in lower || "url(" in lower ||
            "quote" in lower
        ) {
            return null
        }
        val out = StringBuilder()
        var i = 0
        var sawString = false
        while (i < value.length) {
            val c = value[i]
            when {
                c == '"' || c == '\'' -> {
                    sawString = true
                    i++
                    while (i < value.length && value[i] != c) {
                        if (value[i] == '\\' && i + 1 < value.length) {
                            i++
                            val hexStart = i
                            var hexEnd = i
                            while (hexEnd < value.length && hexEnd - hexStart < 6 &&
                                value[hexEnd].isHexDigit()
                            ) {
                                hexEnd++
                            }
                            if (hexEnd > hexStart) {
                                value.substring(hexStart, hexEnd).toIntOrNull(16)
                                    ?.takeIf { it in 1..0x10FFFF }
                                    ?.let { out.appendCodePoint(it) }
                                i = hexEnd

                                if (i < value.length && value[i] == ' ') i++
                            } else {
                                out.append(value[i])
                                i++
                            }
                        } else {
                            out.append(value[i])
                            i++
                        }
                    }
                    i++
                }

                c.isWhitespace() -> i++

                else -> return null
            }
        }
        return if (sawString) out.toString() else null
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'


    private fun declarationsFor(element: Element): Map<String, Declaration> {
        val inline = element.attr("style")
            .takeIf { it.isNotEmpty() }
            ?.let { parseDeclarations(it, stylesheet = false) }
        return cascade(rules, element, inline) ?: emptyMap()
    }


    private fun cascade(
        pool: RuleIndex,
        element: Element,
        inline: Map<String, Declaration>?,
    ): Map<String, Declaration>? {
        val matched = ArrayList<Rule>(4)
        pool.visitCandidates(element) { rule ->
            if (selectorMatchContext.exhausted) return@visitCandidates false
            if (matches(rule.chain, rule.chain.lastIndex, element)) matched.add(rule)
            !selectorMatchContext.exhausted
        }
        if (matched.isEmpty() && inline.isNullOrEmpty()) return null
        matched.sortWith(compareBy({ it.specificity }, { it.order }))
        val merged = LinkedHashMap<String, Declaration>()
        fun putInCascadeOrder(property: String, declaration: Declaration) {


            merged.remove(property)
            merged[property] = declaration
        }
        for (rule in matched) {
            for ((property, decl) in rule.declarations) {
                if (!decl.important) putInCascadeOrder(property, decl)
            }
        }
        inline?.forEach { (property, decl) ->
            if (!decl.important) putInCascadeOrder(property, decl)
        }
        for (rule in matched) {
            for ((property, decl) in rule.declarations) {
                if (decl.important) putInCascadeOrder(property, decl)
            }
        }
        inline?.forEach { (property, decl) ->
            if (decl.important) putInCascadeOrder(property, decl)
        }
        return merged
    }


    private fun matches(chain: List<Compound>, index: Int, element: Element): Boolean =
        matches(chain, index, element, IdentityHashMap())

    private fun matches(
        chain: List<Compound>,
        index: Int,
        element: Element,
        memo: IdentityHashMap<Element, ByteArray>,
    ): Boolean {
        if (!selectorMatchContext.consume()) return false
        val states = memo.getOrPut(element) { ByteArray(chain.size) }
        when (states[index].toInt()) {
            1 -> return false
            2 -> return true
        }
        if (!chain[index].simple.matches(element, selectorMatchContext)) {
            states[index] = 1
            return false
        }
        val matched = if (index == 0) {
            true
        } else when (chain[index].combinator) {
            Combinator.CHILD -> {
                val parent = element.parent()
                parent != null && parent.normalName() != "#root" &&
                    matches(chain, index - 1, parent, memo)
            }

            Combinator.DESCENDANT -> {
                var ancestor = element.parent()
                while (ancestor != null && ancestor.normalName() != "#root") {
                    if (matches(chain, index - 1, ancestor, memo)) {
                        states[index] = 2
                        return true
                    }
                    ancestor = ancestor.parent()
                }
                false
            }

            Combinator.NEXT_SIBLING -> {
                val sibling = element.previousElementSibling()
                sibling != null && matches(chain, index - 1, sibling, memo)
            }

            Combinator.SUBSEQUENT_SIBLING -> {
                var sibling = element.previousElementSibling()
                while (sibling != null) {
                    if (matches(chain, index - 1, sibling, memo)) {
                        states[index] = 2
                        return true
                    }
                    sibling = sibling.previousElementSibling()
                }
                false
            }
        }
        states[index] = if (matched) 2 else 1
        return matched
    }


    private fun resolve(element: Element, parent: Computed?): Computed {
        val decl = declarationsFor(element)


        val parentProps = parent?.customProps ?: emptyMap()
        var customProps = parentProps
        if (decl.keys.any { it.startsWith("--") }) {
            val own = LinkedHashMap(parentProps)
            for ((property, declaration) in decl) {
                if (property.startsWith("--")) {
                    val value = declaration.value
                    own[property] = substituteVars(value, parentProps) ?: value
                }
            }
            customProps = own
        }

        var italic = parent?.italic
        var bold = parent?.bold
        val inheritedFontSize = parent?.fontSizeEm ?: 1f
        val tagFontScale = when (element.normalName()) {
            "h1" -> HeadingDefaults.scale(1)
            "h2" -> HeadingDefaults.scale(2)
            "h3" -> HeadingDefaults.scale(3)
            "h4" -> HeadingDefaults.scale(4)
            "h5" -> HeadingDefaults.scale(5)
            "h6" -> HeadingDefaults.scale(6)
            "small" -> 0.85f
            "big" -> 1.2f
            else -> 1f
        }


        val declaredFontSize = decl["font-size"]?.let { declaration ->
            val value = resolvedDeclarationValue(declaration, customProps) ?: return@let null
            when (value) {
                "inherit", "unset" -> inheritedFontSize
                "initial" -> 1f
                else -> fontSizeFactor(value, inheritedFontSize)
            }
        }
        val fontSize = (declaredFontSize ?: inheritedFontSize * tagFontScale)
            .coerceIn(0.5f, 3f)
        var align = parent?.textAlign
        var hidden = parent?.hidden ?: false
        var display: String? = null
        var underline = parent?.underline ?: false
        var strike = parent?.strike ?: false
        var monospace = parent?.monospace ?: false
        var fontFamilyName = parent?.fontFamilyName
        var lineHeightNumber = parent?.lineHeightNumber
        var lineHeightAbsoluteEm = parent?.lineHeightAbsoluteEm
        var lineHeightMult = safeLineHeightMultiplier(
            when {
                lineHeightNumber != null -> lineHeightNumber
                lineHeightAbsoluteEm != null -> lineHeightAbsoluteEm / fontSize
                else -> null
            },
        )
        var hyphensAuto = parent?.hyphensAuto
        var direction = parent?.direction
        var widthFrac: Float? = null
        var widthEm: Float? = null
        var heightEm: Float? = null


        when (element.attr("dir").trim().lowercase()) {
            "ltr", "rtl", "auto" -> direction = element.attr("dir").trim().lowercase()
        }
        when (declarationValue(decl["direction"], customProps)?.trim()?.lowercase()) {
            "ltr", "rtl" -> direction = declarationValue(decl["direction"], customProps)
                ?.trim()?.lowercase()
            "initial", "revert", "revert-layer" -> direction = "ltr"
            "inherit", "unset" -> direction = parent?.direction
        }
        var unicodeBidi: String? = null
        var listStyleType = parent?.listStyleType
        val declaredForeground = declarationValue(decl["color"], customProps)
            ?: element.attr("color").takeIf { it.isNotBlank() }
            ?: element.attr("text").takeIf {
                element.normalName() == "body" && it.isNotBlank()
            }
        val foregroundColorArgb = declaredForeground?.let {
            resolveForeground(it, parent?.foregroundColorArgb)
        } ?: parent?.foregroundColorArgb
        val declaredBackground = decl["background-color"]?.let {
            resolvedDeclarationValue(it, customProps)
        }
            ?: element.attr("bgcolor").takeIf { it.isNotBlank() }
        val backgroundColorArgb = declaredBackground?.let {
            resolveBackground(it, parent?.backgroundColorArgb, foregroundColorArgb)
        }
        var superScript = false
        var subScript = false
        when (element.normalName()) {
            "i", "em", "dfn", "var", "cite" -> italic = true
            "b", "strong" -> bold = true
            "u", "ins" -> underline = true
            "s", "strike", "del" -> strike = true
            "sup" -> superScript = true
            "sub" -> subScript = true
            "code", "kbd", "samp", "tt", "pre" -> monospace = true
            "h1", "h2", "h3", "h4", "h5", "h6" -> bold = true
        }

        for ((property, declaration) in decl) {
            if (property.startsWith("--")) continue
            val value = resolvedDeclarationValue(declaration, customProps) ?: continue
            when (property) {
                "font-style" -> when {
                    value.startsWith("italic") || value.startsWith("oblique") -> italic = true
                    value == "normal" -> italic = false
                }

                "font-weight" -> {
                    val number = value.toIntOrNull()
                    when {
                        value == "bold" || value == "bolder" -> bold = true
                        number != null -> bold = number >= 600
                        value == "normal" || value == "lighter" -> bold = false
                    }
                }


                "font-size" -> Unit


                "color", "background-color" -> Unit

                "font-family" -> when (value) {
                    "inherit", "unset" -> Unit
                    "initial", "revert", "revert-layer" -> {
                        monospace = false
                        fontFamilyName = null
                    }
                    else -> {
                        monospace = value.contains("monospace")
                        firstFamilyName(value)?.let { fontFamilyName = it }
                    }
                }

                "line-height" -> when (value) {

                    "inherit", "unset" -> Unit
                    "normal", "initial" -> {
                        lineHeightNumber = null
                        lineHeightAbsoluteEm = null
                        lineHeightMult = null
                    }

                    else -> lineHeightValueOf(value, fontSize)?.let { resolved ->
                        lineHeightNumber = resolved.number
                        lineHeightAbsoluteEm = resolved.absoluteEm
                        lineHeightMult = safeLineHeightMultiplier(resolved.multiplier(fontSize))
                    }
                }

                "hyphens", "-webkit-hyphens", "-epub-hyphens", "-moz-hyphens" -> when (value) {
                    "auto" -> hyphensAuto = true
                    "none", "manual" -> hyphensAuto = false
                }

                "direction" -> when (value) {
                    "ltr", "rtl" -> direction = value
                }

                "unicode-bidi" -> when (value) {
                    "normal", "embed", "isolate", "bidi-override",
                    "isolate-override", "plaintext" -> unicodeBidi = value
                    "inherit" -> unicodeBidi = parent?.unicodeBidi
                    "initial", "unset", "revert", "revert-layer" -> unicodeBidi = null
                }

                "adobe-hyphenate" -> when (value) {
                    "explicit", "none" -> hyphensAuto = false
                    "auto" -> hyphensAuto = true
                }

                "text-align" -> when (value) {
                    "center" -> align = "center"
                    "right" -> align = "right"
                    "left" -> align = "left"
                    "end" -> align = "end"
                    "start" -> align = "start"
                    "justify" -> align = "justify"

                }

                "display" -> when (value) {
                    "inherit" -> display = parent?.display
                    "initial", "unset", "revert", "revert-layer" -> display = null
                    else -> {
                        display = value
                        hidden = hidden || value == "none"
                    }
                }

                "width" -> {
                    if (value.endsWith("%")) {
                        leadingNumber(value)?.takeIf { it.isFinite() && it > 0f }
                            ?.let { widthFrac = (it / 100f).coerceAtMost(1f) }
                    } else {
                        lengthEm(value, emScale = fontSize)?.takeIf { it.isFinite() && it > 0f }
                            ?.let { widthEm = it }
                    }
                }

                "height" -> {
                    if (!value.endsWith("%") && value != "auto") {
                        lengthEm(value, emScale = fontSize)?.takeIf { it.isFinite() && it > 0f }
                            ?.let { heightEm = it }
                    }
                }

                "text-decoration", "text-decoration-line" -> {
                    if (value.contains("underline")) underline = true
                    if (value.contains("line-through")) strike = true
                    if (value == "none") { underline = false; strike = false }
                }

                "vertical-align" -> when {
                    value.contains("super") -> superScript = true
                    value.contains("sub") -> subScript = true
                }

                "list-style-type" -> listTypeKeyword(value)?.let { listStyleType = it }

                "list-style" -> value.split(whitespaceRegex)
                    .firstNotNullOfOrNull { listTypeKeyword(it) }
                    ?.let { listStyleType = it }

            }
        }

        return Computed(
            customProps = customProps,
            italic = italic,
            bold = bold,
            fontSizeEm = fontSize,
            textAlign = align,
            hidden = hidden,
            display = display,
            underline = underline,
            strike = strike,
            superScript = superScript,
            subScript = subScript,
            monospace = monospace,
            fontFamilyName = fontFamilyName,
            lineHeightMult = lineHeightMult,
            lineHeightNumber = lineHeightNumber,
            lineHeightAbsoluteEm = lineHeightAbsoluteEm,
            hyphensAuto = hyphensAuto,
            direction = direction,
            unicodeBidi = unicodeBidi,
            listStyleType = listStyleType,
            foregroundColorArgb = foregroundColorArgb,
            backgroundColorArgb = backgroundColorArgb,
            widthFrac = widthFrac,
            widthEm = widthEm,
            heightEm = heightEm,
        )
    }

    private fun declarationValue(
        declaration: Declaration?,
        customProps: Map<String, String>,
    ): String? {
        val value = declaration?.value ?: return null
        return (if ("var(" in value) substituteVars(value, customProps) else value)
            ?.trim()
            ?.lowercase()
    }


    private fun resolvedDeclarationValue(
        declaration: Declaration,
        customProps: Map<String, String>,
    ): String? {
        val raw = if ("var(" in declaration.value) {
            substituteVars(declaration.value, customProps) ?: return null
        } else {
            declaration.value
        }
        val normalized = raw.trim().lowercase()
        return when {
            declaration.fontPart != null ->
                fontShorthandValues(normalized)?.value(declaration.fontPart)
            declaration.backgroundShorthand -> backgroundColorFromShorthand(normalized)
            else -> normalized
        }
    }


    private fun firstStrongRtl(text: String): Boolean? {
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            when (Character.getDirectionality(codePoint)) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            }
            offset += Character.charCount(codePoint)
        }
        return null
    }

    private fun resolveForeground(value: String, inherited: Int?): Int? =
        when (value.trim().lowercase()) {
            "inherit", "unset", "currentcolor" -> inherited
            "initial", "revert", "revert-layer" -> BLACK_ARGB
            else -> CssColor.parse(value)
        }

    private fun resolveBackground(value: String, inherited: Int?, current: Int?): Int? =
        when (value.trim().lowercase()) {
            "inherit" -> inherited
            "currentcolor" -> current
            "initial", "unset", "revert", "revert-layer" -> null
            else -> CssColor.parse(value)
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


    private fun substituteVars(value: String, props: Map<String, String>, depth: Int = 0): String? {
        val start = value.indexOf("var(")
        if (start < 0) return value
        if (depth > 8) return null
        val close = matchingParen(value, start + 3)
        if (close < 0) return null
        val inner = value.substring(start + 4, close)
        var comma = -1
        var parens = 0
        for (i in inner.indices) {
            when (inner[i]) {
                '(' -> parens++
                ')' -> parens--
                ',' -> if (parens == 0) {
                    comma = i
                    break
                }
            }
        }
        val name = (if (comma < 0) inner else inner.substring(0, comma)).trim().lowercase()
        val fallback = if (comma < 0) null else inner.substring(comma + 1).trim()
        val replacement = props[name] ?: fallback ?: return null
        val substituted = value.substring(0, start) + replacement + value.substring(close + 1)
        return substituteVars(substituted, props, depth + 1)
    }


    private fun fontSizeFactor(value: String, inherited: Float): Float? = when (value) {
        "medium" -> 1f
        "small" -> 0.9f
        "x-small" -> 0.75f
        "xx-small" -> 0.6f
        "large" -> 1.2f
        "x-large" -> 1.5f
        "xx-large" -> 2f
        "smaller" -> inherited * 0.85f
        "larger" -> inherited * 1.2f
        else -> {
            if (CssCalc.isMath(value)) {
                CssCalc.eval(value, CssCalc.Ctx(inherited, 1f, 1f / 16f, inherited / 100f))
                    ?.takeIf { it > 0f }
            } else {
                val number = leadingNumber(value) ?: return null
                when (unitOf(value)) {
                    "em" -> inherited * number
                    "rem" -> number
                    "%" -> inherited * number / 100f
                    "px" -> number / 16f
                    "pt" -> number / 12f
                    "pc" -> number
                    "in" -> number * 6f
                    "cm" -> number * 2.3622f
                    "mm" -> number * 0.23622f
                    "ch", "ex" -> inherited * number * 0.5f

                    "vw", "vmin" -> number * 0.30f
                    "vh", "vmax" -> number * 0.50f
                    else -> null
                }
            }
        }
    }


    private fun lengthEm(
        value: String,
        percentBase: Float = 0f,
        emScale: Float = 1f,
    ): Float? {
        if (value == "0") return 0f
        if (CssCalc.isMath(value)) {
            val percentUnit = if (percentBase > 0f) percentBase * 30f / 100f else null
            return CssCalc.eval(
                value,
                CssCalc.Ctx(emScale, 1f, 1f / 16f, percentUnit),
            )
                ?.coerceAtLeast(0f)
        }
        val number = leadingNumber(value) ?: return null
        if (number < 0) return 0f
        return when (unitOf(value)) {
            "em" -> number * emScale
            "rem" -> number
            "px" -> number / 16f
            "pt" -> number / 12f
            "pc" -> number
            "in" -> number * 6f
            "cm" -> number * 2.3622f
            "mm" -> number * 0.23622f
            "q" -> number * 0.059055f
            "ch", "ex" -> number * 0.5f * emScale

            "vw", "vmin" -> number * 0.30f
            "vh", "vmax" -> number * 0.50f
            "%" -> if (percentBase > 0f) number / 100f * percentBase * 30f else null
            else -> null
        }
    }


    private fun unitOf(value: String): String {
        var i = value.length
        while (i > 0 && (value[i - 1].isLetter() || value[i - 1] == '%')) i--
        return value.substring(i)
    }


    private fun leadingNumber(value: String): Float? =
        numberRegex.find(value)?.value?.toFloatOrNull()


    private fun firstFamilyName(value: String): String? =
        value.split(',').firstOrNull()
            ?.trim()?.trim('"', '\'')?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() && it != "inherit" }

    private class LineHeightValue(
        val number: Float? = null,
        val absoluteEm: Float? = null,
    ) {
        fun multiplier(fontSizeEm: Float): Float? =
            number ?: absoluteEm?.div(fontSizeEm.coerceAtLeast(0.1f))
    }


    private fun lineHeightValueOf(value: String, fontSizeEm: Float): LineHeightValue? {
        if (CssCalc.isMath(value)) {
            val isLength = lineHeightLengthUnitRegex.containsMatchIn(value)
            val evaluated = if (isLength) {
                CssCalc.eval(
                    value,
                    CssCalc.Ctx(
                        emUnit = fontSizeEm,
                        remUnit = 1f,
                        pxUnit = 1f / 16f,
                        percentUnit = fontSizeEm / 100f,
                    ),
                )
            } else {
                CssCalc.eval(
                    value,
                    CssCalc.Ctx(1f, 1f, 1f / 16f, null),
                    acceptScalar = true,
                )
            }?.takeIf { it.isFinite() && it > 0f } ?: return null
            return if (isLength) {
                LineHeightValue(absoluteEm = evaluated)
            } else {
                LineHeightValue(number = evaluated)
            }
        }

        val number = leadingNumber(value) ?: return null
        if (!number.isFinite() || number <= 0f) return null
        val resolved = when (unitOf(value)) {
            "" -> LineHeightValue(number = number)
            "%" -> LineHeightValue(absoluteEm = fontSizeEm * number / 100f)
            "em" -> LineHeightValue(absoluteEm = fontSizeEm * number)
            "rem" -> LineHeightValue(absoluteEm = number)
            "px" -> LineHeightValue(absoluteEm = number / 16f)
            "pt" -> LineHeightValue(absoluteEm = number / 12f)
            "pc" -> LineHeightValue(absoluteEm = number)
            "in" -> LineHeightValue(absoluteEm = number * 6f)
            "cm" -> LineHeightValue(absoluteEm = number * 2.3622f)
            "mm" -> LineHeightValue(absoluteEm = number * 0.23622f)
            "q" -> LineHeightValue(absoluteEm = number * 0.059055f)
            "ch", "ex" -> LineHeightValue(absoluteEm = fontSizeEm * number * 0.5f)
            "vw", "vmin" -> LineHeightValue(absoluteEm = number * 0.30f)
            "vh", "vmax" -> LineHeightValue(absoluteEm = number * 0.50f)
            else -> null
        }
        return resolved?.takeIf {
            it.number?.isFinite() != false && it.absoluteEm?.isFinite() != false
        }
    }


    private fun safeLineHeightMultiplier(value: Float?): Float? =
        value?.takeIf { it.isFinite() }?.coerceIn(0.5f, 4f)

    private fun listTypeKeyword(value: String): String? = when (value) {
        "disc", "circle", "square", "none", "decimal",
        "lower-alpha", "lower-latin", "upper-alpha", "upper-latin",
        "lower-roman", "upper-roman",
        -> value

        else -> null
    }


    private fun parseSheetText(sheetText: String, baseDir: String, groupDepth: Int = 0) {
        if (groupDepth > MAX_GROUP_RULE_DEPTH || ruleCount >= MAX_RULES ||
            stylesheetDeclarationCount >= MAX_STYLESHEET_DECLARATIONS
        ) {
            return
        }
        val css = stripComments(sheetText)
        var index = 0
        while (index < css.length) {
            if (ruleCount >= MAX_RULES ||
                stylesheetDeclarationCount >= MAX_STYLESHEET_DECLARATIONS
            ) {
                return
            }
            while (index < css.length && css[index].isWhitespace()) index++
            if (index >= css.length) break


            if (css[index] == '@') {
                val semi = css.indexOf(';', index)
                val brace = css.indexOf('{', index)
                if (semi >= 0 && (brace < 0 || semi < brace)) {
                    index = semi + 1
                    continue
                }
            }

            val braceOpen = css.indexOf('{', index)
            if (braceOpen < 0) break
            val selectorText = css.substring(index, braceOpen).trim()


            if (selectorText.startsWith("@")) {
                if (selectorText.startsWith("@media", ignoreCase = true)) {
                    val bodyEnd = matchingBrace(css, braceOpen)
                    if (bodyEnd < 0) break
                    if (screenMediaApplies(selectorText.substring(6))) {
                        parseSheetText(
                            css.substring(braceOpen + 1, bodyEnd),
                            baseDir,
                            groupDepth + 1,
                        )
                    }
                    index = bodyEnd + 1
                } else if (selectorText.startsWith("@font-face", ignoreCase = true)) {
                    val braceClose = css.indexOf('}', braceOpen)
                    if (braceClose < 0) break


                    ruleCount++
                    if (mutableFontFaces.size < MAX_FONT_FACES) {
                        parseFontFace(
                            parseDeclarations(
                                css.substring(braceOpen + 1, braceClose),
                                stylesheet = true,
                            ),
                            baseDir,
                        )
                    }
                    index = braceClose + 1
                } else {
                    val bodyEnd = matchingBrace(css, braceOpen)
                    if (bodyEnd < 0) break
                    index = bodyEnd + 1
                }
                continue
            }

            val braceClose = css.indexOf('}', braceOpen)
            if (braceClose < 0) break
            val declarations = parseDeclarations(
                css.substring(braceOpen + 1, braceClose),
                stylesheet = true,
            )
            if (declarations.isNotEmpty()) {
                for (selector in selectorSlices(selectorText)) {
                    if (ruleCount >= MAX_RULES) return
                    val parsed = parseSelector(selector.trim()) ?: continue
                    val rule = Rule(parsed.chain, parsed.specificity, orderCounter++, declarations)
                    val index = when (parsed.pseudoElement) {
                        PseudoElement.BEFORE -> beforeRules
                        PseudoElement.AFTER -> afterRules
                        null -> rules
                    }
                    index.add(rule)
                    ruleCount++
                }
            }
            index = braceClose + 1
        }
    }


    private fun selectorSlices(text: String): List<String> {
        val result = ArrayList<String>(minOf(MAX_SELECTORS_PER_RULE, 8))
        var start = 0
        var quote: Char? = null
        var parentheses = 0
        var brackets = 0
        for (index in text.indices) {
            val char = text[index]
            when {
                quote != null -> if (char == quote && !isEscaped(text, index)) quote = null
                char == '\'' || char == '"' -> quote = char
                char == '(' -> parentheses++
                char == ')' && parentheses > 0 -> parentheses--
                char == '[' -> brackets++
                char == ']' && brackets > 0 -> brackets--
                char == ',' && parentheses == 0 && brackets == 0 -> {
                    result.add(text.substring(start, index))
                    if (result.size >= MAX_SELECTORS_PER_RULE) return result
                    start = index + 1
                }
            }
        }
        if (result.size < MAX_SELECTORS_PER_RULE) result.add(text.substring(start))
        return result
    }


    private fun screenMediaApplies(raw: String): Boolean = raw.split(',').any { branch ->
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
        val baseApplies = when (type) {
            "", "all", "screen" -> true
            "print", "speech", "aural", "braille", "embossed", "handheld",
            "projection", "tty", "tv", "amzn-mobi", "amzn-kf8" -> false
            else -> false
        }
        if (negated) !baseApplies else baseApplies
    }

    private fun parseFontFace(declarations: Map<String, Declaration>, baseDir: String) {
        val family = declarations["font-family"]?.let { firstFamilyName(it.value) } ?: return
        val src = declarations["src"]?.value ?: return
        val url = fontUrlRegex.find(src)?.groupValues?.get(1) ?: return
        val weight = declarations["font-weight"]?.value?.trim()?.lowercase()
        val style = declarations["font-style"]?.value?.trim()?.lowercase()
        val bold = weight == "bold" || (weight?.toIntOrNull() ?: 400) >= 600
        val italic = style?.startsWith("italic") == true || style?.startsWith("oblique") == true
        val key = listOf(family.lowercase(), url, baseDir, bold.toString(), italic.toString())
            .joinToString("\u0000")
        if (!fontFaceKeys.add(key) || mutableFontFaces.size >= MAX_FONT_FACES) return
        mutableFontFaces.add(FontFace(
            family = family,
            src = url,
            baseDir = baseDir,
            bold = bold,
            italic = italic,
        ))
    }

    private enum class PseudoElement { BEFORE, AFTER }

    private class ParsedSelector(
        val chain: List<Compound>,
        val specificity: Int,

        val pseudoElement: PseudoElement?,
    )


    private fun parseSelector(selector: String): ParsedSelector? {
        val text = selector.trim()
        if (text.isEmpty() || text.length > MAX_SELECTOR_CHARS) return null
        val chain = mutableListOf<Compound>()
        var ids = 0
        var classLike = 0
        var tags = 0
        var pseudoElement: PseudoElement? = null
        var pseudoElementAt = -1
        var pending: Combinator? = null
        var i = 0

        while (i < text.length) {
            val ch = text[i]
            when (ch) {
                ' ', '\t', '\n', '\r' -> i++

                '>', '+', '~' -> {
                    if (chain.isEmpty() || pending != null) return null
                    pending = when (ch) {
                        '>' -> Combinator.CHILD
                        '+' -> Combinator.NEXT_SIBLING
                        else -> Combinator.SUBSEQUENT_SIBLING
                    }
                    i++
                }

                else -> {

                    var tag: String? = null
                    var id: String? = null
                    val classes = mutableListOf<String>()
                    val attrs = mutableListOf<AttrSelector>()
                    val pseudos = mutableListOf<PseudoClass>()
                    var sawPseudoElement: PseudoElement? = null

                    compound@ while (i < text.length) {
                        when (val c = text[i]) {
                            ' ', '\t', '\n', '\r', '>', '+', '~' -> break@compound

                            '*' -> {
                                tag = "*"
                                i++
                            }

                            '.' -> {
                                val end = readIdent(text, i + 1)
                                if (end == i + 1) return null
                                classes += text.substring(i + 1, end)
                                classLike++
                                i = end
                            }

                            '#' -> {
                                val end = readIdent(text, i + 1)
                                if (end == i + 1) return null
                                id = text.substring(i + 1, end)
                                ids++
                                i = end
                            }

                            '[' -> {
                                val close = text.indexOf(']', i)
                                if (close < 0) return null
                                attrs += parseAttrSelector(text.substring(i + 1, close))
                                    ?: return null
                                classLike++
                                i = close + 1
                            }

                            ':' -> {
                                val isElement = i + 1 < text.length && text[i + 1] == ':'
                                val start = i + if (isElement) 2 else 1
                                val end = readIdent(text, start)
                                if (end == start) return null
                                val name = text.substring(start, end).lowercase()
                                if (end < text.length && text[end] == '(') {


                                    if (isElement) return null
                                    val close = matchingParen(text, end)
                                    if (close < 0) return null
                                    val arg = text.substring(end + 1, close).trim()
                                    when (name) {
                                        "not" -> {
                                            val inner = parseInnerCompound(arg) ?: return null
                                            pseudos += PseudoClass.Not(inner.simple)
                                            ids += inner.ids
                                            classLike += inner.classLike
                                            tags += inner.tags
                                        }

                                        "nth-child", "nth-of-type" -> {
                                            val nth = parseNth(arg) ?: return null
                                            pseudos += PseudoClass.Nth(
                                                nth.first,
                                                nth.second,
                                                ofType = name == "nth-of-type",
                                            )
                                            classLike++
                                        }

                                        else -> return null
                                    }
                                    i = close + 1
                                } else {
                                    when (name) {


                                        "before" -> {
                                            sawPseudoElement = PseudoElement.BEFORE
                                            tags++
                                        }

                                        "after" -> {
                                            sawPseudoElement = PseudoElement.AFTER
                                            tags++
                                        }

                                        "first-child" -> {
                                            if (isElement) return null
                                            pseudos += PseudoClass.FirstChild
                                            classLike++
                                        }

                                        "last-child" -> {
                                            if (isElement) return null
                                            pseudos += PseudoClass.LastChild
                                            classLike++
                                        }

                                        "only-child" -> {
                                            if (isElement) return null
                                            pseudos += PseudoClass.OnlyChild
                                            classLike++
                                        }

                                        "first-of-type" -> {
                                            if (isElement) return null
                                            pseudos += PseudoClass.FirstOfType
                                            classLike++
                                        }

                                        else -> return null
                                    }
                                    i = end
                                }
                            }

                            else -> {
                                if (tag != null || id != null || classes.isNotEmpty() ||
                                    attrs.isNotEmpty() || pseudos.isNotEmpty()
                                ) {
                                    return null
                                }
                                val end = readIdent(text, i)
                                if (end == i) return null
                                tag = text.substring(i, end).lowercase()
                                tags++
                                i = end
                            }
                        }
                    }

                    val empty = tag == null && id == null && classes.isEmpty() &&
                        attrs.isEmpty() && pseudos.isEmpty() && sawPseudoElement == null
                    if (empty) return null
                    if (chain.size >= MAX_SELECTOR_COMPOUNDS ||
                        classes.size + attrs.size + pseudos.size > MAX_SIMPLE_COMPONENTS
                    ) {
                        return null
                    }
                    chain += Compound(
                        pending ?: Combinator.DESCENDANT,
                        Simple(tag, id, classes, attrs, pseudos),
                    )
                    if (sawPseudoElement != null) {
                        pseudoElement = sawPseudoElement
                        pseudoElementAt = chain.lastIndex
                    }
                    pending = null
                }
            }
        }

        if (chain.isEmpty() || pending != null) return null

        if (pseudoElementAt >= 0 && pseudoElementAt != chain.lastIndex) return null
        return ParsedSelector(
            chain = chain,
            specificity = ids * 1_000_000 + classLike * 1_000 + tags,
            pseudoElement = pseudoElement,
        )
    }

    private class InnerCompound(
        val simple: Simple,
        val ids: Int,
        val classLike: Int,
        val tags: Int,
    )


    private fun parseInnerCompound(argument: String): InnerCompound? {
        val text = argument.trim()
        if (text.isEmpty() || text.length > MAX_SELECTOR_CHARS || text.any { it.isWhitespace() }) {
            return null
        }
        var tag: String? = null
        var id: String? = null
        val classes = mutableListOf<String>()
        val attrs = mutableListOf<AttrSelector>()
        var ids = 0
        var classLike = 0
        var tags = 0
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '*' -> {
                    tag = "*"
                    i++
                }

                '.' -> {
                    val end = readIdent(text, i + 1)
                    if (end == i + 1) return null
                    classes += text.substring(i + 1, end)
                    classLike++
                    i = end
                }

                '#' -> {
                    val end = readIdent(text, i + 1)
                    if (end == i + 1) return null
                    id = text.substring(i + 1, end)
                    ids++
                    i = end
                }

                '[' -> {
                    val close = text.indexOf(']', i)
                    if (close < 0) return null
                    attrs += parseAttrSelector(text.substring(i + 1, close)) ?: return null
                    classLike++
                    i = close + 1
                }

                ':' -> return null

                else -> {
                    if (tag != null || id != null || classes.isNotEmpty() || attrs.isNotEmpty()) {
                        return null
                    }
                    val end = readIdent(text, i)
                    if (end == i) return null
                    tag = text.substring(i, end).lowercase()
                    tags++
                    i = end
                }
            }
        }
        if (classes.size + attrs.size > MAX_SIMPLE_COMPONENTS) return null
        if (tag == null && id == null && classes.isEmpty() && attrs.isEmpty()) return null
        return InnerCompound(Simple(tag, id, classes, attrs, emptyList()), ids, classLike, tags)
    }


    private fun parseNth(argument: String): Pair<Int, Int>? {
        val arg = argument.lowercase().replace(" ", "")
        return when {
            arg == "even" -> 2 to 0
            arg == "odd" -> 2 to 1
            !arg.contains('n') -> arg.toIntOrNull()?.let { 0 to it }
            else -> {
                val match = nthRegex.matchEntire(arg) ?: return null
                val a = when (val aText = match.groupValues[1]) {
                    "", "+" -> 1
                    "-" -> -1
                    else -> aText.toIntOrNull() ?: return null
                }
                val b = match.groupValues[2]
                    .takeIf { it.isNotEmpty() }
                    ?.removePrefix("+")
                    ?.let { it.toIntOrNull() ?: return null }
                    ?: 0
                a to b
            }
        }
    }

    private fun matchingParen(text: String, open: Int): Int {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }


    private fun readIdent(text: String, start: Int): Int {
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (c.isLetterOrDigit() || c == '-' || c == '_' || c.code > 127) i++ else break
        }
        return i
    }


    private fun parseAttrSelector(inner: String): AttrSelector? {
        val text = inner.trim()
        if (text.isEmpty()) return null
        val opIndex = text.indexOfFirst { it in "=~|^$*" }
        if (opIndex < 0) {
            val name = text.lowercase()
            return if (name.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == ':' }) {
                AttrSelector(name, AttrOp.EXISTS, null)
            } else {
                null
            }
        }
        val opChar = text[opIndex]
        val op: AttrOp
        val valueStart: Int
        if (opChar == '=') {
            op = AttrOp.EQUALS
            valueStart = opIndex + 1
        } else {
            if (opIndex + 1 >= text.length || text[opIndex + 1] != '=') return null
            op = when (opChar) {
                '~' -> AttrOp.INCLUDES
                '|' -> AttrOp.DASH
                '^' -> AttrOp.PREFIX
                '$' -> AttrOp.SUFFIX
                else -> AttrOp.SUBSTRING
            }
            valueStart = opIndex + 2
        }
        val name = text.substring(0, opIndex).trim().lowercase()
        if (name.isEmpty()) return null
        var value = text.substring(valueStart).trim()

        caseFlagRegex.find(value)?.let { value = it.groupValues[1].trim() }
        value = value.trim('"', '\'')
        return AttrSelector(name, op, value)
    }

    private fun parseDeclarations(
        text: String,
        stylesheet: Boolean,
    ): Map<String, Declaration> {
        val out = LinkedHashMap<String, Declaration>()
        val sourceLimit = if (stylesheet) {
            minOf(
                MAX_DECLARATIONS_PER_BLOCK,
                MAX_STYLESHEET_DECLARATIONS - stylesheetDeclarationCount,
            )
        } else {
            MAX_DECLARATIONS_PER_BLOCK
        }
        if (sourceLimit <= 0) return out

        for (declaration in declarationSlices(text, sourceLimit)) {
            if (stylesheet) stylesheetDeclarationCount++
            val colon = declaration.indexOf(':')
            if (colon <= 0) continue
            val property = declaration.substring(0, colon).trim().lowercase()
            var value = declaration.substring(colon + 1).trim()
            var important = false
            val bang = value.lastIndexOf('!')
            if (bang >= 0 && value.substring(bang + 1).trim().equals("important", true)) {
                important = true
                value = value.substring(0, bang).trim()
            }
            if (property.isNotEmpty() && value.isNotEmpty()) {
                val candidates = when (property) {
                    "background" -> {
                        if (!value.contains("var(", ignoreCase = true) &&
                            backgroundColorFromShorthand(value.lowercase()) == null
                        ) {
                            continue
                        }
                        listOf(
                            "background-color" to Declaration(
                                value = value,
                                important = important,
                                backgroundShorthand = true,
                            ),
                        )
                    }

                    "font" -> fontShorthandDeclarations(value, important) ?: continue
                    else -> listOf(property to Declaration(value, important))
                }


                val newKeys = candidates.asSequence()
                    .map { it.first }
                    .distinct()
                    .count { it !in out }
                if (out.size + newKeys > MAX_DECLARATIONS_PER_BLOCK) break
                candidates.forEach { (name, candidate) ->
                    putDeclaration(out, name, candidate)
                }
            }
        }
        return out
    }


    private fun declarationSlices(text: String, limit: Int): List<String> {
        val result = ArrayList<String>(minOf(limit, 16))
        var start = 0
        var quote: Char? = null
        var parentheses = 0
        var index = 0
        while (index <= text.length && result.size < limit) {
            val atEnd = index == text.length
            val char = if (atEnd) ';' else text[index]
            when {
                quote != null -> if (char == quote && !isEscaped(text, index)) quote = null
                char == '\'' || char == '"' -> quote = char
                char == '(' -> parentheses++
                char == ')' && parentheses > 0 -> parentheses--
                char == ';' && parentheses == 0 -> {
                    val declaration = text.substring(start, index).trim()
                    if (declaration.isNotEmpty()) result.add(declaration)
                    start = index + 1
                }
            }
            index++
        }
        return result
    }


    private fun putDeclaration(
        out: MutableMap<String, Declaration>,
        property: String,
        candidate: Declaration,
    ) {
        val previous = out[property]
        if (previous == null || candidate.important || !previous.important) {
            out.remove(property)
            out[property] = candidate
        }
    }

    private fun backgroundColorFromShorthand(value: String): String? {
        val normalized = value.trim().lowercase()
        if (normalized in CSS_WIDE_KEYWORDS) return normalized
        if (normalized == "none") return "transparent"
        if (normalized == "currentcolor" || CssColor.parse(normalized) != null) {
            return normalized
        }
        val colors = splitTopLevelWhitespace(normalized).filter { CssColor.parse(it) != null }
        return colors.singleOrNull()
    }

    private data class FontShorthandValues(
        val style: String,
        val weight: String,
        val size: String,
        val lineHeight: String,
        val family: String,
    ) {
        fun value(part: FontPart): String = when (part) {
            FontPart.STYLE -> style
            FontPart.WEIGHT -> weight
            FontPart.SIZE -> size
            FontPart.LINE_HEIGHT -> lineHeight
            FontPart.FAMILY -> family
        }
    }

    private fun fontShorthandDeclarations(
        value: String,
        important: Boolean,
    ): List<Pair<String, Declaration>>? {
        val parsed = fontShorthandValues(value)
        if (parsed != null) {
            return listOf(
                "font-style" to Declaration(parsed.style, important),
                "font-weight" to Declaration(parsed.weight, important),
                "font-size" to Declaration(parsed.size, important),
                "line-height" to Declaration(parsed.lineHeight, important),
                "font-family" to Declaration(parsed.family, important),
            )
        }
        if (!value.contains("var(", ignoreCase = true)) return null


        return listOf(
            "font-style" to FontPart.STYLE,
            "font-weight" to FontPart.WEIGHT,
            "font-size" to FontPart.SIZE,
            "line-height" to FontPart.LINE_HEIGHT,
            "font-family" to FontPart.FAMILY,
        ).map { (property, part) ->
            property to Declaration(value, important, fontPart = part)
        }
    }

    private fun fontShorthandValues(raw: String): FontShorthandValues? {
        val value = raw.trim()
        when (value.lowercase()) {
            "inherit", "unset" -> return FontShorthandValues(
                "inherit", "inherit", "inherit", "inherit", "inherit",
            )
            "initial", "revert", "revert-layer" -> return FontShorthandValues(
                "normal", "normal", "medium", "normal", "initial",
            )
            "caption", "icon", "menu", "message-box", "small-caption", "status-bar" ->
                return FontShorthandValues("normal", "normal", "medium", "normal", "sans-serif")
        }

        val tokens = splitFontTokens(value)
        if (tokens.isEmpty()) return null
        var style = "normal"
        var weight = "normal"
        var size: String? = null
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val lower = token.lowercase()
            if (fontSizeFactor(lower, 1f) != null) {
                size = token
                index++
                break
            }
            when {
                lower == "italic" || lower == "oblique" -> {
                    style = lower
                    if (lower == "oblique" && tokens.getOrNull(index + 1).isFontAngle()) index++
                }
                lower == "normal" || lower == "small-caps" -> Unit
                lower == "bold" || lower == "bolder" || lower == "lighter" ||
                    lower.toIntOrNull()?.let { it in 1..1_000 } == true -> weight = lower
                lower in FONT_STRETCH_KEYWORDS -> Unit
                else -> return null
            }
            index++
        }
        val resolvedSize = size ?: return null
        var lineHeight = "normal"
        if (tokens.getOrNull(index) == "/") {
            lineHeight = tokens.getOrNull(index + 1)?.takeIf { it != "/" } ?: return null
            index += 2
        }
        val family = tokens.drop(index).joinToString(" ").trim()
        if (family.isEmpty() || family == "/") return null
        return FontShorthandValues(style, weight, resolvedSize, lineHeight, family)
    }

    private fun String?.isFontAngle(): Boolean {
        val value = this?.lowercase() ?: return false
        return leadingNumber(value) != null &&
            (value.endsWith("deg") || value.endsWith("grad") ||
                value.endsWith("rad") || value.endsWith("turn"))
    }


    private fun splitFontTokens(value: String): List<String> {
        val result = mutableListOf<String>()
        var start = -1
        var quote: Char? = null
        var parentheses = 0
        fun flush(end: Int) {
            if (start >= 0) {
                result += value.substring(start, end)
                start = -1
            }
        }
        value.forEachIndexed { index, char ->
            when {
                quote != null -> {
                    if (char == quote && !isEscaped(value, index)) quote = null
                    if (start < 0) start = index
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    if (start < 0) start = index
                }
                char == '(' -> {
                    parentheses++
                    if (start < 0) start = index
                }
                char == ')' -> {
                    if (parentheses > 0) parentheses--
                    if (start < 0) start = index
                }
                parentheses == 0 && char == '/' -> {
                    flush(index)
                    result += "/"
                }
                parentheses == 0 && char.isWhitespace() -> flush(index)
                else -> if (start < 0) start = index
            }
        }
        flush(value.length)
        return result
    }

    private fun splitTopLevelWhitespace(value: String): List<String> {
        val result = mutableListOf<String>()
        var start = -1
        var quote: Char? = null
        var parentheses = 0
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                quote != null -> {
                    if (char == quote && !isEscaped(value, index)) quote = null
                    if (start < 0) start = index
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    if (start < 0) start = index
                }
                char == '(' -> {
                    parentheses++
                    if (start < 0) start = index
                }
                char == ')' -> {
                    if (parentheses > 0) parentheses--
                    if (start < 0) start = index
                }
                char.isWhitespace() && parentheses == 0 -> {
                    if (start >= 0) {
                        result += value.substring(start, index)
                        start = -1
                    }
                }
                else -> if (start < 0) start = index
            }
            index++
        }
        if (start >= 0) result += value.substring(start)
        return result
    }

    private fun isEscaped(value: String, index: Int): Boolean {
        var cursor = index - 1
        var backslashes = 0
        while (cursor >= 0 && value[cursor] == '\\') {
            backslashes++
            cursor--
        }
        return backslashes % 2 == 1
    }

    private fun stripComments(css: String): String =
        commentRegex.replace(css, " ")

    private fun matchingBrace(css: String, open: Int): Int {
        var depth = 0
        for (i in open until css.length) {
            when (css[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private companion object {
        const val BLACK_ARGB: Int = -0x1000000
        const val MAX_GROUP_RULE_DEPTH = 64
        const val MAX_RULES = 16_384
        const val MAX_FONT_FACES = 512
        const val MAX_STYLESHEET_DECLARATIONS = 131_072
        const val MAX_DECLARATIONS_PER_BLOCK = 512
        const val MAX_SELECTORS_PER_RULE = 256
        const val MAX_SELECTOR_CHARS = 16_384
        const val MAX_SELECTOR_COMPOUNDS = 64
        const val MAX_SIMPLE_COMPONENTS = 64
        const val MAX_UNIVERSAL_RULES = 1_024
        const val MAX_RULES_PER_INDEX_BUCKET = 4_096
        const val MAX_SELECTOR_MATCH_OPERATIONS = 5_000_000
        val commentRegex = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val whitespaceRegex = Regex("""\s+""")


        val numberRegex = Regex("""-?(?:\d+(?:\.\d+)?|\.\d+)""")
        val lineHeightLengthUnitRegex = Regex(
            """(?i)(?:%|(?<=[0-9.])(?:rem|em|px|pt|pc|in|cm|mm|q|ch|ex|vw|vh|vmin|vmax)(?=[\s),*/+\-]|$))""",
        )
        val FONT_STRETCH_KEYWORDS = setOf(
            "ultra-condensed", "extra-condensed", "condensed", "semi-condensed",
            "semi-expanded", "expanded", "extra-expanded", "ultra-expanded",
        )
        val CSS_WIDE_KEYWORDS = setOf(
            "inherit", "initial", "unset", "revert", "revert-layer",
        )
        val BORDER_STYLE_KEYWORDS = setOf(
            "none", "hidden", "solid", "dashed", "dotted", "double",
        )
        val INLINE_REPLACED_TAGS = setOf("img", "image", "svg")
        val BLOCK_LEVEL_DISPLAY_VALUES = setOf(
            "block", "flow-root", "flex", "grid", "list-item", "table",
        )
        val fontUrlRegex = Regex("""url\(\s*['"]?([^'")]+)['"]?\s*\)""")
        val caseFlagRegex = Regex("""^(['"].*['"])\s+[iIsS]$""")
        val nthRegex = Regex("""^([+-]?\d*)n([+-]\d+)?$""")
    }
}
