package com.example.mobilereader

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.mobilereader.epub.EpubBookContent
import com.example.mobilereader.epub.EpubContentElement
import com.example.mobilereader.epub.EpubMetadata
import com.example.mobilereader.epub.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface OpenBookState {
    data object NoBook : OpenBookState
    data object Loading : OpenBookState
    data class Ready(val bookMetadata: EpubMetadata, val bookContent: EpubBookContent) : OpenBookState
    data class Failed(val problemText: String) : OpenBookState
}

class MainActivity : ComponentActivity() {
    private var openBookState by mutableStateOf<OpenBookState>(OpenBookState.NoBook)
    private val selectedBookFile by lazy { File(filesDir, "current-book.epub") }
    private val openBookPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { selectedUri ->
        if (selectedUri != null) importSelectedBook(selectedUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MobileReaderScreen(openBookState) {
                        openBookPicker.launch(arrayOf("application/epub+zip", "application/octet-stream", "application/zip"))
                    }
                }
            }
        }
        if (selectedBookFile.isFile) loadBook(selectedBookFile)
    }

    private fun importSelectedBook(selectedUri: Uri) {
        openBookState = OpenBookState.Loading
        lifecycleScope.launch {
            val loadedBook = withContext(Dispatchers.IO) {
                runCatching {
                    val temporaryFile = File(cacheDir, "incoming-book.epub")
                    contentResolver.openInputStream(selectedUri).use { inputStream ->
                        requireNotNull(inputStream) { "Cannot open selected file" }
                        temporaryFile.outputStream().use { outputStream ->
                            val bufferBytes = ByteArray(16 * 1024)
                            var copiedBytes = 0L
                            while (true) {
                                val readCount = inputStream.read(bufferBytes)
                                if (readCount < 0) break
                                copiedBytes += readCount
                                require(copiedBytes <= 768L * 1024 * 1024) { "Selected file is too large" }
                                outputStream.write(bufferBytes, 0, readCount)
                            }
                        }
                    }
                    val bookMetadata = EpubParser.parseMetadata(temporaryFile)
                    val bookContent = EpubParser.parseContent(temporaryFile)
                    temporaryFile.copyTo(selectedBookFile, overwrite = true)
                    temporaryFile.delete()
                    OpenBookState.Ready(bookMetadata, bookContent)
                }
            }
            openBookState = loadedBook.getOrElse { error ->
                OpenBookState.Failed(error.message ?: "Cannot read this EPUB")
            }
        }
    }

    private fun loadBook(bookFile: File) {
        openBookState = OpenBookState.Loading
        lifecycleScope.launch {
            val loadedBook = withContext(Dispatchers.IO) {
                runCatching {
                    OpenBookState.Ready(
                        EpubParser.parseMetadata(bookFile),
                        EpubParser.parseContent(bookFile),
                    )
                }
            }
            openBookState = loadedBook.getOrElse { error ->
                OpenBookState.Failed(error.message ?: "Cannot read this EPUB")
            }
        }
    }
}

@Composable
private fun MobileReaderScreen(bookState: OpenBookState, onOpenBook: () -> Unit) {
    when (bookState) {
        OpenBookState.NoBook -> BookPickerScreen(onOpenBook)
        OpenBookState.Loading -> Text("Opening EPUB…", modifier = Modifier.padding(24.dp))
        is OpenBookState.Failed -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Could not open EPUB", style = MaterialTheme.typography.titleLarge)
            Text(bookState.problemText)
            Button(onClick = onOpenBook) { Text("Choose another EPUB") }
        }
        is OpenBookState.Ready -> BookReadingScreen(bookState, onOpenBook)
    }
}

@Composable
private fun BookPickerScreen(onOpenBook: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("MobileReader", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.weight(1f))
        Text("Open an EPUB to start reading", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onOpenBook) { Text("Open EPUB") }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BookReadingScreen(bookState: OpenBookState.Ready, onOpenBook: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(bookState.bookMetadata.title ?: "Untitled EPUB", style = MaterialTheme.typography.headlineMedium)
                bookState.bookMetadata.author?.let { authorName ->
                    Text(authorName, style = MaterialTheme.typography.bodyLarge)
                }
                Button(onClick = onOpenBook, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Open another EPUB")
                }
            }
        }
        items(bookState.bookContent.chapters) { bookChapter ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                bookChapter.title?.let { chapterTitle ->
                    Text(chapterTitle, style = MaterialTheme.typography.titleLarge)
                }
                for (contentElement in bookChapter.elements) {
                    when (contentElement) {
                        is EpubContentElement.Paragraph -> Text(
                            contentElement.text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        is EpubContentElement.Heading -> Text(
                            contentElement.text,
                            style = if (contentElement.level <= 2) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BookPickerPreview() {
    MaterialTheme { BookPickerScreen(onOpenBook = {}) }
}
