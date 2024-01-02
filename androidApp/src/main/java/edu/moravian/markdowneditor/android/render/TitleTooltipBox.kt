package edu.moravian.markdowneditor.android.render

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


/**
 * A tooltip box that allows the tooltip text to be dynamically generated along
 * with having an offset from the anchor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TitleTooltipBox(
    modifier: Modifier = Modifier,
    content: @Composable (show: (text: String, rect: Rect?) -> Unit) -> Unit,
) {
    val tooltipState = rememberTooltipState()
    val tooltipPosition = rememberTitleTooltipPositionProvider()
    val coroutineScope = rememberCoroutineScope()
    var tooltipText: String? = null // not a remember { mutableStateOf(null) } because we don't want to trigger recomposition
    TooltipBox(
        positionProvider = tooltipPosition,
        tooltip = { tooltipText?.let { PlainTooltip { Text(it) } } },
        enableUserInput = false,
        state = tooltipState,
        modifier = modifier,
    ) {
        content { text, rect ->
            tooltipText = text
            tooltipPosition.tooltipAnchorOffset = rect
            coroutineScope.launch { tooltipState.show() }
        }
    }
}


@Composable
private fun rememberTitleTooltipPositionProvider(
    spacingBetweenTooltipAndAnchor: Dp = 4.dp
): TitleTooltipPopupPositionProvider {
    val spacing = with(LocalDensity.current) { spacingBetweenTooltipAndAnchor.roundToPx() }
    return remember(spacing) { TitleTooltipPopupPositionProvider(spacing) }
}

private class TitleTooltipPopupPositionProvider(
    private val tooltipAnchorSpacing: Int
) : PopupPositionProvider {
    var tooltipAnchorOffset: Rect? = null
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val rect = tooltipAnchorOffset
        var y = anchorBounds.top - popupContentSize.height - tooltipAnchorSpacing
        return if (rect !== null) {
            val x = anchorBounds.left + rect.left + (rect.width - popupContentSize.width) / 2
            y += rect.top.roundToInt()
            // TODO: positioning is off when y < 0 but isn't for some reason...
            //println("$y, $anchorBounds, ${popupContentSize.height}, $rect")
            if (y < 0)
                y = anchorBounds.top + rect.bottom.roundToInt() + tooltipAnchorSpacing
            IntOffset(x.roundToInt(), y)
        } else {
            val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
            if (y < 0)
                y = anchorBounds.bottom + tooltipAnchorSpacing
            IntOffset(x, y)
        }
    }
}
