package com.example.mobilereader.ui.reader.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds


private val PAGE_HOLD = 150.milliseconds


@Stable
class SelectionAutoAdvance(

    val step: suspend (direction: Int) -> Unit,

    val settle: suspend () -> Unit = {},
)


@Composable
fun SelectionAutoAdvanceEffect(controller: SelectionController) {
    val density = LocalDensity.current
    val bandPx = with(density) { SelectionHitRules.EDGE_BAND.toPx() }

    LaunchedEffect(controller.dragging) {
        if (!controller.dragging) return@LaunchedEffect
        var advanced = false
        try {
            while (true) {
                withFrameNanos { }
                val advance = controller.advance ?: continue
                val point = controller.lastPoint ?: continue
                val size = controller.spaceSize ?: continue
                val direction = SelectionHitRules.edgeAdvance(
                    point = point,
                    width = size.width.toFloat(),
                    bandPx = bandPx,
                )
                if (direction == 0) continue

                advanced = true
                advance.step(direction)
                delay(PAGE_HOLD)

                controller.reExtend()
            }
        } finally {


            if (advanced) withContext(NonCancellable) { controller.advance?.settle() }
        }
    }
}
