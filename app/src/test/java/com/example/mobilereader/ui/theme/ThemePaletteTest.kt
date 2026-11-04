package com.example.mobilereader.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.mobilereader.data.AppTheme
import com.example.mobilereader.ui.reader.readerColors
import com.example.mobilereader.ui.reader.contentsRowBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.luminance
import org.junit.Test

class ThemePaletteTest {

    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + 0.05f) /
            (minOf(firstLuminance, secondLuminance) + 0.05f)
    }

    @Test
    fun `fixed White palette uses the Figma roles`() {
        val scheme = colorSchemeFor(AppTheme.WHITE)

        assertEquals(Color(0xFF526B5D), FixedWhiteColors.primary)
        assertEquals(Color(0xFF7D9184), FixedWhiteColors.secondary)
        assertEquals(Color(0xFFA47B69), FixedWhiteColors.tertiary)
        assertEquals(Color(0xFFFAF7F0), FixedWhiteColors.background)
        assertEquals(Color(0xFF292923), FixedWhiteColors.text)
        assertEquals(Color(0xFFE4EBDD), FixedWhiteColors.idiomHighlight)
        assertEquals(FixedWhiteColors.primary, scheme.primary)
        assertEquals(FixedWhiteColors.secondary, scheme.secondary)
        assertEquals(FixedWhiteColors.tertiary, scheme.tertiary)
        assertEquals(FixedWhiteColors.background, scheme.background)
        assertEquals(FixedWhiteColors.background, scheme.surface)
        assertEquals(FixedWhiteColors.text, scheme.onBackground)
        assertEquals(FixedWhiteColors.text, scheme.onSurface)
        assertEquals(FixedWhiteColors.background to FixedWhiteColors.primary, AppTheme.WHITE.swatch)
    }

    @Test
    fun `fixed White labels and controls meet text contrast`() {
        val scheme = colorSchemeFor(AppTheme.WHITE)

        assertTrue(contrast(scheme.primary, scheme.background) >= 4.5f)
        assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
        assertTrue(contrast(scheme.onSecondary, scheme.secondary) >= 4.5f)
        assertTrue(contrast(scheme.onTertiary, scheme.tertiary) >= 4.5f)
        assertTrue(contrast(scheme.onSurfaceVariant, scheme.background) >= 4.5f)
        assertTrue(contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
        assertTrue(contrast(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 4.5f)
        assertTrue(contrast(scheme.onTertiaryContainer, scheme.tertiaryContainer) >= 4.5f)
        assertTrue(contrast(scheme.error, scheme.background) >= 4.5f)
    }

    @Test
    fun `reader colors compare by value to preserve decorated text caches`() {
        AppTheme.entries.forEach { theme ->
            assertEquals(readerColors(theme), readerColors(theme))
        }
    }

    @Test
    fun `contents title foregrounds contrast against all three reader palettes`() {
        AppTheme.entries.forEach { theme ->
            val scheme = colorSchemeFor(theme)
            assertTrue("$theme chapter", contrast(scheme.onSurface, scheme.surfaceContainerLow) >= 4.5f)
            val group = contentsRowBackground(scheme, current = false, group = true)
            val current = contentsRowBackground(scheme, current = true, group = false)
            assertTrue("$theme group", contrast(scheme.onSurface, group) >= 4.5f)
            assertTrue("$theme current", contrast(scheme.onSurface, current) >= 4.5f)
            assertTrue("$theme section label", contrast(scheme.primary, group) >= 4.5f)
        }
    }

    private val systemBlueScheme = lightColorScheme(
        primary = Color(0xFF315DA8),
        primaryContainer = Color(0xFFD8E2FF),
        onPrimaryContainer = Color(0xFF001A41),
        background = Color(0xFFF9F9FF),
        surface = Color(0xFFF9F9FF),
        surfaceContainerLow = Color(0xFFF1F3FC),
        surfaceContainerHigh = Color(0xFFE2E7F3),
        surfaceContainerLowest = Color(0xFFFFFFFF),
    )

    private val systemDarkBlueScheme = darkColorScheme(
        primary = Color(0xFFA9C7FF),
        primaryContainer = Color(0xFF16447F),
        onPrimaryContainer = Color(0xFFD8E2FF),
        background = Color(0xFF101318),
        surface = Color(0xFF101318),
        surfaceContainerLow = Color(0xFF181C22),
        surfaceContainerHigh = Color(0xFF282C33),
        surfaceContainerLowest = Color(0xFF0B0E13),
    )

    @Test
    fun `dynamic scheme owns the complete app palette`() {
        val resolved = resolveAppColorScheme(AppTheme.WHITE, systemBlueScheme)

        assertSame(systemBlueScheme, resolved)
        assertEquals(systemBlueScheme.background, resolved.background)
        assertEquals(systemBlueScheme.surfaceContainerHigh, resolved.surfaceContainerHigh)
    }

    @Test
    fun `Midnight accepts the complete dynamic dark palette`() {
        val resolved = resolveAppColorScheme(AppTheme.OLED, systemDarkBlueScheme)

        assertSame(systemDarkBlueScheme, resolved)
        assertEquals(systemDarkBlueScheme.background, resolved.background)
        assertEquals(systemDarkBlueScheme.surfaceContainerHigh, resolved.surfaceContainerHigh)
    }

    @Test
    fun `fixed palette remains when dynamic colour is unavailable`() {
        assertSame(colorSchemeFor(AppTheme.WHITE), resolveAppColorScheme(AppTheme.WHITE, null))
    }

    @Test
    fun `mobileReader specific surfaces follow the dynamic palette`() {
        val colors = dynamicMobileReaderColors(systemBlueScheme, dark = false)

        assertEquals(systemBlueScheme.primaryContainer, colors.headerTop)
        assertEquals(systemBlueScheme.surfaceContainerLow, colors.headerBottom)
        assertEquals(systemBlueScheme.surfaceContainerHigh, colors.chip)
        assertEquals(systemBlueScheme.surfaceContainerHigh, colors.nav)
        assertEquals(systemBlueScheme.primary.copy(alpha = 0.14f), colors.folder)
        assertEquals(systemBlueScheme.onPrimaryContainer, colors.ink)
    }

    @Test
    fun `reader keeps its page but adopts the dynamic chrome and accent`() {
        val colors = readerColors(AppTheme.WHITE, chromeScheme = systemBlueScheme)
        val pageScheme = colorSchemeFor(AppTheme.WHITE)

        assertEquals(FixedWhiteColors.background, colors.background)
        assertEquals(pageScheme.onSurface, colors.text)
        assertEquals(systemBlueScheme.surfaceContainerHigh, colors.chrome)
        assertEquals(systemBlueScheme.primary, colors.accent)
        assertEquals(FixedWhiteColors.idiomHighlight, colors.idiomHighlight)
        assertNotEquals(colors.idiomHighlight, colors.quoteHighlight)
        assertNotEquals(colors.idiomHighlight, colors.selection)
    }

}
