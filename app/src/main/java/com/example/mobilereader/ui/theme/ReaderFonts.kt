package com.example.mobilereader.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.mobilereader.R
import com.example.mobilereader.data.ReaderFont
import java.io.File


val LiterataFamily: FontFamily = FontFamily(
    Font(
        R.font.literata,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.literata,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.literata,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        R.font.literata_italic,
        weight = FontWeight.Normal,
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.literata_italic,
        weight = FontWeight.Bold,
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)


private val customFamilies = HashMap<String, FontFamily>()


fun customFontFamily(path: String?): FontFamily? = customFamilyFor(path)

private fun customFamilyFor(path: String?): FontFamily? {
    if (path == null) return null
    synchronized(customFamilies) {
        customFamilies[path]?.let { return it }
        val file = File(path)
        if (!file.exists()) return null
        val family = runCatching { FontFamily(Font(file)) }.getOrNull() ?: return null
        customFamilies[path] = family
        return family
    }
}

fun fontFamilyFor(font: ReaderFont, customFontPath: String? = null): FontFamily = when (font) {
    ReaderFont.LITERATA -> LiterataFamily
    ReaderFont.SERIF -> FontFamily.Serif
    ReaderFont.SANS -> FontFamily.SansSerif
    ReaderFont.CUSTOM -> customFamilyFor(customFontPath) ?: LiterataFamily
}
