package com.example.mobilereader.data

import android.content.Context
import android.net.Uri
import com.example.mobilereader.data.model.Book
import com.example.mobilereader.data.model.BookContent
import com.example.mobilereader.data.model.BookFormat
import com.example.mobilereader.data.model.Bookmark
import com.example.mobilereader.data.model.Quote
import com.example.mobilereader.data.model.ReadingProgress
import com.example.mobilereader.data.model.ReadingStatus
import com.example.mobilereader.data.parser.EpubParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BookRepository(private val context: Context) {
    private val storedBooksFile = File(context.filesDir, "books.json")
    private val storedBooksDirectory = File(context.filesDir, "books")
    private val storedPicturesDirectory = File(context.filesDir, "pictures")
    private val json = Json { ignoreUnknownKeys = true }
    private val writingLock = Mutex()
    private val mutableBooks = MutableStateFlow(readStoredBooks())
    val books: StateFlow<List<Book>> = mutableBooks

    fun bookById(bookId: String): Book? = mutableBooks.value.firstOrNull { it.id == bookId }

    suspend fun importEpub(selectedUri: Uri): Book = withContext(Dispatchers.IO) {
        storedBooksDirectory.mkdirs()
        val bookId = UUID.randomUUID().toString()
        val temporaryBookFile = File(storedBooksDirectory, "$bookId.incoming")
        val finalBookFile = File(storedBooksDirectory, "$bookId.epub")
        try {
            context.contentResolver.openInputStream(selectedUri).use { selectedStream ->
                requireNotNull(selectedStream) { "Cannot open selected EPUB" }
                temporaryBookFile.outputStream().use { storedStream ->
                    val copyBuffer = ByteArray(16 * 1024)
                    var totalCopiedBytes = 0L
                    while (true) {
                        val readBytes = selectedStream.read(copyBuffer)
                        if (readBytes < 0) break
                        totalCopiedBytes += readBytes
                        require(totalCopiedBytes <= 768L * 1024 * 1024) { "Selected EPUB is too large" }
                        storedStream.write(copyBuffer, 0, readBytes)
                    }
                }
            }
            val metadata = EpubParser.parseMetadata(temporaryBookFile)
            val temporaryPictures = File(context.cacheDir, "import-check-$bookId")
            try {
                EpubParser.parseContent(temporaryBookFile, temporaryPictures)
            } finally {
                temporaryPictures.deleteRecursively()
            }
            require(temporaryBookFile.renameTo(finalBookFile)) { "Cannot store selected EPUB" }
            val coverFileName = metadata.coverBytes?.let { coverBytes ->
                storedPicturesDirectory.mkdirs()
                val coverFile = File(storedPicturesDirectory, "$bookId.cover")
                coverFile.writeBytes(coverBytes)
                coverFile.name
            }
            val newBook = Book(
                id = bookId,
                title = metadata.title?.takeIf { it.isNotBlank() } ?: "Untitled EPUB",
                author = metadata.author,
                format = BookFormat.EPUB,
                fileName = finalBookFile.name,
                coverFileName = coverFileName,
                addedAtMillis = System.currentTimeMillis(),
                sizeBytes = finalBookFile.length(),
                genres = metadata.genres,
                series = metadata.series,
                seriesNumber = metadata.seriesNumber,
                publisher = metadata.publisher,
                year = metadata.year,
                isbn = metadata.isbn,
                translators = metadata.translators,
                description = metadata.description,
                language = metadata.language,
            )
            changeBookList { previousBooks -> previousBooks + newBook }
            newBook
        } catch (problem: Exception) {
            temporaryBookFile.delete()
            finalBookFile.delete()
            throw problem
        }
    }

    suspend fun loadContent(book: Book): BookContent = withContext(Dispatchers.IO) {
        val bookFile = File(storedBooksDirectory, book.fileName ?: "${book.id}.epub")
        EpubParser.parseContent(bookFile, File(storedPicturesDirectory, book.id))
    }

    fun coverFileFor(book: Book): File? = book.coverFileName?.let { coverName ->
        File(storedPicturesDirectory, coverName).takeIf { it.isFile }
    }

    fun noteOpened(bookId: String, settings: suspend () -> ReaderSettings) {
        val openingTime = System.currentTimeMillis()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val initialSettings = settings()
            changeBook(bookId) { oldBook ->
                oldBook.copy(
                    lastOpenedAtMillis = openingTime,
                    startedAtMillis = oldBook.startedAtMillis ?: openingTime,
                    status = ReadingStatus.READING,
                    readerSettings = oldBook.readerSettings ?: initialSettings,
                )
            }
        }
    }

    suspend fun saveReaderSettings(bookId: String, readerSettings: ReaderSettings) =
        changeBook(bookId) { it.copy(readerSettings = readerSettings) }

    suspend fun saveProgress(bookId: String, readingProgress: ReadingProgress) =
        changeBook(bookId) { it.copy(progress = readingProgress) }

    suspend fun markFinished(bookId: String) = changeBook(bookId) { oldBook ->
        if (oldBook.status == ReadingStatus.FINISHED) oldBook else oldBook.copy(
            status = ReadingStatus.FINISHED,
            finishedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun addReadingSeconds(bookId: String, elapsedSeconds: Long) =
        changeBook(bookId) { it.copy(readingSeconds = it.readingSeconds + elapsedSeconds) }

    suspend fun toggleBookmark(bookId: String, bookmark: Bookmark) = changeBook(bookId) { oldBook ->
        oldBook.copy(bookmarks = oldBook.bookmarks + bookmark.copy(bookId = bookId))
    }

    suspend fun removeBookmark(bookId: String, bookmarkId: String) = changeBook(bookId) { oldBook ->
        oldBook.copy(bookmarks = oldBook.bookmarks.filterNot { it.id == bookmarkId })
    }

    suspend fun addQuote(bookId: String, quote: Quote) = changeBook(bookId) { oldBook ->
        oldBook.copy(quotes = oldBook.quotes + quote.copy(bookId = bookId))
    }

    suspend fun removeQuote(bookId: String, quoteId: String) = changeBook(bookId) { oldBook ->
        oldBook.copy(quotes = oldBook.quotes.filterNot { it.id == quoteId })
    }

    private suspend fun changeBook(bookId: String, update: (Book) -> Book) = changeBookList { oldBooks ->
        oldBooks.map { oldBook -> if (oldBook.id == bookId) update(oldBook) else oldBook }
    }

    private suspend fun changeBookList(update: (List<Book>) -> List<Book>) = withContext(Dispatchers.IO) {
        writingLock.withLock {
            val newBooks = update(mutableBooks.value)
            val temporaryFile = File(context.filesDir, "books.json.pending")
            temporaryFile.writeText(json.encodeToString(newBooks))
            Files.move(
                temporaryFile.toPath(),
                storedBooksFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            mutableBooks.value = newBooks
        }
    }

    private fun readStoredBooks(): List<Book> = runCatching {
        if (storedBooksFile.isFile) json.decodeFromString<List<Book>>(storedBooksFile.readText()) else emptyList()
    }.getOrDefault(emptyList())
}
