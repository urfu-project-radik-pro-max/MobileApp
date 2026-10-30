package com.example.mobilereader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@kotlinx.serialization.Serializable
enum class ReaderFont { LITERATA, SERIF, SANS, CUSTOM }

@kotlinx.serialization.Serializable
enum class PageMargins { NARROW, NORMAL, WIDE }


@kotlinx.serialization.Serializable
enum class AppTheme { WHITE, SEPIA, OLED }


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


    val pageMargins: PageMargins = PageMargins.NORMAL,

    val startChaptersOnNewPage: Boolean = true,

    val showChapterPagesLeft: Boolean = false,

    val hideFootnotes: Boolean = false,


    val invertImages: Boolean = false,



    val pageTurnAnimationEnabled: Boolean = true,
    val edgeTapPagingEnabled: Boolean = true,
    val brightnessGestureEnabled: Boolean = true,

    @kotlinx.serialization.Transient
    val centerHeadings: Boolean = false,
)


@kotlinx.serialization.Serializable
data class AppSettings(
    val theme: AppTheme = AppTheme.WHITE,
    val dynamicColor: Boolean = false,
    val startupDestination: StartupDestination = StartupDestination.LIBRARY,
    val keepScreenOn: Boolean = true,
    val volumeKeyPaging: Boolean = true,
    val centerHeadings: Boolean = false,
    val autoHideReturnButton: Boolean = true,

    val defaultTranslator: String? = null,

    val autoInvertImages: Boolean = true,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
)

data class TranslationSettings(
    val contextPageCount: Int = 2,
    val instruction: String = "",
    val serverAddress: String = "",
)


private val Context.settingsDataStore by preferencesDataStore(name = "reader_settings")

internal object ReaderGestureSettingsStore {
    private val edgeTapPagingEnabled = booleanPreferencesKey("edge_tap_paging_enabled")
    private val brightnessGestureEnabled = booleanPreferencesKey("brightness_gesture_enabled")

    fun readEdgeTapPagingEnabled(preferences: Preferences): Boolean {
        return preferences[edgeTapPagingEnabled] ?: true
    }

    fun readBrightnessGestureEnabled(preferences: Preferences): Boolean {
        return preferences[brightnessGestureEnabled] ?: true
    }

    fun write(preferences: MutablePreferences, settings: ReaderSettings) {
        preferences[edgeTapPagingEnabled] = settings.edgeTapPagingEnabled
        preferences[brightnessGestureEnabled] = settings.brightnessGestureEnabled
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val fontSize = floatPreferencesKey("font_size_sp")
        val lineHeight = floatPreferencesKey("line_height")
        val font = stringPreferencesKey("font")
        val customFontPath = stringPreferencesKey("custom_font_path")
        val justify = booleanPreferencesKey("justify")
        val hyphenation = booleanPreferencesKey("hyphenation")
        val pageMargins = stringPreferencesKey("page_margins")
        val chapterNewPage = booleanPreferencesKey("chapter_new_page")
        val chapterPagesLeft = booleanPreferencesKey("chapter_pages_left")
        val hideFootnotes = booleanPreferencesKey("hide_footnotes")
        val invertImages = booleanPreferencesKey("invert_images")
        val pageTurnAnimationEnabled = booleanPreferencesKey("page_turn_animation_enabled")


        val appTheme = stringPreferencesKey("app_theme_v2")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val startupDestination = stringPreferencesKey("startup_destination")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val volumeKeyPaging = booleanPreferencesKey("volume_key_paging")
        val centerHeadings = booleanPreferencesKey("center_headings")
        val autoHideReturnButton = booleanPreferencesKey("auto_hide_return_button")
        val defaultTranslator = stringPreferencesKey("default_translator")
        val autoInvertImages = booleanPreferencesKey("auto_invert_images")
        val libraryViewMode = stringPreferencesKey("library_view_mode")
        val deviceId = stringPreferencesKey("device_id")
        val translationContextPageCount = intPreferencesKey("translation_context_page_count")
        val translationInstruction = stringPreferencesKey("translation_instruction")
        val translationServerAddress = stringPreferencesKey("translation_server_address")
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

    val translationSettings: Flow<TranslationSettings> =
        context.settingsDataStore.data.map { preferences ->
            TranslationSettings(
                contextPageCount = (preferences[Keys.translationContextPageCount] ?: 2).coerceIn(1, 5),
                instruction = preferences[Keys.translationInstruction].orEmpty(),
                serverAddress = preferences[Keys.translationServerAddress].orEmpty(),
            )
        }

    suspend fun updateTranslationSettings(transform: (TranslationSettings) -> TranslationSettings) {
        context.settingsDataStore.edit { preferences ->
            val current = TranslationSettings(
                contextPageCount = (preferences[Keys.translationContextPageCount] ?: 2).coerceIn(1, 5),
                instruction = preferences[Keys.translationInstruction].orEmpty(),
                serverAddress = preferences[Keys.translationServerAddress].orEmpty(),
            )
            val updated = transform(current)
            preferences[Keys.translationContextPageCount] = updated.contextPageCount.coerceIn(1, 5)
            preferences[Keys.translationInstruction] = updated.instruction.take(2000)
            preferences[Keys.translationServerAddress] = updated.serverAddress.trim()
        }
    }

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
            prefs[Keys.pageMargins] = updated.pageMargins.name
            prefs[Keys.chapterNewPage] = updated.startChaptersOnNewPage
            prefs[Keys.chapterPagesLeft] = updated.showChapterPagesLeft
            prefs[Keys.hideFootnotes] = updated.hideFootnotes
            prefs[Keys.invertImages] = updated.invertImages
            prefs[Keys.pageTurnAnimationEnabled] = updated.pageTurnAnimationEnabled
            ReaderGestureSettingsStore.write(prefs, updated)
        }
    }

    suspend fun updateApp(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val updated = transform(prefs.readAppSettings())
            prefs[Keys.appTheme] = updated.theme.name
            prefs[Keys.dynamicColor] = updated.dynamicColor
            prefs[Keys.startupDestination] = updated.startupDestination.name
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
            pageMargins = enumOrDefault(this[Keys.pageMargins], defaults.pageMargins),
            startChaptersOnNewPage = this[Keys.chapterNewPage] ?: defaults.startChaptersOnNewPage,
            showChapterPagesLeft = this[Keys.chapterPagesLeft] ?: defaults.showChapterPagesLeft,
            hideFootnotes = this[Keys.hideFootnotes] ?: defaults.hideFootnotes,
            invertImages = this[Keys.invertImages] ?: defaults.invertImages,
            pageTurnAnimationEnabled = this[Keys.pageTurnAnimationEnabled]
                ?: defaults.pageTurnAnimationEnabled,
            edgeTapPagingEnabled = ReaderGestureSettingsStore.readEdgeTapPagingEnabled(this),
            brightnessGestureEnabled = ReaderGestureSettingsStore.readBrightnessGestureEnabled(this),
        )
    }

    private fun Preferences.readAppSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = enumOrDefault(this[Keys.appTheme], defaults.theme),
            dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
            startupDestination = enumOrDefault(
                this[Keys.startupDestination],
                defaults.startupDestination,
            ),
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
