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
import com.example.mobilereader.data.model.INLINE_IMAGE_ID_TAG
import com.example.mobilereader.data.model.INLINE_IMAGE_SIZE_TAG
import com.example.mobilereader.data.model.INLINE_IMAGE_TAG
import com.example.mobilereader.data.model.InlineImageSize
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

private const val DEFAULT_INLINE_IMAGE_HEIGHT_EM = 0.9f

private val aspectCache = ConcurrentHashMap<String, Float>()

private data class InlineImageOccurrence(
    val id: String,
    val path: String,
    val description: String?,
    val size: InlineImageSize,
    val start: Int,
    val end: Int,
)

internal fun inlineImagePlaceholder(
    path: String,
    size: InlineImageSize,
    availableWidthPx: Int,
    fontSizePx: Float,
): Placeholder {
    val heightPerWidth = aspectCache.getOrPut(path) { imageAspectRatio(path) ?: 1f }
        .coerceAtLeast(0.05f)
    val safeFontSizePx = fontSizePx.coerceAtLeast(1f)
    val maximumWidthEm = availableWidthPx.coerceAtLeast(1) / safeFontSizePx
    val authorWidthFraction = size.widthFrac?.takeIf { it.isFinite() && it > 0f }
    val authorWidthEm = size.widthEm?.takeIf { it.isFinite() && it > 0f }
    val authorHeightEm = size.heightEm?.takeIf { it.isFinite() && it > 0f }
    val requestedWidthEm = when {
        authorWidthFraction != null -> maximumWidthEm * authorWidthFraction.coerceAtMost(1f)
        authorWidthEm != null -> authorWidthEm
        authorHeightEm != null -> authorHeightEm / heightPerWidth
        else -> DEFAULT_INLINE_IMAGE_HEIGHT_EM / heightPerWidth
    }
    val requestedHeightEm = authorHeightEm
        ?: if (authorWidthFraction == null && authorWidthEm == null) {
            DEFAULT_INLINE_IMAGE_HEIGHT_EM
        } else {
            requestedWidthEm * heightPerWidth
        }
    val widthScale = min(1f, maximumWidthEm / requestedWidthEm)
    return Placeholder(
        width = (requestedWidthEm * widthScale).em,
        height = (requestedHeightEm * widthScale).em,
        placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
    )
}

private fun inlineImageOccurrences(text: AnnotatedString): List<InlineImageOccurrence> {
    val images = text.getStringAnnotations(INLINE_IMAGE_TAG, 0, text.length)
    if (images.isEmpty()) {
        return emptyList()
    }
    val ids = text.getStringAnnotations(INLINE_IMAGE_ID_TAG, 0, text.length)
        .associateBy { it.start to it.end }
    val sizes = text.getStringAnnotations(INLINE_IMAGE_SIZE_TAG, 0, text.length)
        .associateBy { it.start to it.end }
    val descriptions = text.getStringAnnotations(INLINE_IMAGE_ALT_TAG, 0, text.length)
        .associateBy { it.start to it.end }
    return images.map { image ->
        val position = image.start to image.end
        InlineImageOccurrence(
            id = ids[position]?.item ?: image.item,
            path = image.item,
            description = descriptions[position]?.item,
            size = sizes[position]?.item?.let(InlineImageSize::fromAnnotationValue)
                ?: InlineImageSize(),
            start = image.start,
            end = image.end,
        )
    }
}

internal fun inlineImagePlaceholders(
    text: AnnotatedString,
    availableWidthPx: Int,
    fontSizePx: Float,
): List<AnnotatedString.Range<Placeholder>> {
    return inlineImageOccurrences(text).map { image ->
        AnnotatedString.Range(
            item = inlineImagePlaceholder(image.path, image.size, availableWidthPx, fontSizePx),
            start = image.start,
            end = image.end,
        )
    }
}

internal fun inlineImageContent(
    text: AnnotatedString,
    availableWidthPx: Int,
    fontSizePx: Float,
    invert: Boolean = false,
): Map<String, InlineTextContent> {
    return inlineImageOccurrences(text).associate { image ->
        image.id to InlineTextContent(
            inlineImagePlaceholder(image.path, image.size, availableWidthPx, fontSizePx),
        ) {
            AsyncImage(
                model = File(image.path),
                contentDescription = image.description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = imageColorFilter(invert),
            )
        }
    }
}
