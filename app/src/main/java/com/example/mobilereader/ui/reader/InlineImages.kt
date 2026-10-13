package com.example.mobilereader.ui.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.example.mobilereader.data.model.INLINE_IMAGE_ALT_TAG
import com.example.mobilereader.data.model.INLINE_IMAGE_TAG
import java.io.File
import java.util.concurrent.ConcurrentHashMap


private const val INLINE_IMAGE_HEIGHT_EM = 0.9f


private val aspectCache = ConcurrentHashMap<String, Float>()


private fun placeholderFor(path: String): Placeholder {
    val aspect = aspectCache.getOrPut(path) { imageAspectRatio(path) ?: 1f }
    return Placeholder(
        width = (INLINE_IMAGE_HEIGHT_EM / aspect.coerceAtLeast(0.05f)).em,
        height = INLINE_IMAGE_HEIGHT_EM.em,

        placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
    )
}


internal fun inlineImagePlaceholders(
    text: AnnotatedString,
): List<AnnotatedString.Range<Placeholder>> {
    val marks = text.getStringAnnotations(INLINE_IMAGE_TAG, 0, text.length)
    if (marks.isEmpty()) return emptyList()
    return marks.map { mark ->
        AnnotatedString.Range(placeholderFor(mark.item), mark.start, mark.end)
    }
}


internal fun inlineImageContent(
    text: AnnotatedString,
    invert: Boolean = false,
): Map<String, InlineTextContent> {
    val marks = text.getStringAnnotations(INLINE_IMAGE_TAG, 0, text.length)
    if (marks.isEmpty()) return emptyMap()
    val descriptions = text.getStringAnnotations(INLINE_IMAGE_ALT_TAG, 0, text.length)
    return marks.associate { mark ->
        val description = descriptions.firstOrNull {
            it.start == mark.start && it.end == mark.end
        }?.item
        mark.item to InlineTextContent(placeholderFor(mark.item)) {
            AsyncImage(
                model = File(mark.item),
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = imageColorFilter(invert),
            )
        }
    }
}
