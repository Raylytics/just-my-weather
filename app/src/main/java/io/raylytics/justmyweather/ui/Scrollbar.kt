package io.raylytics.justmyweather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * A quiet scrollbar for the boxes that scroll inside a screen — the forecast
 * and sun tiles, the places list, the quiet-hours picker.
 *
 * Compose draws none by default, and a box that clips its content at a row
 * boundary can read as "that's all there is". The thumb is the hint that it
 * isn't: a thin rounded bar on the right, in the muted text colour, faint at
 * rest and a little firmer while a finger is moving it. It never takes
 * touches and never takes width from the content — it is drawn over the
 * scroller, or, with [gutter], out in the padding beside it.
 *
 * Apply it BEFORE the scroll modifier in the chain
 * (`Modifier.scrollbar(state).verticalScroll(state)`) so it is drawn in the
 * viewport's frame rather than scrolling away with the content.
 */

private val THUMB_WIDTH = 3.dp
private val THUMB_MIN_HEIGHT = 20.dp
private const val ALPHA_AT_REST = 0.28f
private const val ALPHA_SCROLLING = 0.6f

/** A scrollbar for a `verticalScroll(state)`. Draws nothing when the content fits. */
@Composable
fun Modifier.scrollbar(
    state: ScrollState,
    /** How far right of the scroller's edge to centre the thumb — half the
     * surrounding padding puts it in the margin instead of over the content. */
    gutter: Dp = 0.dp,
    /** Off hides it, e.g. while the glance is being arranged and the inner
     * scroll is switched off too. */
    enabled: Boolean = true,
): Modifier {
    val alpha by thumbAlpha(state.isScrollInProgress)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    return drawWithContent {
        drawContent()
        val max = state.maxValue
        if (!enabled || max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
        val viewport = size.height
        drawThumb(
            visible = viewport / (viewport + max),
            progress = state.value.toFloat() / max,
            gutter = gutter,
            color = color.copy(alpha = alpha),
        )
    }
}

/**
 * A scrollbar for a `LazyColumn(state = state)`. A lazy list only measures the
 * rows on screen, so the content height is estimated from their average —
 * close enough for a hint, which is all this is.
 */
@Composable
fun Modifier.scrollbar(
    state: LazyListState,
    gutter: Dp = 0.dp,
    enabled: Boolean = true,
): Modifier {
    val alpha by thumbAlpha(state.isScrollInProgress)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    return drawWithContent {
        drawContent()
        if (!enabled || !(state.canScrollForward || state.canScrollBackward)) return@drawWithContent
        val info = state.layoutInfo
        val rows = info.visibleItemsInfo
        if (rows.isEmpty() || info.totalItemsCount == 0) return@drawWithContent
        val average = rows.sumOf { it.size }.toFloat() / rows.size
        val content = average * info.totalItemsCount
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        if (content <= viewport) return@drawWithContent
        val scrolled = state.firstVisibleItemIndex * average + state.firstVisibleItemScrollOffset
        drawThumb(
            visible = viewport / content,
            // At the true end the estimate can fall short of 1; trust the state.
            progress = if (state.canScrollForward) (scrolled / (content - viewport)).coerceIn(0f, 1f) else 1f,
            gutter = gutter,
            color = color.copy(alpha = alpha),
        )
    }
}

@Composable
private fun thumbAlpha(scrolling: Boolean) =
    animateFloatAsState(if (scrolling) ALPHA_SCROLLING else ALPHA_AT_REST, label = "scrollbarAlpha")

/** [visible] is the share of the content on screen, [progress] how far down it is (0–1). */
private fun ContentDrawScope.drawThumb(
    visible: Float,
    progress: Float,
    gutter: Dp,
    color: Color,
) {
    val width = THUMB_WIDTH.toPx()
    val height = (size.height * visible).coerceIn(THUMB_MIN_HEIGHT.toPx().coerceAtMost(size.height), size.height)
    val top = (size.height - height) * progress.coerceIn(0f, 1f)
    val left = if (gutter > 0.dp) size.width + gutter.toPx() - width / 2 else size.width - width
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2),
    )
}
