package com.example.mobilereader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mobilereader.MobileReaderApp
import com.example.mobilereader.data.AppSettings
import com.example.mobilereader.data.BookRepository
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.SettingsRepository
import com.example.mobilereader.data.model.Book
import com.example.mobilereader.data.model.BookContent
import com.example.mobilereader.data.model.Bookmark
import com.example.mobilereader.data.model.BookNavigationTarget
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.LinkedDocumentTarget
import com.example.mobilereader.data.model.NoteDocument
import com.example.mobilereader.data.model.PageProgression
import com.example.mobilereader.data.model.previewText
import com.example.mobilereader.data.model.Quote
import com.example.mobilereader.data.model.ReadingProgress
import com.example.mobilereader.data.model.withFootnoteRefStyle
import com.example.mobilereader.data.model.withoutFootnotes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.mobilereader.ui.reader.selection.BookSelection
import java.io.File
import java.util.UUID


class ReaderItem(
    val chapterIndex: Int,
    val element: ContentElement,

    val publisherBoxes: List<ReaderPublisherBox> = emptyList(),

    val publisherFloat: ReaderPublisherFloat? = null,

    val suppressedByPublisherFloat: Boolean = false,
    val publisherClearBefore: com.example.mobilereader.data.model.PublisherClear =
        com.example.mobilereader.data.model.PublisherClear.NONE,
)


class ReaderLinkedDocument(
    val id: String,
    val title: String?,
    val items: List<ReaderItem>,
)


sealed interface ReaderNavigationTarget {
    data class ReadingOrder(
        val flatItemIndex: Int,
        val chapterIndex: Int,
    ) : ReaderNavigationTarget

    data class Linked(val target: LinkedDocumentTarget) : ReaderNavigationTarget
}

class ReaderNavigationEntry(
    val title: String?,
    val depth: Int,
    val target: ReaderNavigationTarget,
)

sealed interface ReaderState {
    data object Loading : ReaderState
    data object Error : ReaderState

    class Ready(
        val book: Book,
        val items: List<ReaderItem>,

        val chapterStarts: List<Int>,
        val chapterTitles: List<String?>,

        val chapterDepths: List<Int>,

        val notes: Map<String, NoteDocument>,

        val linkTargets: Map<String, Int> = emptyMap(),

        val linkedDocuments: Map<String, ReaderLinkedDocument> = emptyMap(),

        val linkedDocumentTargets: Map<String, LinkedDocumentTarget> = emptyMap(),

        val navigation: List<ReaderNavigationEntry> = emptyList(),

        val publisherStyle: com.example.mobilereader.data.model.PublisherStyle? = null,

        val bookFonts: Map<String, androidx.compose.ui.text.font.FontFamily> = emptyMap(),

        val embeddedFontSignature: String = embeddedFontSignature(emptyList()),

        val language: String? = null,

        val pageProgression: PageProgression = PageProgression.LTR,
    ) : ReaderState {
        fun chapterAt(flatIndex: Int): Int =
            chapterStarts.indexOfLast { it <= flatIndex }.coerceAtLeast(0)
    }
}

class ReaderViewModel(
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val bookId: String,
    private val paginationCacheDir: File,
) : ViewModel() {


    val book: StateFlow<Book?> = repository.books
        .map { books -> books.firstOrNull { it.id == bookId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.bookById(bookId))


    val settings: StateFlow<ReaderSettings> =
        kotlinx.coroutines.flow.combine(
            settingsRepository.settings, book, settingsRepository.appSettings,
        ) { global, current, app ->
            (current?.readerSettings ?: global).copy(centerHeadings = app.centerHeadings)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    val appSettings: StateFlow<AppSettings> = settingsRepository.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state = _state.asStateFlow()


    class PaginationHolder(
        val key: String,
        val pages: List<BookPage>,
        val settings: ReaderSettings,
        val partial: Boolean = false,
    )

    private val _pagination = MutableStateFlow<PaginationHolder?>(null)
    val pagination = _pagination.asStateFlow()
    private var paginationJob: Job? = null
    private var paginationKeyInFlight: String? = null


    var currentFlatIndex: Int = 0
        private set


    var currentCharOffset: Int = 0
        private set


    var currentScrollOffset: Int = 0
        private set

    private val navigationSession = ReaderNavigationSession(
        viewModelScope, android.os.SystemClock::elapsedRealtime,
    )
    private var returnTimeoutMillis = ReaderNavigationSession.DEFAULT_TIMEOUT_MILLIS
    private var expirePageReturns = AppSettings().autoHideReturnButton

    val navigationReturnLocation = navigationSession.returnLocation

    fun rememberNavigationOrigin(location: ReaderReturnLocation, expires: Boolean = false) {
        navigationSession.remember(location, expires)
    }

    fun takeNavigationOrigin(): ReaderReturnLocation? = navigationSession.take()

    fun clearNavigationHistory() = navigationSession.clear()

    fun setReturnTimeoutMillis(timeoutMillis: Long) {
        returnTimeoutMillis = timeoutMillis
        navigationSession.configureExpiry(returnTimeoutMillis.takeIf { expirePageReturns })
    }


    fun observeVisiblePosition(flatIndex: Int, scrollOffset: Int, charOffset: Int = 0) {
        currentFlatIndex = flatIndex
        currentScrollOffset = scrollOffset
        currentCharOffset = charOffset
    }


    fun ensurePagination(
        key: String,
        settings: ReaderSettings,
        items: List<ReaderItem>,
        quick: (suspend () -> List<BookPage>)? = null,
        full: suspend () -> List<BookPage>,
    ) {
        if (paginationKeyInFlight == key) return


        paginationJob?.cancel()
        paginationKeyInFlight = null
        if (_pagination.value?.key == key && _pagination.value?.partial == false) return
        paginationKeyInFlight = key
        paginationJob = viewModelScope.launch(Dispatchers.Default) {


            val cacheFile = PaginationCache.fileFor(paginationCacheDir, bookId)
            val cached = PaginationCache.load(cacheFile, key, items)
            if (!cached.isNullOrEmpty()) {
                withContext(Dispatchers.Main.immediate) {
                    _pagination.value = PaginationHolder(key, cached, settings)
                    paginationKeyInFlight = null
                }
                return@launch
            }
            if (quick != null && _pagination.value?.key != key) {
                val quickPages = quick()
                withContext(Dispatchers.Main.immediate) {
                    _pagination.value = PaginationHolder(key, quickPages, settings, partial = true)
                }
            }
            val pages = full()


            withContext(Dispatchers.Main.immediate) {
                _pagination.value = PaginationHolder(key, pages, settings)
                paginationKeyInFlight = null
            }
            PaginationCache.save(cacheFile, key, items, pages)
        }
    }


    private val _searchResults = MutableStateFlow<SearchResults?>(null)
    val searchResults = _searchResults.asStateFlow()
    private var searchJob: Job? = null


    fun search(rawQuery: String) {
        val query = rawQuery.trim()
        searchJob?.cancel()
        val ready = _state.value as? ReaderState.Ready
        if (ready == null || query.length < 2) {
            _searchResults.value = null
            return
        }
        if (_searchResults.value?.query == query) return
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val results = searchBook(ready.items, query)
            withContext(Dispatchers.Main.immediate) { _searchResults.value = results }
        }
    }

    private var loadedBook: Book? = null
    private var loadedContent: BookContent? = null

    fun load() {
        viewModelScope.launch {
            _state.value = ReaderState.Loading
            val book = repository.bookById(bookId)
            if (book == null) {
                _state.value = ReaderState.Error
                return@launch
            }
            currentFlatIndex = book.progress.elementIndex
            currentScrollOffset = book.progress.scrollOffset
            clearNavigationHistory()
            runCatching { repository.loadContent(book) }
                .onSuccess { content ->
                    loadedBook = book
                    loadedContent = content
                    rebuildReady()


                    repository.noteOpened(bookId) { settingsRepository.settings.first() }
                }
                .onFailure { _state.value = ReaderState.Error }
        }
    }


    private suspend fun rebuildReady() {
        val book = loadedBook ?: return
        val content = loadedContent ?: return
        val hideFootnotes = settings.value.hideFootnotes
        withContext(Dispatchers.Main.immediate) {

            searchJob?.cancel()
            _searchResults.value = null


            clearNavigationHistory()
        }
        val next = withContext(Dispatchers.Default) { buildReady(book, content, hideFootnotes) }
        withContext(Dispatchers.Main.immediate) { _state.value = next }
    }

    private fun buildReady(
        book: Book,
        content: BookContent,
        hideFootnotes: Boolean,
    ): ReaderState {


        if (!content.hasSequentialContent()) return ReaderState.Error

        val items = mutableListOf<ReaderItem>()


        repository.coverFileFor(book)?.let { cover: File ->
            items += ReaderItem(0, ContentElement.Image(cover.absolutePath))
        }

        val chapterStarts = mutableListOf<Int>()
        val chapterTitles = mutableListOf<String?>()
        val chapterDepths = mutableListOf<Int>()
        fun adjustedText(text: androidx.compose.ui.text.AnnotatedString) =
            if (hideFootnotes) text.withoutFootnotes() else text.withFootnoteRefStyle()
        fun adjusted(element: ContentElement): ContentElement = when (element) {
            is ContentElement.Paragraph -> element.copy(text = adjustedText(element.text))
            is ContentElement.Heading -> element.copy(
                styledText = adjustedText(element.styledText),
            )
            is ContentElement.Table -> element.copy(
                rows = element.rows.map { row ->
                    com.example.mobilereader.data.model.TableRow(
                        cells = row.cells.map { cell ->
                            com.example.mobilereader.data.model.TableCell(
                                text = adjustedText(cell.text),
                                colSpan = cell.colSpan,
                                rowSpan = cell.rowSpan,
                                align = cell.align,
                                header = cell.header,
                                block = cell.block,
                                publisherBox = cell.publisherBox,
                                publisherPaddingSpecified = cell.publisherPaddingSpecified,
                                publisherBorderSpecified = cell.publisherBorderSpecified,
                            )
                        },
                        isHeader = row.isHeader,
                    )
                },
            )
            else -> element
        }
        content.chapters.forEachIndexed { index, chapter ->
            chapterStarts += items.size
            chapterTitles += chapter.title
            chapterDepths += chapter.depth
            val adjustedElements = chapter.elements.map(::adjusted)
            val plan = planPublisherChapter(adjustedElements, chapter.publisherBoxes)
            val itemOffset = items.size
            adjustedElements.forEachIndexed { elementIndex, element ->
                val plannedFloat = plan.floatByTarget[elementIndex]
                items += ReaderItem(
                    chapterIndex = index,
                    element = element,
                    publisherBoxes = plan.boxesByElement[elementIndex],
                    publisherFloat = plannedFloat?.let { float ->
                        ReaderPublisherFloat(
                            side = float.side,
                            style = float.style,
                            contents = float.contents.map { content ->
                                ReaderPublisherFloatContent(
                                    sourceItemIndex = itemOffset + content.sourceElementIndex,
                                    element = content.element,
                                    publisherBoxes = content.publisherBoxes,
                                )
                            },
                        )
                    },
                    suppressedByPublisherFloat = elementIndex in plan.suppressedElements,
                    publisherClearBefore = plan.clearBefore[elementIndex],
                )
            }
        }

        val linkedDocuments = content.linkedDocuments.mapValues { (_, document) ->
            val adjustedElements = document.elements.map(::adjusted)
            val plan = planPublisherChapter(adjustedElements, document.publisherBoxes)
            ReaderLinkedDocument(
                id = document.id,
                title = document.title,
                items = adjustedElements.mapIndexed { elementIndex, element ->
                    val plannedFloat = plan.floatByTarget[elementIndex]
                    ReaderItem(
                        chapterIndex = 0,
                        element = element,
                        publisherBoxes = plan.boxesByElement[elementIndex],
                        publisherFloat = plannedFloat?.let { float ->
                            ReaderPublisherFloat(
                                side = float.side,
                                style = float.style,
                                contents = float.contents.map { content ->
                                    ReaderPublisherFloatContent(
                                        sourceItemIndex = content.sourceElementIndex,
                                        element = content.element,
                                        publisherBoxes = content.publisherBoxes,
                                    )
                                },
                            )
                        },
                        suppressedByPublisherFloat = elementIndex in plan.suppressedElements,
                        publisherClearBefore = plan.clearBefore[elementIndex],
                    )
                },
            )
        }
        val navigation = content.navigation.mapNotNull { entry ->
            val target = when (val source = entry.target) {
                is BookNavigationTarget.ReadingOrder -> {
                    val start = chapterStarts.getOrNull(source.chapterIndex)
                        ?: return@mapNotNull null
                    ReaderNavigationTarget.ReadingOrder(
                        flatItemIndex = (start + source.elementIndex)
                            .coerceIn(0, items.lastIndex.coerceAtLeast(0)),
                        chapterIndex = source.chapterIndex,
                    )
                }

                is BookNavigationTarget.Linked -> {
                    val document = linkedDocuments[source.documentId]
                        ?: return@mapNotNull null
                    if (source.elementIndex !in document.items.indices) return@mapNotNull null
                    ReaderNavigationTarget.Linked(
                        LinkedDocumentTarget(source.documentId, source.elementIndex),
                    )
                }
            }
            ReaderNavigationEntry(entry.title, entry.depth, target)
        }.ifEmpty {


            chapterTitles.mapIndexed { index, title ->
                ReaderNavigationEntry(
                    title = title,
                    depth = chapterDepths[index],
                    target = ReaderNavigationTarget.ReadingOrder(
                        flatItemIndex = chapterStarts[index],
                        chapterIndex = index,
                    ),
                )
            }
        }

        if (items.isEmpty()) {
            return ReaderState.Error
        } else {
            return ReaderState.Ready(
                book = book,
                items = items,
                chapterStarts = chapterStarts,
                chapterTitles = chapterTitles,
                chapterDepths = chapterDepths,
                notes = if (hideFootnotes) {
                    emptyMap()
                } else {
                    content.notes.mapValues { (_, note) ->
                        NoteDocument(
                            elements = note.elements.map(::adjusted),
                            publisherBoxes = note.publisherBoxes,
                        )
                    }
                },

                linkTargets = content.linkTargets.mapNotNull { (key, target) ->
                    val (chapter, element) = target
                    chapterStarts.getOrNull(chapter)?.let { start ->
                        key to (start + element).coerceIn(0, items.lastIndex.coerceAtLeast(0))
                    }
                }.toMap(),
                linkedDocuments = linkedDocuments,
                linkedDocumentTargets = content.linkedDocumentTargets.filterValues { target ->
                    val document = linkedDocuments[target.documentId]
                    document != null && target.elementIndex in document.items.indices
                },
                navigation = navigation,
                bookFonts = loadBookFonts(content),
                embeddedFontSignature = embeddedFontSignature(content.fonts),
                language = content.language,
                pageProgression = ReaderProgression.resolve(
                    content.pageProgression,
                    content.language,
                ),
                publisherStyle = com.example.mobilereader.data.model.publisherStyleOf(content),
            )
        }
    }


    private fun loadBookFonts(
        content: BookContent,
    ): Map<String, androidx.compose.ui.text.font.FontFamily> =
        content.fonts
            .filter { File(it.path).exists() }
            .groupBy { it.family }
            .mapNotNull { (family, faces) ->
                runCatching {
                    family to androidx.compose.ui.text.font.FontFamily(
                        faces.map { face ->
                            androidx.compose.ui.text.font.Font(
                                file = File(face.path),
                                weight = if (face.bold) {
                                    androidx.compose.ui.text.font.FontWeight.Bold
                                } else {
                                    androidx.compose.ui.text.font.FontWeight.Normal
                                },
                                style = if (face.italic) {
                                    androidx.compose.ui.text.font.FontStyle.Italic
                                } else {
                                    androidx.compose.ui.text.font.FontStyle.Normal
                                },
                            )
                        },
                    )
                }.getOrNull()
            }
            .toMap()

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch {

            val updated = transform(settings.value).copy(centerHeadings = false)


            repository.saveReaderSettings(bookId, updated)
            settingsRepository.update { updated }
        }
    }

    fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.updateApp(transform) }
    }

    suspend fun setDefaultTranslator(component: String) {
        settingsRepository.updateApp { it.copy(defaultTranslator = component) }
    }

    fun saveProgress(firstVisibleIndex: Int, scrollOffset: Int, charOffset: Int = 0) {
        val ready = _state.value as? ReaderState.Ready ?: return
        observeVisiblePosition(firstVisibleIndex, scrollOffset, charOffset)
        val fraction = if (ready.items.size <= 1) {
            0f
        } else {
            (firstVisibleIndex.toFloat() / (ready.items.size - 1)).coerceIn(0f, 1f)
        }
        val chapter = ready.chapterAt(firstVisibleIndex)
        val pageCounts = pageCountsFor(ready, chapter, firstVisibleIndex)
        val progress = ReadingProgress(
            chapterIndex = chapter,
            elementIndex = firstVisibleIndex,
            scrollOffset = scrollOffset,
            fraction = fraction,
            pagesLeftInChapter = pageCounts.first,
            totalPages = pageCounts.second,
        )
        viewModelScope.launch { repository.saveProgress(bookId, progress) }
    }


    private fun pageCountsFor(
        ready: ReaderState.Ready,
        chapter: Int,
        firstVisibleIndex: Int,
    ): Pair<Int, Int> {
        val holder = _pagination.value ?: return -1 to 0
        if (holder.partial) return -1 to 0
        val pages = holder.pages
        if (pages.isEmpty()) return -1 to 0

        val currentPage = lastPageStartingAtOrBefore(pages, firstVisibleIndex)
        val chapterEnd = ready.chapterStarts.getOrNull(chapter + 1) ?: ready.items.size


        val lastPageOfChapter = lastPageStartingAtOrBefore(pages, chapterEnd - 1)

        return (lastPageOfChapter - currentPage).coerceAtLeast(0) to pages.size
    }


    private fun lastPageStartingAtOrBefore(pages: List<BookPage>, flatIndex: Int): Int {
        var low = 0
        var high = pages.size - 1
        var found = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pages[mid].firstItemIndex <= flatIndex) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }


    fun markFinished() {
        viewModelScope.launch { repository.markFinished(bookId) }
    }


    fun addReadingTime(seconds: Long) {
        if (seconds < 1 || seconds > 24 * 60 * 60) return
        viewModelScope.launch {
            repository.addReadingSeconds(bookId, seconds)
        }
    }


    val bookmarkWindow = 4

    fun bookmarkNear(flatIndex: Int): Bookmark? =
        book.value?.bookmarks?.firstOrNull {
            it.flatIndex in flatIndex..(flatIndex + bookmarkWindow)
        }


    fun toggleBookmarkAt(flatIndex: Int) {
        val ready = _state.value as? ReaderState.Ready ?: return
        val existing = bookmarkNear(flatIndex)
        viewModelScope.launch {
            if (existing != null) {
                repository.removeBookmark(bookId, existing.id)
            } else {
                repository.toggleBookmark(
                    bookId,
                    Bookmark(
                        id = UUID.randomUUID().toString(),
                        flatIndex = flatIndex,
                        chapterIndex = ready.chapterAt(flatIndex),
                        preview = previewAt(ready, flatIndex),
                        createdAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun removeBookmark(bookmarkId: String) {
        viewModelScope.launch { repository.removeBookmark(bookId, bookmarkId) }
    }

    fun addQuote(text: String, chapterIndex: Int, range: BookSelection) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addQuote(
                bookId,
                Quote(
                    id = UUID.randomUUID().toString(),
                    text = trimmed,
                    chapterIndex = chapterIndex,
                    createdAtMillis = System.currentTimeMillis(),
                    startItem = range.start.itemIndex,
                    startChar = range.start.charOffset,
                    endItem = range.end.itemIndex,
                    endChar = range.end.charOffset,
                ),
            )
        }
    }

    fun removeQuote(quoteId: String) {
        viewModelScope.launch { repository.removeQuote(bookId, quoteId) }
    }

    private fun previewAt(ready: ReaderState.Ready, flatIndex: Int): String {
        for (i in flatIndex until ready.items.size) {
            ready.items[i].element.previewText()?.let { return it }
        }
        return ""
    }


    init {
        load()
        viewModelScope.launch {
            settingsRepository.appSettings.map { it.autoHideReturnButton }.distinctUntilChanged()
                .collect { enabled ->
                    expirePageReturns = enabled
                    navigationSession.configureExpiry(returnTimeoutMillis.takeIf { enabled })
                }
        }

        viewModelScope.launch {
            settings
                .map { it.hideFootnotes }
                .distinctUntilChanged()
                .collect { if (loadedContent != null) rebuildReady() }
        }
    }

    companion object {
        fun factory(bookId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MobileReaderApp
                ReaderViewModel(
                    repository = app.bookRepository,
                    settingsRepository = app.settingsRepository,
                    bookId = bookId,
                    paginationCacheDir = File(app.filesDir, "pagination"),
                )
            }
        }
    }
}


internal fun BookContent.hasSequentialContent(): Boolean =
    chapters.any { it.elements.isNotEmpty() }
