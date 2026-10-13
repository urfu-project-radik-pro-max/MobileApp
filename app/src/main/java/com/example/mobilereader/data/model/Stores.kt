package com.example.mobilereader.data.model

import kotlinx.serialization.Serializable


@Serializable
data class BookRecord(
    val id: String,
    val title: String,
    val author: String? = null,
    val format: BookFormat,

    val fileName: String? = null,

    val coverFileName: String? = null,
    val addedAtMillis: Long,


    val contentHash: String? = null,

    val sizeBytes: Long = 0,

    val genres: List<String> = emptyList(),
    val series: String? = null,
    val seriesNumber: Float? = null,
    val publisher: String? = null,
    val year: String? = null,
    val isbn: String? = null,
    val translators: List<String> = emptyList(),
    val description: String? = null,
    val language: String? = null,
    override val updatedAtMillis: Long = 0L,
) : Timestamped<BookRecord> {
    override fun withUpdatedAt(millis: Long) = copy(updatedAtMillis = millis)
}


@Serializable
data class UserBookData(

    val startedAtMillis: Long? = null,

    val finishedAtMillis: Long? = null,
    val status: ReadingStatus = ReadingStatus.NONE,


    val readerSettings: com.example.mobilereader.data.ReaderSettings? = null,
    override val updatedAtMillis: Long = 0L,
) : Timestamped<UserBookData> {
    override fun withUpdatedAt(millis: Long) = copy(updatedAtMillis = millis)


    val isEmpty: Boolean
        get() = startedAtMillis == null && finishedAtMillis == null &&
                status == ReadingStatus.NONE &&
                readerSettings == null
}


@Serializable
data class BookProgress(
    val position: ReadingProgress = ReadingProgress(),
    val lastOpenedAtMillis: Long? = null,

    val readingSeconds: Long = 0,
    override val updatedAtMillis: Long = 0L,
) : Timestamped<BookProgress> {
    override fun withUpdatedAt(millis: Long) = copy(updatedAtMillis = millis)

    val isEmpty: Boolean
        get() = position == ReadingProgress() && lastOpenedAtMillis == null && readingSeconds == 0L
}

@Serializable
data class LibraryIndex(
    val books: List<BookRecord> = emptyList(),


    val shelves: List<Shelf> = emptyList(),
)


@Serializable
data class UserDataStore(
    val userData: Map<String, UserBookData> = emptyMap(),

    val quotes: Map<String, Quote> = emptyMap(),

    val bookmarks: Map<String, Bookmark> = emptyMap(),


    val deletedIds: Map<String, Long> = emptyMap(),
)

@Serializable
data class ProgressStore(
    val progress: Map<String, BookProgress> = emptyMap(),
)


fun BookRecord.withUserData(
    user: UserBookData?,
    prog: BookProgress?,
    quotes: List<Quote> = emptyList(),
    bookmarks: List<Bookmark> = emptyList(),
): Book = Book(
    id = id,
    title = title,
    author = author,
    format = format,
    fileName = fileName,
    coverFileName = coverFileName,
    addedAtMillis = addedAtMillis,
    contentHash = contentHash,
    sizeBytes = sizeBytes,
    lastOpenedAtMillis = prog?.lastOpenedAtMillis,
    progress = prog?.position ?: ReadingProgress(),
    bookmarks = bookmarks,
    quotes = quotes,
    startedAtMillis = user?.startedAtMillis,
    finishedAtMillis = user?.finishedAtMillis,
    readingSeconds = prog?.readingSeconds ?: 0L,
    status = user?.status ?: ReadingStatus.NONE,
    readerSettings = user?.readerSettings,
    genres = genres,
    series = series,
    seriesNumber = seriesNumber,
    publisher = publisher,
    year = year,
    isbn = isbn,
    translators = translators,
    description = description,
    language = language,
)


fun Book.toRecord(): BookRecord = BookRecord(
    id = id,
    title = title,
    author = author,
    format = format,
    fileName = fileName,
    coverFileName = coverFileName,
    addedAtMillis = addedAtMillis,
    contentHash = contentHash,
    sizeBytes = sizeBytes,
    genres = genres,
    series = series,
    seriesNumber = seriesNumber,
    publisher = publisher,
    year = year,
    isbn = isbn,
    translators = translators,
    description = description,
    language = language,
)


fun Book.toUserData(): UserBookData = UserBookData(
    startedAtMillis = startedAtMillis,
    finishedAtMillis = finishedAtMillis,
    status = status,
    readerSettings = readerSettings,
)


fun Book.toProgress(): BookProgress = BookProgress(
    position = progress,
    lastOpenedAtMillis = lastOpenedAtMillis,
    readingSeconds = readingSeconds,
)
