package com.example.mobilereader.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DensityLarge
import androidx.compose.material.icons.rounded.DensitySmall
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobilereader.R
import com.example.mobilereader.data.AppSettings
import com.example.mobilereader.data.AppTheme
import com.example.mobilereader.data.PageMargins
import com.example.mobilereader.data.ReaderFont
import com.example.mobilereader.data.model.Book
import com.example.mobilereader.data.model.Bookmark as SavedBookmark
import com.example.mobilereader.data.model.Quote
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.TranslationSettings
import kotlin.math.pow
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPanelsContent(
    colorScheme: ColorScheme,
    ready: ReaderState.Ready,
    book: Book?,
    currentChapter: Int,
    chapterStartPages: List<Int>?,

    dragModifier: Modifier = Modifier,
    onChapterClick: (ReaderNavigationTarget) -> Unit,
    onBookmarkClick: (SavedBookmark) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onCopyQuote: (String) -> Unit,
    onQuoteClick: (Quote) -> Unit,
    onRemoveQuote: (String) -> Unit,

    bookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }


    MaterialTheme(colorScheme = colorScheme) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = tab,
                containerColor = colorScheme.surfaceContainerLow,
                contentColor = colorScheme.primary,
                divider = {},
                modifier = dragModifier.clip(RoundedCornerShape(16.dp)),
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    unselectedContentColor = colorScheme.onSurfaceVariant,
                    text = { Text(stringResource(R.string.reader_contents)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    unselectedContentColor = colorScheme.onSurfaceVariant,
                    text = { Text(stringResource(R.string.reader_bookmarks)) },
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    unselectedContentColor = colorScheme.onSurfaceVariant,
                    text = { Text(stringResource(R.string.reader_quotes)) },
                )
            }

            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> ContentsTab(ready, currentChapter, chapterStartPages, onChapterClick)
                    1 -> BookmarksTab(
                        ready, book, bookmarked, onToggleBookmark,
                        onBookmarkClick, onRemoveBookmark,
                    )
                    else -> QuotesTab(ready, book, onCopyQuote, onQuoteClick, onRemoveQuote)
                }
            }
        }
    }
}
@Composable
private fun ContentsTab(
    ready: ReaderState.Ready,
    currentChapter: Int,
    chapterStartPages: List<Int>?,
    onChapterClick: (ReaderNavigationTarget) -> Unit,
) {
    val entries = ready.navigation
    val tree = remember(entries) { ReaderContentsTree(entries.map { it.depth }) }
    val currentEntryIndex = remember(entries, currentChapter) {
        entries.indexOfFirst { entry ->
            (entry.target as? ReaderNavigationTarget.ReadingOrder)?.chapterIndex == currentChapter
        }.takeIf { it >= 0 }
    }
    val collapsed = remember(tree, currentEntryIndex) {
        val expanded = currentEntryIndex?.let { tree.ancestorsOf(it) + it }.orEmpty().toSet()
        mutableStateMapOf<Int, Boolean>().apply {
            tree.isGroup.indices.forEach { index ->
                if (tree.isGroup[index]) put(index, index !in expanded)
            }
        }
    }
    val visibleIndices by remember(tree, collapsed) {
        derivedStateOf { tree.visibleIndices { collapsed[it] == true } }
    }
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerLow),
    ) {
        itemsIndexed(
            items = visibleIndices,
            key = { _, index -> index },
            contentType = { _, index -> if (tree.isGroup[index]) "group" else "chapter" },
        ) { visibleIndex, index ->
            val entry = entries[index]
            val group = tree.isGroup[index]
            val isCurrent = index == currentEntryIndex
            val previous = visibleIndices.getOrNull(visibleIndex - 1)
            val startsSection = group || previous != null && tree.parent[previous] != tree.parent[index]
            val title = entry.title ?: stringResource(R.string.reader_chapter_n, index + 1)
            val titleParts = remember(title, group) {
                if (group) contentsGroupTitle(title) else null to title.replace('\n', ' ')
            }
            val rowBackground = contentsRowBackground(scheme, current = isCurrent, group = group)


            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 14.dp * tree.depth[index].coerceAtMost(4),
                        top = if (startsSection && visibleIndex > 0) 10.dp else 2.dp,
                        bottom = 2.dp,
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(rowBackground)
                    .semantics { selected = isCurrent }
                    .clickable { onChapterClick(entry.target) }
                    .heightIn(min = 48.dp)
                    .padding(start = 14.dp, end = if (group) 2.dp else 14.dp),
            ) {
                Column(Modifier.weight(1f).padding(vertical = if (group) 14.dp else 12.dp)) {
                    titleParts.first?.let { prefix ->
                        Text(
                            text = prefix,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = titleParts.second,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent || group) FontWeight.SemiBold else FontWeight.Normal,
                        color = scheme.onSurface,
                    )
                }
                val page = (entry.target as? ReaderNavigationTarget.ReadingOrder)
                    ?.chapterIndex?.let { chapterStartPages?.getOrNull(it) }
                if (page != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${page + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (group) {
                    val isCollapsed = collapsed[index] == true
                    val rotation by animateFloatAsState(if (isCollapsed) 0f else 180f, label = "contentsChevron")
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        collapsed[index] = !isCollapsed
                    }) {
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = stringResource(
                                if (isCollapsed) R.string.reader_expand_section else R.string.reader_collapse_section,
                            ),
                            tint = scheme.primary,
                            modifier = Modifier.rotate(rotation),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksTab(
    ready: ReaderState.Ready,
    book: Book?,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onBookmarkClick: (SavedBookmark) -> Unit,
    onRemoveBookmark: (String) -> Unit,
) {
    val bookmarks = book?.bookmarks.orEmpty()
    Column(Modifier.fillMaxSize()) {


        Surface(
            onClick = onToggleBookmark,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (bookmarked) 0.14f else 0.08f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Icon(
                    imageVector = if (bookmarked) {
                        Icons.Rounded.Bookmark
                    } else {
                        Icons.Rounded.BookmarkBorder
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        if (bookmarked) {
                            R.string.reader_bookmark_remove_here
                        } else {
                            R.string.reader_bookmark_here
                        },
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (bookmarks.isEmpty()) {
            EmptyTabHint(stringResource(R.string.reader_bookmarks_empty))
            return@Column
        }
        BookmarkList(ready, bookmarks, onBookmarkClick, onRemoveBookmark)
    }
}

@Composable
private fun BookmarkList(
    ready: ReaderState.Ready,
    bookmarks: List<SavedBookmark>,
    onBookmarkClick: (SavedBookmark) -> Unit,
    onRemoveBookmark: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
        items(bookmarks, key = { it.id }) { bookmark ->
            val anchor = ready.bookmarkAnchor(bookmark)
            val available = anchor != null
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()


                    .clickable(enabled = available) {
                        onBookmarkClick(bookmark)
                    }
                    .padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (anchor == null) {
                            stringResource(R.string.reader_bookmark_orphaned)
                        } else {
                            val chapterIndex = ready.chapterAt(anchor.itemIndex)
                            ready.chapterTitles.getOrNull(chapterIndex)
                                ?: stringResource(R.string.reader_chapter_n, chapterIndex + 1)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (!available) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = bookmark.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!available) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onRemoveBookmark(bookmark.id) }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.reader_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotesTab(
    ready: ReaderState.Ready,
    book: Book?,
    onCopyQuote: (String) -> Unit,
    onQuoteClick: (Quote) -> Unit,
    onRemoveQuote: (String) -> Unit,
) {
    val quotes = book?.quotes.orEmpty()
    if (quotes.isEmpty()) {
        EmptyTabHint(stringResource(R.string.reader_quotes_empty))
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
        items(quotes, key = { it.id }) { quote ->


            val range = ready.quoteRange(quote)
            val available = range != null
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = available) {
                        onQuoteClick(quote)
                    }
                    .padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (range == null) {
                            stringResource(R.string.reader_bookmark_orphaned)
                        } else {
                            val chapterIndex = ready.chapterAt(range.start.itemIndex)
                            ready.chapterTitles.getOrNull(chapterIndex)
                                ?: stringResource(R.string.reader_chapter_n, chapterIndex + 1)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (available) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "“${quote.text}”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = if (available) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                    )
                }
                IconButton(onClick = { onCopyQuote(quote.text) }) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.selection_copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onRemoveQuote(quote.id) }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.reader_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTabHint(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
@Composable
internal fun sheetMaxContentHeight(): Dp =
    LocalConfiguration.current.screenHeightDp.dp - 140.dp


private enum class ReaderSettingsCategory(
    val titleResource: Int,
    val icon: ImageVector,
) {
    APPEARANCE(R.string.reader_settings_tab_appearance, Icons.Rounded.Palette),
    PAGES(R.string.reader_settings_tab_pages, Icons.AutoMirrored.Rounded.MenuBook),
    TRANSLATION(R.string.reader_settings_tab_translation, Icons.Rounded.Translate),
}

@Composable
fun ReaderSettingsControls(
    selectedCategoryIndex: Int,
    onCategorySelected: (Int) -> Unit,
    settings: ReaderSettings,
    appSettings: AppSettings,
    colors: ReaderColors,
    brightness: Float?,
    onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onUpdateApp: ((AppSettings) -> AppSettings) -> Unit,
    onBrightnessChange: (Float?) -> Unit,
    translationSettings: TranslationSettings,
    onSaveTranslationSettings: (TranslationSettings) -> Unit,
) {
    val safeCategoryIndex = selectedCategoryIndex.coerceIn(0, ReaderSettingsCategory.entries.lastIndex)
    val selectedCategory = ReaderSettingsCategory.entries[safeCategoryIndex]
    val scrollState = rememberScrollState()
    var translationPageCount by rememberSaveable(translationSettings.contextPageCount) {
        mutableIntStateOf(translationSettings.contextPageCount)
    }
    var translationInstruction by rememberSaveable(translationSettings.instruction) {
        mutableStateOf(translationSettings.instruction)
    }
    var translationServerAddress by rememberSaveable(translationSettings.serverAddress) {
        mutableStateOf(translationSettings.serverAddress)
    }

    LaunchedEffect(selectedCategory) {
        scrollState.scrollTo(0)
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            ReaderSettingsCategoryContent(
                category = selectedCategory,
                settings = settings,
                appSettings = appSettings,
                colors = colors,
                brightness = brightness,
                onUpdate = onUpdate,
                onUpdateApp = onUpdateApp,
                onBrightnessChange = onBrightnessChange,
                translationSettings = TranslationSettings(
                    contextPageCount = translationPageCount,
                    instruction = translationInstruction,
                    serverAddress = translationServerAddress,
                ),
                onTranslationSettingsChange = { updated ->
                    translationPageCount = updated.contextPageCount
                    translationInstruction = updated.instruction
                    translationServerAddress = updated.serverAddress
                },
                onSaveTranslationSettings = {
                    onSaveTranslationSettings(
                        TranslationSettings(
                            contextPageCount = translationPageCount,
                            instruction = translationInstruction.trim(),
                            serverAddress = translationServerAddress.trim(),
                        ),
                    )
                },
            )
        }
        SecondaryTabRow(
            selectedTabIndex = safeCategoryIndex,
            modifier = Modifier.fillMaxWidth(),
            containerColor = colors.chrome,
            contentColor = colors.accent,
            divider = {},
        ) {
            ReaderSettingsCategory.entries.forEachIndexed { index, category ->
                Tab(
                    selected = safeCategoryIndex == index,
                    onClick = { onCategorySelected(index) },
                    unselectedContentColor = colors.secondaryText,
                    icon = {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(category.titleResource),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun TranslationSettingsControls(
    settings: TranslationSettings,
    colors: ReaderColors,
    onChange: (TranslationSettings) -> Unit,
    onSave: () -> Unit,
) {
    Spacer(Modifier.height(20.dp))
    SettingsGroup(colors) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.contextual_translation_page_count_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onChrome,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        onChange(
                            settings.copy(
                                contextPageCount = (settings.contextPageCount - 1).coerceAtLeast(1),
                            ),
                        )
                    },
                    enabled = settings.contextPageCount > 1,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.contextual_translation_fewer_pages),
                    )
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.contextual_translation_page_count,
                        settings.contextPageCount,
                        settings.contextPageCount,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onChrome,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                IconButton(
                    onClick = {
                        onChange(
                            settings.copy(
                                contextPageCount = (settings.contextPageCount + 1).coerceAtMost(5),
                            ),
                        )
                    },
                    enabled = settings.contextPageCount < 5,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.contextual_translation_more_pages),
                    )
                }
            }
            OutlinedTextField(
                value = settings.instruction,
                onValueChange = { onChange(settings.copy(instruction = it.take(2000))) },
                label = { Text(stringResource(R.string.contextual_translation_instruction)) },
                supportingText = {
                    Text(stringResource(R.string.contextual_translation_instruction_description))
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.serverAddress,
                onValueChange = { onChange(settings.copy(serverAddress = it)) },
                label = { Text(stringResource(R.string.contextual_translation_server_address)) },
                supportingText = {
                    Text(stringResource(R.string.contextual_translation_server_description))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSave,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.contextual_translation_save))
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ReaderSettingsCategoryContent(
    category: ReaderSettingsCategory,
    settings: ReaderSettings,
    appSettings: AppSettings,
    colors: ReaderColors,
    brightness: Float?,
    onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onUpdateApp: ((AppSettings) -> AppSettings) -> Unit,
    onBrightnessChange: (Float?) -> Unit,
    translationSettings: TranslationSettings,
    onTranslationSettingsChange: (TranslationSettings) -> Unit,
    onSaveTranslationSettings: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current


    Column {
        when (category) {
            ReaderSettingsCategory.APPEARANCE -> {
                Spacer(Modifier.height(8.dp))

                val themes = AppTheme.entries
                ChipRow(


                    options = listOf(
                        stringResource(R.string.theme_light),
                        stringResource(R.string.theme_beige),
                        stringResource(R.string.theme_midnight),
                    ),
                    selectedIndex = themes.indexOf(appSettings.theme),
                    colors = colors,
                    onSelect = { index -> onUpdateApp { it.copy(theme = themes[index]) } },
                    leading = { index ->
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(readerColors(themes[index]).background)
                                .border(1.dp, colors.onChrome.copy(alpha = 0.25f), CircleShape),
                        )
                    },
                )

                Spacer(Modifier.height(20.dp))


                val autoBrightness = brightness == null
                fun readSystemBrightness(): Float = runCatching {
                    android.provider.Settings.System.getInt(
                        context.contentResolver,
                        android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    ) / 255f
                }.getOrDefault(0.5f)
                var systemBrightness by remember { mutableFloatStateOf(readSystemBrightness()) }
                DisposableEffect(Unit) {
                    val observer = object : android.database.ContentObserver(
                        android.os.Handler(android.os.Looper.getMainLooper()),
                    ) {
                        override fun onChange(selfChange: Boolean) {
                            systemBrightness = readSystemBrightness()
                        }
                    }
                    context.contentResolver.registerContentObserver(
                        android.provider.Settings.System.getUriFor(
                            android.provider.Settings.System.SCREEN_BRIGHTNESS,
                        ),
                        false,
                        observer,
                    )
                    onDispose { context.contentResolver.unregisterContentObserver(observer) }
                }
                PanelSectionLabel(stringResource(R.string.reader_brightness), colors)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.WbSunny,
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(16.dp),
                    )
                    SettingSlider(
                        value = brightnessPosition(brightness ?: systemBrightness),
                        onChange = { position -> onBrightnessChange(brightnessFromPosition(position)) },
                        colors = colors,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    Icon(
                        Icons.Rounded.WbSunny,
                        contentDescription = null,
                        tint = colors.onChrome,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (autoBrightness) colors.accent else Color.Transparent,
                            )
                            .border(
                                width = 1.dp,
                                color = if (autoBrightness) {
                                    Color.Transparent
                                } else {
                                    colors.onChrome.copy(alpha = 0.3f)
                                },
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                onBrightnessChange(null)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.BrightnessAuto,
                            contentDescription = null,
                            tint = if (autoBrightness) colors.background else colors.secondaryText,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.reader_brightness_auto),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (autoBrightness) FontWeight.Bold else FontWeight.Medium,
                            color = if (autoBrightness) colors.background else colors.secondaryText,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))

                SettingsGroup(colors) {
                    SettingToggleRow(
                        icon = Icons.Rounded.InvertColors,
                        title = stringResource(R.string.reader_invert_images),
                        subtitle = stringResource(R.string.reader_invert_images_subtitle),
                        colors = colors,
                        checked = appSettings.autoInvertImages,
                        onCheckedChange = { onUpdateApp { s -> s.copy(autoInvertImages = it) } },
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        PanelSectionLabel(stringResource(R.string.reader_line_height), colors)
                        StepperGroup(
                            value = "×%.1f".format(settings.lineHeight),
                            colors = colors,
                            onDecrease = {
                                onUpdate {
                                    it.copy(
                                        lineHeight = (((it.lineHeight - 0.1f) * 10).roundToInt() / 10f)
                                            .coerceAtLeast(1.2f),
                                    )
                                }
                            },
                            onIncrease = {
                                onUpdate {
                                    it.copy(
                                        lineHeight = (((it.lineHeight + 0.1f) * 10).roundToInt() / 10f)
                                            .coerceAtMost(2.0f),
                                    )
                                }
                            },
                            decrease = {
                                Icon(
                                    Icons.Rounded.DensitySmall,
                                    contentDescription = null,
                                    tint = colors.onChrome,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            increase = {
                                Icon(
                                    Icons.Rounded.DensityLarge,
                                    contentDescription = null,
                                    tint = colors.onChrome,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        PanelSectionLabel(stringResource(R.string.reader_font_size), colors)
                        StepperGroup(
                            value = stringResource(
                                R.string.reader_font_size_pt,
                                settings.fontSizeSp.roundToInt(),
                            ),
                            colors = colors,
                            onDecrease = {
                                onUpdate { it.copy(fontSizeSp = (it.fontSizeSp - 1f).coerceAtLeast(12f)) }
                            },
                            onIncrease = {
                                onUpdate { it.copy(fontSizeSp = (it.fontSizeSp + 1f).coerceAtMost(32f)) }
                            },
                            decrease = {
                                Text(
                                    "A−",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                )
                            },
                            increase = {
                                Text(
                                    "A+",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))


                PanelSectionLabel(stringResource(R.string.reader_font), colors)
                ChipRow(
                    options = listOf(
                        stringResource(R.string.reader_font_literata),
                        stringResource(R.string.reader_font_serif),
                        stringResource(R.string.reader_font_sans),
                    ),
                    optionFontFamilies = listOf(
                        com.example.mobilereader.ui.theme.LiterataFamily,
                        androidx.compose.ui.text.font.FontFamily.Serif,
                        androidx.compose.ui.text.font.FontFamily.SansSerif,
                    ),
                    selectedIndex = ReaderFont.entries.indexOf(settings.font),
                    colors = colors,
                    onSelect = { index ->
                        onUpdate { current ->
                            current.copy(font = ReaderFont.entries[index])
                        }
                    },
                )

                Spacer(Modifier.height(24.dp))
            }
            ReaderSettingsCategory.PAGES -> {
                Spacer(Modifier.height(18.dp))

                PanelSectionLabel(stringResource(R.string.reader_section_margins), colors)
                val marginOptions = PageMargins.entries
                ChipRow(
                    options = listOf(
                        stringResource(R.string.reader_margins_narrow),
                        stringResource(R.string.reader_margins_normal),
                        stringResource(R.string.reader_margins_wide),
                    ),
                    selectedIndex = marginOptions.indexOf(settings.pageMargins),
                    colors = colors,
                    onSelect = { index ->
                        onUpdate { current ->
                            current.copy(pageMargins = marginOptions[index])
                        }
                    },
                )

                Spacer(Modifier.height(20.dp))
                SettingsGroup(colors) {
                    SettingToggleRow(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = stringResource(R.string.reader_section_page_anim),
                        colors = colors,
                        checked = settings.pageTurnAnimationEnabled,
                        onCheckedChange = { onUpdate { s -> s.copy(pageTurnAnimationEnabled = it) } },
                    )
                    PanelDivider(colors)
                    SettingToggleRow(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = stringResource(R.string.reader_chapter_new_page),
                        colors = colors,
                        checked = settings.startChaptersOnNewPage,
                        onCheckedChange = { onUpdate { s -> s.copy(startChaptersOnNewPage = it) } },
                    )
                    PanelDivider(colors)
                    SettingToggleRow(
                        icon = Icons.Rounded.Tag,
                        title = stringResource(R.string.reader_show_chapter_pages),
                        colors = colors,
                        checked = settings.showChapterPagesLeft,
                        onCheckedChange = { onUpdate { s -> s.copy(showChapterPagesLeft = it) } },
                    )
                    PanelDivider(colors)
                    SettingToggleRow(
                        icon = Icons.Rounded.TouchApp,
                        title = stringResource(R.string.reader_edge_tap_paging),
                        colors = colors,
                        checked = settings.edgeTapPagingEnabled,
                        onCheckedChange = { enabled ->
                            onUpdate { current ->
                                current.copy(edgeTapPagingEnabled = enabled)
                            }
                        },
                    )
                    PanelDivider(colors)
                    SettingToggleRow(
                        icon = Icons.Rounded.WbSunny,
                        title = stringResource(R.string.reader_brightness_gesture),
                        colors = colors,
                        checked = settings.brightnessGestureEnabled,
                        onCheckedChange = { enabled ->
                            onUpdate { current ->
                                current.copy(brightnessGestureEnabled = enabled)
                            }
                        },
                    )
                    PanelDivider(colors)
                    SettingToggleRow(
                        icon = Icons.Rounded.Lightbulb,
                        title = stringResource(R.string.settings_keep_screen_on),
                        colors = colors,
                        checked = appSettings.keepScreenOn,
                        onCheckedChange = { onUpdateApp { s -> s.copy(keepScreenOn = it) } },
                    )
                    PanelDivider(colors)
                    SettingToggleRow(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        title = stringResource(R.string.reader_volume_keys),
                        colors = colors,
                        checked = appSettings.volumeKeyPaging,
                        onCheckedChange = { onUpdateApp { s -> s.copy(volumeKeyPaging = it) } },
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
            ReaderSettingsCategory.TRANSLATION -> {
                TranslationSettingsControls(
                    settings = translationSettings,
                    colors = colors,
                    onChange = onTranslationSettingsChange,
                    onSave = onSaveTranslationSettings,
                )
            }
        }
    }
}


@Composable
private fun StepperGroup(
    value: String,
    colors: ReaderColors,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decrease: @Composable () -> Unit,
    increase: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.onChrome.copy(alpha = 0.06f))
            .padding(5.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 46.dp, height = 42.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colors.onChrome.copy(alpha = 0.08f))
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    onDecrease()
                },
        ) { decrease() }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 46.dp, height = 42.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colors.onChrome.copy(alpha = 0.08f))
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    onIncrease()
                },
        ) { increase() }
    }
}


@Composable
private fun ChipRow(
    options: List<String>,
    selectedIndex: Int,
    colors: ReaderColors,
    onSelect: (Int) -> Unit,
    leading: (@Composable (Int) -> Unit)? = null,

    optionFontFamilies: List<androidx.compose.ui.text.font.FontFamily?>? = null,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(colors.onChrome.copy(alpha = 0.08f))
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            ) {
                if (leading != null) {
                    leading(index)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = optionFontFamilies?.getOrNull(index),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) colors.background else colors.onChrome,
                    maxLines = 1,
                )
            }
        }
    }
}


internal fun brightnessPosition(value: Float): Float = value.coerceIn(0f, 1f).pow(1f / 2.2f)

internal fun brightnessFromPosition(position: Float): Float =
    position.coerceIn(0f, 1f).pow(2.2f)


@Composable
private fun SettingSlider(
    value: Float,
    onChange: (Float) -> Unit,
    colors: ReaderColors,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val shown = value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOnChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    currentOnChange((change.position.x / size.width).coerceIn(0f, 1f))
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
            val position = shown * w

            drawRoundRect(inactive, Offset(0f, top), Size(w, trackHeight), radius)
            if (position > 0f) {
                drawRoundRect(active, Offset(0f, top), Size(position, trackHeight), radius)
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
}

@Composable
private fun PanelSectionLabel(
    text: String,
    colors: ReaderColors,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = colors.secondaryText,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun SettingsGroup(colors: ReaderColors, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.onChrome.copy(alpha = 0.06f)),
    ) {
        content()
    }
}

@Composable
private fun PanelDivider(colors: ReaderColors) {
    HorizontalDivider(
        color = colors.onChrome.copy(alpha = 0.08f),
        modifier = Modifier.padding(start = 54.dp),
    )
}

@Composable
private fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    colors: ReaderColors,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,


    fromBook: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val lockedNote = stringResource(R.string.reader_set_by_book)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onChrome,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            val caption = if (fromBook) lockedNote else subtitle
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fromBook) colors.accent else colors.secondaryText,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.chrome,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.secondaryText,
                uncheckedTrackColor = colors.onChrome.copy(alpha = 0.06f),
                uncheckedBorderColor = colors.secondaryText.copy(alpha = 0.5f),


                disabledCheckedThumbColor = colors.chrome,
                disabledCheckedTrackColor = colors.accent.copy(alpha = 0.55f),
                disabledUncheckedThumbColor = colors.secondaryText.copy(alpha = 0.55f),
                disabledUncheckedTrackColor = colors.onChrome.copy(alpha = 0.06f),
                disabledUncheckedBorderColor = colors.secondaryText.copy(alpha = 0.3f),
            ),
            onCheckedChange = { value ->
                haptics.performHapticFeedback(
                    if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
                onCheckedChange(value)
            },
        )
    }
}
