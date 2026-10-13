package com.example.mobilereader.ui.reader.selection

import com.example.mobilereader.data.model.Quote


data class BookAnchor(val itemIndex: Int, val charOffset: Int) : Comparable<BookAnchor> {
    override fun compareTo(other: BookAnchor): Int = when {
        itemIndex != other.itemIndex -> itemIndex.compareTo(other.itemIndex)
        else -> charOffset.compareTo(other.charOffset)
    }
}


data class CharSpan(val start: Int, val end: Int) {
    val isEmpty: Boolean get() = end <= start
    val length: Int get() = (end - start).coerceAtLeast(0)
}


enum class SelectionEdge { START, END }


data class BookSelection(val start: BookAnchor, val end: BookAnchor) {

    init {
        require(start <= end) { "BookSelection must be normalized: $start > $end" }
    }

    val isEmpty: Boolean get() = start == end


    val isSingleElement: Boolean get() = start.itemIndex == end.itemIndex


    fun intersect(itemIndex: Int, charStart: Int, length: Int): CharSpan? {
        if (isEmpty) return null
        if (itemIndex < start.itemIndex || itemIndex > end.itemIndex) return null

        val fragmentEnd = charStart + length
        val from = if (itemIndex == start.itemIndex) start.charOffset else charStart
        val to = if (itemIndex == end.itemIndex) end.charOffset else fragmentEnd

        val low = maxOf(from, charStart)
        val high = minOf(to, fragmentEnd)
        if (high <= low) return null
        return CharSpan(low - charStart, high - charStart)
    }

    operator fun contains(anchor: BookAnchor): Boolean = anchor >= start && anchor <= end


    fun move(edge: SelectionEdge, anchor: BookAnchor): BookSelection = when (edge) {
        SelectionEdge.START -> of(anchor, end)
        SelectionEdge.END -> of(start, anchor)
    }


    fun union(other: BookSelection): BookSelection =
        BookSelection(minOf(start, other.start), maxOf(end, other.end))

    companion object {
        fun of(a: BookAnchor, b: BookAnchor): BookSelection =
            if (a <= b) BookSelection(a, b) else BookSelection(b, a)

        fun of(itemIndex: Int, span: CharSpan): BookSelection =
            BookSelection(
                BookAnchor(itemIndex, span.start),
                BookAnchor(itemIndex, span.end),
            )
    }
}


fun Quote.range(): BookSelection? {
    if (startItem < 0 || startChar < 0 || endItem < 0 || endChar < 0) return null
    val range = BookSelection.of(
        BookAnchor(startItem, startChar),
        BookAnchor(endItem, endChar),
    )
    return range.takeUnless { it.isEmpty }
}
