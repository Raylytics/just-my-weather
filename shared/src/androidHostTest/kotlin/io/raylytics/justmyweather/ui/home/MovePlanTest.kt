package io.raylytics.justmyweather.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.raylytics.justmyweather.view.ModuleSize
import io.raylytics.justmyweather.view.packGrid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The move drag's settle decision on real lattice geometry: one unit per
 * cell, no gaps, each tile's bounds straight from packGrid, so the centres
 * are the ones ModuleGrid would measure. Each step re-packs the new order and
 * settles again under a finger that has not moved, which is what a stream of
 * pointer events over a still thumb does.
 */
class MovePlanTest {
    private data class Tile(val name: String, val size: ModuleSize)

    private val hero = Tile("hero", ModuleSize(4, 2))
    private val tall = Tile("tall", ModuleSize(2, 2))
    private val top = Tile("top", ModuleSize(2, 1))
    private val bottom = Tile("bottom", ModuleSize(2, 1))

    private fun bounds(order: List<Tile>): Map<String, Rect> =
        packGrid(order) { it.size }.associate { p ->
            p.item.name to
                Rect(
                    left = p.column.toFloat(),
                    top = p.row.toFloat(),
                    right = (p.column + p.size.columns).toFloat(),
                    bottom = (p.row + p.size.rows).toFloat(),
                )
        }

    /** Settle [steps] times at [finger] with the hero held; the order after each. */
    private fun settle(
        start: List<Tile>,
        finger: Offset,
        steps: Int,
        remember: Boolean = true,
    ): List<List<String>> {
        var order = start
        var crossing: RowCrossing<String>? = null
        val seen = mutableListOf<List<String>>()
        repeat(steps) {
            val laid = bounds(order)
            val plan =
                planMove(
                    order = order,
                    current = order.indexOf(hero),
                    key = { it.name },
                    size = { it.size },
                    bounds = { laid[it] },
                    pointer = finger,
                    lastCrossing = if (remember) crossing else null,
                )
            if (plan != null) {
                order = order.toMutableList().apply {
                    remove(hero)
                    add(plan.target, hero)
                }
                crossing = plan.crossing
            }
            seen += order.map { it.name }
        }
        return seen
    }

    private val start = listOf(hero, tall, top, bottom)
    private val after = listOf("tall", "top", "bottom", "hero")

    @Test
    fun `without the remembered crossing the hero bounces over the band and back`() {
        // The defect this guards against, reproduced: the finger on the
        // hero's right side, half a cell into the band.
        val orders = settle(start, Offset(x = 3f, y = 2f), steps = 4, remember = false)
        assertEquals(listOf(after, start.map { it.name }, after, start.map { it.name }), orders)
    }

    @Test
    fun `a full-width tile that crossed a band stays put under a still finger`() {
        val orders = settle(start, Offset(x = 3f, y = 2f), steps = 6)
        assertEquals(List(6) { after }, orders)
    }

    @Test
    fun `dragging back past the band's middle carries it back`() {
        val down = settle(start, Offset(x = 3f, y = 2f), steps = 2).last()
        assertEquals(after, down)
        // The band now fills rows 0-1, so its middle is y = 1. A finger above
        // that is a deliberate drag back.
        val order = listOf(tall, top, bottom, hero)
        val laid = bounds(order)
        val plan =
            planMove(
                order = order,
                current = 3,
                key = { it.name },
                size = { it.size },
                bounds = { laid[it] },
                pointer = Offset(x = 3f, y = 0.5f),
                lastCrossing = RowCrossing(setOf("tall", "top", "bottom"), movedDown = true),
            )
        assertEquals(0, plan?.target)
    }

    @Test
    fun `a narrower tile moves one place and remembers no crossing`() {
        val order = listOf(hero, top, bottom)
        val laid = bounds(order)
        val plan =
            planMove(
                order = order,
                current = 2,
                key = { it.name },
                size = { it.size },
                bounds = { laid[it] },
                pointer = laid.getValue("top").center,
                lastCrossing = null,
            )
        assertEquals(MovePlan<String>(target = 1, crossing = null), plan)
    }
}
