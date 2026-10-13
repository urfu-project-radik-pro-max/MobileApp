package com.example.mobilereader.data.model


class PublisherStyle(

    val justify: Boolean? = null,

    val lineHeight: Float? = null,
    val hyphenation: Boolean? = null,

    val dropCaps: Boolean = false,

    val fontName: String? = null,

    val fontPath: String? = null,

    val fontCss: String? = null,


    val embeddedFonts: List<EmbeddedFont> = emptyList(),
) {
    val isEmpty: Boolean
        get() = justify == null && lineHeight == null && hyphenation == null &&
                !dropCaps && fontName == null && embeddedFonts.isEmpty()
}


class EmbeddedFont(val name: String, val path: String)


fun publisherStyleOf(content: BookContent): PublisherStyle? {
    val justifyVotes = mutableMapOf<Boolean, Int>()
    val lineHeightVotes = mutableMapOf<Float, Int>()
    val hyphenVotes = mutableMapOf<Boolean, Int>()
    val fontVotes = mutableMapOf<String, Int>()
    var dropCaps = false

    val families = content.fonts.groupBy { it.family }

    for (chapter in content.chapters) {
        for (element in chapter.elements) {
            val paragraph = element as? ContentElement.Paragraph ?: continue
            if (paragraph.style != ParagraphStyle.NORMAL) continue
            val block = paragraph.block ?: continue


            when (block.align) {
                BlockAlign.JUSTIFY -> justifyVotes.merge(true, 1, Int::plus)
                BlockAlign.START -> justifyVotes.merge(false, 1, Int::plus)
                else -> Unit
            }
            block.lineHeightMult?.let { lineHeightVotes.merge(it, 1, Int::plus) }
            block.hyphens?.let { hyphenVotes.merge(it, 1, Int::plus) }
            block.fontFamily?.let { fontVotes.merge(it, 1, Int::plus) }
            if (block.firstLetter?.isDropCap == true) dropCaps = true
        }
    }


    val font = fontVotes.maxByOrNull { it.value }?.key


    val embedded = font?.let { family ->
        families[family]?.let { faces ->
            faces.firstOrNull { !it.bold && !it.italic }
                ?: faces.firstOrNull { !it.bold }
                ?: faces.first()
        }
    }
    val genericName = when (font) {
        "serif" -> "Serif"
        "sans-serif" -> "Sans"
        "monospace" -> "Monospace"
        "cursive" -> "Cursive"
        else -> null
    }

    val style = PublisherStyle(
        justify = justifyVotes.maxByOrNull { it.value }?.key,
        lineHeight = lineHeightVotes.maxByOrNull { it.value }?.key,
        hyphenation = hyphenVotes.maxByOrNull { it.value }?.key,
        dropCaps = dropCaps,
        fontName = when {
            embedded != null -> font.replaceFirstChar { it.uppercase() }
            else -> genericName
        },
        fontPath = embedded?.path,
        fontCss = font?.takeIf { embedded != null || genericName != null },
        embeddedFonts = families.entries
            .sortedBy { it.key }
            .mapNotNull { (family, faces) ->
                val face = faces.firstOrNull { !it.bold && !it.italic }
                    ?: faces.firstOrNull { !it.bold }
                    ?: faces.firstOrNull()
                face?.let {
                    EmbeddedFont(family.replaceFirstChar { c -> c.uppercase() }, it.path)
                }
            },
    )
    return style.takeIf { !it.isEmpty }
}
