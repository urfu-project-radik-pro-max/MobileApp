package com.example.mobilereader.data.parser


private val partKeyword = Regex(
    """^((?:часть|частина|книга|том|part|book|volume)\s+[0-9ivxlcdmа-яіїєґ]+[.:)]?)(?=\s|$)""",
    RegexOption.IGNORE_CASE,
)

private val firstSentence = Regex("""^(.+?[.!?…])\s+\S""")


internal fun partDesignator(title: String): String {
    val line = title.substringBefore('\n').trim()
    partKeyword.find(line)?.let { return it.groupValues[1].trimEnd('.', ':', ')').trim() }
    firstSentence.find(line)?.let { return it.groupValues[1].trimEnd('.').trim() }
    return line
}


internal fun composeNestedChapterTitle(partTitle: String?, chapterTitle: String): String {
    val head = partTitle?.let(::partDesignator)?.takeIf { it.isNotBlank() }
        ?: return chapterTitle
    val firstLine = chapterTitle.substringBefore('\n').trim()
    val rest = chapterTitle.substringAfter('\n', "").replace('\n', ' ').trim()
    return if (rest.isEmpty()) "$head $firstLine" else "$head $firstLine\n$rest"
}
