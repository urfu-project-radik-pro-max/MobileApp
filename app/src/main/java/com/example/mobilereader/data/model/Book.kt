package com.example.mobilereader.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class BookFormat {
    EPUB, FB2, MOBI, TXT, MD
}


@Serializable
data class ReadingProgress(
    val chapterIndex: Int = 0,
    val elementIndex: Int = 0,
    val scrollOffset: Int = 0,
    val fraction: Float = 0f,


    val pagesLeftInChapter: Int = -1,

    val totalPages: Int = 0,
)


interface Timestamped<T> {
    val updatedAtMillis: Long
    fun withUpdatedAt(millis: Long): T
}


@Serializable
data class Bookmark(
    val id: String,
    val flatIndex: Int,
    val chapterIndex: Int,
    val preview: String,
    val createdAtMillis: Long,


    val orphaned: Boolean = false,

    val bookId: String = "",
    override val updatedAtMillis: Long = 0L,
) : Timestamped<Bookmark> {
    override fun withUpdatedAt(millis: Long) = copy(updatedAtMillis = millis)
}


@Serializable
data class Quote(
    val id: String,
    val text: String,
    val chapterIndex: Int,
    val createdAtMillis: Long,


    val startItem: Int = -1,
    val startChar: Int = -1,
    val endItem: Int = -1,
    val endChar: Int = -1,

    val note: String? = null,

    val bookId: String = "",
    override val updatedAtMillis: Long = 0L,
) : Timestamped<Quote> {
    override fun withUpdatedAt(millis: Long) = copy(updatedAtMillis = millis)
}


@Serializable
enum class ReadingStatus { NONE, WANT_TO_READ, READING, FINISHED, ABANDONED }


@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String? = null,
    val format: BookFormat,


    val fileName: String? = null,

    val coverFileName: String? = null,
    val addedAtMillis: Long,


    val contentHash: String? = null,

    val sizeBytes: Long = 0,
    val lastOpenedAtMillis: Long? = null,
    val progress: ReadingProgress = ReadingProgress(),
    val bookmarks: List<Bookmark> = emptyList(),
    val quotes: List<Quote> = emptyList(),

    val startedAtMillis: Long? = null,

    val finishedAtMillis: Long? = null,

    val readingSeconds: Long = 0,
    val status: ReadingStatus = ReadingStatus.NONE,


    val readerSettings: com.example.mobilereader.data.ReaderSettings? = null,

    val genres: List<String> = emptyList(),
    val series: String? = null,
    val seriesNumber: Float? = null,
    val publisher: String? = null,
    val year: String? = null,
    val isbn: String? = null,
    val translators: List<String> = emptyList(),
    val description: String? = null,
    val language: String? = null,
)


@Serializable
data class Shelf(
    val id: String,

    val name: String = "",


    val bookIds: List<String> = emptyList(),
    val createdAtMillis: Long,


    val sortKey: Long = 0L,
    override val updatedAtMillis: Long = 0L,
) : Timestamped<Shelf> {
    override fun withUpdatedAt(millis: Long) = copy(updatedAtMillis = millis)
}


fun bookOrderKey(bookId: String): String = "b:$bookId"

fun shelfOrderKey(shelfId: String): String = "s:$shelfId"


val Book.sortTs: Long get() = lastOpenedAtMillis ?: addedAtMillis


val Shelf.sortTs: Long get() = if (sortKey != 0L) sortKey else createdAtMillis
