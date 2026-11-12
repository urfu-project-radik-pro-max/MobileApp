package com.example.mobilereader

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.mobilereader.data.AppSettings
import com.example.mobilereader.data.model.Book
import com.example.mobilereader.ui.library.LibraryScreen
import com.example.mobilereader.ui.reader.ReaderScreen
import com.example.mobilereader.ui.theme.MobileReaderTheme
import com.example.mobilereader.ui.theme.isDark
import kotlinx.coroutines.launch

enum class VolumeKeyPhase {
    PRESS,
    REPEAT,
    RELEASE,
}

internal sealed interface BookRoute {
    data object Library : BookRoute
    data object Picking : BookRoute
    data object Importing : BookRoute
    data class Reading(val bookId: String) : BookRoute
}

internal fun initialBookRoute(
    hasSavedState: Boolean,
    savedBookId: String?,
    savedRoute: String?,
    books: List<Book>,
): BookRoute = if (hasSavedState) {
    when {
        savedRoute == "picking" -> BookRoute.Picking
        savedRoute == "importing" -> BookRoute.Library
        savedBookId != null && books.any { it.id == savedBookId } -> BookRoute.Reading(savedBookId)
        else -> BookRoute.Library
    }
} else {
    books.maxByOrNull { it.lastOpenedAtMillis ?: it.addedAtMillis }
        ?.let { BookRoute.Reading(it.id) } ?: BookRoute.Library
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
    private var bookRoute by mutableStateOf<BookRoute>(BookRoute.Library)
    private var restoredPickerPending = false
    private var openingProblem by mutableStateOf<String?>(null)
    private val openBookPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { selectedUri ->
            restoredPickerPending = false
            if (selectedUri == null) {
                bookRoute = BookRoute.Library
            } else {
                bookRoute = BookRoute.Importing
                importSelectedBook(selectedUri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookRoute = initialBookRoute(
            hasSavedState = savedInstanceState != null,
            savedBookId = savedInstanceState?.getString("selectedBookId"),
            savedRoute = savedInstanceState?.getString("bookRoute"),
            books = readerApplication.bookRepository.books.value,
        )
        restoredPickerPending = bookRoute == BookRoute.Picking
        setContent {
            val appSettings by readerApplication.settingsRepository.appSettings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            MobileReaderTheme(
                appSettings.theme,
                appSettings.dynamicColor
            ) {
                val colorScheme = MaterialTheme.colorScheme
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !appSettings.theme.isDark()
                    controller.isAppearanceLightNavigationBars = !appSettings.theme.isDark()
                    window.statusBarColor = colorScheme.background.toArgb()
                    window.navigationBarColor = colorScheme.background.toArgb()
                }
                when (val route = bookRoute) {
                    is BookRoute.Reading -> ReaderScreen(
                        bookId = route.bookId,
                        onBack = { bookRoute = BookRoute.Library },
                    )
                    BookRoute.Picking, BookRoute.Importing -> BookLoadingScreen()
                    BookRoute.Library -> LibraryScreen(
                        theme = appSettings.theme,
                        loadingBook = false,
                        openingProblem = openingProblem,
                        onOpenBook = {
                            if (bookRoute == BookRoute.Library) {
                                openingProblem = null
                                bookRoute = BookRoute.Picking
                                runCatching {
                                    openBookPicker.launch(
                                        arrayOf(
                                            "application/epub+zip",
                                            "application/octet-stream",
                                            "application/zip"
                                        )
                                    )
                                }.onFailure { problem ->
                                    openingProblem = problem.message ?: "Cannot open file picker"
                                    bookRoute = BookRoute.Library
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selectedBookId", (bookRoute as? BookRoute.Reading)?.bookId)
        outState.putString("bookRoute", when (bookRoute) {
            BookRoute.Library -> "library"
            BookRoute.Picking -> "picking"
            BookRoute.Importing -> "importing"
            is BookRoute.Reading -> "reading"
        })
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (restoredPickerPending) {
            window.decorView.post {
                if (restoredPickerPending && bookRoute == BookRoute.Picking) {
                    restoredPickerPending = false
                    bookRoute = BookRoute.Library
                }
            }
        }
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
        openingProblem = null
        lifecycleScope.launch {
            runCatching { readerApplication.bookRepository.importEpub(selectedUri) }
                .onSuccess { importedBook -> bookRoute = BookRoute.Reading(importedBook.id) }
                .onFailure { problem ->
                    openingProblem = problem.message ?: "Cannot read this EPUB"
                    bookRoute = BookRoute.Library
                }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}
