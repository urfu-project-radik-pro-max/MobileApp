package com.example.mobilereader.data.model


enum class PublisherBorderStyle {
    NONE,
    SOLID,
    DASHED,
    DOTTED,
    DOUBLE,
}


data class PublisherBorderSide(
    val widthEm: Float,
    val colorArgb: Int,
    val style: PublisherBorderStyle,
)


data class PublisherBoxStyle(
    val marginTopEm: Float = 0f,
    val marginRightEm: Float = 0f,
    val marginRightFrac: Float = 0f,
    val marginBottomEm: Float = 0f,
    val marginLeftEm: Float = 0f,
    val marginLeftFrac: Float = 0f,
    val paddingTopEm: Float = 0f,
    val paddingRightEm: Float = 0f,
    val paddingRightFrac: Float = 0f,
    val paddingBottomEm: Float = 0f,
    val paddingLeftEm: Float = 0f,
    val paddingLeftFrac: Float = 0f,
    val backgroundColorArgb: Int? = null,
    val borderTop: PublisherBorderSide? = null,
    val borderRight: PublisherBorderSide? = null,
    val borderBottom: PublisherBorderSide? = null,
    val borderLeft: PublisherBorderSide? = null,
    val widthFrac: Float? = null,
    val widthEm: Float? = null,

    val horizontalAlign: PublisherBoxAlign = PublisherBoxAlign.START,
    val breakInsideAvoid: Boolean = false,
) {
    val isDefault: Boolean
        get() = this == DEFAULT

    companion object {
        val DEFAULT = PublisherBoxStyle()
    }
}

enum class PublisherBoxAlign { START, CENTER, END }

enum class PublisherFloatSide { LEFT, RIGHT }

enum class PublisherClear { NONE, LEFT, RIGHT, BOTH }


data class PublisherBoxSpan(
    val id: String,
    val parentId: String? = null,
    val startElement: Int,
    val endElementExclusive: Int,
    val style: PublisherBoxStyle = PublisherBoxStyle.DEFAULT,
    val floatSide: PublisherFloatSide? = null,
    val clear: PublisherClear = PublisherClear.NONE,
    val drawStart: Boolean = true,
    val drawEnd: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "Publisher box id must not be blank" }
        require(parentId != id) { "Publisher box cannot parent itself" }
        require(startElement >= 0) { "Publisher box start must be non-negative" }
        require(endElementExclusive >= startElement) {
            "Publisher box end must not precede its start"
        }
        if (endElementExclusive == startElement) {
            require(clear != PublisherClear.NONE) {
                "Only a clear marker may have an empty leaf range"
            }
            require(style.isDefault && floatSide == null) {
                "An empty clear marker cannot carry decoration or float"
            }
        }
    }


    fun intersects(start: Int, endExclusive: Int): Boolean {
        require(start >= 0) { "Slice start must be non-negative" }
        require(endExclusive >= start) { "Slice end must not precede its start" }
        return if (startElement == endElementExclusive) {
            startElement >= start && startElement < endExclusive
        } else {
            startElement < endExclusive && endElementExclusive > start
        }
    }


    fun sliceTo(
        start: Int,
        endExclusive: Int,
        destinationOffset: Int = 0,
    ): PublisherBoxSpan? {
        require(start >= 0) { "Slice start must be non-negative" }
        require(endExclusive >= start) { "Slice end must not precede its start" }
        require(destinationOffset >= 0) { "Destination offset must be non-negative" }
        if (!intersects(start, endExclusive)) return null

        if (startElement == endElementExclusive) {
            val rebased = startElement - start + destinationOffset
            return copy(startElement = rebased, endElementExclusive = rebased)
        }

        val includesStart = start <= startElement
        val includesEnd = endExclusive >= endElementExclusive
        return copy(
            startElement = maxOf(startElement, start) - start + destinationOffset,
            endElementExclusive = minOf(endElementExclusive, endExclusive) - start +
                    destinationOffset,
            clear = if (includesStart) clear else PublisherClear.NONE,
            drawStart = drawStart && includesStart,
            drawEnd = drawEnd && includesEnd,
        )
    }
}


fun List<PublisherBoxSpan>.slicePublisherBoxes(
    start: Int,
    endExclusive: Int,
): List<PublisherBoxSpan> = slicePublisherBoxSpans(this, start, endExclusive)


fun slicePublisherBoxSpans(
    spans: List<PublisherBoxSpan>,
    startElement: Int,
    endElementExclusive: Int,
    destinationOffset: Int = 0,
): List<PublisherBoxSpan> {
    require(startElement >= 0) { "Slice start must be non-negative" }
    require(endElementExclusive >= startElement) {
        "Slice end must not precede its start"
    }
    require(destinationOffset >= 0) { "Destination offset must be non-negative" }
    val sliced = spans.mapNotNull {
        it.sliceTo(startElement, endElementExclusive, destinationOffset)
    }
    if (sliced.isEmpty()) return emptyList()
    val retainedIds = sliced.asSequence().map(PublisherBoxSpan::id).toHashSet()
    return sliced.map { span ->
        span.takeIf { it.parentId == null || it.parentId in retainedIds }
            ?: span.copy(parentId = null)
    }
}
