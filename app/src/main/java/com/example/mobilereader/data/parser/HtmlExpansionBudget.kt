package com.example.mobilereader.data.parser


class HtmlExpansionBudget(
    private val maxGeneratedRunChars: Int = DEFAULT_MAX_GENERATED_RUN_CHARS,
    private val maxGeneratedTotalChars: Long = DEFAULT_MAX_GENERATED_TOTAL_CHARS,
) {
    private var generatedChars: Long = 0

    init {
        require(maxGeneratedRunChars > 0)
        require(maxGeneratedTotalChars > 0)
    }

    @Synchronized
    internal fun acceptGenerated(charCount: Int): Boolean {
        if (charCount <= 0 || charCount > maxGeneratedRunChars) return false
        val count = charCount.toLong()
        if (generatedChars > maxGeneratedTotalChars - count) return false
        generatedChars += count
        return true
    }

    companion object {
        const val DEFAULT_MAX_GENERATED_RUN_CHARS = 4 * 1024
        const val DEFAULT_MAX_GENERATED_TOTAL_CHARS = 1024L * 1024L
    }
}
