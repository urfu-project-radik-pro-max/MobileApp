package com.example.mobilereader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@kotlinx.serialization.Serializable
enum class ReaderFont { LITERATA, SERIF, SANS, CUSTOM }

@kotlinx.serialization.Serializable
enum class ReadingMode { SCROLL, PAGES }


@kotlinx.serialization.Serializable
enum class PageMargins { NARROW, NORMAL, WIDE }


@kotlinx.serialization.Serializable
enum class PageTurnAnimation { SLIDE, CASCADE, PAGE_CURL }


@kotlinx.serialization.Serializable
enum class AppTheme { WHITE, SEPIA, OLED }


@kotlinx.serialization.Serializable
enum class LightThemeDefault { LIGHT, BEIGE }


@kotlinx.serialization.Serializable
enum class StartupDestination { LIBRARY, LAST_BOOK }


@kotlinx.serialization.Serializable
enum class LibraryViewMode { GRID, LIST }


@kotlinx.serialization.Serializable
data class ReaderSettings(
    val fontSizeSp: Float = 18f,
    val lineHeight: Float = 1.5f,
    val font: ReaderFont = ReaderFont.LITERATA,

    val customFontPath: String? = null,
    val justify: Boolean = true,
    val hyphenation: Boolean = true,


    val bookStyles: Boolean = false,
    val pageMargins: PageMargins = PageMargins.NORMAL,
    val readingMode: ReadingMode = ReadingMode.SCROLL,

    val startChaptersOnNewPage: Boolean = true,

    val showChapterPagesLeft: Boolean = false,

    val hideFootnotes: Boolean = false,


    val invertImages: Boolean = false,


    val dropCaps: Boolean = false,

    val pageTurnAnimation: PageTurnAnimation = PageTurnAnimation.SLIDE,

    @kotlinx.serialization.Transient
    val centerHeadings: Boolean = false,
)


@kotlinx.serialization.Serializable
data class AppSettings(
    val theme: AppTheme = AppTheme.WHITE,
    val followSystemTheme: Boolean = true,
    val lightThemeDefault: LightThemeDefault = LightThemeDefault.LIGHT,
    val dynamicColor: Boolean = false,
    val startupDestination: StartupDestination = StartupDestination.LIBRARY,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = true,
    val volumeKeyPaging: Boolean = true,
    val centerHeadings: Boolean = false,
    val autoHideReturnButton: Boolean = true,

    val defaultTranslator: String? = null,

    val autoInvertImages: Boolean = true,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
)


fun AppSettings.effectiveTheme(systemDark: Boolean): AppTheme {
    if (!followSystemTheme) return theme
    if (systemDark) return AppTheme.OLED
    return when (lightThemeDefault) {
        LightThemeDefault.LIGHT -> AppTheme.WHITE
        LightThemeDefault.BEIGE -> AppTheme.SEPIA
    }
}

private val Context.settingsDataStore by preferencesDataStore(name = "reader_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val fontSize = floatPreferencesKey("font_size_sp")
        val lineHeight = floatPreferencesKey("line_height")
        val font = stringPreferencesKey("font")
        val customFontPath = stringPreferencesKey("custom_font_path")
        val justify = booleanPreferencesKey("justify")
        val hyphenation = booleanPreferencesKey("hyphenation")
        val bookStyles = booleanPreferencesKey("book_styles")
        val pageMargins = stringPreferencesKey("page_margins")
        val readingMode = stringPreferencesKey("reading_mode")
        val chapterNewPage = booleanPreferencesKey("chapter_new_page")
        val chapterPagesLeft = booleanPreferencesKey("chapter_pages_left")
        val hideFootnotes = booleanPreferencesKey("hide_footnotes")
        val invertImages = booleanPreferencesKey("invert_images")
        val dropCaps = booleanPreferencesKey("drop_caps")
        val pageTurnAnimation = stringPreferencesKey("page_turn_animation")


        val appTheme = stringPreferencesKey("app_theme_v2")
        val followSystemTheme = booleanPreferencesKey("follow_system_theme")
        val lightThemeDefault = stringPreferencesKey("light_theme_default")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val startupDestination = stringPreferencesKey("startup_destination")
        val haptics = booleanPreferencesKey("haptics")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val volumeKeyPaging = booleanPreferencesKey("volume_key_paging")
        val centerHeadings = booleanPreferencesKey("center_headings")
        val autoHideReturnButton = booleanPreferencesKey("auto_hide_return_button")
        val defaultTranslator = stringPreferencesKey("default_translator")
        val autoInvertImages = booleanPreferencesKey("auto_invert_images")
        val libraryViewMode = stringPreferencesKey("library_view_mode")
        val deviceId = stringPreferencesKey("device_id")
    }


    suspend fun deviceId(): String {
        context.settingsDataStore.data.first()[Keys.deviceId]?.let { return it }
        val minted = java.util.UUID.randomUUID().toString()
        var result = minted
        context.settingsDataStore.edit { prefs ->

            result = prefs[Keys.deviceId] ?: minted.also { prefs[Keys.deviceId] = it }
        }
        return result
    }

    val settings: Flow<ReaderSettings> =
        context.settingsDataStore.data.map { it.readReaderSettings() }

    val appSettings: Flow<AppSettings> =
        context.settingsDataStore.data.map { it.readAppSettings() }

    val libraryViewMode: Flow<LibraryViewMode> =
        context.settingsDataStore.data.map { prefs ->
            enumOrDefault(prefs[Keys.libraryViewMode], LibraryViewMode.GRID)
        }

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        updateApp { it.copy(viewMode = mode) }
    }

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        context.settingsDataStore.edit { prefs ->
            val updated = transform(prefs.readReaderSettings())
            prefs[Keys.fontSize] = updated.fontSizeSp
            prefs[Keys.lineHeight] = updated.lineHeight
            prefs[Keys.font] = updated.font.name
            updated.customFontPath
                ?.let { prefs[Keys.customFontPath] = it }
                ?: prefs.remove(Keys.customFontPath)
            prefs[Keys.justify] = updated.justify
            prefs[Keys.hyphenation] = updated.hyphenation
            prefs[Keys.bookStyles] = updated.bookStyles
            prefs[Keys.pageMargins] = updated.pageMargins.name
            prefs[Keys.readingMode] = updated.readingMode.name
            prefs[Keys.chapterNewPage] = updated.startChaptersOnNewPage
            prefs[Keys.chapterPagesLeft] = updated.showChapterPagesLeft
            prefs[Keys.hideFootnotes] = updated.hideFootnotes
            prefs[Keys.invertImages] = updated.invertImages
            prefs[Keys.dropCaps] = updated.dropCaps
            prefs[Keys.pageTurnAnimation] = updated.pageTurnAnimation.name
        }
    }

    suspend fun updateApp(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val updated = transform(prefs.readAppSettings())
            prefs[Keys.appTheme] = updated.theme.name
            prefs[Keys.followSystemTheme] = updated.followSystemTheme
            prefs[Keys.lightThemeDefault] = updated.lightThemeDefault.name
            prefs[Keys.dynamicColor] = updated.dynamicColor
            prefs[Keys.startupDestination] = updated.startupDestination.name
            prefs[Keys.haptics] = updated.haptics
            prefs[Keys.keepScreenOn] = updated.keepScreenOn
            prefs[Keys.volumeKeyPaging] = updated.volumeKeyPaging
            prefs[Keys.centerHeadings] = updated.centerHeadings
            prefs[Keys.autoHideReturnButton] = updated.autoHideReturnButton
            updated.defaultTranslator?.let { prefs[Keys.defaultTranslator] = it }
                ?: prefs.remove(Keys.defaultTranslator)
            prefs[Keys.autoInvertImages] = updated.autoInvertImages
            prefs[Keys.libraryViewMode] = updated.viewMode.name
        }
    }

    private fun Preferences.readReaderSettings(): ReaderSettings {
        val defaults = ReaderSettings()
        return ReaderSettings(
            fontSizeSp = this[Keys.fontSize] ?: defaults.fontSizeSp,
            lineHeight = this[Keys.lineHeight] ?: defaults.lineHeight,
            font = enumOrDefault(this[Keys.font], defaults.font),
            customFontPath = this[Keys.customFontPath],
            justify = this[Keys.justify] ?: defaults.justify,
            hyphenation = this[Keys.hyphenation] ?: defaults.hyphenation,
            bookStyles = this[Keys.bookStyles] ?: defaults.bookStyles,
            pageMargins = enumOrDefault(this[Keys.pageMargins], defaults.pageMargins),
            readingMode = enumOrDefault(this[Keys.readingMode], defaults.readingMode),
            startChaptersOnNewPage = this[Keys.chapterNewPage] ?: defaults.startChaptersOnNewPage,
            showChapterPagesLeft = this[Keys.chapterPagesLeft] ?: defaults.showChapterPagesLeft,
            hideFootnotes = this[Keys.hideFootnotes] ?: defaults.hideFootnotes,
            invertImages = this[Keys.invertImages] ?: defaults.invertImages,
            dropCaps = this[Keys.dropCaps] ?: defaults.dropCaps,
            pageTurnAnimation = enumOrDefault(
                this[Keys.pageTurnAnimation],
                defaults.pageTurnAnimation,
            ),
        )
    }

    private fun Preferences.readAppSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = enumOrDefault(this[Keys.appTheme], defaults.theme),
            followSystemTheme = this[Keys.followSystemTheme] ?: defaults.followSystemTheme,
            lightThemeDefault = enumOrDefault(
                this[Keys.lightThemeDefault],
                defaults.lightThemeDefault,
            ),
            dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
            startupDestination = enumOrDefault(
                this[Keys.startupDestination],
                defaults.startupDestination,
            ),
            haptics = this[Keys.haptics] ?: defaults.haptics,
            keepScreenOn = this[Keys.keepScreenOn] ?: defaults.keepScreenOn,
            volumeKeyPaging = this[Keys.volumeKeyPaging] ?: defaults.volumeKeyPaging,
            centerHeadings = this[Keys.centerHeadings] ?: defaults.centerHeadings,
            autoHideReturnButton = this[Keys.autoHideReturnButton] ?: defaults.autoHideReturnButton,
            defaultTranslator = this[Keys.defaultTranslator],
            autoInvertImages = this[Keys.autoInvertImages] ?: defaults.autoInvertImages,
            viewMode = enumOrDefault(this[Keys.libraryViewMode], defaults.viewMode),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    companion object {
        private const val BOOT_PREFS = "boot_hints"
        private const val KEY_THEME = "app_theme"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"


        fun bootTheme(context: Context): AppTheme {
            val name = context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME, null)
            return name?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.WHITE
        }

        fun bootDynamicColor(context: Context): Boolean =
            context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_DYNAMIC_COLOR, AppSettings().dynamicColor)

        fun rememberBootAppearance(
            context: Context,
            theme: AppTheme,
            dynamicColor: Boolean,
        ) {
            if (bootTheme(context) == theme && bootDynamicColor(context) == dynamicColor) return
            context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, theme.name)
                .putBoolean(KEY_DYNAMIC_COLOR, dynamicColor)
                .apply()
        }
    }
}
