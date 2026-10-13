package com.example.mobilereader.data.parser


object ListMarkers {

    private val BULLET_TYPES = setOf("disc", "circle", "square")


    fun marker(type: String, index: Int): String = when (type) {
        "none" -> ""
        "disc" -> "• "
        "circle" -> "◦ "
        "square" -> "▪ "
        "decimal" -> "$index. "
        "lower-alpha", "lower-latin" -> "${toAlpha(index)}. "
        "upper-alpha", "upper-latin" -> "${toAlpha(index).uppercase()}. "
        "lower-roman" -> "${toRoman(index)}. "
        "upper-roman" -> "${toRoman(index).uppercase()}. "
        else -> "$index. "
    }


    fun toAlpha(n: Int): String {
        if (n < 1) return n.toString()
        var value = n
        val builder = StringBuilder()
        while (value > 0) {
            value--
            builder.append('a' + value % 26)
            value /= 26
        }
        return builder.reverse().toString()
    }


    fun toRoman(n: Int): String {
        if (n < 1 || n > 3999) return n.toString()
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i")
        var value = n
        val builder = StringBuilder()
        for (i in values.indices) {
            while (value >= values[i]) {
                builder.append(symbols[i])
                value -= values[i]
            }
        }
        return builder.toString()
    }


    fun cssTypeFor(typeAttr: String?): String? = when (typeAttr) {
        "1" -> "decimal"
        "a" -> "lower-alpha"
        "A" -> "upper-alpha"
        "i" -> "lower-roman"
        "I" -> "upper-roman"
        else -> null
    }


    fun applicableCssType(type: String?, ordered: Boolean): String? = type?.takeIf {
        it == "none" || (it in BULLET_TYPES) != ordered
    }


    fun defaultType(ordered: Boolean, depth: Int): String = if (ordered) {
        "decimal"
    } else {
        when ((depth - 1) % 3) {
            0 -> "disc"
            1 -> "circle"
            else -> "square"
        }
    }
}
