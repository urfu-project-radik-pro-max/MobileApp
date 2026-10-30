package com.example.mobilereader.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderGestureSettingsTest {
    @Test
    fun olderSettingsKeepBothGesturesEnabled() {
        val savedBookSettings = Json.decodeFromString<ReaderSettings>("{}")
        val savedDataStoreSettings = emptyPreferences()

        assertTrue(savedBookSettings.edgeTapPagingEnabled)
        assertTrue(savedBookSettings.brightnessGestureEnabled)
        assertTrue(ReaderGestureSettingsStore.readEdgeTapPagingEnabled(savedDataStoreSettings))
        assertTrue(ReaderGestureSettingsStore.readBrightnessGestureEnabled(savedDataStoreSettings))
    }

    @Test
    fun gesturesPersistIndependentlyInBookAndDataStoreSettings() {
        val preferences = mutablePreferencesOf()
        val edgeTapDisabled = ReaderSettings(
            edgeTapPagingEnabled = false,
            brightnessGestureEnabled = true,
        )

        ReaderGestureSettingsStore.write(preferences, edgeTapDisabled)
        val restoredBookSettings = Json.decodeFromString<ReaderSettings>(
            Json.encodeToString(edgeTapDisabled),
        )

        assertFalse(restoredBookSettings.edgeTapPagingEnabled)
        assertTrue(restoredBookSettings.brightnessGestureEnabled)
        assertFalse(ReaderGestureSettingsStore.readEdgeTapPagingEnabled(preferences))
        assertTrue(ReaderGestureSettingsStore.readBrightnessGestureEnabled(preferences))

        val brightnessDisabled = ReaderSettings(
            edgeTapPagingEnabled = true,
            brightnessGestureEnabled = false,
        )
        ReaderGestureSettingsStore.write(preferences, brightnessDisabled)

        assertTrue(ReaderGestureSettingsStore.readEdgeTapPagingEnabled(preferences))
        assertFalse(ReaderGestureSettingsStore.readBrightnessGestureEnabled(preferences))
    }
}
