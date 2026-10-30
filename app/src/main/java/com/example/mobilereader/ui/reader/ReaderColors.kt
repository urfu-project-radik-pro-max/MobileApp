package com.example.mobilereader.ui.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.mobilereader.data.AppTheme
import com.example.mobilereader.ui.theme.FixedWhiteColors
import com.example.mobilereader.ui.theme.colorSchemeFor
import com.example.mobilereader.ui.theme.isDark


@Immutable
data class ReaderColors(
    val background: Color,
    val text: Color,
    val secondaryText: Color,
    val chrome: Color,
    val onChrome: Color,
    val accent: Color,
    val quoteHighlight: Color,
    val selection: Color,
    val idiomHighlight: Color,
)


fun readerColors(
    theme: AppTheme,
    chromeScheme: ColorScheme = colorSchemeFor(theme),
): ReaderColors {
    val pageScheme = colorSchemeFor(theme)
    return ReaderColors(
        background = pageScheme.surface,
        text = pageScheme.onSurface,
        secondaryText = pageScheme.onSurfaceVariant,
        chrome = chromeScheme.surfaceContainerHigh,
        onChrome = chromeScheme.onSurface,
        accent = chromeScheme.primary,
        quoteHighlight = chromeScheme.primary.copy(
            alpha = if (theme.isDark()) 0.28f else 0.22f,
        ),
        selection = chromeScheme.primary.copy(
            alpha = if (theme.isDark()) 0.45f else 0.34f,
        ),
        idiomHighlight = when (theme) {
            AppTheme.WHITE -> FixedWhiteColors.idiomHighlight
            AppTheme.SEPIA -> pageScheme.primaryContainer
            AppTheme.OLED -> pageScheme.primary.copy(alpha = 0.18f)
        },
    )
}
