package com.example.mobilereader.data.parser

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.example.mobilereader.data.model.INLINE_IMAGE_CHAR
import com.example.mobilereader.data.model.INLINE_IMAGE_ALT_TAG
import com.example.mobilereader.data.model.INLINE_IMAGE_ID_TAG
import com.example.mobilereader.data.model.INLINE_IMAGE_SIZE_TAG
import com.example.mobilereader.data.model.INLINE_IMAGE_TAG
import com.example.mobilereader.data.model.InlineImageSize


class InlineTextBuilder {
    private val builder = AnnotatedString.Builder()
    private var lastWasSpace = true
    private var hasVisibleContent = false
    private var nextImageId = 0

    fun text(raw: String) {
        if (raw.isEmpty()) return
        var chunk = raw.replace(whitespace, " ")
        if (chunk.isEmpty()) return
        if (lastWasSpace && chunk.startsWith(" ")) {
            chunk = chunk.trimStart()
            if (chunk.isEmpty()) return
        }
        builder.append(chunk)
        lastWasSpace = chunk.endsWith(" ")
        if (chunk.isNotBlank()) hasVisibleContent = true
    }


    fun preformattedText(raw: String) {
        if (raw.isEmpty()) return
        val chunk = raw.replace("\r\n", "\n").replace('\r', '\n')
        builder.append(chunk)
        lastWasSpace = chunk.lastOrNull()?.isWhitespace() == true
        if (chunk.any { !it.isWhitespace() }) hasVisibleContent = true
    }

    fun lineBreak() {
        builder.append("\n")
        lastWasSpace = true
    }


    fun wordBreakOpportunity() {
        builder.append('\u200B')
        lastWasSpace = false
    }


    fun inlineImage(
        ref: String,
        altText: String? = null,
        size: InlineImageSize = InlineImageSize(),
    ) {
        val alt = altText?.trim()?.takeIf { it.isNotEmpty() }
        val imageId = "inline-image-$nextImageId"
        nextImageId += 1
        if (alt != null) {
            builder.pushStringAnnotation(INLINE_IMAGE_ALT_TAG, alt)
        }
        builder.pushStringAnnotation(INLINE_IMAGE_TAG, ref)
        builder.pushStringAnnotation(INLINE_IMAGE_ID_TAG, imageId)
        if (size.isSpecified) {
            builder.pushStringAnnotation(INLINE_IMAGE_SIZE_TAG, size.annotationValue())
        }
        builder.appendInlineContent(imageId, INLINE_IMAGE_CHAR)
        if (size.isSpecified) {
            builder.pop()
        }
        builder.pop()
        builder.pop()
        if (alt != null) {
            builder.pop()
        }
        mutableImageRefs += ref
        lastWasSpace = false
    }


    val imageRefs: List<String> get() = mutableImageRefs

    private val mutableImageRefs = mutableListOf<String>()

    fun pushStyle(style: SpanStyle) {
        builder.pushStyle(style)
    }

    fun pushAnnotation(tag: String, value: String) {
        builder.pushStringAnnotation(tag, value)
    }

    fun pop() {
        builder.pop()
    }

    fun build(): AnnotatedString = builder.toAnnotatedString()

    val isBlank: Boolean
        get() = !hasVisibleContent

    private companion object {
        val whitespace = Regex("[\\s\\u00A0]+")
    }
}
