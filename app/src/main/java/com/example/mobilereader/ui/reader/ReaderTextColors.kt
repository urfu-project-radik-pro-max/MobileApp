package com.example.mobilereader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

internal fun AnnotatedString.withoutPublisherColors(): AnnotatedString = mapAnnotations { range ->
    val style = range.item as? SpanStyle ?: return@mapAnnotations range
    AnnotatedString.Range(
        style.copy(color = Color.Unspecified, background = Color.Unspecified),
        range.start,
        range.end,
        range.tag,
    )
}
