package com.example.mobilereader.epub

import androidx.compose.ui.text.AnnotatedString

data class EpubBookContent(
    val chapters: List<EpubChapter>,
)

data class EpubChapter(
    val title: String?,
    val elements: List<EpubContentElement>,
)

sealed interface EpubContentElement {
    data class Paragraph(val text: AnnotatedString) : EpubContentElement
    data class Heading(val text: AnnotatedString, val level: Int) : EpubContentElement
}
