package com.example.mobilereader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mobilereader.data.AppSettings
import com.example.mobilereader.data.AppTheme
import com.example.mobilereader.data.ReaderSettings
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.ui.preview.PreviewBooks
import com.example.mobilereader.ui.theme.MobileReaderTheme

private fun previewReady() = ReaderState.Ready(
    book = PreviewBooks.current,
    items = listOf(
        ReaderItem(0, ContentElement.Heading("Chapter One", level = 1)),
        ReaderItem(0, ContentElement.Paragraph(AnnotatedString("The road wound across the moor."))),
        ReaderItem(1, ContentElement.Heading("Chapter Two", level = 1)),
    ),
    chapterStarts = listOf(0, 2),
    chapterTitles = listOf("Chapter One", "Chapter Two"),
    chapterDepths = listOf(0, 0),
    notes = emptyMap(),
)

@Preview(name = "Reader · contents", showBackground = true, widthDp = 412, heightDp = 500)
@Composable
private fun ReaderContentsPreview() {
    MobileReaderTheme(AppTheme.WHITE) {
        ReaderPanelsContent(
            colorScheme = MaterialTheme.colorScheme,
            ready = previewReady(),
            book = PreviewBooks.current,
            currentChapter = 0,
            chapterStartPages = listOf(0, 10),
            onChapterClick = {},
            onBookmarkClick = {},
            onRemoveBookmark = {},
            onCopyQuote = {},
            onQuoteClick = {},
            onRemoveQuote = {},
        )
    }
}

@Preview(name = "Reader · display controls", showBackground = true, widthDp = 412, heightDp = 650)
@Composable
private fun ReaderControlsPreview() {
    MobileReaderTheme(AppTheme.WHITE) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            ReaderSettingsControls(
                settings = ReaderSettings(),
                appSettings = AppSettings(),
                colors = readerColors(AppTheme.WHITE),
                brightness = null,
                onUpdate = {},
                onUpdateApp = {},
                onBrightnessChange = {},
            )
        }
    }
}

@Preview(name = "Reader · search", showBackground = true, widthDp = 412, heightDp = 650)
@Composable
private fun ReaderSearchPreview() {
    MobileReaderTheme(AppTheme.WHITE) {
        ReaderSearchOverlay(
            visible = true,
            query = "moor",
            colors = readerColors(AppTheme.WHITE),
            chapterTitles = listOf("Chapter One"),
            results = SearchResults(
                query = "moor",
                matches = listOf(SearchMatch(1, 0, 26, "The road wound across the ", "moor", ".")),
                totalCount = 1,
            ),
            pages = null,
            onResultClick = {},
            onGoToPage = {},
            onClose = {},
        )
    }
}

@Preview(name = "Reader · finished", showBackground = true, widthDp = 412, heightDp = 650)
@Composable
private fun ReaderCompletionPreview() {
    MobileReaderTheme(AppTheme.WHITE) {
        Box(Modifier.fillMaxSize().background(readerColors(AppTheme.WHITE).background)) {
            CompletionPage(PreviewBooks.current, readerColors(AppTheme.WHITE), Modifier.fillMaxSize())
        }
    }
}
