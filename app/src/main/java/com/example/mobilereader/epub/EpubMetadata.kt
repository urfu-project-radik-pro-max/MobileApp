package com.example.mobilereader.epub

data class EpubMetadata(
    val title: String?,
    val author: String?,
    val allAuthors: List<String>,
    val language: String?,
    val description: String?,
    val coverBytes: ByteArray?,
    val genres: List<String>,
    val publisher: String?,
    val publicationYear: String?,
    val isbn: String?,
    val series: String?,
    val seriesNumber: Float?,
    val translators: List<String>,
)
