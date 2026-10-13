package com.example.mobilereader.data.parser

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.example.mobilereader.data.model.INLINE_IMAGE_CHAR
import com.example.mobilereader.data.model.INLINE_IMAGE_ALT_TAG
import com.example.mobilereader.data.model.INLINE_IMAGE_TAG


class InlineTextBuilder {
    private val builder = AnnotatedString.Builder()
    private var lastWasSpace = true
    private var hasVisibleContent = false

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


    fun inlineImage(ref: String, altText: String? = null) {
        val alt = altText?.trim()?.takeIf { it.isNotEmpty() }
        if (alt != null) builder.pushStringAnnotation(INLINE_IMAGE_ALT_TAG, alt)
        builder.pushStringAnnotation(INLINE_IMAGE_TAG, ref)
        builder.appendInlineContent(ref, INLINE_IMAGE_CHAR)
        builder.pop()
        if (alt != null) builder.pop()
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
