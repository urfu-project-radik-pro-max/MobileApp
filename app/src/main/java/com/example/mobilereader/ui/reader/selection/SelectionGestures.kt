package com.example.mobilereader.ui.reader.selection

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope


private enum class Intent { TAP, ELSEWHERE, LONG_PRESS }


suspend fun PointerInputScope.bookSelectionGestures(
    controller: SelectionController,
    haptics: HapticFeedback,
) {
    val grabRadius = SelectionHitRules.HANDLE_GRAB_RADIUS.toPx()
    val startGap = SelectionHitRules.START_MAX_GAP.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val hadSelection = controller.selection != null


        val grabbed = if (hadSelection) {
            SelectionHitRules.grabbedHandle(
                start = controller.handleCenter(SelectionEdge.START, grabRadius),
                end = controller.handleCenter(SelectionEdge.END, grabRadius),
                point = down.position,
                radiusPx = grabRadius,
            )
        } else {
            null
        }
        if (grabbed != null) {
            down.consume()
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            dragEdge(controller, down, grabbed, haptics)
            return@awaitEachGesture
        }


        when (awaitIntent(down, hadSelection)) {
            Intent.ELSEWHERE -> return@awaitEachGesture

            Intent.TAP -> {


                controller.clear()
                return@awaitEachGesture
            }

            Intent.LONG_PRESS -> Unit
        }

        val word = controller.wordAt(down.position, maxVerticalGap = startGap)
        if (word == null) {


            return@awaitEachGesture
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        controller.set(word)
        dragWords(controller, down, word, haptics)
    }
}


private suspend fun AwaitPointerEventScope.awaitIntent(
    down: PointerInputChange,
    hadSelection: Boolean,
): Intent = try {
    withTimeout(viewConfiguration.longPressTimeoutMillis) {
        val slop = viewConfiguration.touchSlop
        var outcome: Intent? = null
        while (outcome == null) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            outcome = when {
                change == null -> Intent.ELSEWHERE
                event.changes.count { it.pressed } > 1 -> Intent.ELSEWHERE
                !change.pressed -> if (hadSelection) {


                    change.consume()
                    Intent.TAP
                } else {
                    Intent.ELSEWHERE
                }

                (change.position - down.position).getDistance() > slop -> Intent.ELSEWHERE
                else -> null
            }
        }
        outcome
    }
} catch (_: PointerEventTimeoutCancellationException) {
    Intent.LONG_PRESS
}


private suspend fun AwaitPointerEventScope.dragWords(
    controller: SelectionController,
    down: PointerInputChange,
    anchorWord: BookSelection,
    haptics: HapticFeedback,
) {
    consumeDrag(controller, down) { position ->
        val word = controller.wordAt(position) ?: return@consumeDrag
        val next = anchorWord.union(word)
        if (next != controller.selection) {
            controller.set(next)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
}


private suspend fun AwaitPointerEventScope.dragEdge(
    controller: SelectionController,
    down: PointerInputChange,
    grabbed: SelectionEdge,
    haptics: HapticFeedback,
) {
    var edge = grabbed


    val caret = controller.caretOf(edge)
    val grabOffset = caret?.let { down.position - it.center } ?: Offset.Zero

    consumeDrag(controller, down) { position ->
        val current = controller.selection ?: return@consumeDrag
        val anchor = controller.anchorAt(position - grabOffset) ?: return@consumeDrag
        val fixed = if (edge == SelectionEdge.START) current.end else current.start
        if (anchor == fixed) return@consumeDrag
        val next = BookSelection.of(anchor, fixed)
        if (next != current) {
            controller.set(next)
            edge = if (anchor < fixed) SelectionEdge.START else SelectionEdge.END
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
}


private suspend fun AwaitPointerEventScope.consumeDrag(
    controller: SelectionController,
    down: PointerInputChange,
    onMove: (Offset) -> Unit,
) {
    controller.lastPoint = down.position


    controller.extend = onMove
    controller.dragging = true
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (event.changes.count { it.pressed } > 1) break
            event.changes.forEach { it.consume() }
            if (!change.pressed) break
            if (change.position != controller.lastPoint) {
                controller.lastPoint = change.position
                onMove(change.position)
            }
        }
    } finally {
        controller.dragging = false
        controller.extend = null
    }
}
