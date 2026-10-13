package com.example.mobilereader.ui.reader

import com.example.mobilereader.ui.translation.rememberTranslationAction
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.mobilereader.MainActivity
import com.example.mobilereader.R
import com.example.mobilereader.ui.theme.appColorSchemeFor
import com.example.mobilereader.ui.theme.MobileReaderTheme
import com.example.mobilereader.data.AppSettings
import com.example.mobilereader.data.AppTheme
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.ReadingMode
import com.example.mobilereader.data.model.Book
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.data.model.EXTERNAL_LINK_TAG
import com.example.mobilereader.data.model.FOOTNOTE_TAG
import com.example.mobilereader.data.model.LINK_TAG
import com.example.mobilereader.data.model.LinkedDocumentTarget
import com.example.mobilereader.data.model.NoteDocument
import com.example.mobilereader.data.model.ParagraphStyle
import com.example.mobilereader.data.model.Quote
import com.example.mobilereader.ui.reader.selection.BookSelection
import com.example.mobilereader.ui.reader.selection.BookAnchor
import com.example.mobilereader.ui.reader.selection.ReaderHighlights
import com.example.mobilereader.ui.reader.selection.SelectionAutoAdvance
import com.example.mobilereader.ui.reader.selection.SelectionAutoAdvanceEffect
import com.example.mobilereader.ui.reader.selection.SelectionController
import com.example.mobilereader.ui.reader.selection.SelectionHandles
import com.example.mobilereader.ui.reader.selection.SelectionText
import com.example.mobilereader.ui.reader.selection.bookSelectionGestures
import com.example.mobilereader.ui.reader.selection.range
import com.example.mobilereader.ui.reader.selection.readerHighlights
import com.example.mobilereader.ui.reader.selection.rememberReaderHighlights
import com.example.mobilereader.ui.reader.selection.rememberTextFragment
import com.example.mobilereader.data.effectiveTheme
import com.example.mobilereader.ui.theme.isDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
) {
    val viewModel: ReaderViewModel =
        viewModel(key = "reader-$bookId", factory = ReaderViewModel.factory(bookId))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()


    var chromeVisible by rememberSaveable { mutableStateOf(false) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val readerTheme = appSettings.effectiveTheme(systemDark)
    SystemBarsEffect(readerTheme, chromeVisible)

    when (val current = state) {
        ReaderState.Loading -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }

        ReaderState.Error -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.reader_error_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reader_error_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) { Text(stringResource(R.string.reader_back)) }
            }
        }

        is ReaderState.Ready -> ReaderContent(
            ready = current,
            settings = settings,
            viewModel = viewModel,
            appSettings = appSettings,
            chromeVisible = chromeVisible,
            onToggleChrome = { chromeVisible = !chromeVisible },
            onShowChrome = { chromeVisible = true },
            onBack = onBack,
        )
    }
}

@Preview(name = "Reader · light", showBackground = true, widthDp = 412, heightDp = 916)
@Composable
private fun ReaderLightPreview() = ReaderPagePreview(AppTheme.WHITE)

@Preview(name = "Reader · sepia", showBackground = true, widthDp = 412, heightDp = 916)
@Composable
private fun ReaderSepiaPreview() = ReaderPagePreview(AppTheme.SEPIA)

@Composable
private fun ReaderPagePreview(theme: AppTheme) {
    MobileReaderTheme(theme) {
        val colors = readerColors(theme)
        val settings = ReaderSettings()
        val appSettings = AppSettings()
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
            val contentWidth = maxWidth - 40.dp
            Column {
                ReaderTopBar(
                    visible = true,
                    title = "The Hound of the Baskervilles",
                    author = "Arthur Conan Doyle",
                    colors = colors,
                    searchVisible = false,
                    searchQuery = "",
                    onQueryChange = {},
                    onSearch = {},
                    onBack = {},
                    onCloseSearch = {},
                )
                Column(Modifier.padding(horizontal = 20.dp, vertical = 32.dp)) {
                    val elements = listOf(
                        ContentElement.Heading("Chapter One", level = 1),
                        ContentElement.Paragraph(AnnotatedString("The road wound across the dark moor. Beyond the hills, a single window glowed in the gathering mist.")),
                        ContentElement.Paragraph(AnnotatedString("She stopped at the gate and listened. The wind moved through the grass, carrying a voice she could not quite understand.")),
                        ContentElement.Paragraph(AnnotatedString("Every unfamiliar word seemed to belong to the place itself, as though the landscape had invented its own language.")),
                    )
                    elements.forEach { element ->
                        RenderPart(
                            element = element,
                            textOverride = null,
                            isParagraphStart = true,
                            imageHeightPx = null,
                            settings = settings,
                            appSettings = appSettings,
                            fontSize = settings.fontSizeSp,
                            colors = colors,
                            contentWidth = contentWidth,
                        )
                    }
                }
            }
        }
    }
}

private data class VisibleNote(
    val key: String,
    val document: NoteDocument,
    val itemIndex: Int = 0,
    val scrollOffset: Int = 0,

    val underlay: ReaderReturnLocation,
    val contextualReturn: Boolean = false,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderContent(
    ready: ReaderState.Ready,
    settings: ReaderSettings,
    viewModel: ReaderViewModel,
    appSettings: AppSettings,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onShowChrome: () -> Unit,
    onBack: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    val chromeScheme = appColorSchemeFor(appSettings.theme, appSettings.dynamicColor)
    val colors = remember(appSettings.theme, chromeScheme) {
        readerColors(theme = appSettings.theme, chromeScheme = chromeScheme)
    }
    val liveBook by viewModel.book.collectAsStateWithLifecycle()


    val panelFraction = remember { mutableFloatStateOf(0f) }


    var liveFontSize by remember(settings.fontSizeSp) { mutableFloatStateOf(settings.fontSizeSp) }


    val displayedIndex = remember { mutableIntStateOf(viewModel.currentFlatIndex) }
    var seekTarget by remember { mutableStateOf<Int?>(null) }
    var seekFraction by remember { mutableStateOf<Float?>(null) }


    var seekPosition by remember { mutableStateOf<ReaderReturnLocation.Main?>(null) }


    var linkedDocumentId by rememberSaveable(ready.book.id) { mutableStateOf<String?>(null) }
    var linkedDocumentItem by rememberSaveable(ready.book.id) { mutableIntStateOf(0) }
    var linkedDocumentScrollOffset by rememberSaveable(ready.book.id) { mutableIntStateOf(0) }
    var linkedDocumentSeek by remember { mutableStateOf<ReaderReturnLocation.Linked?>(null) }
    LaunchedEffect(ready) {
        val id = linkedDocumentId
        if (id != null && id !in ready.linkedDocuments) {
            linkedDocumentId = null
            linkedDocumentItem = 0
            linkedDocumentScrollOffset = 0
            linkedDocumentSeek = null
        }
    }


    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchHighlight by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(searchQuery) {
        delay(150.milliseconds)
        viewModel.search(searchQuery)
    }


    val pagePosition = remember { mutableStateOf<Pair<Int, Int>?>(null) }


    val livePagePosition = remember { mutableStateOf<Float?>(null) }
    val liveScrollPosition = remember { mutableStateOf<Float?>(null) }


    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var sessionStart = System.currentTimeMillis()
        var running = true
        fun flush() {
            if (running) {
                viewModel.addReadingTime((System.currentTimeMillis() - sessionStart) / 1000)
                running = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    sessionStart = System.currentTimeMillis()
                    running = true
                }

                Lifecycle.Event.ON_PAUSE -> flush()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            flush()
        }
    }

    val pinchHandlers = PinchHandlers(
        onPinch = { zoom -> liveFontSize = (liveFontSize * zoom).coerceIn(12f, 32f) },
        onPinchEnd = {
            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
            val committed = liveFontSize.roundToInt().toFloat()
            viewModel.updateSettings { it.copy(fontSizeSp = committed) }
        },
    )


    val activity = LocalActivity.current
    var brightness by remember { mutableStateOf<Float?>(null) }
    var brightnessVisibleUntil by remember { mutableLongStateOf(0L) }

    val setBrightness: (Float?) -> Unit = { value ->
        activity?.window?.let { window ->
            brightness = value
            window.attributes = window.attributes.apply {
                screenBrightness = value
                    ?: android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
    val brightnessHandlers = remember(activity) {
        BrightnessHandlers { delta ->
            val window = activity?.window ?: return@BrightnessHandlers
            val current = brightness
                ?: window.attributes.screenBrightness.takeIf { it >= 0f }
                ?: 0.5f


            val updated = brightnessFromPosition(brightnessPosition(current) + delta)
            brightness = updated
            window.attributes = window.attributes.apply { screenBrightness = updated }
            brightnessVisibleUntil = System.currentTimeMillis() + 900
        }
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                window.attributes = window.attributes.apply {
                    screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }


    DisposableEffect(activity, appSettings.keepScreenOn) {
        val window = activity?.window
        if (appSettings.keepScreenOn) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }


    val turnEvents = remember { MutableSharedFlow<Boolean>(extraBufferCapacity = 4) }
    val mainActivity = activity as? MainActivity
    DisposableEffect(mainActivity) {
        val handler: (Boolean) -> Boolean = handler@{ forward ->
            if (!viewModel.appSettings.value.volumeKeyPaging) return@handler false
            if (searchVisible) return@handler false
            if (linkedDocumentId != null) return@handler false
            turnEvents.tryEmit(forward)
        }
        mainActivity?.volumeKeyHandler = handler
        onDispose {
            if (mainActivity?.volumeKeyHandler == handler) mainActivity.volumeKeyHandler = null
        }
    }

    val context = LocalContext.current


    val selection = remember { SelectionController() }
    selection.textAt = { index ->
        ready.items.getOrNull(index)?.element?.let(SelectionText::elementText)
    }
    fun selectedText(): String =
        selection.selectedText(ready.items.size) { ready.items[it].element }


    LaunchedEffect(ready) { selection.clear() }


    LaunchedEffect(settings.readingMode) { selection.clear() }

    var noteToShow by remember { mutableStateOf<VisibleNote?>(null) }
    val navigationReturnLocation by viewModel.navigationReturnLocation.collectAsStateWithLifecycle()
    val returnTimeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = ReaderNavigationSession.DEFAULT_TIMEOUT_MILLIS,
        containsIcons = true,
        containsText = true,
        containsControls = true,
    ) ?: ReaderNavigationSession.DEFAULT_TIMEOUT_MILLIS
    LaunchedEffect(returnTimeout) { viewModel.setReturnTimeoutMillis(returnTimeout) }
    val navigationBackAvailable = navigationReturnLocation != null
    val translateSelection = rememberTranslationAction(
        defaultTranslator = appSettings.defaultTranslator,
        saveDefault = viewModel::setDefaultTranslator,
    )
    val pagination by viewModel.pagination.collectAsStateWithLifecycle()
    val returnPageNumber = if (settings.readingMode == ReadingMode.PAGES) {
        val origin = navigationReturnLocation as? ReaderReturnLocation.Main
        val pages = pagination?.takeIf { !it.partial }?.pages
        if (origin != null && pages != null) {
            ReaderNavigationPolicy.pageIndexForLocation(pages, origin)?.plus(1)
        } else null
    } else null
    val currentReturnLabel = returnPageNumber?.let { stringResource(R.string.reader_return_page, it) }
        ?: stringResource(R.string.reader_return)


    var returnLabel by remember { mutableStateOf(currentReturnLabel) }
    if (navigationBackAvailable) returnLabel = currentReturnLabel
    val readerDensity = LocalDensity.current
    val readingTopReferencePx = with(readerDensity) {
        (WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()
            .calculateTopPadding() + 20.dp).toPx()
    }

    fun currentBookSurfaceLocation(): ReaderReturnLocation {
        val documentId = linkedDocumentId
        return if (documentId != null) {
            ReaderReturnLocation.Linked(
                documentId = documentId,
                itemIndex = linkedDocumentItem,
                scrollOffset = linkedDocumentScrollOffset,
            )
        } else {
            val logicalAnchor = if (settings.readingMode == ReadingMode.SCROLL) {
                selection.spaceSize?.let { size ->
                    selection.anchorAt(
                        point = Offset(size.width / 2f, readingTopReferencePx),
                        maxVerticalGap = with(readerDensity) { 96.dp.toPx() },
                    )
                }?.takeIf { it.itemIndex == viewModel.currentFlatIndex }
            } else {
                null
            }
            ReaderReturnLocation.Main(
                flatItemIndex = viewModel.currentFlatIndex,
                charOffset = if (settings.readingMode == ReadingMode.PAGES) {
                    viewModel.currentCharOffset
                } else {
                    logicalAnchor?.charOffset
                },
                scrollOffset = viewModel.currentScrollOffset,
            )
        }
    }

    fun currentReturnLocation(): ReaderReturnLocation = noteToShow?.let { visible ->
        ReaderReturnLocation.Note(
            noteKey = visible.key,
            itemIndex = visible.itemIndex,
            scrollOffset = visible.scrollOffset,
            underlay = visible.underlay,
            contextualReturn = visible.contextualReturn,
        )
    } ?: currentBookSurfaceLocation()

    fun restoreReturnLocation(location: ReaderReturnLocation) {
        noteToShow = null
        selection.clear()
        fun restoreBookSurface(surface: ReaderReturnLocation): Boolean = when (surface) {
            is ReaderReturnLocation.Main -> {
                linkedDocumentId = null
                linkedDocumentItem = 0
                linkedDocumentScrollOffset = 0
                linkedDocumentSeek = null
                seekPosition = surface
                true
            }

            is ReaderReturnLocation.Linked -> {
                if (surface.documentId !in ready.linkedDocuments) {
                    false
                } else {
                    linkedDocumentId = surface.documentId
                    linkedDocumentItem = surface.itemIndex
                    linkedDocumentScrollOffset = surface.scrollOffset
                    linkedDocumentSeek = surface
                    true
                }
            }

            is ReaderReturnLocation.Note -> restoreBookSurface(surface.underlay)
        }
        when (location) {
            is ReaderReturnLocation.Main,
            is ReaderReturnLocation.Linked -> restoreBookSurface(location)

            is ReaderReturnLocation.Note -> {
                if (!restoreBookSurface(location.underlay)) return
                val note = ready.notes[location.noteKey] ?: return
                noteToShow = VisibleNote(
                    key = location.noteKey,
                    document = note,
                    itemIndex = location.itemIndex,
                    scrollOffset = location.scrollOffset,
                    underlay = location.underlay,
                    contextualReturn = location.contextualReturn,
                )
            }
        }
    }

    fun navigateWithHistory(target: ReaderReturnLocation) {
        val origin = currentReturnLocation()
        if (origin == target) return
        viewModel.rememberNavigationOrigin(
            origin, expires = origin is ReaderReturnLocation.Main && target is ReaderReturnLocation.Main,
        )
        restoreReturnLocation(target)
        if (target is ReaderReturnLocation.Main) onShowChrome()
    }

    fun navigateBackInBook() {
        val target = viewModel.takeNavigationOrigin() ?: return
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        restoreReturnLocation(target)
        if (target is ReaderReturnLocation.Main) onShowChrome()
    }

    BackHandler(enabled = selection.active) { selection.clear() }


    BackHandler(enabled = !selection.active && linkedDocumentId != null) {
        if (navigationBackAvailable) {
            navigateBackInBook()
        } else {
            linkedDocumentId = null
            linkedDocumentItem = 0
            linkedDocumentScrollOffset = 0
            linkedDocumentSeek = null
        }
    }


    LaunchedEffect(ready.notes) {
        if (ready.notes.isEmpty()) noteToShow = null
    }


    val quoteRanges = remember(liveBook) {
        liveBook?.quotes.orEmpty().mapNotNull { it.range() }
    }


    val latestOnNote = rememberUpdatedState<(String, NoteDocument) -> Unit> { key, note ->
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        val previous = noteToShow
        if (previous != null) {
            viewModel.rememberNavigationOrigin(currentReturnLocation())
        }
        noteToShow = VisibleNote(
            key = key,
            document = note,
            underlay = previous?.underlay ?: currentBookSurfaceLocation(),
            contextualReturn = previous != null,
        )
    }
    val latestOnNavigate = rememberUpdatedState<(Int) -> Unit> { itemIndex ->
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        navigateWithHistory(ReaderReturnLocation.Main(itemIndex))
    }
    val latestOnNavigateLinked = rememberUpdatedState<(LinkedDocumentTarget) -> Unit> { target ->
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        navigateWithHistory(
            ReaderReturnLocation.Linked(
                documentId = target.documentId,
                itemIndex = target.elementIndex,
            ),
        )
    }
    val footnotes = remember(ready) {
        if (ready.notes.isEmpty() && ready.linkTargets.isEmpty() &&
            ready.linkedDocumentTargets.isEmpty()
        ) {
            null
        } else {
            FootnoteHandler(
                notes = ready.notes,
                onNote = { key, note -> latestOnNote.value(key, note) },
                linkTargets = ready.linkTargets,
                onNavigate = { itemIndex -> latestOnNavigate.value(itemIndex) },
                linkedDocumentTargets = ready.linkedDocumentTargets,
                onNavigateLinked = { target -> latestOnNavigateLinked.value(target) },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {


        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPlaced { selection.space = it }
                .pointerInput(selection) { bookSelectionGestures(selection, haptics) }


                .magnifier(sourceCenter = { selection.magnifierSource() }),
        ) {
            when (settings.readingMode) {
                ReadingMode.SCROLL -> ScrollReader(
                    ready = ready,
                    settings = settings,
                    appSettings = appSettings,
                    fontSize = liveFontSize,
                    colors = colors,
                    viewModel = viewModel,
                    displayedIndex = displayedIndex,
                    livePosition = liveScrollPosition,
                    seekTarget = seekTarget,
                    onSeekConsumed = { seekTarget = null },
                    seekFraction = seekFraction,
                    onSeekFractionConsumed = { seekFraction = null },
                    seekPosition = seekPosition,
                    onSeekPositionConsumed = { seekPosition = null },
                    onToggleChrome = {
                        onToggleChrome()
                        searchHighlight = null
                    },
                    searchHighlight = searchHighlight,
                    pinchHandlers = pinchHandlers,
                    brightnessHandlers = brightnessHandlers,
                    turnEvents = turnEvents,
                    footnotes = footnotes,
                    quoteRanges = quoteRanges,
                    selection = selection,
                )

                ReadingMode.PAGES -> PagedReader(
                    ready = ready,
                    settings = settings,
                    appSettings = appSettings,
                    colors = colors,
                    viewModel = viewModel,
                    displayedIndex = displayedIndex,
                    pagePosition = pagePosition,
                    livePagePosition = livePagePosition,
                    seekTarget = seekTarget,
                    onSeekConsumed = { seekTarget = null },
                    seekFraction = seekFraction,
                    onSeekFractionConsumed = { seekFraction = null },
                    seekPosition = seekPosition,
                    onSeekPositionConsumed = { seekPosition = null },
                    onToggleChrome = {
                        onToggleChrome()
                        searchHighlight = null
                    },
                    searchHighlight = searchHighlight,
                    pinchHandlers = pinchHandlers,
                    brightnessHandlers = brightnessHandlers,
                    turnEvents = turnEvents,
                    footnotes = footnotes,
                    quoteRanges = quoteRanges,
                    selection = selection,
                )
            }

            SelectionAutoAdvanceEffect(selection)
            SelectionHandles(selection, colors.accent)
        }


        SelectionToolbar(
            controller = selection,
            colors = colors,
            onQuote = {
                val selected = selectedText()
                val range = selection.selection
                if (selected.isNotEmpty() && range != null) {


                    viewModel.addQuote(
                        text = selected,
                        chapterIndex = ready.chapterAt(range.start.itemIndex),
                        range = range,
                    )
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                selection.clear()
            },
            onTranslate = {
                val selected = selectedText()
                if (selected.isNotEmpty()) {
                    translateSelection(selected)
                }
                selection.clear()
            },
            onCopy = {
                val selected = selectedText()
                if (selected.isNotEmpty()) {
                    clipboard.setText(AnnotatedString(selected))
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                selection.clear()
            },
        )

        BrightnessOverlay(
            brightness = brightness,
            visibleUntil = brightnessVisibleUntil,
            colors = colors,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        val bookmarked = liveBook?.bookmarks?.any {
            it.flatIndex in displayedIndex.intValue..(displayedIndex.intValue + viewModel.bookmarkWindow)
        } == true

        val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
        val fullPages = pagination?.takeIf { !it.partial }?.pages


        var lastFullPages by remember { mutableStateOf<List<BookPage>?>(null) }
        LaunchedEffect(fullPages) { if (fullPages != null) lastFullPages = fullPages }
        val searchPages = fullPages ?: lastFullPages

        var topBarHeightPx by remember { mutableIntStateOf(0) }
        val topBarHeight = with(LocalDensity.current) { topBarHeightPx.toDp() }

        ReaderSearchOverlay(
            visible = searchVisible,
            query = searchQuery,
            colors = colors,
            chapterTitles = ready.chapterTitles,
            results = searchResults,
            pages = searchPages,
            onResultClick = { match ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                searchHighlight = match.match
                navigateWithHistory(
                    ReaderReturnLocation.Main(match.itemIndex, charOffset = match.charStart),
                )
                searchVisible = false
            },
            onGoToPage = { page ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                searchPages?.getOrNull(page)?.let { target ->
                    navigateWithHistory(
                        ReaderReturnLocation.Main(
                            target.firstItemIndex,
                            charOffset = target.firstCharOffset,
                        ),
                    )
                }
                searchVisible = false
            },
            onClose = { searchVisible = false },
            topPadding = topBarHeight,
        )

        ReaderTopBar(
            visible = chromeVisible,
            title = ready.book.title,
            author = ready.book.author,
            colors = colors,
            searchVisible = searchVisible,
            searchQuery = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { searchVisible = true },
            onBack = onBack,
            onCloseSearch = { searchVisible = false },
            panelFraction = { panelFraction.floatValue },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { topBarHeightPx = it.height },
        )

        val currentChapter = ready.chapterAt(displayedIndex.intValue)

        val pageInfo = pagePosition.value
        val livePage = livePagePosition.value
        val fraction = pageInfo?.let { (page, total) ->
            if (total <= 1) 1f else (livePage ?: page.toFloat()) / (total - 1)
        } ?: if (ready.items.size <= 1) {
            0f
        } else {
            (liveScrollPosition.value ?: displayedIndex.intValue.toFloat()) /
                (ready.items.size - 1)
        }


        val chapterStartPages = remember(pagination, ready) {
            val pages = pagination?.takeIf { !it.partial }?.pages
            if (pages.isNullOrEmpty()) {
                null
            } else {
                ready.chapterStarts.map { start ->
                    pages.indexOfLast { it.firstItemIndex <= start }.coerceAtLeast(0)
                }
            }
        }
        val usePages = pageInfo != null && chapterStartPages != null
        val bookSegments = remember(ready, chapterStartPages, usePages) {
            if (usePages) {
                val total = ((pagination?.pages?.size ?: 1) - 1).coerceAtLeast(1)
                chapterStartPages.map { it.toFloat() / total }
            } else {
                val total = (ready.items.size - 1).coerceAtLeast(1)
                ready.chapterStarts.map { it.toFloat() / total }
            }
        }

        val chapterStart = ready.chapterStarts[currentChapter]
        val chapterEnd = ready.chapterStarts.getOrNull(currentChapter + 1) ?: ready.items.size
        val chapterFraction = if (usePages) {
            val page = livePage ?: pageInfo.first.toFloat()
            val startPage = chapterStartPages[currentChapter]
            val endPage = chapterStartPages.getOrNull(currentChapter + 1)?.minus(1)
                ?: (pageInfo.second - 1)
            if (endPage <= startPage) 1f else (page - startPage) / (endPage - startPage)
        } else {
            val span = chapterEnd - 1 - chapterStart
            val position = liveScrollPosition.value ?: displayedIndex.intValue.toFloat()
            if (span <= 0) 1f else ((position - chapterStart) / span).coerceIn(0f, 1f)
        }

        fun commitProgressSeek(changed: Boolean, seek: () -> Unit): Boolean {
            if (!changed) return false


            viewModel.rememberNavigationOrigin(currentReturnLocation(), expires = true)
            seek()
            return true
        }

        ReaderBottomBar(
            visible = chromeVisible,
            showReturnButton = navigationBackAvailable && linkedDocumentId == null &&
                noteToShow == null && !searchVisible && !selection.active,
            returnLabel = returnLabel,
            onReturn = ::navigateBackInBook,


            allowBackDismiss = !searchVisible && linkedDocumentId == null,
            colors = colors,
            panelFraction = panelFraction,
            chapterStartPages = chapterStartPages,
            totalPages = pageInfo?.second,
            progressValuesReady = settings.readingMode == ReadingMode.SCROLL || usePages,
            ready = ready,
            book = liveBook,
            currentChapter = currentChapter,
            chapterLabel = ready.chapterTitles.getOrNull(currentChapter)
                ?: stringResource(R.string.reader_chapter_n, currentChapter + 1),
            chapterFraction = chapterFraction.coerceIn(0f, 1f),
            bookFraction = fraction.coerceIn(0f, 1f),
            bookSegments = bookSegments,
            onSeekChapter = { f ->
                if (usePages) {
                    val startPage = chapterStartPages[currentChapter]
                    val endPage = chapterStartPages.getOrNull(currentChapter + 1)?.minus(1)
                        ?: (pageInfo.second - 1)
                    val targetPage = ReaderNavigationPolicy.progressTargetIndex(
                        fraction = f,
                        firstIndex = startPage,
                        lastIndexInclusive = endPage,
                    )
                    val currentPage = (livePage ?: pageInfo.first.toFloat()).roundToInt()
                    commitProgressSeek(targetPage != currentPage) {
                        seekFraction = targetPage.toFloat() /
                            (pageInfo.second - 1).coerceAtLeast(1)
                    }
                } else {
                    val targetItem = ReaderNavigationPolicy.progressTargetIndex(
                        fraction = f,
                        firstIndex = chapterStart,
                        lastIndexInclusive = chapterEnd - 1,
                    )
                    commitProgressSeek(targetItem != displayedIndex.intValue) {
                        seekTarget = targetItem
                    }
                }
            },
            onSeekBook = { f ->
                val info = pageInfo
                if (pagination?.partial == true) {


                    val stableCurrentFraction = if (ready.items.size > 1) {
                        displayedIndex.intValue.toFloat() / (ready.items.size - 1)
                    } else {
                        0f
                    }
                    val pending = ReaderNavigationPolicy.pendingBookFractionSeek(
                        currentFraction = stableCurrentFraction,
                        requestedFraction = f,
                    )
                    if (pending == null) {
                        false
                    } else {
                        commitProgressSeek(changed = true) { seekFraction = pending }
                    }
                } else if (info != null) {
                    val targetPage = ReaderNavigationPolicy.progressTargetIndex(
                        fraction = f,
                        firstIndex = 0,
                        lastIndexInclusive = info.second - 1,
                    )
                    val currentPage = (livePage ?: info.first.toFloat()).roundToInt()
                    commitProgressSeek(targetPage != currentPage) {
                        seekFraction = targetPage.toFloat() /
                            (info.second - 1).coerceAtLeast(1)
                    }
                } else {
                    val targetItem = ReaderNavigationPolicy.progressTargetIndex(
                        fraction = f,
                        firstIndex = 0,
                        lastIndexInclusive = ready.items.size - 1,
                    )
                    commitProgressSeek(targetItem != displayedIndex.intValue) {
                        seekFraction = f
                    }
                }
            },
            settings = settings,
            appSettings = appSettings,
            brightness = brightness,
            onUpdate = { transform -> viewModel.updateSettings(transform) },
            onUpdateApp = { transform -> viewModel.updateAppSettings(transform) },
            onBrightnessChange = setBrightness,
            onChapterClick = { target ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                when (target) {
                    is ReaderNavigationTarget.ReadingOrder -> {
                        navigateWithHistory(ReaderReturnLocation.Main(target.flatItemIndex))
                    }

                    is ReaderNavigationTarget.Linked -> {
                        navigateWithHistory(
                            ReaderReturnLocation.Linked(
                                documentId = target.target.documentId,
                                itemIndex = target.target.elementIndex,
                            ),
                        )
                    }
                }
            },
            onBookmarkClick = { flatIndex ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                navigateWithHistory(ReaderReturnLocation.Main(flatIndex))
            },
            onRemoveBookmark = { viewModel.removeBookmark(it) },
            onCopyQuote = { text ->
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            },
            onQuoteClick = { quote ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)


                navigateWithHistory(
                    ReaderReturnLocation.Main(quote.startItem, charOffset = quote.startChar),
                )
            },
            onRemoveQuote = { viewModel.removeQuote(it) },
            bookmarked = bookmarked,
            onToggleBookmark = {
                haptics.performHapticFeedback(
                    if (bookmarked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                )
                viewModel.toggleBookmarkAt(displayedIndex.intValue)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (settings.readingMode == ReadingMode.PAGES && pagination == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }
    }

    linkedDocumentId?.let { id ->
        ready.linkedDocuments[id]?.let { document ->
            key(document.id) {
                LinkedDocumentOverlay(
                    document = document,
                    initialItem = linkedDocumentItem,
                    initialScrollOffset = linkedDocumentScrollOffset,
                    seek = linkedDocumentSeek,
                    onSeekConsumed = { linkedDocumentSeek = null },
                    settings = settings,
                    appSettings = appSettings,
                    fontSize = liveFontSize,
                    colors = colors,
                    bookFonts = ready.bookFonts,
                    language = ready.language,
                    footnotes = footnotes,
                    showReturnButton = navigationBackAvailable,
                    returnLabel = returnLabel,
                    onReturn = ::navigateBackInBook,
                    onBack = {
                        if (navigationBackAvailable) {
                            navigateBackInBook()
                        } else {
                            linkedDocumentId = null
                            linkedDocumentItem = 0
                            linkedDocumentScrollOffset = 0
                            linkedDocumentSeek = null
                        }
                    },
                    onPositionChanged = { item, offset ->
                        linkedDocumentItem = item
                        linkedDocumentScrollOffset = offset
                    },
                )
            }
        }
    }

    noteToShow?.let { note ->
        val returnToPreviousNote = shouldReturnToPreviousNote(
            contextualReturn = note.contextualReturn,
            navigationBackAvailable = navigationBackAvailable,
        )
        NoteSheet(
            noteKey = note.key,
            note = note.document,
            initialItem = note.itemIndex,
            initialScrollOffset = note.scrollOffset,
            settings = settings,
            appSettings = appSettings,
            fontSize = liveFontSize,
            colors = colors,
            bookFonts = ready.bookFonts,
            language = ready.language,
            footnotes = footnotes,
            systemBackReturnsToPreviousNote = returnToPreviousNote,
            showReturnButton = note.contextualReturn && navigationBackAvailable,
            returnLabel = returnLabel,
            onReturn = ::navigateBackInBook,
            onPositionChanged = { item, offset ->
                noteToShow = noteToShow?.let { current ->
                    if (current.key == note.key) {
                        current.copy(itemIndex = item, scrollOffset = offset)
                    } else {
                        current
                    }
                }
            },
            onDismiss = { noteToShow = null },
        )
    }
}


internal fun noteInitialLazyIndex(savedIndex: Int, elementCount: Int): Int =
    savedIndex.coerceIn(0, elementCount.coerceAtLeast(0))


internal fun shouldReturnToPreviousNote(
    contextualReturn: Boolean,
    navigationBackAvailable: Boolean,
): Boolean = contextualReturn && navigationBackAvailable


@Composable
private fun ReaderReturnAffordance(
    visible: Boolean,
    colors: ReaderColors,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = visible
    AnimatedVisibility(
        visibleState = transition,
        modifier = modifier,
        enter = scaleIn(
            initialScale = 0.9f,
            animationSpec = keyframes {
                durationMillis = 180
                0.9f at 0
                1.025f at 100
                1f at 180
            },
        ) + fadeIn(tween(70)),
        exit = scaleOut(targetScale = 0.94f, animationSpec = tween(100)) + fadeOut(tween(100)),
    ) {


        Box(Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.padding(12.dp)) {
            ReaderReturnButton(colors = colors, onClick = onClick, label = label, enabled = visible)
        }
    }
}

@Composable
private fun ReaderReturnButton(
    colors: ReaderColors,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(24.dp)


    Box(
        modifier = Modifier
            .dropShadow(
                shape,
                Shadow(radius = 6.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = 0.16f)),
            )
            .background(colors.chrome, shape)
            .clip(shape)
            .then(
                if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier.clearAndSetSemantics {},
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Undo,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = colors.onChrome,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(
    noteKey: String,
    note: NoteDocument,
    initialItem: Int,
    initialScrollOffset: Int,
    settings: ReaderSettings,
    appSettings: AppSettings,
    fontSize: Float,
    colors: ReaderColors,
    bookFonts: Map<String, androidx.compose.ui.text.font.FontFamily>,
    language: String?,
    footnotes: FootnoteHandler?,
    systemBackReturnsToPreviousNote: Boolean,
    showReturnButton: Boolean,
    returnLabel: String,
    onReturn: () -> Unit,
    onPositionChanged: (itemIndex: Int, scrollOffset: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        contentColor = colors.text,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !systemBackReturnsToPreviousNote,
            shouldDismissOnClickOutside = true,
        ),
    ) {


        BackHandler(enabled = systemBackReturnsToPreviousNote, onBack = onReturn)


        key(noteKey) {
            val notePlan = remember(note) {
                planPublisherChapter(note.elements, note.publisherBoxes)
            }


            val initialIndex = noteInitialLazyIndex(initialItem, note.elements.size)
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = initialIndex,
                initialFirstVisibleItemScrollOffset = initialScrollOffset.coerceAtLeast(0),
            )
            LaunchedEffect(noteKey, listState) {
                snapshotFlow {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }.collect { (item, offset) -> onPositionChanged(item, offset) }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()


                    .heightIn(max = sheetMaxContentHeight()),
            ) {
                val returnRightPadding = WindowInsets.navigationBars.asPaddingValues()
                    .calculateRightPadding(LayoutDirection.Ltr) + 16.dp
                val contentWidth = maxWidth -
                    ReaderMetrics.horizontalPadding(settings.pageMargins) * 2
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 84.dp),
                ) {
                    item(key = "note-title") {
                        Text(
                            text = stringResource(R.string.reader_note_title),
                            style = MaterialTheme.typography.titleLarge.copy(color = colors.text),
                            modifier = Modifier.padding(
                                start = 24.dp,
                                end = 24.dp,
                                bottom = 12.dp,
                            ),
                        )
                    }
                    items(note.elements.size, key = { it }) { index ->
                        if (index in notePlan.suppressedElements && settings.bookStyles) {
                            return@items
                        }
                        val plannedFloat = notePlan.floatByTarget[index]
                        RenderPart(
                            element = note.elements[index],
                            textOverride = null,
                            isParagraphStart = true,
                            imageHeightPx = null,
                            settings = settings,
                            appSettings = appSettings,
                            fontSize = fontSize,
                            colors = colors,
                            contentWidth = contentWidth,
                            bookFonts = bookFonts,
                            language = language,
                            footnotes = footnotes,
                            publisherBoxes = notePlan.boxesByElement[index],
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
                        )
                    }
                }
                ReaderReturnAffordance(
                    visible = showReturnButton,
                    colors = colors,
                    onClick = onReturn,
                    label = returnLabel,
                    modifier = Modifier
                        .align(AbsoluteAlignment.BottomRight)
                        .absolutePadding(right = returnRightPadding, bottom = 12.dp),
                )
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkedDocumentOverlay(
    document: ReaderLinkedDocument,
    initialItem: Int,
    initialScrollOffset: Int,
    seek: ReaderReturnLocation.Linked?,
    onSeekConsumed: () -> Unit,
    settings: ReaderSettings,
    appSettings: AppSettings,
    fontSize: Float,
    colors: ReaderColors,
    bookFonts: Map<String, androidx.compose.ui.text.font.FontFamily>,
    language: String?,
    footnotes: FootnoteHandler?,
    showReturnButton: Boolean,
    returnLabel: String,
    onReturn: () -> Unit,
    onBack: () -> Unit,
    onPositionChanged: (itemIndex: Int, scrollOffset: Int) -> Unit,
) {
    val initialIndex = if (document.items.isEmpty()) {
        0
    } else {
        resolvePublisherFloatTarget(document.items, initialItem)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset.coerceAtLeast(0),
    )
    LaunchedEffect(document.id, seek) {
        val target = seek
        if (target != null && target.documentId == document.id && document.items.isNotEmpty()) {
            listState.scrollToItem(
                resolvePublisherFloatTarget(document.items, target.itemIndex),
                target.scrollOffset.coerceAtLeast(0),
            )
            onSeekConsumed()
        }
    }
    LaunchedEffect(document.id, listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (item, offset) -> onPositionChanged(item, offset) }
    }
    val stableInsets = WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val returnRightPadding = stableInsets.calculateRightPadding(LayoutDirection.Ltr) + 16.dp
        val contentWidth = maxWidth - ReaderMetrics.horizontalPadding(settings.pageMargins) * 2
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = stableInsets.calculateTopPadding() + 76.dp,
                bottom = stableInsets.calculateBottomPadding() + 48.dp,
            ),
        ) {
            items(document.items.size, key = { it }) { index ->
                val readerItem = document.items[index]
                if (readerItem.suppressedByPublisherFloat && settings.bookStyles) {
                    return@items
                }
                RenderPart(
                    element = readerItem.element,
                    textOverride = null,
                    isParagraphStart = true,
                    imageHeightPx = null,
                    settings = settings,
                    appSettings = appSettings,
                    fontSize = fontSize,
                    colors = colors,
                    contentWidth = contentWidth,
                    bookFonts = bookFonts,
                    language = language,
                    footnotes = footnotes,
                    publisherBoxes = readerItem.publisherBoxes,
                    publisherFloat = readerItem.publisherFloat,
                )
            }
        }

        Surface(
            color = colors.chrome,
            contentColor = colors.onChrome,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = stableInsets.calculateTopPadding())
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.reader_back),
                    )
                }
                Text(
                    text = document.title ?: stringResource(R.string.reader_contents),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        ReaderReturnAffordance(
            visible = showReturnButton,
            colors = colors,
            onClick = onReturn,
            label = returnLabel,
            modifier = Modifier
                .align(AbsoluteAlignment.BottomRight)
                .absolutePadding(
                    right = returnRightPadding,
                    bottom = stableInsets.calculateBottomPadding() + 18.dp,
                ),
        )
    }
}


class FootnoteHandler(
    val notes: Map<String, NoteDocument>,
    val onNote: (key: String, note: NoteDocument) -> Unit,
    val linkTargets: Map<String, Int> = emptyMap(),
    val onNavigate: (Int) -> Unit = {},
    val linkedDocumentTargets: Map<String, LinkedDocumentTarget> = emptyMap(),
    val onNavigateLinked: (LinkedDocumentTarget) -> Unit = {},
) {


    internal val linkInteractionListener = LinkInteractionListener { link ->
        (link as? LinkAnnotation.Clickable)?.let { open(it.tag) }
    }


    fun handles(key: String): Boolean =
        key in notes || key in linkTargets || key in linkedDocumentTargets

    fun open(key: String) {
        val note = notes[key]
        if (note != null) {
            onNote(key, note)
            return
        }
        linkTargets[key]?.let {
            onNavigate(it)
            return
        }
        linkedDocumentTargets[key]?.let(onNavigateLinked)
    }
}


internal fun AnnotatedString.withFootnoteLinks(
    accent: Color,
    handler: FootnoteHandler?,
): AnnotatedString {
    val internalRefs = if (handler == null) {
        emptyList()
    } else {
        (
            getStringAnnotations(FOOTNOTE_TAG, 0, length) +
                getStringAnnotations(LINK_TAG, 0, length)
            ).filter { handler.handles(it.item) }
    }
    val externalRefs = getStringAnnotations(EXTERNAL_LINK_TAG, 0, length)
    if (internalRefs.isEmpty() && externalRefs.isEmpty()) return this

    val builder = AnnotatedString.Builder(this)
    val styles = TextLinkStyles(
        style = SpanStyle(
            color = accent,
            textDecoration = TextDecoration.None,
        ),
        pressedStyle = SpanStyle(
            color = accent,
            textDecoration = TextDecoration.None,
            background = accent.copy(alpha = 0.18f),
        ),
    )
    for (ref in internalRefs) {
        builder.addLink(
            LinkAnnotation.Clickable(
                tag = ref.item,
                styles = styles,
                linkInteractionListener = handler?.linkInteractionListener,
            ),
            ref.start,
            ref.end,
        )
    }
    for (ref in externalRefs) {
        builder.addLink(
            LinkAnnotation.Url(url = ref.item, styles = styles),
            ref.start,
            ref.end,
        )
    }
    return builder.toAnnotatedString()
}


internal fun AnnotatedString.withSearchHighlight(
    term: String?,
    highlight: Color,
): AnnotatedString {
    if (term.isNullOrBlank()) return this
    var builder: AnnotatedString.Builder? = null
    var index = text.indexOf(term, ignoreCase = true)
    while (index >= 0) {
        val target = builder ?: AnnotatedString.Builder(this).also { builder = it }
        target.addStyle(SpanStyle(background = highlight), index, index + term.length)
        index = text.indexOf(term, index + term.length, ignoreCase = true)
    }
    return builder?.toAnnotatedString() ?: this
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScrollReader(
    ready: ReaderState.Ready,
    settings: ReaderSettings,
    appSettings: AppSettings,
    fontSize: Float,
    colors: ReaderColors,
    viewModel: ReaderViewModel,
    displayedIndex: MutableIntState,
    livePosition: androidx.compose.runtime.MutableState<Float?>,
    seekTarget: Int?,
    onSeekConsumed: () -> Unit,
    seekFraction: Float?,
    onSeekFractionConsumed: () -> Unit,
    seekPosition: ReaderReturnLocation.Main?,
    onSeekPositionConsumed: () -> Unit,
    onToggleChrome: () -> Unit,
    pinchHandlers: PinchHandlers,
    brightnessHandlers: BrightnessHandlers,
    turnEvents: SharedFlow<Boolean>,
    footnotes: FootnoteHandler?,
    quoteRanges: List<BookSelection>,
    searchHighlight: String?,
    selection: SelectionController,
) {
    val scope = rememberCoroutineScope()
    val liveBook by viewModel.book.collectAsStateWithLifecycle()
    val rawInitialIndex = viewModel.currentFlatIndex.coerceIn(0, ready.items.size - 1)
    val initialIndex = resolvePublisherFloatTarget(ready.items, rawInitialIndex)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = if (initialIndex == rawInitialIndex &&
            rawInitialIndex == ready.book.progress.elementIndex
        ) {
            ready.book.progress.scrollOffset
        } else {
            0
        },
    )
    val density = LocalDensity.current
    val stableInsets = WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()
    val readingTopReferencePx = with(density) {
        (stableInsets.calculateTopPadding() + 20.dp).toPx()
    }
    fun currentScrollLocation(): ReaderReturnLocation.Main {
        val index = listState.firstVisibleItemIndex.coerceIn(ready.items.indices)
        val anchor = selection.spaceSize?.let { size ->
            selection.anchorAt(
                point = Offset(size.width / 2f, readingTopReferencePx),
                maxVerticalGap = with(density) { 96.dp.toPx() },
            )
        }?.takeIf { it.itemIndex == index }
        return ReaderReturnLocation.Main(
            flatItemIndex = index,
            charOffset = anchor?.charOffset,
            scrollOffset = listState.firstVisibleItemScrollOffset,
        )
    }

    LaunchedEffect(ready) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
                displayedIndex.intValue = index.coerceAtMost(ready.items.size - 1)
                viewModel.observeVisiblePosition(index, offset)
                if (index >= ready.items.size - 1) viewModel.markFinished()
            }
    }


    LaunchedEffect(ready, listState) {
        var origin: ReaderReturnLocation.Main? = null
        var previousOffsets = emptyMap<Int, Int>()
        var travelledPx = 0f
        var viewportPx = 1
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.layoutInfo.visibleItemsInfo.map { it.index to it.offset },
                listState.layoutInfo.viewportSize.height.coerceAtLeast(1),
            )
        }.collect { (moving, visible, viewport) ->
            val currentOffsets = visible.toMap()
            viewportPx = viewport
            if (moving && origin == null) {
                origin = currentScrollLocation()
                travelledPx = 0f
            }
            if (moving) {
                val common = previousOffsets.keys.firstOrNull { it in currentOffsets }
                if (common != null) {
                    travelledPx += abs(
                        previousOffsets.getValue(common) - currentOffsets.getValue(common),
                    )
                }
            } else {
                val start = origin
                if (start != null && !selection.active &&
                    ReaderNavigationPolicy.isLargeScrollJump(travelledPx, viewportPx)
                ) {
                    val end = currentScrollLocation()
                    if (end != start) viewModel.rememberNavigationOrigin(start, expires = true)
                }
                origin = null
                travelledPx = 0f
            }
            previousOffsets = currentOffsets
        }
    }


    LaunchedEffect(ready) {
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (info == null || info.size <= 0) {
                null
            } else {
                info.index + ((-info.offset).toFloat() / info.size).coerceIn(0f, 1f)
            }
        }.collect { position -> position?.let { livePosition.value = it } }
    }
    LaunchedEffect(ready) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                delay(400.milliseconds)
                viewModel.saveProgress(index, offset)
            }
    }
    LaunchedEffect(seekTarget) {
        val target = seekTarget ?: return@LaunchedEffect
        val sourceItem = target.coerceIn(0, ready.items.size - 1)
        val visibleItem = resolvePublisherFloatTarget(ready.items, sourceItem)
        livePosition.value = sourceItem.toFloat()
        listState.scrollToItem(visibleItem)
        onSeekConsumed()
    }
    LaunchedEffect(seekFraction) {
        val fraction = seekFraction ?: return@LaunchedEffect
        val item = (fraction * (ready.items.size - 1)).roundToInt()
            .coerceIn(0, ready.items.size - 1)
        livePosition.value = item.toFloat()
        listState.scrollToItem(resolvePublisherFloatTarget(ready.items, item))
        onSeekFractionConsumed()
    }
    LaunchedEffect(seekPosition) {
        val position = seekPosition ?: return@LaunchedEffect
        val sourceTarget = position.flatItemIndex.coerceIn(0, ready.items.size - 1)
        val visibleTarget = resolvePublisherFloatTarget(ready.items, sourceTarget)
        livePosition.value = sourceTarget.toFloat()
        val charOffset = position.charOffset
        if (charOffset == null) {
            listState.scrollToItem(visibleTarget, position.scrollOffset.coerceAtLeast(0))
        } else {


            listState.scrollToItem(visibleTarget)
            var restored = false
            repeat(3) {
                withFrameNanos { }
                val caret = selection.caretAt(BookAnchor(sourceTarget, charOffset))
                if (caret != null) {
                    val correction = caret.top - readingTopReferencePx
                    if (abs(correction) > 0.5f) listState.scrollBy(correction)
                    restored = true
                }
            }
            if (!restored) {
                listState.scrollToItem(
                    visibleTarget,
                    position.scrollOffset.coerceAtLeast(0),
                )
            }
        }
        onSeekPositionConsumed()
    }
    LaunchedEffect(ready) {
        turnEvents.collect { forward ->
            val viewport = listState.layoutInfo.viewportSize.height * 0.88f
            listState.animateScrollBy(if (forward) viewport else -viewport)
        }
    }
    DisposableEffect(ready) {
        onDispose {
            viewModel.saveProgress(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    val highlights = rememberReaderHighlights(
        controller = selection,
        quotes = quoteRanges,
        quoteColor = colors.quoteHighlight,
        selectionColor = colors.selection,
    )

    DisposableEffect(selection, listState) {
        val mine = SelectionAutoAdvance(
            paged = false,
            step = { _, pixels -> listState.scrollBy(pixels) },
        )
        selection.advance = mine
        selection.surfaceMoving = { listState.isScrollInProgress }


        onDispose {
            if (selection.advance === mine) {
                selection.advance = null
                selection.surfaceMoving = { false }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentWidth = maxWidth - ReaderMetrics.horizontalPadding(settings.pageMargins) * 2
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .readerGestures(
                    onTapLeft = {
                        scope.launch {
                            val delta = ReaderProgression.horizontalTapDelta(
                                leftEdge = true,
                                progression = ready.pageProgression,
                            )
                            listState.animateScrollBy(
                                delta * listState.layoutInfo.viewportSize.height * 0.88f,
                            )
                        }
                    },
                    onTapRight = {
                        scope.launch {
                            val delta = ReaderProgression.horizontalTapDelta(
                                leftEdge = false,
                                progression = ready.pageProgression,
                            )
                            listState.animateScrollBy(
                                delta * listState.layoutInfo.viewportSize.height * 0.88f,
                            )
                        }
                    },
                    onTapCenter = onToggleChrome,
                    pinchHandlers = pinchHandlers,
                    brightnessHandlers = brightnessHandlers,
                ),
            contentPadding = PaddingValues(
                top = stableInsets.calculateTopPadding() + 20.dp,
                bottom = stableInsets.calculateBottomPadding() + 48.dp,
            ),
        ) {
            items(ready.items.size, key = { it }) { index ->
                val readerItem = ready.items[index]
                if (readerItem.suppressedByPublisherFloat && settings.bookStyles) {
                    return@items
                }
                RenderPart(
                    element = readerItem.element,
                    textOverride = null,
                    isParagraphStart = true,
                    imageHeightPx = null,
                    settings = settings,
                    appSettings = appSettings,
                    fontSize = fontSize,
                    colors = colors,
                    contentWidth = contentWidth,
                    bookFonts = ready.bookFonts,
                    language = ready.language,
                    footnotes = footnotes,
                    searchHighlight = searchHighlight,
                    highlights = highlights,
                    itemIndex = index,
                    publisherBoxes = readerItem.publisherBoxes,
                    publisherFloat = readerItem.publisherFloat,
                )
            }
            item(key = "completion") {
                CompletionPage(
                    book = liveBook,
                    colors = colors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, bottom = 24.dp),
                )
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PagedReader(
    ready: ReaderState.Ready,
    settings: ReaderSettings,
    appSettings: AppSettings,
    colors: ReaderColors,
    viewModel: ReaderViewModel,
    displayedIndex: MutableIntState,
    pagePosition: androidx.compose.runtime.MutableState<Pair<Int, Int>?>,
    livePagePosition: androidx.compose.runtime.MutableState<Float?>,
    seekTarget: Int?,
    onSeekConsumed: () -> Unit,
    seekFraction: Float?,
    onSeekFractionConsumed: () -> Unit,
    seekPosition: ReaderReturnLocation.Main?,
    onSeekPositionConsumed: () -> Unit,
    onToggleChrome: () -> Unit,
    pinchHandlers: PinchHandlers,
    brightnessHandlers: BrightnessHandlers,
    turnEvents: SharedFlow<Boolean>,
    footnotes: FootnoteHandler?,
    quoteRanges: List<BookSelection>,
    searchHighlight: String?,
    selection: SelectionController,
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val liveBook by viewModel.book.collectAsStateWithLifecycle()


    DisposableEffect(Unit) {
        onDispose {
            pagePosition.value = null
            livePagePosition.value = null
        }
    }
    val density = LocalDensity.current
    val uiLayoutIsRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val stableInsets = WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()
        val topPadding = stableInsets.calculateTopPadding() + 16.dp
        val bottomPadding = stableInsets.calculateBottomPadding() + 40.dp

        val contentWidthPx = with(density) {
            (maxWidth - ReaderMetrics.horizontalPadding(settings.pageMargins) * 2).roundToPx()
        }
        val contentHeightPx = with(density) {
            (maxHeight - topPadding - bottomPadding).roundToPx()
        }

        val spec = remember(
            contentWidthPx,
            contentHeightPx,
            density.density,
            density.fontScale,
            settings,
            ready,
        ) {
            PaginationSpec(
                contentWidthPx, contentHeightPx, density, settings, settings.fontSizeSp,
                bookFonts = ready.bookFonts,
                embeddedFontSignature = ready.embeddedFontSignature,
                language = ready.language,
            )
        }
        LaunchedEffect(spec.key, ready) {


            val quick: (suspend () -> List<BookPage>)? =
                if (settings.startChaptersOnNewPage && viewModel.pagination.value != null) {
                    val chapter = ready.chapterAt(viewModel.currentFlatIndex)
                    val from = ready.chapterStarts[chapter]
                    val to = ready.chapterStarts.getOrNull(chapter + 1) ?: ready.items.size
                    if (from == 0 && to == ready.items.size) {
                        null
                    } else {
                        { paginateBook(ready.items, measurer, spec, from, to) }
                    }
                } else {
                    null
                }
            viewModel.ensurePagination(spec.key, settings, ready.items, quick) {
                paginateBook(ready.items, measurer, spec)
            }
        }

        val holder by viewModel.pagination.collectAsStateWithLifecycle()
        val current = holder
        if (current == null || current.pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@BoxWithConstraints
        }


        val recomputing = current.key != spec.key || current.partial
        val renderSettings = if (current.key != spec.key) current.settings else settings

        key(current.key, current.partial) {


            val highlights = rememberReaderHighlights(
                controller = selection,
                quotes = quoteRanges,
                quoteColor = colors.quoteHighlight,
                selectionColor = colors.selection,
            )
            val pages = current.pages
            val realPageCount = pages.size


            val initialPage = pages.indexOfLast { page ->
                page.firstItemIndex < viewModel.currentFlatIndex ||
                    (
                        page.firstItemIndex == viewModel.currentFlatIndex &&
                            page.firstCharOffset <= viewModel.currentCharOffset
                        )
            }.coerceAtLeast(0)


            val extraPages = if (current.partial) 0 else 1
            val pagerState = rememberPagerState(initialPage = initialPage) {
                realPageCount + extraPages
            }


            val chapterLastPage = remember(current) {
                val chapterOf = IntArray(realPageCount) { ready.chapterAt(pages[it].firstItemIndex) }
                val last = IntArray(realPageCount)
                for (i in realPageCount - 1 downTo 0) {
                    last[i] = if (i == realPageCount - 1 || chapterOf[i + 1] != chapterOf[i]) {
                        i
                    } else {
                        last[i + 1]
                    }
                }
                last
            }

            LaunchedEffect(pagerState) {
                snapshotFlow {
                    (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                        .coerceIn(0f, (realPageCount - 1).coerceAtLeast(0).toFloat())
                }.collect { livePagePosition.value = it }
            }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { settled ->
                    if (settled >= realPageCount) {

                        pagePosition.value = (realPageCount - 1) to realPageCount
                        viewModel.markFinished()
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        return@collect
                    }
                    if (current.partial) {


                        val flat = pages[settled].firstItemIndex
                        displayedIndex.intValue = flat
                        viewModel.saveProgress(flat, 0, pages[settled].firstCharOffset)
                        return@collect
                    }
                    val flatIndex = pages[settled].firstItemIndex
                    displayedIndex.intValue = flatIndex
                    pagePosition.value = settled to realPageCount
                    viewModel.saveProgress(flatIndex, 0, pages[settled].firstCharOffset)
                }
            }


            LaunchedEffect(pagerState, pages) {
                var initialChapter = -1
                var gestureTickedChapter = -1
                var lastTargetPage = -1
                snapshotFlow {
                    Triple(pagerState.targetPage, pagerState.isScrollInProgress, pagerState.currentPage)
                }.collect { (target, inProgress, current) ->
                    if (!inProgress) {
                        initialChapter = -1
                        gestureTickedChapter = -1
                        lastTargetPage = -1
                        return@collect
                    }

                    if (initialChapter == -1) {
                        initialChapter = pages.getOrNull(current)?.let {
                            ready.chapterAt(it.firstItemIndex)
                        } ?: -1
                        lastTargetPage = target
                    }

                    val page = pages.getOrNull(target) ?: return@collect
                    val chapter = ready.chapterAt(page.firstItemIndex)


                    val isStep = lastTargetPage != -1 && abs(target - lastTargetPage) <= 1
                    if (isStep && chapter != initialChapter && chapter != gestureTickedChapter) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        gestureTickedChapter = chapter
                    }

                    lastTargetPage = target
                }
            }
            LaunchedEffect(seekTarget) {
                val target = seekTarget ?: return@LaunchedEffect
                val page = pages.indexOfLast { it.firstItemIndex <= target }.coerceAtLeast(0)
                livePagePosition.value = page.toFloat()
                pagerState.scrollToPage(page)
                onSeekConsumed()
            }
            LaunchedEffect(seekFraction, current.partial) {
                val fraction = seekFraction ?: return@LaunchedEffect
                if (!ReaderNavigationPolicy.canConsumeBookFractionSeek(current.partial)) {
                    return@LaunchedEffect
                }
                val page = (fraction * (realPageCount - 1)).roundToInt()
                    .coerceIn(0, realPageCount - 1)
                livePagePosition.value = page.toFloat()
                pagerState.scrollToPage(page)
                onSeekFractionConsumed()
            }
            LaunchedEffect(seekPosition) {
                val position = seekPosition ?: return@LaunchedEffect
                val item = position.flatItemIndex
                if (current.partial) {


                    if (!ReaderNavigationPolicy.partialHolderContainsTarget(
                            chapterStarts = ready.chapterStarts,
                            itemCount = ready.items.size,
                            partialFirstItem = pages.firstOrNull()?.firstItemIndex,
                            targetItem = item,
                        )
                    ) {
                        return@LaunchedEffect
                    }
                }
                val page = ReaderNavigationPolicy.pageIndexForLocation(pages, position)
                    ?: return@LaunchedEffect
                livePagePosition.value = page.toFloat()
                pagerState.scrollToPage(page)
                onSeekPositionConsumed()
            }
            LaunchedEffect(pagerState) {
                turnEvents.collect { forward ->
                    val delta = if (forward) 1 else -1
                    pagerState.animateScrollToPage(
                        (pagerState.currentPage + delta)
                            .coerceIn(0, realPageCount + extraPages - 1),
                    )
                }
            }


            DisposableEffect(selection, pagerState, realPageCount) {
                val mine = SelectionAutoAdvance(
                    paged = true,
                    step = { direction, _ ->
                        val logicalDirection = ReaderProgression.selectionPageDelta(
                            direction,
                            ready.pageProgression,
                        )
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + logicalDirection)
                                .coerceIn(0, (realPageCount - 1).coerceAtLeast(0)),
                        )
                    },
                    settle = { pagerState.animateScrollToPage(pagerState.currentPage) },
                )
                selection.advance = mine
                selection.surfaceMoving = { pagerState.isScrollInProgress }
                onDispose {
                    if (selection.advance === mine) {
                        selection.advance = null
                        selection.surfaceMoving = { false }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                reverseLayout = ReaderProgression.usesReversePagerLayout(
                    ready.pageProgression,
                    uiLayoutIsRtl = uiLayoutIsRtl,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .readerGestures(
                        onTapLeft = {
                            scope.launch {
                                val delta = ReaderProgression.horizontalTapDelta(
                                    leftEdge = true,
                                    progression = ready.pageProgression,
                                )
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage + delta)
                                        .coerceIn(0, realPageCount + extraPages - 1),
                                )
                            }
                        },
                        onTapRight = {
                            scope.launch {
                                val delta = ReaderProgression.horizontalTapDelta(
                                    leftEdge = false,
                                    progression = ready.pageProgression,
                                )
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage + delta)
                                        .coerceIn(0, realPageCount + extraPages - 1),
                                )
                            }
                        },
                        onTapCenter = onToggleChrome,
                        pinchHandlers = pinchHandlers,
                        brightnessHandlers = brightnessHandlers,
                    ),
            ) { pageIndex ->
                if (pageIndex < realPageCount) {
                    PageView(
                        page = pages[pageIndex],
                        settings = renderSettings,
                        appSettings = appSettings,
                        colors = colors,
                        topPadding = topPadding,
                        bottomPadding = bottomPadding,
                        contentWidth = maxWidth -
                            ReaderMetrics.horizontalPadding(renderSettings.pageMargins) * 2,
                        bookFonts = ready.bookFonts,
                        language = ready.language,
                        bookId = ready.book.id,
                        footnotes = footnotes,
                        searchHighlight = searchHighlight,
                        highlights = highlights,
                    )
                } else {
                    CompletionPage(
                        book = liveBook,
                        colors = colors,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topPadding),
                    )
                }
            }

            val shownPage = pagerState.currentPage
            if (shownPage < realPageCount) {
                val footerText = if (settings.showChapterPagesLeft) {
                    val left = chapterLastPage[shownPage] - shownPage
                    if (left == 0) {
                        stringResource(R.string.reader_chapter_last_page)
                    } else {
                        stringResource(R.string.reader_chapter_pages_left, left)
                    }
                } else {
                    stringResource(R.string.reader_page_of, shownPage + 1, realPageCount)
                }
                Text(
                    text = footerText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = stableInsets.calculateBottomPadding() + 10.dp),
                )
            }
        }


        if (recomputing) {
            LoadingIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topPadding, end = 20.dp)
                    .size(28.dp),
            )
        }
    }
}

@Composable
private fun PageView(
    page: BookPage,
    settings: ReaderSettings,
    appSettings: AppSettings,
    colors: ReaderColors,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    contentWidth: androidx.compose.ui.unit.Dp,
    bookFonts: Map<String, androidx.compose.ui.text.font.FontFamily>,
    language: String?,
    bookId: String,
    footnotes: FootnoteHandler?,
    searchHighlight: String?,
    highlights: ReaderHighlights,
) {
    val allowsVerticalScroll = page.parts.any(PagePart::allowsVerticalScroll)
    val overflowScroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding)
            .then(
                if (allowsVerticalScroll) {


                    Modifier.verticalScroll(overflowScroll)
                } else {


                    Modifier.clipToBounds()
                },
            ),
    ) {
        page.parts.forEach { part ->
            RenderPart(
                element = part.element,
                textOverride = part.text,
                isParagraphStart = part.isParagraphStart,
                imageHeightPx = part.imageHeightPx,
                settings = settings,
                appSettings = appSettings,
                fontSize = settings.fontSizeSp,
                colors = colors,
                contentWidth = contentWidth,
                bookFonts = bookFonts,
                language = language,
                footnotes = footnotes,
                searchHighlight = searchHighlight,
                tableLayout = part.tableLayout,
                tableRowStart = part.rowStart,
                tableRowEnd = part.rowEnd,
                tableHeaderRepeated = part.headerRepeated,
                sideBox = part.sideBox,
                floatImagePath = part.floatImagePath,
                highlights = highlights,
                itemIndex = part.itemIndex,


                charStart = if (part.text != null) part.charStart.coerceAtLeast(0) else 0,
                publisherBoxes = part.publisherBoxes,
                publisherStartsElement = part.publisherStartsElement,
                publisherEndsElement = part.publisherEndsElement,
            )
        }
    }
}


class PinchHandlers(
    val onPinch: (Float) -> Unit,
    val onPinchEnd: () -> Unit,
)

class BrightnessHandlers(

    val onDelta: (Float) -> Unit,
)


private fun Modifier.readerGestures(
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onTapCenter: () -> Unit,
    pinchHandlers: PinchHandlers,
    brightnessHandlers: BrightnessHandlers,
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures { offset ->
            val width = size.width.toFloat()
            when {
                offset.x < width * 0.22f -> onTapLeft()
                offset.x > width * 0.78f -> onTapRight()
                else -> onTapCenter()
            }
        }
    }
    .pointerInput(pinchHandlers) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoomed = false
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) {
                        pinchHandlers.onPinch(zoom)
                        zoomed = true
                        event.changes.forEach { it.consume() }
                    }
                }
                if (event.changes.none { it.pressed }) break
            }
            if (zoomed) pinchHandlers.onPinchEnd()
        }
    }
    .pointerInput(brightnessHandlers) {

        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            if (down.position.x > size.width * 0.12f) return@awaitEachGesture

            val slop = viewConfiguration.touchSlop
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (event.changes.count { it.pressed } > 1) break


                if (change.isConsumed) break

                val totalDx = change.position.x - down.position.x
                val totalDy = change.position.y - down.position.y
                if (!dragging) {
                    if (abs(totalDy) > slop && abs(totalDy) > abs(totalDx) * 1.5f) {
                        dragging = true
                    } else if (abs(totalDx) > slop) {
                        break
                    }
                }
                if (dragging) {
                    val dy = change.position.y - change.previousPosition.y
                    brightnessHandlers.onDelta(-dy / (size.height * 0.7f))
                    change.consume()
                }
            }
        }
    }


@Composable
private fun BrightnessOverlay(
    brightness: Float?,
    visibleUntil: Long,
    colors: ReaderColors,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(visibleUntil) {
        if (visibleUntil == 0L) return@LaunchedEffect
        visible = true
        delay((visibleUntil - System.currentTimeMillis()).coerceAtLeast(0).milliseconds)
        visible = false
    }
    AnimatedVisibility(
        visible = visible && brightness != null,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            color = colors.chrome,
            contentColor = colors.onChrome,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 4.dp,
            modifier = Modifier.padding(start = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = colors.accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${(brightnessPosition(brightness ?: 0f) * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}


@Composable
private fun RenderPart(
    element: ContentElement,
    textOverride: androidx.compose.ui.text.AnnotatedString?,
    isParagraphStart: Boolean,
    imageHeightPx: Int?,
    settings: ReaderSettings,
    appSettings: AppSettings,
    fontSize: Float,
    colors: ReaderColors,
    contentWidth: androidx.compose.ui.unit.Dp,
    bookFonts: Map<String, androidx.compose.ui.text.font.FontFamily> = emptyMap(),
    language: String? = null,
    footnotes: FootnoteHandler? = null,
    searchHighlight: String? = null,

    highlights: ReaderHighlights? = null,

    itemIndex: Int = -1,

    charStart: Int = 0,
    tableLayout: TableLayout? = null,
    tableRowStart: Int = -1,
    tableRowEnd: Int = -1,
    tableHeaderRepeated: Boolean = false,
    sideBox: SideBoxSpec? = null,
    floatImagePath: String? = null,
    publisherBoxes: List<ReaderPublisherBox> = emptyList(),
    publisherFloat: ReaderPublisherFloat? = null,
    publisherStartsElement: Boolean = true,
    publisherEndsElement: Boolean = true,
) {
    val (vTop, vBottom) = ReaderMetrics.verticalPaddings(
        element,
        fontSize,
        settings.bookStyles,
    )
    val density = LocalDensity.current
    val columnWidthPx = with(density) { contentWidth.roundToPx() }
    val fontSizePx = with(density) { fontSize.sp.toPx() }
    val boxGeometry = remember(
        publisherBoxes,
        columnWidthPx,
        fontSizePx,
        publisherStartsElement,
        publisherEndsElement,
        settings.bookStyles,
    ) {
        publisherBoxGeometry(
            boxes = publisherBoxes,
            columnWidthPx = columnWidthPx,
            fontSizePx = fontSizePx,
            partStartsElement = publisherStartsElement,
            partEndsElement = publisherEndsElement,
            enabled = settings.bookStyles,
        )
    }
    val boxContentWidth = with(density) { boxGeometry.contentWidthPx.toDp() }
    val (startInset, endInset) =
        ReaderMetrics.horizontalInsets(element, boxContentWidth, fontSize)
    val (leftInset, _) =
        ReaderMetrics.physicalHorizontalInsets(element, boxContentWidth, fontSize)
    val basePadding = ReaderMetrics.horizontalPadding(settings.pageMargins)
    val publisherLeftInset = with(density) { boxGeometry.contentLeftPx.toDp() }
    val publisherTopInset = with(density) { boxGeometry.topInsetPx.toDp() }
    val publisherBottomInset = with(density) { boxGeometry.bottomInsetPx.toDp() }
    val leftPadding = basePadding + publisherLeftInset + leftInset
    val effectiveVTop = vTop + publisherTopInset
    val effectiveVBottom = vBottom + publisherBottomInset
    val publisherOwnsBackground = settings.bookStyles &&
        hasPublisherBoxBackground(publisherBoxes)
    val surroundingBackground = if (settings.bookStyles) {
        publisherBoxEffectiveBackground(publisherBoxes, colors.background)
    } else {
        colors.background
    }

    val actuallyInvert = appSettings.autoInvertImages && appSettings.theme == AppTheme.OLED


    val exactTextWidth = with(density) {
        (boxGeometry.contentWidthPx - startInset.roundToPx() - endInset.roundToPx())
            .coerceAtLeast(1)
            .toDp()
    }


    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val columnLeftPx = with(density) { basePadding.toPx() }
        Box(
            Modifier
                .fillMaxWidth()
                .drawPublisherBoxes(boxGeometry, columnLeftPx, fontSizePx),
        ) {
            when (element) {
        is ContentElement.Paragraph -> {


            if (floatImagePath != null) {
                val heightModifier = if (imageHeightPx != null) {
                    with(LocalDensity.current) { Modifier.height(imageHeightPx.toDp()) }
                } else {
                    Modifier.heightIn(max = ReaderMetrics.maxImageHeight)
                }
                AsyncImage(
                    model = File(floatImagePath),
                    contentDescription = element.block?.floatImage?.altText,
                    contentScale = ContentScale.Fit,
                    colorFilter = imageColorFilter(actuallyInvert),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = basePadding, vertical = 12.dp)
                        .then(heightModifier),
                )
                return@Box
            }

            val defaultTextColor = when (element.style) {
                ParagraphStyle.QUOTE -> colors.secondaryText
                else -> colors.text
            }
            val colorPair = publisherColorPair(
                element.block,
                settings.bookStyles,
                defaultTextColor,
                surroundingBackground,
                backgroundAlreadyApplied = publisherOwnsBackground,
            )
            val linkColor = readableReaderForeground(colors.accent, colorPair.effectiveBackground)
            fun decorated(raw: androidx.compose.ui.text.AnnotatedString) =
                raw.withPublisherColors(settings.bookStyles, colorPair)
                    .withFootnoteLinks(linkColor, footnotes)
                    .withSearchHighlight(searchHighlight, colors.accent.copy(alpha = 0.3f))


            if (sideBox != null && textOverride != null) {
                val besideDisplay = remember(
                    textOverride, settings.bookStyles, colorPair,
                    colors, footnotes, searchHighlight,
                ) { decorated(textOverride) }
                SideBoxComposite(
                    element = element,
                    sideBox = sideBox,
                    besideText = besideDisplay,
                    settings = settings,
                    fontSize = fontSize,
                    bookFonts = bookFonts,
                    language = language,
                    colors = colors,
                    surroundingBackground = surroundingBackground,
                    publisherBackgroundAlreadyApplied = publisherOwnsBackground,
                    totalWidthPx = with(LocalDensity.current) { exactTextWidth.roundToPx() },
                    invertImages = actuallyInvert,
                    highlights = highlights,
                    itemIndex = itemIndex,
                    footnotes = footnotes,
                    searchHighlight = searchHighlight,
                    modifier = Modifier.absolutePadding(
                        left = leftPadding,
                        top = effectiveVTop,
                        bottom = effectiveVBottom,
                    ).then(
                        colorPair.background
                            ?.takeUnless { publisherOwnsBackground }
                            ?.let { Modifier.background(it) }
                            ?: Modifier,
                    ),
                )
                return@Box
            }


            val isScrollMode = textOverride == null
            if (isScrollMode) {
                val scrollMeasurer = rememberTextMeasurer(cacheSize = 0)
                val density = LocalDensity.current
                val widthPx = with(density) { exactTextWidth.roundToPx() }
                val plan = remember(
                    element,
                    publisherFloat,
                    widthPx,
                    density.density,
                    density.fontScale,
                    settings,
                    fontSize,
                    language,
                    bookFonts,
                ) {
                    planSideBox(
                        element, scrollMeasurer, density, settings, fontSize,
                        bookFonts, language, widthPx,
                        publisherFloat = publisherFloat,
                    )
                }
                val floatBlock = element.block?.floatImage
                    ?.takeIf { !settings.bookStyles }
                if (plan != null || floatBlock != null) {
                    Column(
                        Modifier
                            .absolutePadding(
                                left = leftPadding,
                                top = effectiveVTop,
                                bottom = effectiveVBottom,
                            )
                            .width(exactTextWidth)
                            .then(
                                colorPair.background
                                    ?.takeUnless { publisherOwnsBackground }
                                    ?.let { Modifier.background(it) }
                                    ?: Modifier,
                            ),
                    ) {
                        if (floatBlock != null) {
                            AsyncImage(
                                model = File(floatBlock.path),
                                contentDescription = floatBlock.altText,
                                contentScale = ContentScale.Fit,
                                colorFilter = imageColorFilter(actuallyInvert),
                                modifier = Modifier
                                    .width(exactTextWidth)
                                    .heightIn(max = ReaderMetrics.maxImageHeight)
                                    .padding(bottom = 12.dp),
                            )
                        }
                        if (plan != null) {
                            val capLen = plan.capText?.length ?: 0
                            val beside = remember(
                                element, plan, settings.bookStyles, colorPair,
                                colors, footnotes, searchHighlight,
                            ) {
                                decorated(element.text.subSequence(capLen, plan.besideEndChar))
                            }
                            SideBoxComposite(
                                element = element,
                                sideBox = plan,
                                besideText = beside,
                                settings = settings,
                                fontSize = fontSize,
                                bookFonts = bookFonts,
                                language = language,
                                colors = colors,
                                surroundingBackground = surroundingBackground,
                                publisherBackgroundAlreadyApplied = publisherOwnsBackground,
                                totalWidthPx = widthPx,
                                invertImages = actuallyInvert,
                                highlights = highlights,
                                itemIndex = itemIndex,
                                footnotes = footnotes,
                                searchHighlight = searchHighlight,
                            )
                            if (plan.besideEndChar < element.text.length) {
                                val remainder = remember(
                                    element, plan, settings.bookStyles, colorPair,
                                    colors, footnotes, searchHighlight,
                                ) {
                                    decorated(
                                        element.text.subSequence(
                                            plan.besideEndChar, element.text.length,
                                        ),
                                    )
                                }
                                val remainderBidi = remember(remainder) {
                                    BidiLayoutText.of(remainder)
                                }
                                val tail = rememberTextFragment(
                                    highlights, itemIndex,
                                    charStart = plan.besideEndChar,
                                    length = element.text.length - plan.besideEndChar,
                                    bidi = remainderBidi,
                                )
                                Text(
                                    text = remainderBidi.display,
                                    style = ReaderMetrics
                                        .textStyle(
                                            element, settings, fontSize,
                                            isParagraphStart = false,
                                            bookFonts = bookFonts,
                                            language = language,
                                        )
                                        .copy(color = colorPair.foreground),
                                    onTextLayout = { tail?.layout = it },
                                    modifier = Modifier
                                        .width(exactTextWidth)
                                        .readerHighlights(tail, highlights),
                                    inlineContent = inlineImageContent(
                                        remainderBidi.display,
                                        actuallyInvert,
                                    ),
                                )
                            }
                        } else {
                            val whole = remember(
                                element, settings.bookStyles, colorPair,
                                colors, footnotes, searchHighlight,
                            ) {
                                decorated(element.text)
                            }
                            val wholeBidi = remember(whole) { BidiLayoutText.of(whole) }
                            val fragment = rememberTextFragment(
                                highlights, itemIndex, charStart = 0, length = element.text.length,
                                bidi = wholeBidi,
                            )
                            Text(
                                text = wholeBidi.display,
                                style = ReaderMetrics
                                    .textStyle(
                                        element, settings, fontSize,
                                        isParagraphStart, bookFonts, language,
                                    )
                                    .copy(color = colorPair.foreground),
                                onTextLayout = { fragment?.layout = it },
                                modifier = Modifier
                                    .width(exactTextWidth)
                                    .readerHighlights(fragment, highlights),
                                inlineContent = inlineImageContent(
                                    wholeBidi.display,
                                    actuallyInvert,
                                ),
                            )
                        }
                    }
                    return@Box
                }
            }

            val raw = textOverride ?: element.text
            val decoratedDisplay = remember(
                raw, settings.bookStyles, colorPair, colors, footnotes, searchHighlight,
            ) {
                decorated(raw)
            }
            val bidiDisplay = remember(decoratedDisplay) {
                BidiLayoutText.of(decoratedDisplay)
            }
            val display = bidiDisplay.display
            val fragment = rememberTextFragment(
                highlights, itemIndex, charStart, raw.length, bidiDisplay,
            )
            Text(
                text = display,
                style = ReaderMetrics
                    .textStyle(element, settings, fontSize, isParagraphStart, bookFonts, language)
                    .copy(color = colorPair.foreground),
                onTextLayout = { fragment?.layout = it },
                modifier = Modifier
                    .absolutePadding(
                        left = leftPadding,
                        top = effectiveVTop,
                        bottom = effectiveVBottom,
                    )
                    .width(exactTextWidth)
                    .then(
                        colorPair.background
                            ?.takeUnless { publisherOwnsBackground }
                            ?.let { Modifier.background(it) }
                            ?: Modifier,
                    )
                    .readerHighlights(fragment, highlights),
                inlineContent = inlineImageContent(display, actuallyInvert),
            )
        }

        is ContentElement.Heading -> {
            val raw = textOverride ?: element.styledText
            val colorPair = publisherColorPair(
                element.block,
                settings.bookStyles,
                colors.text,
                surroundingBackground,
                backgroundAlreadyApplied = publisherOwnsBackground,
            )
            val linkColor = readableReaderForeground(colors.accent, colorPair.effectiveBackground)
            val decoratedDisplay = remember(
                raw, settings.bookStyles, colorPair, linkColor, footnotes, searchHighlight, colors.accent,
            ) {
                raw.withPublisherColors(settings.bookStyles, colorPair)
                    .withFootnoteLinks(linkColor, footnotes)
                    .withSearchHighlight(searchHighlight, colors.accent.copy(alpha = 0.3f))
            }
            val bidiDisplay = remember(decoratedDisplay) {
                BidiLayoutText.of(decoratedDisplay)
            }
            val display = bidiDisplay.display
            val fragment = rememberTextFragment(
                highlights, itemIndex, charStart, raw.length, bidiDisplay,
            )
            Text(
                text = display,
                style = ReaderMetrics
                    .textStyle(element, settings, fontSize, isParagraphStart, bookFonts, language)
                    .copy(color = colorPair.foreground),
                onTextLayout = { fragment?.layout = it },
                modifier = Modifier
                    .absolutePadding(
                        left = leftPadding,
                        top = effectiveVTop,
                        bottom = effectiveVBottom,
                    )
                    .width(exactTextWidth)
                    .then(
                        colorPair.background
                            ?.takeUnless { publisherOwnsBackground }
                            ?.let { Modifier.background(it) }
                            ?: Modifier,
                    )
                    .readerHighlights(fragment, highlights),
                inlineContent = inlineImageContent(display, actuallyInvert),
            )
        }

        is ContentElement.Image -> {
            val availableImageWidthPx = with(density) { exactTextWidth.roundToPx() }
            val publisherOwnsImageWidth = publisherBoxesOwnImageWidth(
                publisherBoxes,
                settings.bookStyles,
            )
            val displayImageWidthPx = imageWidthPx(
                element = element,
                contentWidthPx = availableImageWidthPx,
                fontSizePx = fontSizePx,
                publisherOwnsWidth = publisherOwnsImageWidth,
            )
            val displayImageHeightPx = imageHeightPx ?:
                com.example.mobilereader.ui.reader.imageHeightPx(
                element = element,
                contentWidthPx = availableImageWidthPx,
                fontSizePx = fontSizePx,
                maxHeightPx = with(density) { ReaderMetrics.maxImageHeight.roundToPx() },
                publisherOwnsWidth = publisherOwnsImageWidth,
                )


            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .absolutePadding(
                        left = leftPadding,
                        top = effectiveVTop,
                        bottom = effectiveVBottom,
                    )
                    .width(exactTextWidth),
            ) {


                AsyncImage(
                    model = File(element.path),
                    contentDescription = element.altText,
                    contentScale = ContentScale.Fit,
                    colorFilter = imageColorFilter(actuallyInvert),
                    modifier = Modifier
                        .width(with(density) { displayImageWidthPx.toDp() })
                        .height(with(density) { displayImageHeightPx.coerceAtLeast(1).toDp() }),
                )
            }
        }

        ContentElement.Divider -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = effectiveVTop, bottom = effectiveVBottom),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalDivider(
                color = colors.secondaryText.copy(alpha = 0.4f),
                modifier = Modifier.width(96.dp),
            )
        }

        is ContentElement.Spacer -> Spacer(
            Modifier.height(
                ReaderMetrics.spacerHeight(element, fontSize) +
                    effectiveVTop + effectiveVBottom,
            ),
        )

        is ContentElement.Table -> {


            val scrollMeasurer = rememberTextMeasurer(cacheSize = 0)
            val density = LocalDensity.current
            val widthPx = boxGeometry.contentWidthPx
            val layout = tableLayout ?: remember(
                element,
                widthPx,
                density.density,
                density.fontScale,
                settings,
                fontSize,
                language,
                bookFonts,
            ) {
                measureTableLayout(
                    element, scrollMeasurer, density, settings,
                    fontSize, widthPx, language, bookFonts,
                )
            }
            TableBlock(
                table = element,
                layout = layout,
                rowStart = if (tableRowStart >= 0) tableRowStart else 0,
                rowEnd = if (tableRowEnd >= 0) tableRowEnd else element.rows.size,
                headerRepeated = tableHeaderRepeated,
                settings = settings,
                fontSize = fontSize,
                language = language,
                bookFonts = bookFonts,
                invertImages = actuallyInvert,
                footnotes = footnotes,
                searchHighlight = searchHighlight,
                colors = colors,
                surroundingBackground = surroundingBackground,
                publisherBackgroundAlreadyApplied = publisherOwnsBackground,
                highlights = highlights,
                itemIndex = itemIndex,
                modifier = Modifier.absolutePadding(
                    left = basePadding + publisherLeftInset,
                    top = effectiveVTop,
                    bottom = effectiveVBottom,
                ).then(
                    publisherColorPair(
                        element.block,
                        settings.bookStyles,
                        colors.text,
                        surroundingBackground,
                        backgroundAlreadyApplied = publisherOwnsBackground,
                    ).background
                        ?.takeUnless { publisherOwnsBackground }
                        ?.let { Modifier.background(it) }
                        ?: Modifier,
                ),
            )
            }
        }
    }
}
}


@Composable
private fun SystemBarsEffect(theme: AppTheme, chromeVisible: Boolean) {
    val activity = LocalActivity.current
    val currentTheme by rememberUpdatedState(theme)

    LaunchedEffect(theme, chromeVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !theme.isDark()


        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            val controller = WindowCompat.getInsetsController(window, window.decorView)


            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = !currentTheme.isDark()
        }
    }
}


@Composable
private fun TonalBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    colors: ReaderColors,
    onClick: () -> Unit,
    tint: Color = colors.onChrome,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.onChrome.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
private fun ReaderTopBar(
    visible: Boolean,
    title: String,
    author: String?,
    colors: ReaderColors,
    searchVisible: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier,
    panelFraction: () -> Float = { 0f },
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchVisible) {
        if (searchVisible) focusRequester.requestFocus()
    }

    AnimatedVisibility(
        visible = visible || searchVisible,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Surface(
            color = colors.chrome,
            contentColor = colors.onChrome,
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 4.dp,
            modifier = Modifier
                .graphicsLayer {

                    translationY = if (searchVisible) 0f else -panelFraction() * size.height * 1.25f
                }
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            AnimatedContent(
                targetState = searchVisible,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 2 }).togetherWith(
                        fadeOut() + slideOutVertically { -it / 2 }
                    ).using(SizeTransform(clip = false))
                },
                label = "topBarContent",
            ) { searching ->
                if (searching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        TonalBarButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.reader_back),
                            colors = colors,
                            onClick = onCloseSearch,
                        )
                        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.reader_search_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.secondaryText,
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onChrome),
                                cursorBrush = SolidColor(colors.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onQueryChange("")
                                        focusRequester.requestFocus()
                                        keyboard?.show()
                                    },
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.reader_search_clear),
                                    tint = colors.secondaryText,
                                )
                            }
                        } else {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        TonalBarButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.reader_back),
                            colors = colors,
                            onClick = onBack,
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!author.isNullOrBlank()) {
                                Text(
                                    text = author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        TonalBarButton(
                            icon = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.reader_search),
                            colors = colors,
                            onClick = onSearch,
                        )
                    }
                }
            }
        }

    }
}


private val chapterHeadPattern = Regex(
    """^((?:розділ|частина|глава|часть|раздел|том|книга|chapter|part|book)\s+[0-9ivxlcdm]+[.:)]?|\d+[.:)]?)\s+(.+)$""",
    RegexOption.IGNORE_CASE,
)


private fun splitChapterTitle(title: String): Pair<String, String?> {
    val cleaned = title.trim()
    val newline = cleaned.indexOf('\n')
    if (newline > 0) {
        val second = cleaned.substring(newline + 1).replace('\n', ' ').trim()
        return cleaned.take(newline).trim() to second.ifEmpty { null }
    }
    val boundary = Regex("""[.!?…]\s+""").find(cleaned)
    if (boundary != null) {
        val first = cleaned.substring(0, boundary.range.first + 1).trimEnd('.').trim()
        val second = cleaned.substring(boundary.range.last + 1).trim()
        if (second.isNotEmpty()) return first to second
    }
    chapterHeadPattern.matchEntire(cleaned)?.let { match ->
        return match.groupValues[1].trimEnd('.', ':', ')') to match.groupValues[2].trim()
    }
    return cleaned to null
}


private enum class BarPanel { SETTINGS, CONTENTS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderBottomBar(
    visible: Boolean,
    showReturnButton: Boolean,
    returnLabel: String,
    onReturn: () -> Unit,
    allowBackDismiss: Boolean,
    colors: ReaderColors,
    panelFraction: androidx.compose.runtime.MutableFloatState,

    chapterStartPages: List<Int>?,

    totalPages: Int?,

    progressValuesReady: Boolean,
    ready: ReaderState.Ready,
    book: Book?,
    currentChapter: Int,
    chapterLabel: String,
    chapterFraction: Float,
    bookFraction: Float,
    bookSegments: List<Float>,

    onSeekChapter: (Float) -> Boolean,
    onSeekBook: (Float) -> Boolean,
    settings: ReaderSettings,
    appSettings: AppSettings,
    brightness: Float?,
    onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onUpdateApp: ((AppSettings) -> AppSettings) -> Unit,
    onBrightnessChange: (Float?) -> Unit,
    onChapterClick: (ReaderNavigationTarget) -> Unit,
    onBookmarkClick: (Int) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onCopyQuote: (String) -> Unit,
    onQuoteClick: (Quote) -> Unit,
    onRemoveQuote: (String) -> Unit,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current


    val screenHeight = LocalConfiguration.current.screenHeightDp.dp


    var halfPx by remember {
        mutableFloatStateOf(with(density) { (screenHeight * 0.48f).toPx() })
    }
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    val statusTopPx = with(density) {
        WindowInsets.systemBarsIgnoringVisibility.asPaddingValues().calculateTopPadding().toPx()
    }
    val navBottomPx = with(density) {
        WindowInsets.systemBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding().toPx()
    }
    var barChromePx by remember { mutableFloatStateOf(with(density) { 190.dp.toPx() }) }
    val fullPx = (
        windowHeightPx - statusTopPx - navBottomPx -
            with(density) { 16.dp.toPx() } - barChromePx
        ).coerceAtLeast(halfPx)
    val panelMotion = remember(scope) { ReaderPanelMotion(scope) }
    val panelHeight = panelMotion.height
    val panelOpen = panelMotion.isOpen
    var panelContent by remember { mutableStateOf(BarPanel.SETTINGS) }

    fun settlePanel(targetPx: Float) = panelMotion.animateTo(targetPx)


    BackHandler(enabled = allowBackDismiss && visible &&
        (panelOpen || panelHeight.value > 0.5f)
    ) {
        settlePanel(0f)
    }

    fun settleWithVelocity(velocity: Float) {
        val position = panelHeight.value
        val anchors = listOf(0f, halfPx, fullPx)
        val target = when {

            velocity < -2500f -> fullPx
            velocity > 2500f -> 0f
            velocity < -400f -> anchors.filter { it > position + 1f }.minOrNull() ?: fullPx
            velocity > 400f -> anchors.filter { it < position - 1f }.maxOrNull() ?: 0f
            else -> anchors.minByOrNull { abs(it - position) } ?: 0f
        }
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
        settlePanel(target)
    }


    val panelDrag = Modifier.draggable(
        orientation = Orientation.Vertical,
        onDragStarted = { panelMotion.startDrag() },
        state = rememberDraggableState { delta ->
            panelMotion.snapTo((panelHeight.value - delta).coerceIn(0f, fullPx))
        },
        onDragStopped = { velocity -> settleWithVelocity(velocity) },
    )


    val panelNestedScroll = remember(halfPx, fullPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val dy = available.y
                if (dy > 0f && panelHeight.value > 0f) {
                    val target = (panelHeight.value - dy).coerceAtLeast(0f)
                    val used = panelHeight.value - target
                    panelMotion.snapTo(target)
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val anchors = listOf(0f, halfPx, fullPx)
                if (anchors.none { abs(it - panelHeight.value) < 1f }) {
                    settleWithVelocity(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }


    fun togglePanel(content: BarPanel) {
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        when {
            !panelMotion.isOpen -> {
                panelContent = content
                settlePanel(halfPx)
            }

            panelContent != content -> panelContent = content
            else -> settlePanel(0f)
        }
    }


    LaunchedEffect(visible) {
        if (!visible) {
            panelMotion.snapTo(0f)
        }
    }


    LaunchedEffect(halfPx) {
        snapshotFlow { (panelHeight.value / halfPx).coerceIn(0f, 1f) }
            .collect { panelFraction.floatValue = it }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .padding(
                    WindowInsets.systemBarsIgnoringVisibility.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ).asPaddingValues(),
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {


            ReaderReturnAffordance(


                visible = showReturnButton && !panelOpen &&
                    panelHeight.value <= with(density) { 12.dp.toPx() },
                colors = colors,
                onClick = onReturn,
                label = returnLabel,
                modifier = Modifier
                    .align(AbsoluteAlignment.Right)


                    .absoluteOffset(x = 12.dp)
                    .zIndex(1f)
                    .graphicsLayer { translationY = panelHeight.value },
            )
            Surface(
                color = colors.chrome,
                contentColor = colors.onChrome,
                shape = RoundedCornerShape(30.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size -> barChromePx = size.height - panelHeight.value },
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {


                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .then(panelDrag)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                settlePanel(if (panelMotion.isOpen) 0f else halfPx)
                            },
                    ) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(colors.onChrome.copy(alpha = 0.35f)),
                        )
                    }


                    val panelDp = with(density) { panelHeight.value.toDp() }
                    if (panelDp > 0.dp) {
                        Box(
                            Modifier
                                .height(panelDp)
                                .nestedScroll(panelNestedScroll),
                        ) {
                            when (panelContent) {
                                BarPanel.SETTINGS -> Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                ) {
                                    ReaderSettingsControls(
                                        settings = settings,
                                        appSettings = appSettings,
                                        colors = colors,
                                        publisherStyle = ready.publisherStyle,
                                        brightness = brightness,
                                        onUpdate = onUpdate,
                                        onUpdateApp = onUpdateApp,
                                        onBrightnessChange = onBrightnessChange,
                                        onPeekHeight = { measured ->
                                            val target = measured +
                                                with(density) { 10.dp.toPx() }
                                            if (abs(target - halfPx) > 2f) {
                                                val previous = halfPx
                                                halfPx = target
                                                panelMotion.updatePeek(previous, target)
                                            }
                                        },
                                    )
                                }

                                BarPanel.CONTENTS -> ReaderPanelsContent(
                                    colorScheme = appColorSchemeFor(appSettings.theme, appSettings.dynamicColor),
                                    ready = ready,
                                    book = book,
                                    currentChapter = currentChapter,
                                    chapterStartPages = chapterStartPages,
                                    dragModifier = panelDrag,
                                    onChapterClick = { index ->
                                        settlePanel(0f)
                                        onChapterClick(index)
                                    },
                                    onBookmarkClick = { flatIndex ->
                                        settlePanel(0f)
                                        onBookmarkClick(flatIndex)
                                    },
                                    onRemoveBookmark = onRemoveBookmark,
                                    onCopyQuote = onCopyQuote,
                                    onQuoteClick = { quote ->
                                        settlePanel(0f)
                                        onQuoteClick(quote)
                                    },
                                    onRemoveQuote = onRemoveQuote,
                                    bookmarked = bookmarked,
                                    onToggleBookmark = onToggleBookmark,
                                )
                            }
                        }
                    }


                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TonalBarButton(
                            icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                            contentDescription = stringResource(R.string.reader_contents),
                            colors = colors,
                            onClick = { togglePanel(BarPanel.CONTENTS) },
                            tint = if (panelOpen && panelContent == BarPanel.CONTENTS) {
                                colors.accent
                            } else {
                                colors.onChrome
                            },
                        )


                        val titleLines = remember(chapterLabel) { splitChapterTitle(chapterLabel) }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .then(panelDrag)
                                .padding(horizontal = 8.dp),
                        ) {
                            Text(
                                text = titleLines.first,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            titleLines.second?.let { secondLine ->


                                Text(
                                    text = secondLine.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryText,
                                    letterSpacing = 1.2.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 7.sp,
                                        maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        stepSize = 0.5.sp,
                                    ),
                                )
                            }
                        }
                        TonalBarButton(
                            icon = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.reader_settings),
                            colors = colors,
                            onClick = { togglePanel(BarPanel.SETTINGS) },
                            tint = if (panelOpen && panelContent == BarPanel.SETTINGS) {
                                colors.accent
                            } else {
                                colors.onChrome
                            },
                        )
                    }

                    Spacer(Modifier.height(10.dp))


                    val chapterSpan = if (chapterStartPages != null && totalPages != null) {
                        val startPage = chapterStartPages.getOrNull(currentChapter) ?: 0
                        val endPage = chapterStartPages.getOrNull(currentChapter + 1)?.minus(1)
                            ?: (totalPages - 1)
                        (endPage - startPage).coerceAtLeast(0)
                    } else {
                        null
                    }
                    ProgressRow(
                        label = stringResource(R.string.reader_track_chapter).uppercase(),
                        fraction = chapterFraction,
                        onSeek = onSeekChapter,
                        colors = colors,
                        pagesLeftOf = chapterSpan?.let { span ->
                            { f -> ((1f - f) * span).roundToInt() }
                        },
                        showValue = progressValuesReady,
                    )
                    Spacer(Modifier.height(6.dp))
                    ProgressRow(
                        label = stringResource(R.string.reader_track_book).uppercase(),
                        fraction = bookFraction,
                        onSeek = onSeekBook,
                        colors = colors,
                        segments = bookSegments,
                        pagesLeftOf = totalPages?.let { total ->
                            { f -> ((1f - f) * (total - 1).coerceAtLeast(0)).roundToInt() }
                        },
                        showValue = progressValuesReady,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}


@Composable
private fun ProgressRow(
    label: String,
    fraction: Float,

    onSeek: (Float) -> Boolean,
    colors: ReaderColors,
    segments: List<Float> = emptyList(),

    pagesLeftOf: ((Float) -> Int)? = null,

    showValue: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val shown = (dragValue ?: fraction).coerceIn(0f, 1f)


    LaunchedEffect(fraction, dragging, dragValue) {
        val held = dragValue
        if (!dragging && held != null) {
            if (abs(fraction - held) < 0.02f) {
                dragValue = null
            } else {
                delay(600.milliseconds)
                dragValue = null
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondaryText,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            modifier = Modifier.width(72.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val value = (offset.x / size.width).coerceIn(0f, 1f)
                        dragValue = value
                        if (onSeek(value)) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        } else {
                            dragValue = null
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            dragging = false
                            val committed = dragValue?.let(onSeek) == true
                            if (!committed) dragValue = null
                        },
                        onDragCancel = {
                            dragging = false
                            dragValue = null
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val previous = dragValue ?: 0f
                        val updated = (previous + dragAmount / size.width).coerceIn(0f, 1f)
                        if ((updated * 100).roundToInt() != (previous * 100).roundToInt()) {
                            haptics.performHapticFeedback(
                                HapticFeedbackType.SegmentFrequentTick,
                            )
                        }
                        dragValue = updated
                    }
                },
        ) {
            val active = colors.accent
            val inactive = colors.secondaryText.copy(alpha = 0.25f)
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val trackHeight = 6.dp.toPx()
                val top = (size.height - trackHeight) / 2
                val radius = CornerRadius(trackHeight / 2)

                val minStep = 4.dp.toPx()
                val bounds = mutableListOf(0f)
                val raw = segments.filter { it > 0.001f && it < 0.999f }.distinct().sorted()
                var lastBound = 0f
                for (s in raw) {
                    if ((s - lastBound) * w >= minStep) {
                        bounds.add(s)
                        lastBound = s
                    }
                }
                if ((1f - lastBound) * w >= minStep || bounds.size == 1) {
                    bounds.add(1f)
                } else {
                    bounds[bounds.lastIndex] = 1f
                }

                val position = shown * w

                drawRoundRect(
                    color = inactive,
                    topLeft = Offset(0f, top),
                    size = Size(w, trackHeight),
                    cornerRadius = radius,
                )


                if (position > 0) {
                    drawRoundRect(
                        color = active,
                        topLeft = Offset(0f, top),
                        size = Size(position, trackHeight),
                        cornerRadius = radius,
                    )
                }


                val tickWidth = 1.dp.toPx()
                for (i in 1 until bounds.size - 1) {
                    val x = bounds[i] * w
                    drawRect(
                        color = colors.chrome.copy(alpha = 0.7f),
                        topLeft = Offset(x - tickWidth / 2, top),
                        size = Size(tickWidth, trackHeight)
                    )
                }


                val thumbWidth = 4.dp.toPx()
                val thumbHeight = 18.dp.toPx()
                val thumbX = position.coerceIn(thumbWidth / 2, w - thumbWidth / 2)
                drawRoundRect(
                    color = active,
                    topLeft = Offset(thumbX - thumbWidth / 2, (size.height - thumbHeight) / 2),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(thumbWidth / 2),
                )
            }
        }
        val leftWord = stringResource(R.string.reader_percent_left)
        Text(
            text = buildAnnotatedString {
                if (!showValue) {
                    withStyle(SpanStyle(color = colors.secondaryText)) { append("…") }
                    return@buildAnnotatedString
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.onChrome)) {
                    append(
                        pagesLeftOf?.let { "${it(shown)}" }
                            ?: "${((1f - shown) * 100).roundToInt()}%",
                    )
                }
                withStyle(SpanStyle(color = colors.secondaryText)) {
                    append(" ")
                    append(leftWord)
                }
            },
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(84.dp),
        )
    }
}
