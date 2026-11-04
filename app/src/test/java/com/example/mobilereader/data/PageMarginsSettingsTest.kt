package com.example.mobilereader.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PageMarginsSettingsTest {
    @Test
    fun stepsHaveRequestedDpValuesAndDefaultToTwenty() {
        assertEquals(listOf(6, 10, 14, 17, 20, 24, 28, 34, 40), PageMargins.entries.map { it.dp })
        assertEquals(PageMargins.DP_20, ReaderSettings().pageMargins)
    }

    @Test
    fun everyStepPersistsInPreferencesAndBookSettings() {
        val preferences = mutablePreferencesOf()
        val key = stringPreferencesKey("page_margins")
        PageMargins.entries.forEach { margins ->
            PageMarginsSettingsStore.write(preferences, margins)
            assertEquals(margins.name, preferences[key])
            assertEquals(margins, PageMarginsSettingsStore.read(preferences))
            val saved = Json.encodeToString(ReaderSettings(pageMargins = margins))
            assertEquals(margins, Json.decodeFromString<ReaderSettings>(saved).pageMargins)
        }
    }
}
