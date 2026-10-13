package com.example.mobilereader.ui.reader.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.TextLayoutResult
import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.ui.reader.BidiLayoutText


@Stable
class TextFragment(
    val itemIndex: Int,
    val charStart: Int,
    val length: Int,

    val bidi: BidiLayoutText?,


    val epoch: Int,
) {


    var layout: TextLayoutResult? by mutableStateOf(null)


    var coords: LayoutCoordinates? = null

    private var cacheOwner: TextLayoutResult? = null
    private val paths = HashMap<CharSpan, Path>(4)


    fun pathFor(layout: TextLayoutResult, span: CharSpan): Path {
        if (cacheOwner !== layout) {
            cacheOwner = layout
            paths.clear()
        }
        return paths.getOrPut(span) {
            layout.getPathForRange(
                bidi?.layoutStart(span.start) ?: span.start,
                bidi?.layoutEnd(span.end) ?: span.end,
            )
        }
    }


    fun holds(anchor: BookAnchor): Boolean =
        anchor.itemIndex == itemIndex &&
            anchor.charOffset >= charStart &&
            anchor.charOffset <= charStart + length
}


@Stable
class SelectionController {


    var selection by mutableStateOf<BookSelection?>(null)
        private set


    var active by mutableStateOf(false)
        private set


    var dragging by mutableStateOf(false)
        internal set


    var textAt: (Int) -> String? = { null }


    internal var space: LayoutCoordinates? = null


    internal var lastPoint: Offset? = null


    internal var extend: ((Offset) -> Unit)? = null


    var advance: SelectionAutoAdvance? = null


    var surfaceMoving: () -> Boolean = { false }

    internal val spaceSize: androidx.compose.ui.unit.IntSize?
        get() = liveSpace()?.size


    internal fun reExtend() {
        val point = lastPoint ?: return
        extend?.invoke(point)
    }


    fun magnifierSource(): Offset {
        if (!dragging) return Offset.Unspecified
        val point = lastPoint ?: return Offset.Unspecified
        val carets = listOfNotNull(
            caretOf(SelectionEdge.START),
            caretOf(SelectionEdge.END),
        )
        val nearest = carets.minByOrNull { (it.center - point).getDistance() }
        return nearest?.center ?: Offset.Unspecified
    }

    private val fragments = ArrayList<TextFragment>()
    private var epochCounter = 0


    fun newEpoch(): Int = ++epochCounter

    fun register(fragment: TextFragment) {
        fragments += fragment
    }

    fun unregister(fragment: TextFragment) {
        fragments.remove(fragment)
    }

    fun set(value: BookSelection?) {
        selection = value?.takeUnless { it.isEmpty }
        active = selection != null
    }

    fun clear() {
        selection = null
        active = false
        dragging = false
    }


    fun anchorAt(point: Offset, maxVerticalGap: Float = Float.MAX_VALUE): BookAnchor? {
        val space = liveSpace() ?: return null
        val visible = visibleFragments(space)
        if (visible.isEmpty()) return null
        val index = SelectionHitRules.pick(visible.map { it.second }, point, maxVerticalGap)
        if (index < 0) return null
        val fragment = visible[index].first
        val coords = fragment.coords ?: return null
        val layout = fragment.layout ?: return null
        val local = coords.localPositionOf(space, point)
        val layoutOffset = layout.getOffsetForPosition(local)
        val offset = (fragment.bidi?.sourceOffset(layoutOffset) ?: layoutOffset)
            .coerceIn(0, fragment.length)
        return BookAnchor(fragment.itemIndex, fragment.charStart + offset)
    }


    fun caretAt(anchor: BookAnchor): Rect? {
        val space = liveSpace() ?: return null
        var best: TextFragment? = null
        var bestScore = Int.MAX_VALUE
        for (candidate in fragments) {
            if (candidate.layout == null) continue
            val coords = candidate.coords ?: continue
            if (!coords.isAttached || !candidate.holds(anchor)) continue
            val local = anchor.charOffset - candidate.charStart


            val boundaryPenalty = if (local == candidate.length && local > 0) 1 else 0
            val score = boundaryPenalty * 1_000_000 - candidate.epoch
            if (score < bestScore) {
                best = candidate
                bestScore = score
            }
        }
        val fragment = best ?: return null
        val coords = fragment.coords ?: return null
        val layout = fragment.layout ?: return null
        val local = (anchor.charOffset - fragment.charStart).coerceIn(0, fragment.length)
        val layoutOffset = fragment.bidi?.layoutStart(local) ?: local
        val caret = layout.getCursorRect(layoutOffset)
        return Rect(
            space.localPositionOf(coords, caret.topLeft),
            space.localPositionOf(coords, caret.bottomRight),
        )
    }


    fun wordAt(point: Offset, maxVerticalGap: Float = Float.MAX_VALUE): BookSelection? {
        val anchor = anchorAt(point, maxVerticalGap) ?: return null
        val text = textAt(anchor.itemIndex) ?: return null
        val span = SelectionText.wordAt(text, anchor.charOffset)
        if (span.isEmpty) return null
        return BookSelection.of(anchor.itemIndex, span)
    }


    fun caretOf(edge: SelectionEdge): Rect? {
        val current = selection ?: return null
        val space = liveSpace() ?: return null
        val anchor = if (edge == SelectionEdge.START) current.start else current.end


        var fragment: TextFragment? = null
        var best = Int.MAX_VALUE
        for (candidate in fragments) {
            if (candidate.layout == null || !candidate.holds(anchor)) continue
            if (rectOf(space, candidate).isEmpty) continue
            val score = if (edge == SelectionEdge.END) {
                anchor.charOffset - candidate.charStart
            } else {
                candidate.charStart + candidate.length - anchor.charOffset
            }
            if (score < best || (score == best && candidate.epoch > (fragment?.epoch ?: -1))) {
                best = score
                fragment = candidate
            }
        }
        if (fragment == null) return null
        val coords = fragment.coords ?: return null
        val layout = fragment.layout ?: return null
        val local = (anchor.charOffset - fragment.charStart).coerceIn(0, fragment.length)
        val layoutOffset = when (edge) {
            SelectionEdge.START -> fragment.bidi?.layoutStart(local)
            SelectionEdge.END -> fragment.bidi?.layoutEnd(local)
        } ?: local
        val caret = layout.getCursorRect(layoutOffset)
        return Rect(
            space.localPositionOf(coords, caret.topLeft),
            space.localPositionOf(coords, caret.bottomRight),
        )
    }


    fun handleCenter(edge: SelectionEdge, radiusPx: Float): Offset? =
        caretOf(edge)?.let { SelectionHitRules.handleCenter(it, edge, radiusPx) }


    fun toolbarBounds(): Rect? {
        val current = selection ?: return null
        val space = liveSpace() ?: return null


        var column: Rect? = null
        var first: BookAnchor? = null
        for (fragment in fragments) {
            if (fragment.layout == null) continue
            val span = current.intersect(
                fragment.itemIndex, fragment.charStart, fragment.length,
            ) ?: continue
            val rect = rectOf(space, fragment)
            if (rect.isEmpty) continue
            val at = BookAnchor(fragment.itemIndex, fragment.charStart + span.start)
            if (first == null || at < first) {
                first = at
                column = rect
            }
        }
        val anchor = column ?: return null


        var result: Rect? = null
        for (fragment in fragments) {
            val span = current.intersect(
                fragment.itemIndex, fragment.charStart, fragment.length,
            ) ?: continue
            val layout = fragment.layout ?: continue
            val coords = fragment.coords ?: continue
            val rect = rectOf(space, fragment)
            if (rect.isEmpty || !SelectionHitRules.sharesColumn(anchor, rect)) continue
            val box = fragment.pathFor(layout, span).getBounds()
            if (box.isEmpty) continue
            val inSpace = Rect(
                space.localPositionOf(coords, box.topLeft),
                space.localPositionOf(coords, box.bottomRight),
            )
            result = result?.expandToInclude(inSpace) ?: inSpace
        }
        return result
    }


    fun selectedText(count: Int, elementAt: (Int) -> ContentElement): String {
        val current = selection ?: return ""
        return SelectionText.extract(current, count, elementAt)
    }

    private fun liveSpace(): LayoutCoordinates? = space?.takeIf { it.isAttached }


    private fun visibleFragments(space: LayoutCoordinates): List<Pair<TextFragment, Rect>> =
        fragments
            .mapNotNull { fragment ->
                if (fragment.layout == null) return@mapNotNull null
                val rect = rectOf(space, fragment)
                if (rect.isEmpty) null else fragment to rect
            }
            .sortedByDescending { it.first.epoch }

    private fun rectOf(space: LayoutCoordinates, fragment: TextFragment): Rect {
        val coords = fragment.coords ?: return Rect.Zero
        if (!coords.isAttached) return Rect.Zero
        return space.localBoundingBoxOf(coords, clipBounds = true)
    }
}

private fun Rect.expandToInclude(other: Rect) = Rect(
    left = minOf(left, other.left),
    top = minOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom),
)


@Stable
class ReaderHighlights(
    val controller: SelectionController,
    val epoch: Int,

    val quotes: List<BookSelection>,
    val quoteColor: Color,
    val selectionColor: Color,
)


@Composable
fun rememberReaderHighlights(
    controller: SelectionController,
    quotes: List<BookSelection>,
    quoteColor: Color,
    selectionColor: Color,
): ReaderHighlights {
    val epoch = remember { controller.newEpoch() }
    return remember(epoch, quotes, quoteColor, selectionColor) {
        ReaderHighlights(controller, epoch, quotes, quoteColor, selectionColor)
    }
}


@Composable
fun rememberTextFragment(
    highlights: ReaderHighlights?,
    itemIndex: Int,
    charStart: Int,
    length: Int,
    bidi: BidiLayoutText? = null,
): TextFragment? {
    if (highlights == null) return null
    val controller = highlights.controller
    val fragment = remember(itemIndex, charStart, length, bidi, highlights.epoch) {
        TextFragment(itemIndex, charStart, length, bidi, highlights.epoch)
    }
    DisposableEffect(fragment) {
        controller.register(fragment)
        onDispose { controller.unregister(fragment) }
    }
    return fragment
}


fun Modifier.readerHighlights(
    fragment: TextFragment?,
    highlights: ReaderHighlights?,
): Modifier {
    if (fragment == null || highlights == null) return this
    return this
        .onPlaced { fragment.coords = it }
        .drawBehind {
            val layout = fragment.layout ?: return@drawBehind
            for (quote in highlights.quotes) {
                val span = quote.intersect(
                    fragment.itemIndex, fragment.charStart, fragment.length,
                ) ?: continue
                drawPath(fragment.pathFor(layout, span), highlights.quoteColor)
            }
            val selected = highlights.controller.selection?.intersect(
                fragment.itemIndex, fragment.charStart, fragment.length,
            ) ?: return@drawBehind
            drawPath(fragment.pathFor(layout, selected), highlights.selectionColor)
        }
}
