package com.example.mobilereader.data.parser

import com.example.mobilereader.data.model.Chapter
import com.example.mobilereader.data.model.ContentElement


object LanguageTag {

    private val validTag = Regex("^[a-z]{2,3}(-[a-z0-9]{2,8})*$")
    private val rtlLanguages = setOf(
        "ar", "arc", "ckb", "dv", "fa", "he", "iw", "ks", "lrc", "mzn",
        "nqo", "pnb", "ps", "rhg", "sd", "syr", "ug", "ur", "yi",
    )
    private val rtlScripts = setOf(
        "adlm", "arab", "hebr", "mand", "nkoo", "rohg", "samr", "syrc", "thaa",
    )


    fun normalize(raw: String?): String? {
        val tag = raw?.trim()?.lowercase()?.replace('_', '-') ?: return null
        if (!validTag.matches(tag)) return null
        return if (tag == "ua") "uk" else tag
    }


    fun isRtl(raw: String?): Boolean {
        val subtags = normalize(raw)?.split('-') ?: return false
        return subtags.any { it in rtlScripts } || subtags.firstOrNull() in rtlLanguages
    }


    fun detect(sample: String): String? {
        var letters = 0
        var cyrillic = 0
        var arabic = 0
        var hebrew = 0
        var ukrainian = 0
        for (ch in sample) {
            if (!ch.isLetter()) continue
            letters++
            if (ch in 'Ѐ'..'ӿ') cyrillic++
            if (ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' ||
                ch in '\u08A0'..'\u08FF' || ch in '\uFB50'..'\uFDFF' ||
                ch in '\uFE70'..'\uFEFF'
            ) {
                arabic++
            }
            if (ch in '\u0590'..'\u05FF' || ch in '\uFB1D'..'\uFB4F') hebrew++
            when (ch) {
                'і', 'ї', 'є', 'ґ', 'І', 'Ї', 'Є', 'Ґ' -> ukrainian++
            }
        }
        if (letters < 40) return null
        val threshold = letters * 6
        return when {
            arabic * 10 >= threshold && arabic >= hebrew && arabic >= cyrillic -> "ar"
            hebrew * 10 >= threshold && hebrew >= arabic && hebrew >= cyrillic -> "he"
            cyrillic * 10 >= threshold -> if (ukrainian >= 3) "uk" else "ru"
            else -> null
        }
    }


    fun detectFromChapters(chapters: List<Chapter>): String? {
        val sample = StringBuilder()
        outer@ for (chapter in chapters) {
            for (element in chapter.elements) {
                val text = when (element) {
                    is ContentElement.Paragraph -> element.text.text
                    is ContentElement.Heading -> element.text
                    else -> continue
                }
                sample.append(text).append(' ')
                if (sample.length >= 2000) break@outer
            }
        }
        return detect(sample.toString())
    }
}
