package io.raylytics.justmyweather.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.raylytics.justmyweather.resources.Res
import io.raylytics.justmyweather.resources.home_collapse
import io.raylytics.justmyweather.resources.home_collapsed
import io.raylytics.justmyweather.resources.home_details
import io.raylytics.justmyweather.resources.home_expand
import io.raylytics.justmyweather.resources.home_move_down
import io.raylytics.justmyweather.resources.home_move_up
import io.raylytics.justmyweather.resources.home_narrower
import io.raylytics.justmyweather.resources.home_shorter
import io.raylytics.justmyweather.resources.home_taller
import io.raylytics.justmyweather.resources.home_wider
import io.raylytics.justmyweather.view.Detail
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ModuleContent
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ModuleSize
import io.raylytics.justmyweather.view.ModuleValue
import io.raylytics.justmyweather.view.wholeRowTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/*
 * The modular glance: every visible field as a bordered tile on a 4-column
 * lattice, plus the launcher-style gestures that rearrange and resize it. All
 * the gesture and animation machinery is deliberately confined to this file —
 * the geometry it acts on (sizes, cell packing, reorder transforms) is pure
 * Kotlin in view/, and the rest of the app only ever sees a ViewConfig change.
 * docs/modular-v2-evaluation.md holds the criteria this design is judged by.
 */

/** Wiggle amplitude, degrees. Visible without being carnival. */
private const val WIGGLE_DEGREES = 1.6f

/** One half-cycle of the wiggle. */
private const val WIGGLE_PERIOD_MS = 160

/**
 * Which gesture detector is driving a drag.
 *
 * Both are live while arranging — the hold that enters the mode and carries
 * straight on into a drag, and the plain drag that works once the tiles are
 * already wiggling — and a slow press that then moves can look like the start
 * of either. Without an owner they would both add to the drag position and the
 * tile would travel at double speed.
 */
private enum class DragOwner { LONG_PRESS, IMMEDIATE }

/**
 * What a drag is doing to its tile. Decided where the finger landed: on the
 * corner handle it resizes, anywhere else on the tile it moves. One drag is
 * one or the other for its whole life.
 */
private enum class DragKind { MOVE, RESIZE }

/**
 * How long a tile must be held, without moving, to LEAVE arrange mode — timed
 * from the finger landing. Halved from 1s at Evan's request (2026-10-07), so
 * it now sits just past the platform's long-press (~400ms), which while
 * arranging already lifts the tile for a drag. Any movement past touch slop
 * forfeits the exit for that gesture.
 */
private const val EXIT_HOLD_MS = 500L

/**
 * The least time the exit waits AFTER the long-press lifts the tile. On a
 * device whose long-press is 500ms or more (an accessibility "touch & hold
 * delay", or older Android), [EXIT_HOLD_MS] alone would fire at the very
 * moment the tile is picked up, and every attempt to move a tile would leave
 * arrange mode instead. This keeps a beat in which a still finger means
 * "about to move", not "done".
 */
private const val EXIT_AFTER_LIFT_MIN_MS = 100L

/** The dragged tile lifts slightly, the way a launcher icon does. */
private const val DRAG_SCALE = 1.04f

/** The resize handle: a dot on the tile's bottom-right corner. */
private val HANDLE_RADIUS = 6.dp

/** How far the handle's dot sits inside the corner, so it rides the border's
 * curve rather than floating off the tile. */
private val HANDLE_INSET = 3.dp

/**
 * The region around a tile's bottom-right corner that starts a resize instead
 * of a move: this far INSIDE the corner along each edge, and [HANDLE_REACH]
 * outside it. Far bigger than the dot it surrounds: the dot is what to aim
 * at, this is what a thumb actually lands on.
 */
private val HANDLE_HIT = 40.dp

/**
 * How far past the corner the handle still catches. A drag's start is not the
 * finger's landing point but where it crossed the touch slop, which for a
 * growing drag is down-and-right of the dot — outside the tile — by the slop
 * plus whatever a fast finger covered in the frame that crossed it. Verified
 * on-device: with only half a gap of reach, a grow from the dot missed and
 * was taken for a move. The cost is a thin strip of the neighbour's corner
 * that resizes this tile instead of moving that one.
 */
private val HANDLE_REACH = 24.dp

/**
 * How a tile lifts, settles and slides: a firm spring with no bounce. Quick
 * enough that a drop reads as the tile landing, not as an animation being
 * played at you; no overshoot because the whole screen is built to be calm.
 */
private fun <T> settleSpring() =
    spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * Where a tile is drawn relative to where layout put it, and how big. One per
 * module, held by the grid rather than the tile: a reorder re-packs the rows
 * and re-targets the (unkeyed) tile composables, so state a tile remembered
 * for itself would follow the slot, not the module.
 *
 * Both values are Animatables read only inside the graphics layer, so every
 * frame of a lift, a settle or a slide invalidates the layer and nothing else.
 */
private class TileMotion {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    val scale = Animatable(1f)
}

/**
 * The grid itself. Tiles flow in the user's order at their configured spans;
 * a trailing gap in a row is honest grid space and stays empty. Outside
 * arrange mode this composable is inert — the borders are the only sign the
 * grid exists.
 *
 * Arrange mode is the launcher's grammar: long-press any tile to enter (tiles
 * wiggle, borders take the accent, a handle appears on every corner), and the
 * same hold flows straight into a drag. While arranging, drag a tile to move
 * it and drag its bottom-right corner to resize it — the corner snaps to
 * cells as the finger draws the rectangle, and stops at the module's own
 * minimum. Holding a tile still for [EXIT_HOLD_MS] while arranging leaves the
 * mode, as Done, Back and a tap on empty ground do. Every edit lands as a
 * [ViewConfig][io.raylytics.justmyweather.view.ViewConfig] transform through
 * [onMove]/[onResize], so a drag persists like any other customization —
 * there is no separate "editing copy" to commit or lose.
 *
 * All pointer detection lives on the GRID, not the tiles, with tiles found by
 * hit-testing the bounds they report. That is a correctness requirement, not a
 * style choice: a reorder re-packs the rows, which re-targets the (unkeyed)
 * tile composables, and a gesture coroutine keyed to a tile dies mid-drag when
 * its tile changes row — the drop event then never arrives, and the dragged
 * tile freezes mid-air (verified on-device via the drag lifecycle logs). The
 * grid node never moves, so a gesture that starts on it always sees its end.
 *
 * Motion follows the same rule. A tile that changes slot — because a drag
 * moved it, its neighbour, or an accessibility action did — slides from where
 * it was to where it now is, and a dropped tile springs into its slot rather
 * than snapping. Both are per-module Animatables held HERE (see [TileMotion]),
 * driven from the tile's layout callback, and applied in its graphics layer,
 * so the reflow is legible without a single tile recomposing for it.
 */
@Composable
fun ModuleGrid(
    modules: List<ModuleValue>,
    arranging: Boolean,
    spec: DensitySpec,
    onStartArranging: () -> Unit,
    /** A still hold on a tile while arranging: leave the mode. */
    onStopArranging: () -> Unit,
    /** Give a module this footprint. The config clamps it to the module's
     * minimum, so the grid asks for whatever the finger drew. */
    onResize: (ModuleKey, ModuleSize) -> Unit,
    /** Move a module so it lands at this index among the visible ones. */
    onMove: (ModuleKey, Int) -> Unit,
    /** The forecast module's own option: which framing it shows. */
    onSetForecastMode: (ForecastMode) -> Unit,
    /** A tap on a tile outside arrange mode, or null when tap-for-details is
     * off. The module's detail is built by the caller, which holds the
     * observation the tile's value came from. */
    onOpenModule: ((ModuleValue) -> Unit)? = null,
    /** For the tiles INSIDE the forecast module, which build their own. */
    onOpenDetail: ((Detail) -> Unit)? = null,
    /** A tap on a folded collapsible tile opens it (true); its collapse
     * control folds it back (false). See ModuleValue.collapsed. */
    onSetExpanded: (ModuleKey, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val gapPx = with(density) { spec.moduleGap.toPx() }
    val handleHitPx = with(density) { HANDLE_HIT.toPx() }
    val handleReachPx = with(density) { HANDLE_REACH.toPx() }

    // The gesture coroutines below live in pointerInput(Unit) — never
    // restarted, so they cannot lose a gesture — which means everything they
    // touch must be readable through state, never captured parameter values.
    val currentModules by rememberUpdatedState(modules)
    val isArranging by rememberUpdatedState(arranging)
    val startArranging by rememberUpdatedState(onStartArranging)
    val stopArranging by rememberUpdatedState(onStopArranging)
    val resize by rememberUpdatedState(onResize)
    val move by rememberUpdatedState(onMove)
    val openModule by rememberUpdatedState(onOpenModule)
    val setExpanded by rememberUpdatedState(onSetExpanded)

    // Drag state. Positions are all in window-root coordinates — one shared
    // frame for the pointer and every tile's bounds, so nothing needs to know
    // which row anything is in. `bounds` is refreshed by layout after every
    // reorder, which is what keeps the dragged tile anchored under the finger
    // when its slot (and therefore its layout position) changes mid-drag.
    var dragged by remember { mutableStateOf<ModuleKey?>(null) }
    var dragKind by remember { mutableStateOf<DragKind?>(null) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    // A resize measures the rectangle the finger has drawn from the tile's
    // top-left AS IT WAS when the drag began. Growing a tile can move it (it
    // no longer fits where it sat and re-packs onto the next row); measuring
    // from where it is now would then change the answer under a still finger.
    var resizeOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableStateMapOf<ModuleKey, Rect>() }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Where each tile last sat, in the GRID's frame rather than the window's:
    // the page scrolls, and a scroll moves every tile in root coordinates
    // without moving any of them on the grid. Comparing slots here is what
    // lets a layout callback tell "the order changed" from "the page moved".
    val slots = remember { mutableMapOf<ModuleKey, Offset>() }
    val motions = remember { mutableMapOf<ModuleKey, TileMotion>() }

    fun motionOf(field: ModuleKey) = motions.getOrPut(field) { TileMotion() }
    // A module that leaves the grid (hidden on the customize screen) and comes
    // back should appear in place, not fly in from wherever it last was.
    SideEffect {
        val visible = modules.mapTo(HashSet()) { it.module }
        slots.keys.retainAll(visible)
    }
    // Which detector is driving the current drag. While arranging BOTH are
    // live — the launcher gesture and the plain one — and a slow press that
    // then moves can look like the start of either. Without an owner they
    // would both add to dragPosition and the tile would travel at double
    // speed; with one, the second detector sees a drag already in progress
    // and keeps out of it.
    var dragOwner by remember { mutableStateOf<DragOwner?>(null) }
    // The move already requested but not yet reflected in the modules list —
    // the save round-trips through DataStore, and re-requesting the same move
    // on every drag event in that window would thrash.
    var pendingTarget by remember { mutableStateOf<Int?>(null) }
    // The band a full-width tile last crossed in this drag (planMove).
    var crossing by remember { mutableStateOf<RowCrossing<ModuleKey>?>(null) }
    // The resize's twin of pendingTarget.
    var pendingSize by remember { mutableStateOf<ModuleSize?>(null) }
    // Counts drags, so a drop's deferred clean-up can tell whether the drag it
    // belongs to is still the current one.
    var dragSerial by remember { mutableStateOf(0) }
    // Whether the current long-press drag has strayed past touch slop from
    // where it began — once it has, it is a drag, never a hold-to-exit.
    var holdMoved by remember { mutableStateOf(false) }
    var holdOrigin by remember { mutableStateOf(Offset.Zero) }
    // Counts hold-to-exits. The release that ends such a hold must not then
    // be read as a tap on the tile — by then the mode is off, so it would open
    // the tile's detail. The tap detector compares this against its value at
    // the press, which holds whatever order the detectors see the up in.
    var holdExits by remember { mutableStateOf(0) }

    // Gesture positions arrive grid-local; tiles report window-root bounds.
    fun toRoot(local: Offset): Offset = gridCoords?.localToRoot(local) ?: local

    fun tileAt(rootPos: Offset): ModuleKey? =
        currentModules.firstOrNull { bounds[it.module]?.contains(rootPos) == true }?.module

    /** The tile whose corner handle is under [rootPos], if any: inside the
     * corner by [HANDLE_HIT], past it by [HANDLE_REACH]. A folded tile has
     * no handle: its cell is not its size, so there is nothing to drag. */
    fun handleAt(rootPos: Offset): ModuleKey? =
        currentModules
            .firstOrNull {
                if (it.collapsed) return@firstOrNull false
                val rect = bounds[it.module] ?: return@firstOrNull false
                rootPos.x in (rect.right - handleHitPx)..(rect.right + handleReachPx) &&
                    rootPos.y in (rect.bottom - handleHitPx)..(rect.bottom + handleReachPx)
            }?.module

    /** What a drag starting at [rootPos] would do, and to which tile — or null
     * off every tile. The handle is only live while arranging: outside the
     * mode there is no handle to see, so a hold on a corner is a hold on the
     * tile. */
    fun dragAt(rootPos: Offset): Pair<ModuleKey, DragKind>? {
        if (isArranging) handleAt(rootPos)?.let { return it to DragKind.RESIZE }
        return tileAt(rootPos)?.let { it to DragKind.MOVE }
    }

    /** Claim the drag for [owner], or return false if someone else has it. */
    fun beginDrag(owner: DragOwner, field: ModuleKey, kind: DragKind, rootPos: Offset): Boolean {
        if (dragOwner != null) return false
        dragSerial++
        dragOwner = owner
        dragged = field
        dragKind = kind
        dragPosition = rootPos
        pendingTarget = null
        crossing = null
        pendingSize = null
        val rest = bounds[field]?.topLeft ?: rootPos
        when (kind) {
            DragKind.RESIZE -> resizeOrigin = rest
            DragKind.MOVE -> {
                grabOffset = rootPos - rest
                // The lift. Any slide still in flight is abandoned: the drag
                // positions the tile absolutely from here on.
                val motion = motionOf(field)
                scope.launch {
                    motion.offset.snapTo(Offset.Zero)
                    motion.scale.animateTo(DRAG_SCALE, settleSpring())
                }
            }
        }
        return true
    }

    fun endDrag(owner: DragOwner) {
        if (dragOwner != owner) return
        val field = dragged
        val kind = dragKind
        dragOwner = null
        pendingTarget = null
        crossing = null
        pendingSize = null
        if (field == null) return
        if (kind == DragKind.RESIZE) {
            // Nothing to land: a resize never lifted the tile, and each cell
            // it crossed was committed as it crossed it.
            dragged = null
            dragKind = null
            return
        }
        // The drop. The tile is wherever the finger left it and layout has its
        // slot; spring the difference to zero and the scale back to rest,
        // together, so it lands rather than appears. `dragged` is cleared
        // INSIDE the coroutine, after the offset is snapped: until then the
        // tile is still drawn at its drag position, and clearing it a frame
        // early would flash the tile in its slot before it flew back out to
        // land there. The serial guards the hand-off — if another drag has
        // begun by the time this runs, the tile is that drag's to place.
        val serial = dragSerial
        scope.launch {
            if (dragSerial != serial) return@launch
            val motion = motionOf(field)
            bounds[field]?.topLeft?.let { rest -> motion.offset.snapTo(dragPosition - grabOffset - rest) }
            dragged = null
            dragKind = null
            coroutineScope {
                launch { motion.scale.animateTo(1f, settleSpring()) }
                launch { motion.offset.animateTo(Offset.Zero, settleSpring()) }
            }
        }
    }

    // Nearest-center wins, not rect containment: rect hit-testing oscillates
    // when tiles of different sizes land where the pointer already is. A
    // full-width tile crosses the whole row it lands on, with a memory of the
    // band it crossed so the drag cannot bounce straight back (planMove).
    fun settleMove() {
        val field = dragged ?: return
        val current = currentModules.indexOfFirst { it.module == field }
        if (current == -1) return
        val plan =
            planMove(
                order = currentModules,
                current = current,
                key = { it.module },
                size = { it.size },
                bounds = { bounds[it] },
                pointer = dragPosition,
                lastCrossing = crossing,
            ) ?: return
        if (plan.target != pendingTarget) {
            pendingTarget = plan.target
            crossing = plan.crossing
            // A tick per slot, the launcher's way of letting the thumb feel
            // the grid it cannot see under itself.
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            move(field, plan.target)
        }
    }

    // The finger is drawing the tile's far corner; the size is however many
    // cells that rectangle rounds to, so the tile grows half a cell before it
    // snaps and shrinks the same way. The cell pitch is read off the tile
    // itself — its width is `columns` cells and `columns - 1` gaps — rather
    // than passed in, so this needs to know nothing about the lattice's
    // arithmetic. The config clamps to the module's minimum; the tick fires
    // only when a size is actually requested, so dragging past the floor is
    // silent, which is how the floor is felt.
    fun settleResize() {
        val field = dragged ?: return
        val module = currentModules.firstOrNull { it.module == field } ?: return
        val rect = bounds[field] ?: return
        val current = module.size
        val pitchX = (rect.width + gapPx) / current.columns
        val pitchY = (rect.height + gapPx) / current.rows
        val drawn = dragPosition - resizeOrigin
        val target =
            ModuleSize(
                columns = (drawn.x / pitchX).roundToInt(),
                rows = (drawn.y / pitchY).roundToInt(),
            ).clamp(field.minSize)
        if (target != current && target != pendingSize) {
            pendingSize = target
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            resize(field, target)
        }
    }

    fun settleDrag() {
        when (dragKind) {
            DragKind.MOVE -> settleMove()
            DragKind.RESIZE -> settleResize()
            null -> Unit
        }
    }

    CellGrid(
        items = modules,
        size = { it.size },
        gap = spec.moduleGap,
        cellAspect = spec.cellAspect,
        modifier =
            modifier
                .widthIn(max = GRID_MAX_WIDTH)
                .onGloballyPositioned { gridCoords = it }
                .pointerInput(Unit) {
                    // One detector for both modes: outside arrange mode the
                    // long-press starts the wiggle AND flows straight into the
                    // drag without lifting — the launcher gesture in full.
                    detectDragGesturesAfterLongPress(
                        onDragStart = { local ->
                            val rootPos = toRoot(local)
                            // Decided BEFORE the mode flips: a hold that opens
                            // the mode is a hold on a tile, not on a handle
                            // that was not there to be held.
                            val (field, kind) = dragAt(rootPos) ?: return@detectDragGesturesAfterLongPress
                            val wasArranging = isArranging
                            if (!wasArranging) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                startArranging()
                            }
                            if (!beginDrag(DragOwner.LONG_PRESS, field, kind, rootPos)) {
                                return@detectDragGesturesAfterLongPress
                            }
                            holdMoved = false
                            holdOrigin = rootPos
                            // A hold that began while already arranging can
                            // be the way out: if the finger is still down and
                            // still in place at EXIT_HOLD_MS, drop the tile
                            // where it was and leave the mode. The hold that
                            // OPENED the mode never counts — keeping a finger
                            // on the tile after the wiggle starts is normal.
                            if (wasArranging) {
                                val serial = dragSerial
                                val remaining = EXIT_HOLD_MS - viewConfiguration.longPressTimeoutMillis
                                scope.launch {
                                    delay(remaining.coerceAtLeast(EXIT_AFTER_LIFT_MIN_MS))
                                    val stillHeld = dragSerial == serial && dragOwner == DragOwner.LONG_PRESS
                                    if (!stillHeld || holdMoved || !isArranging) return@launch
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Releases the drag, so the rest of this
                                    // gesture's movement is ignored by onDrag.
                                    endDrag(DragOwner.LONG_PRESS)
                                    holdExits++
                                    stopArranging()
                                }
                            }
                        },
                        onDrag = { change, amount ->
                            if (dragOwner != DragOwner.LONG_PRESS) return@detectDragGesturesAfterLongPress
                            change.consume()
                            dragPosition += amount
                            if (!holdMoved && (dragPosition - holdOrigin).getDistance() > viewConfiguration.touchSlop) {
                                holdMoved = true
                            }
                            settleDrag()
                        },
                        onDragEnd = { endDrag(DragOwner.LONG_PRESS) },
                        onDragCancel = { endDrag(DragOwner.LONG_PRESS) },
                    )
                }
                // Once the tiles are wiggling, a plain drag moves one — or,
                // from its corner, resizes it — with no second long-press.
                // That is what a launcher does, and holding again for every
                // tile you want to nudge is the kind of friction people read
                // as the gesture not having worked.
                //
                // Keyed on `arranging` so the detector does not EXIST outside
                // arrange mode: an always-on drag detector over the grid would
                // swallow the glance's vertical scroll. Keying it is safe here
                // in a way it is not for the block above — that one is running
                // the very gesture that flips `arranging`, and restarting it
                // mid-hold is what once killed the drag and froze a tile
                // mid-air. This one is only ever created BEFORE its gesture
                // starts.
                .pointerInput(arranging) {
                    if (!arranging) return@pointerInput
                    // The `finally` is load-bearing. Keying this detector on
                    // `arranging` means Compose CANCELS it the moment arrange
                    // mode ends — and that can happen with a finger still
                    // down, through a channel this detector never sees: system
                    // Back is wired to leave arrange mode, and a second finger
                    // can reach "Done arranging". A cancelled coroutine
                    // unwinds through its suspension point; it does NOT run
                    // onDragEnd or onDragCancel. Without cleanup here the
                    // drag state would simply be abandoned mid-flight: the
                    // tile stranded at its drag offset and scale with nothing
                    // left to clear it, and dragOwner still claimed, so
                    // beginDrag would refuse every later drag for the life of
                    // the process. That is the same "frozen tile" failure the
                    // long-press detector is kept on pointerInput(Unit) to
                    // avoid, arriving by a different road.
                    try {
                        detectDragGestures(
                            onDragStart = { local ->
                                val rootPos = toRoot(local)
                                val (field, kind) = dragAt(rootPos) ?: return@detectDragGestures
                                beginDrag(DragOwner.IMMEDIATE, field, kind, rootPos)
                            },
                            onDrag = { change, amount ->
                                if (dragOwner != DragOwner.IMMEDIATE) return@detectDragGestures
                                change.consume()
                                dragPosition += amount
                                settleDrag()
                            },
                            onDragEnd = { endDrag(DragOwner.IMMEDIATE) },
                            onDragCancel = { endDrag(DragOwner.IMMEDIATE) },
                        )
                    } finally {
                        // A no-op when this detector did not own the drag.
                        endDrag(DragOwner.IMMEDIATE)
                    }
                }
                // Outside arrange mode a tap on a tile opens its detail — or,
                // on a folded tile, opens the tile: the fold is the one case
                // where the tile itself is the button, whatever the
                // tap-for-details option says. In
                // arrange mode taps do nothing — and are still consumed, so
                // they do nothing to anything else either: the
                // tap-on-empty-ground detector above this grid leaves arrange
                // mode, and a tap on a wiggling tile must not; the launcher
                // contract is that the way out is Done, Back, or the ground,
                // never the thing you were editing. Tiles inside the forecast
                // carry their own clickable and take their taps before this
                // sees them.
                .pointerInput(Unit) {
                    var exitsAtPress = 0
                    detectTapGestures(onPress = { exitsAtPress = holdExits }) { local ->
                        if (isArranging || holdExits != exitsAtPress) return@detectTapGestures
                        val field = tileAt(toRoot(local)) ?: return@detectTapGestures
                        val module = currentModules.firstOrNull { it.module == field } ?: return@detectTapGestures
                        if (module.collapsed) {
                            setExpanded(field, true)
                        } else {
                            openModule?.invoke(module)
                        }
                    }
                },
    ) { module, index, tileModifier ->
        val field = module.module
        // Only a MOVE lifts the tile and pins it under the finger; a resize
        // leaves it in the lattice and changes its cells.
        val isLifted = dragged == field && dragKind == DragKind.MOVE
        val wiggle = wiggleAngle(active = arranging && !isLifted, phase = index)
        val motion = motionOf(field)
        ModuleTile(
            index = index,
            lastIndex = modules.lastIndex,
            // The screen reader's "Move up/down" steps one place; through
            // the same rule as the drag, a full-width tile steps a row.
            onMove = { key, to -> onMove(key, wholeRowTarget(modules, index, to) { it.size }) },
            onResize = onResize,
            onSetForecastMode = onSetForecastMode,
            onOpenModule = onOpenModule,
            onOpenDetail = onOpenDetail,
            onSetExpanded = onSetExpanded,
            module = module,
            arranging = arranging,
            spec = spec,
            modifier =
                tileModifier
                    // The dragged tile draws over its neighbours, or it slides
                    // UNDER the next row on a long drag.
                    .zIndex(if (dragged == field) 1f else 0f)
                    .onGloballyPositioned { coords ->
                        bounds[field] = coords.boundsInRoot()
                        // A changed slot while not being moved means the
                        // order (or a neighbour's size) changed under this
                        // tile: slide from the old slot to the new one. The
                        // lifted tile is exempt — the finger, not layout, says
                        // where it is. A tile being RESIZED is not: growing
                        // can re-pack it onto the next row, and it should be
                        // seen going there.
                        val grid = gridCoords ?: return@onGloballyPositioned
                        val slot = grid.localPositionOf(coords, Offset.Zero)
                        val previous = slots.put(field, slot)
                        if (previous != null && previous != slot && !isLifted) {
                            scope.slide(motion, from = previous - slot)
                        }
                    }
                    .graphicsLayer {
                        // Every property is written on every pass, both
                        // branches: the layer keeps its last values between
                        // invocations, so a branch that "doesn't touch"
                        // translation would freeze the drag's offset onto the
                        // tile.
                        if (isLifted) {
                            // Anchor the grab point under the finger, wherever
                            // layout put the tile this frame.
                            val base = bounds[field]?.topLeft ?: (dragPosition - grabOffset)
                            val shift = dragPosition - grabOffset - base
                            translationX = shift.x
                            translationY = shift.y
                            scaleX = motion.scale.value
                            scaleY = motion.scale.value
                            rotationZ = 0f
                        } else {
                            translationX = motion.offset.value.x
                            translationY = motion.offset.value.y
                            scaleX = motion.scale.value
                            scaleY = motion.scale.value
                            rotationZ = wiggle.value
                        }
                    }
                    .testTag("module_${field.key}"),
        )
    }
}

/**
 * Slide a tile whose slot moved: it appears where it WAS and springs to where
 * it IS. Added to any motion already under way rather than replacing it, so a
 * tile whose slot moves twice in quick succession — a drop followed by the
 * pending reorder landing — keeps one continuous path.
 */
private fun CoroutineScope.slide(motion: TileMotion, from: Offset) =
    launch {
        motion.offset.snapTo(motion.offset.value + from)
        motion.offset.animateTo(Offset.Zero, settleSpring())
    }

/**
 * One tile: the always-on border (the user asked for the grid footprint to be
 * legible outside the editor — this thin line is that), a quiet label, the
 * value at the size its cells allow, and — while arranging — the handle on
 * its corner. Size IS prominence: the value is FITTED to the tile (see
 * FittedText) up to the hero size, so a 4×2 temperature is the hero, the same
 * reading at 1×1 is a small number, and a conditions phrase or a pressure
 * with its unit shrinks to fit rather than breaking words or spilling past the
 * border. A full-width tile drops its label — a value that wide speaks for
 * itself, as the old hero did.
 *
 * The tile is also where arranging becomes reachable without gestures. Its
 * accessibility actions — Move up/down, Wider/Narrower, Taller/Shorter — are
 * the SAME [onMove]/[onResize] calls the drags make, and they are offered at
 * all times rather than only while arranging: long-press-and-drag is not a
 * gesture a TalkBack or switch-access user can perform at all, so gating them
 * behind a mode they cannot enter would be gating them behind nothing.
 * Merging the tile into one node is what makes them reachable — an unmerged
 * node carrying no text of its own never takes accessibility focus, and
 * unfocusable actions are not actions.
 */
@Composable
private fun ModuleTile(
    module: ModuleValue,
    arranging: Boolean,
    spec: DensitySpec,
    /** This tile's place among the visible modules, and the last such index —
     * together they decide which move actions exist at the ends. */
    index: Int,
    lastIndex: Int,
    onMove: (ModuleKey, Int) -> Unit,
    onResize: (ModuleKey, ModuleSize) -> Unit,
    onSetForecastMode: (ForecastMode) -> Unit,
    onOpenModule: ((ModuleValue) -> Unit)?,
    onOpenDetail: ((Detail) -> Unit)?,
    onSetExpanded: (ModuleKey, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The screen-reader actions' names, read here because the semantics block
    // below is not composable.
    val a11y = TileActionLabels(
        details = stringResource(Res.string.home_details),
        expand = stringResource(Res.string.home_expand),
        collapse = stringResource(Res.string.home_collapse),
        collapsed = stringResource(Res.string.home_collapsed),
        moveUp = stringResource(Res.string.home_move_up),
        moveDown = stringResource(Res.string.home_move_down),
        wider = stringResource(Res.string.home_wider),
        narrower = stringResource(Res.string.home_narrower),
        taller = stringResource(Res.string.home_taller),
        shorter = stringResource(Res.string.home_shorter),
    )
    val borderColor =
        if (arranging) {
            // The accent marks "these are editable now" — the one moment the
            // border is allowed to speak up.
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    // A full-width tile drops its label — the content is big enough to speak
    // for itself, as the old hero did. The forecast draws its own header,
    // with its toggle in it. And the sun drops its DEFAULT caption while it
    // has times to show: they already say "Sunrise" and "Sunset", so "Sun"
    // above them only repeated the obvious (Evan, 2026-09-22). Two cases keep
    // it (found in review): a name the user chose on the customize screen,
    // which would otherwise be accepted and never shown; and the no-location
    // state, where the tile is a lone dash and the label is all that says
    // what it is — to the eye and to a screen reader.
    val sunSpeaksForItself =
        (module.content as? ModuleContent.Sun)?.let { sun ->
            sun.days.isNotEmpty() && module.label == module.module.defaultLabel
        } ?: false
    val showLabel =
        module.size.columns != ModuleSize.COLUMNS &&
            module.content !is ModuleContent.Forecast &&
            !sunSpeaksForItself
    val size = module.size
    val min = module.module.minSize
    val handleColor = MaterialTheme.colorScheme.primary
    val handleRing = MaterialTheme.colorScheme.background
    TileShell(
        borderColor = borderColor,
        modifier =
            modifier
                .drawWithContent {
                    drawContent()
                    // The handle: a dot on the corner, ringed in the ground
                    // colour so it reads as sitting ON the border rather than
                    // as a thickening of it. Drawn, not composed, so it adds
                    // no node to the tile's semantics and no layout to its
                    // cells. A folded tile has none: its cell is not its
                    // size (see handleAt).
                    if (arranging && !module.collapsed) {
                        val inset = HANDLE_INSET.toPx()
                        val center = Offset(this.size.width - inset, this.size.height - inset)
                        drawCircle(handleRing, radius = HANDLE_RADIUS.toPx() + 2.dp.toPx(), center = center)
                        drawCircle(handleColor, radius = HANDLE_RADIUS.toPx(), center = center)
                    }
                }
                .semantics(mergeDescendants = true) {
                    // The size is state, not a label: it changes under the
                    // user and is what the resize actions act on, so it
                    // belongs where a screen reader re-reads it rather than
                    // in the name.
                    // Folded, the tile's size is a cell by definition, and
                    // "collapsed" is the state worth hearing.
                    stateDescription = if (module.collapsed) a11y.collapsed else size.label
                    customActions =
                        buildList {
                            // The tap, for a user who cannot tap: offered
                            // only when the tap itself would do something.
                            // The forecast's hours and days are their own
                            // clickable nodes and need no action here.
                            if (module.collapsed) {
                                add(
                                    CustomAccessibilityAction(a11y.expand) {
                                        onSetExpanded(module.module, true)
                                        true
                                    },
                                )
                            } else if (module.collapsible) {
                                add(
                                    CustomAccessibilityAction(a11y.collapse) {
                                        onSetExpanded(module.module, false)
                                        true
                                    },
                                )
                            }
                            val hasDetail = onOpenModule != null && module.content !is ModuleContent.Forecast
                            if (hasDetail && !module.collapsed) {
                                add(
                                    CustomAccessibilityAction(a11y.details) {
                                        onOpenModule(module)
                                        true
                                    },
                                )
                            }
                            if (index > 0) {
                                add(
                                    CustomAccessibilityAction(a11y.moveUp) {
                                        onMove(module.module, index - 1)
                                        true
                                    },
                                )
                            }
                            if (index < lastIndex) {
                                add(
                                    CustomAccessibilityAction(a11y.moveDown) {
                                        onMove(module.module, index + 1)
                                        true
                                    },
                                )
                            }
                            // One cell at a time in each direction, and only
                            // the steps that exist: a tile at the grid's edge
                            // is not offered "Wider", one at its module's
                            // minimum is not offered "Narrower". An action
                            // that would do nothing is absent, not ignored.
                            // A folded tile is offered none: its cell is not
                            // its size, so a step from it means nothing.
                            if (module.collapsed) return@buildList
                            if (size.columns < ModuleSize.COLUMNS) {
                                add(
                                    CustomAccessibilityAction(a11y.wider) {
                                        onResize(module.module, size.wider())
                                        true
                                    },
                                )
                            }
                            if (size.columns > min.columns) {
                                add(
                                    CustomAccessibilityAction(a11y.narrower) {
                                        onResize(module.module, size.narrower())
                                        true
                                    },
                                )
                            }
                            if (size.rows < ModuleSize.MAX_ROWS) {
                                add(
                                    CustomAccessibilityAction(a11y.taller) {
                                        onResize(module.module, size.taller())
                                        true
                                    },
                                )
                            }
                            if (size.rows > min.rows) {
                                add(
                                    CustomAccessibilityAction(a11y.shorter) {
                                        onResize(module.module, size.shorter())
                                        true
                                    },
                                )
                            }
                        }
                },
    ) {
        // Folded: the name is the whole tile, whatever the module draws
        // open. Nothing else below applies to a cell that is only a button.
        if (module.collapsed) {
            CollapsedModuleContent(label = module.label)
            return@TileShell
        }
        // The way back for an open collapsible tile. Not while arranging:
        // every tap on a wiggling tile belongs to the grid.
        val onCollapse = if (module.collapsible && !arranging) ({ onSetExpanded(module.module, false) }) else null
        // The forecast fills its tile edge to edge — it is a grid with a
        // header, not a value to centre — so it skips the label column, and
        // its collapse control sits in that header.
        val content = module.content
        if (content is ModuleContent.Forecast) {
            ForecastModuleContent(
                content = content,
                columns = module.size.columns,
                gap = spec.moduleGap,
                arranging = arranging,
                onSetMode = onSetForecastMode,
                onOpenDetail = onOpenDetail,
                onCollapse = onCollapse,
            )
            return@TileShell
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The label line: the name centred, and for an open collapsible
            // tile the way back at its end. The control lives on this line
            // rather than floating in the tile's corner because the value
            // below is fitted to the tile's full width, and a corner is
            // exactly where a wide value's first line ends up. Here the label
            // keeps the control's width clear on both sides (both, so it
            // stays centred), and the value starts below the line, so
            // neither can run under the caret. A tile with no label of its
            // own (full width, or the sun speaking for itself) still gets
            // the line when it has a control to carry.
            if (showLabel || onCollapse != null) {
                // Fitted like the value, on one line: "Temperature" does not
                // fit a single cell at the label size, and "Temperat…" is
                // worse than the same word a shade smaller.
                val labelStyle = MaterialTheme.typography.labelSmall
                // The control is taller than the label's line, for the thumb's
                // sake. It overhangs that line a little above and below
                // (centred) rather than pushing the value down by the
                // difference: above is the tile's padding, and below is air
                // the value's own line height leaves before its glyphs.
                val lineHeight = with(LocalDensity.current) { labelStyle.lineHeight.toDp() }
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = lineHeight)) {
                    if (showLabel) {
                        FittedText(
                            text = module.label,
                            style = labelStyle,
                            ceiling = labelStyle.fontSize,
                            floor = LABEL_FLOOR,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = if (onCollapse != null) COLLAPSE_CONTROL_SIZE else 0.dp),
                        )
                    }
                    onCollapse?.let {
                        CollapseControl(
                            module = module.module,
                            onCollapse = it,
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    // Half into the tile's padding, as the old
                                    // corner placement was: it is a control,
                                    // not content, and reads better off the
                                    // content's edge.
                                    .offset(x = TILE_PADDING / 2)
                                    .height(lineHeight)
                                    .wrapContentHeight(unbounded = true),
                        )
                    }
                }
            }
            when (val content = module.content) {
                is ModuleContent.Reading ->
                    // The hero's face and weight at every size, so a reading
                    // is one thing scaled rather than several styles that
                    // happen to share a tile. The ceiling is the hero itself;
                    // the tile's cells decide how close the value gets.
                    FittedText(
                        text = content.text,
                        style = spec.heroStyle,
                        ceiling = spec.heroStyle.fontSize,
                        floor = VALUE_FLOOR,
                        color = MaterialTheme.colorScheme.onBackground,
                        // Two lines per row of cells: a phrase in a taller
                        // tile may use the height rather than shrink.
                        maxLines = 2 * size.rows,
                    )
                // Sun times draw themselves: a table at full size, today's
                // pair when smaller. See SunModule.kt for why that is
                // adaptation rather than two designs.
                is ModuleContent.Sun ->
                    SunModuleContent(
                        days = content.days,
                        size = module.size,
                        zone = content.zone,
                        arranging = arranging,
                    )
                // Handled above: the forecast takes the whole shell.
                is ModuleContent.Forecast -> Unit
            }
        }
    }
}

/**
 * The wiggle, per tile. Returns [State] rather than a value so only the
 * graphics layer reads it — the animation then invalidates the layer each
 * frame, never recomposition. [phase] staggers neighbours (the prime keeps the
 * offsets from lining up), because tiles wiggling in lockstep read as one
 * shivering screen instead of many loose tiles.
 */
@Composable
private fun wiggleAngle(active: Boolean, phase: Int): State<Float> {
    if (!active) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "wiggle")
    return transition.animateFloat(
        initialValue = -WIGGLE_DEGREES,
        targetValue = WIGGLE_DEGREES,
        animationSpec =
            infiniteRepeatable(
                animation = tween(WIGGLE_PERIOD_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset((phase * 37) % WIGGLE_PERIOD_MS),
            ),
        label = "wiggleAngle",
    )
}

/** The custom actions a tile offers a screen reader, by name, and the state
 * a folded tile announces. */
private class TileActionLabels(
    val details: String,
    val expand: String,
    val collapse: String,
    val collapsed: String,
    val moveUp: String,
    val moveDown: String,
    val wider: String,
    val narrower: String,
    val taller: String,
    val shorter: String,
)
