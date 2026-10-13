package com.example.mobilereader.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.mobilereader.R
import com.example.mobilereader.ui.reader.selection.PillSide
import com.example.mobilereader.ui.reader.selection.SelectionController
import com.example.mobilereader.ui.reader.selection.SelectionHitRules
import com.example.mobilereader.ui.reader.selection.rememberFrameTicker
import com.example.mobilereader.ui.reader.selection.rememberSelectionOnScreen


private val TOOLBAR_GAP = 12.dp


private val TOOLBAR_INSET = 8.dp


private const val APPEAR_FROM = 0.86f


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionToolbar(
    controller: SelectionController,
    colors: ReaderColors,
    onQuote: () -> Unit,
    onTranslate: () -> Unit,
    onCopy: () -> Unit,
) {
    if (!controller.active || controller.dragging) return

    val onScreen = rememberSelectionOnScreen(controller)
    val frame = rememberFrameTicker()
    val density = LocalDensity.current
    var pill by remember { mutableStateOf(IntSize.Zero) }


    val visible = onScreen && !controller.surfaceMoving()


    val safeArea = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gapPx = with(density) { TOOLBAR_GAP.toPx() }
        val handleReachPx = with(density) { SelectionHitRules.HANDLE_REACH.toPx() }
        val marginPx = with(density) { TOOLBAR_INSET.toPx() }
        val insetTop = safeArea.getTop(density) + marginPx
        val insetBottom = safeArea.getBottom(density) + marginPx
        val insetLeft = safeArea.getLeft(density, layoutDirection) + marginPx
        val insetRight = safeArea.getRight(density, layoutDirection) + marginPx
        val screen = IntSize(constraints.maxWidth, constraints.maxHeight)

        if (!visible) return@BoxWithConstraints


        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f))
        }

        Surface(
            color = colors.chrome,
            contentColor = colors.onChrome,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .onSizeChanged { pill = it }
                .graphicsLayer {


                    frame.longValue
                    val bounds = controller.toolbarBounds()


                    if (bounds == null || pill == IntSize.Zero) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    val side = SelectionHitRules.toolbarSide(
                        bounds = bounds,
                        pillHeight = pill.height.toFloat(),
                        screenHeight = screen.height.toFloat(),
                        gapPx = gapPx,
                        handleReachPx = handleReachPx,
                        insetTop = insetTop,
                        insetBottom = insetBottom,
                    )
                    val where = SelectionHitRules.toolbarOffset(
                        bounds = bounds,
                        pillWidth = pill.width.toFloat(),
                        pillHeight = pill.height.toFloat(),
                        screenWidth = screen.width.toFloat(),
                        gapPx = gapPx,
                        handleReachPx = handleReachPx,
                        insetTop = insetTop,
                        insetLeft = insetLeft,
                        insetRight = insetRight,
                        side = side,
                    )
                    translationX = where.x
                    translationY = where.y

                    val grown = appear.value


                    alpha = (grown * 2f).coerceIn(0f, 1f)
                    scaleX = APPEAR_FROM + (1f - APPEAR_FROM) * grown
                    scaleY = scaleX


                    transformOrigin = TransformOrigin(
                        pivotFractionX = 0.5f,
                        pivotFractionY = if (side == PillSide.BELOW) 0f else 1f,
                    )
                },
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
            ) {
                ToolbarAction(
                    label = stringResource(R.string.selection_quote),
                    icon = { Icon(Icons.Rounded.FormatQuote, contentDescription = null) },
                    onClick = onQuote,
                )
                ToolbarAction(
                    label = stringResource(R.string.selection_translate),
                    icon = { Icon(Icons.Rounded.Translate, contentDescription = null) },
                    onClick = onTranslate,
                )
                ToolbarAction(
                    label = stringResource(R.string.selection_copy),
                    icon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = onCopy,
                )
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
