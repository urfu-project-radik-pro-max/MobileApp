package com.example.mobilereader.ui.reader.selection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val HANDLE_RADIUS = SelectionHitRules.HANDLE_RADIUS


@Composable
fun SelectionHandles(controller: SelectionController, color: Color) {
    if (!controller.active) return
    val radius = with(LocalDensity.current) { HANDLE_RADIUS.toPx() }
    val frame = rememberFrameTicker()

    Canvas(Modifier.fillMaxSize()) {


        frame.longValue
        controller.caretOf(SelectionEdge.START)?.let {
            drawHandle(it, SelectionEdge.START, radius, color)
        }
        controller.caretOf(SelectionEdge.END)?.let {
            drawHandle(it, SelectionEdge.END, radius, color)
        }
    }
}


@Composable
internal fun rememberFrameTicker(): MutableLongState {
    val ticker = remember { mutableLongStateOf(0L) }
    LaunchedEffect(ticker) {
        while (true) withFrameNanos { ticker.longValue = it }
    }
    return ticker
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandle(
    caret: Rect,
    edge: SelectionEdge,
    radius: Float,
    color: Color,
) {
    val center = SelectionHitRules.handleCenter(caret, edge, radius)
    drawCircle(color = color, radius = radius, center = center)


    val corner = if (edge == SelectionEdge.START) {
        Offset(center.x, center.y - radius)
    } else {
        Offset(center.x - radius, center.y - radius)
    }
    drawRect(color = color, topLeft = corner, size = Size(radius, radius))
}


@Composable
fun rememberSelectionOnScreen(controller: SelectionController): Boolean {
    var onScreen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val next = controller.toolbarBounds() != null
            if (next != onScreen) onScreen = next
        }
    }
    return onScreen
}
