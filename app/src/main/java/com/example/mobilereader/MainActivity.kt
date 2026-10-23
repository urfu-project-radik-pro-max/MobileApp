package com.example.mobilereader

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.mobilereader.data.AppSettings
import com.example.mobilereader.ui.reader.ReaderScreen
import com.example.mobilereader.ui.theme.MobileReaderTheme
import kotlinx.coroutines.launch

enum class VolumeKeyPhase {
    PRESS,
    REPEAT,
    RELEASE,
}

class MainActivity : ComponentActivity() {
    var volumeKeyHandler: ((forward: Boolean, phase: VolumeKeyPhase) -> Boolean)? = null
    private enum class VolumePressState {
        CAPTURED,
        PASSTHROUGH,
    }

    private var volumeUpPressState: VolumePressState? = null
    private var volumeDownPressState: VolumePressState? = null
    private val readerApplication by lazy { application as MobileReaderApp }
    private var selectedBookId by mutableStateOf<String?>(null)
    private var loadingBook by mutableStateOf(false)
    private var openingProblem by mutableStateOf<String?>(null)
    private val openBookPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { selectedUri ->
            if (selectedUri != null) importSelectedBook(selectedUri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedBookId = savedInstanceState?.getString("selectedBookId")
            ?: readerApplication.bookRepository.books.value.maxByOrNull {
                it.lastOpenedAtMillis ?: it.addedAtMillis
            }?.id
        setContent {
            val appSettings by readerApplication.settingsRepository.appSettings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            MobileReaderTheme(
                appSettings.theme,
                appSettings.dynamicColor
            ) {
                val currentBookId = selectedBookId
                if (currentBookId != null) {
                    ReaderScreen(bookId = currentBookId, onBack = { selectedBookId = null })
                } else {
                    BookPickerScreen(
                        loadingBook = loadingBook,
                        openingProblem = openingProblem,
                        onOpenBook = {
                            openBookPicker.launch(
                                arrayOf(
                                    "application/epub+zip",
                                    "application/octet-stream",
                                    "application/zip"
                                )
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selectedBookId", selectedBookId)
        super.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val forward = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            val pressState = if (forward) {
                volumeDownPressState
            } else {
                volumeUpPressState
            }
            val isInitialDown = event == null || event.repeatCount == 0

            if (isInitialDown) {
                if (pressState == VolumePressState.CAPTURED) {
                    volumeKeyHandler?.invoke(forward, VolumeKeyPhase.RELEASE)
                }
                val newPressState = if (
                    volumeKeyHandler?.invoke(forward, VolumeKeyPhase.PRESS) == true
                ) {
                    VolumePressState.CAPTURED
                } else {
                    VolumePressState.PASSTHROUGH
                }
                if (forward) {
                    volumeDownPressState = newPressState
                } else {
                    volumeUpPressState = newPressState
                }
                if (newPressState == VolumePressState.CAPTURED) {
                    return true
                }
            } else if (pressState == VolumePressState.CAPTURED) {
                volumeKeyHandler?.invoke(forward, VolumeKeyPhase.REPEAT)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val pressState = if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                val previousState = volumeDownPressState
                volumeDownPressState = null
                previousState
            } else {
                val previousState = volumeUpPressState
                volumeUpPressState = null
                previousState
            }
            if (pressState == VolumePressState.CAPTURED) {
                volumeKeyHandler?.invoke(
                    keyCode == KeyEvent.KEYCODE_VOLUME_DOWN,
                    VolumeKeyPhase.RELEASE,
                )
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun importSelectedBook(selectedUri: Uri) {
        loadingBook = true
        openingProblem = null
        lifecycleScope.launch {
            runCatching { readerApplication.bookRepository.importEpub(selectedUri) }
                .onSuccess { importedBook -> selectedBookId = importedBook.id }
                .onFailure { problem ->
                    openingProblem = problem.message ?: "Cannot read this EPUB"
                }
            loadingBook = false
        }
    }
}

@Composable
private fun BookPickerScreen(
    loadingBook: Boolean,
    openingProblem: String?,
    onOpenBook: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("MobileReader", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            if (loadingBook) "Opening EPUB…" else "Open an EPUB to start reading",
            style = MaterialTheme.typography.titleLarge,
        )
        if (openingProblem != null) Text(openingProblem, color = MaterialTheme.colorScheme.error)
        Button(onClick = onOpenBook, enabled = !loadingBook) { Text("Open EPUB") }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Preview(showBackground = true)
@Composable
private fun BookPickerPreview() {
    MobileReaderTheme(com.example.mobilereader.data.AppTheme.WHITE) {
        BookPickerScreen(loadingBook = false, openingProblem = null, onOpenBook = {})
    }
}
