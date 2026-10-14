package io.raylytics.justmyweather.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.raylytics.justmyweather.view.ModuleSize
import io.raylytics.justmyweather.view.wholeRowTarget

/**
 * The band a full-width tile last crossed in this drag, and which way it went.
 * Remembered so the very next pointer event cannot carry the tile straight
 * back over the same band.
 */
internal data class RowCrossing<K>(val band: Set<K>, val movedDown: Boolean)

/** Where a settle step moves the dragged item, and the crossing to remember. */
internal data class MovePlan<K>(val target: Int, val crossing: RowCrossing<K>?)

/**
 * One settle step of a move drag, kept pure so the decision can be driven on
 * the JVM with real lattice geometry. It returns null when the item stays put.
 *
 * Nearest-centre wins, as before; a full-width item then crosses the whole
 * band it lands on (wholeRowTarget). That jump breaks the old guarantee that
 * the moved tile is now the nearest centre. A tall tile beside two stacked
 * cells is the worst case: the hero jumps below the band, the lower cell
 * moves up under the finger, and without a memory the next event jumps the
 * hero straight back, flip-flopping on every event. So after a full-width
 * crossing, a target inside the band just crossed counts only once the
 * pointer is past that band's middle in the reverse direction. That is a
 * deliberate drag back, not the band settling under a finger that has not
 * moved.
 */
internal fun <T, K> planMove(
    order: List<T>,
    current: Int,
    key: (T) -> K,
    size: (T) -> ModuleSize,
    bounds: (K) -> Rect?,
    pointer: Offset,
    lastCrossing: RowCrossing<K>?,
): MovePlan<K>? {
    if (current !in order.indices) return null
    val nearest =
        order.indices.minByOrNull { i ->
            val center = bounds(key(order[i]))?.center ?: return@minByOrNull Float.MAX_VALUE
            (center - pointer).getDistanceSquared()
        } ?: return null
    if (lastCrossing != null && key(order[nearest]) in lastCrossing.band) {
        val rects = lastCrossing.band.mapNotNull(bounds)
        if (rects.isNotEmpty()) {
            val middle = (rects.minOf { it.top } + rects.maxOf { it.bottom }) / 2
            val goingBack = if (lastCrossing.movedDown) pointer.y < middle else pointer.y > middle
            if (!goingBack) return null
        }
    }
    val target = wholeRowTarget(order, current, nearest, size = size)
    if (target == current) return null
    val fullWidth = size(order[current]).columns >= ModuleSize.COLUMNS
    val crossing =
        if (fullWidth) {
            val passed = if (target > current) current + 1..target else target until current
            RowCrossing(passed.map { key(order[it]) }.toSet(), movedDown = target > current)
        } else {
            null
        }
    return MovePlan(target, crossing)
}
