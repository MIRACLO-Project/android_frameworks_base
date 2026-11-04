/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec

/**
 * Android's real tile editor, for the styles to draw in their own look: every system and app tile
 * (current first, then the ones that can be added), add, remove, reorder, and the small/large
 * size each style maps to its own shapes. Changes apply at once, as in the stock editor.
 */
internal class MiraEditHost(
    val tiles: List<EditTileViewModel>,
    val add: (TileSpec) -> Unit,
    val remove: (TileSpec) -> Unit,
    val setOrder: (List<TileSpec>) -> Unit,
    val isSmall: (TileSpec) -> Boolean,
    val resize: (TileSpec, Boolean) -> Unit,
    val done: () -> Unit,
) {
    val current: List<EditTileViewModel>
        get() = tiles.filter { it.isCurrent && it.tileSpec.spec !in MIRA_PROTO_EXCLUDED }

    val available: List<EditTileViewModel>
        get() = tiles.filter { !it.isCurrent && it.tileSpec.spec !in MIRA_PROTO_EXCLUDED }
}

/** Set while a style's own editor is open (null otherwise). */
internal val LocalMiraEditHost = compositionLocalOf<MiraEditHost?> { null }

/** How one style draws its editor: its colours and its tile shapes. */
internal class MiraEditLook(
    val fg: Color,
    val fg2: Color,
    val accent: Color,
    val onAccent: Color,
    val divider: Color,
    /** The surface of a tile in the editor (glass, card, circle fill). */
    val tileSurface: @Composable (shape: Shape) -> Modifier,
    val smallShape: Shape = CircleShape,
    val largeShape: Shape = RoundedCornerShape(32.dp),
    val smallSize: Dp = 60.dp,
    val iconTint: Color = fg,
    /** What each size is called in this style, shown under the hint. */
    val sizeNames: Pair<String, String> = "Round" to "Wide",
    /** The sizes a tile can take in this style, in the order a tap goes through them. */
    val sizes: List<MiraCell> = listOf(MiraCell.S11, MiraCell.S21),
    /** What each of [sizes] is called, shown in the hint. */
    val sizeLabels: List<String> = listOf(sizeNames.first, sizeNames.second),
    /** The shape of a 2x2 tile. */
    val bigShape: Shape = RoundedCornerShape(28.dp),
)

@Composable
internal fun MiraEditIcon(t: EditTileViewModel, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    SmallTileContent(iconProvider = { t.icon }, color = tint, size = { size }, modifier = modifier)
}

/**
 * The size a style gives tile [spec]: 1x1 when Android has it small, otherwise the size saved for
 * the style (one of [allowed], never 1x1), or 2x1.
 */
internal fun miraTileCell(
    spec: String,
    androidSmall: Boolean,
    store: MiraTileSizes?,
    allowed: List<MiraCell>,
): MiraCell {
    if (androidSmall) return MiraCell.S11
    val s = store?.of(spec, MiraCell.S21, allowed) ?: MiraCell.S21
    return if (s.isSmall) MiraCell.S21 else s
}

/**
 * The styled editor: your tiles in their real order and real shape, then every tile Android has
 * (system and app tiles) to add.
 *  - touch and hold, then drag, to move a tile (the others make room)
 *  - tap a tile, or its corner handle, to change its size (the style's sizes, in turn)
 *  - − removes it; tapping a tile underneath adds it at the end
 * Sizes other than Android's small and large are kept per style in [store].
 */
@Composable
internal fun MiraStyledEditor(
    look: MiraEditLook,
    host: MiraEditHost,
    store: MiraTileSizes? = null,
    blocks: MiraBlockLayout? = null,
) {
    val current = host.current
    val order = current.map { it.tileSpec }
    var dragging by remember { mutableStateOf<TileSpec?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragOrder by remember { mutableStateOf<List<TileSpec>?>(null) }
    val centres = remember { mutableStateMapOf<TileSpec, Offset>() }
    val shown = (dragOrder ?: order).filter { s -> current.any { it.tileSpec == s } }
    val bySpec = current.associateBy { it.tileSpec }
    val sizes = look.sizes
    fun sizeOf(spec: TileSpec): MiraCell = miraTileCell(spec.spec, host.isSmall(spec), store, sizes)
    fun resize(spec: TileSpec) {
        val next = miraNextSize(sizes, sizeOf(spec))
        if (!next.isSmall) store?.set(spec.spec, next)
        if (next.isSmall != host.isSmall(spec)) host.resize(spec, next.isSmall)
    }

    Box(Modifier.fillMaxSize()) {
        // Header: title and Done.
        Row(
            Modifier.offset(20.dp, 44.dp).width(350.dp).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Edit", color = look.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Box(
                Modifier.height(38.dp)
                    .background(look.accent, RoundedCornerShape(19.dp))
                    .protoTap(onTap = host.done)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Done", color = look.onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Hold and drag to move · tap to resize (" + look.sizeLabels.joinToString(" → ") + ") · − to remove",
            color = look.fg2,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(16.dp, 96.dp).width(358.dp),
        )
        Column(
            Modifier.offset(0.dp, 130.dp).fillMaxWidth().height((866 - 130).dp).verticalScroll(rememberScrollState())
        ) {
            // The style's widgets: move them, hide or bring back media and sliders.
            if (blocks != null) {
                MiraBlockEditor(look, blocks, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp).fillMaxWidth())
                Text(
                    "Tiles",
                    color = look.fg2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 26.dp, bottom = 10.dp),
                )
            }
            // Your tiles: four columns, each tile taking the cells its size says, packed densely.
            MiraCellGrid(
                sizes = shown.map { sizeOf(it) },
                cols = 4,
                cellHeight = 92.dp,
                gap = 0.dp,
                modifier = Modifier.padding(start = 26.dp, end = 26.dp).fillMaxWidth(),
            ) {
                shown.forEach { spec ->
                    val t = bySpec[spec]
                    val isDragged = dragging == spec
                    Box(
                        Modifier.zIndex(if (isDragged) 2f else 0f)
                            // where the slot is (measured before the drag offset)
                            .onGloballyPositioned { centres[spec] = it.boundsInRoot().center }
                            .graphicsLayer {
                                if (isDragged) {
                                    translationX = dragDelta.x
                                    translationY = dragDelta.y
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                }
                            }
                            .pointerInput(spec) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragging = spec
                                        dragDelta = Offset.Zero
                                        dragOrder = order
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDelta += amount
                                        val me = centres[spec] ?: return@detectDragGesturesAfterLongPress
                                        val p = me + dragDelta
                                        val list = dragOrder ?: order
                                        val target = centres.entries
                                            .filter { it.key != spec && it.key in list }
                                            .minByOrNull { (it.value - p).getDistanceSquared() }
                                        if (target != null && (target.value - p).getDistance() < 60f * density) {
                                            val to = list.indexOf(target.key)
                                            if (to >= 0 && list.indexOf(spec) != to) {
                                                val before = centres[spec]
                                                dragOrder = list.protoMove(spec, to)
                                                // keep the finger on the tile as it changes slot
                                                val after = centres[target.key]
                                                if (before != null && after != null) dragDelta += before - after
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        dragOrder?.let { if (it != order) host.setOrder(it) }
                                        dragging = null
                                        dragDelta = Offset.Zero
                                        dragOrder = null
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        dragDelta = Offset.Zero
                                        dragOrder = null
                                    },
                                )
                            },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (t != null) {
                            MiraEditTile(
                                look, t, sizeOf(spec),
                                badge = if (t.isRemovable) "−" else null,
                                badgeColor = Color(0xFF8E8E93),
                                onBadge = { host.remove(spec) },
                                onTap = { resize(spec) },
                                onResize = if (sizes.size > 1) ({ resize(spec) }) else null,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(look.divider))
            Text(
                "Tap to add",
                color = look.fg2,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 26.dp, top = 16.dp, bottom = 14.dp),
            )
            // Every tile that can be added: system tiles, then app tiles.
            Column(Modifier.padding(start = 26.dp, end = 26.dp).fillMaxWidth()) {
                host.available.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(100.dp)) {
                        row.forEach { t ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                MiraEditTile(
                                    look, t, MiraCell.S11,
                                    badge = "+",
                                    badgeColor = look.accent,
                                    onBadge = { host.add(t.tileSpec) },
                                    onTap = { host.add(t.tileSpec) },
                                    showApp = true,
                                )
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun MiraEditTile(
    look: MiraEditLook,
    t: EditTileViewModel,
    cell: MiraCell,
    badge: String?,
    badgeColor: Color,
    onBadge: () -> Unit,
    onTap: () -> Unit,
    showApp: Boolean = false,
    onResize: (() -> Unit)? = null,
) {
    val label = t.label.text
    val small = cell.isSmall
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.protoTap(onTap = onTap),
    ) {
        // One cell row is 92dp: the tile plus the label space under small tiles.
        val height = look.smallSize + 92.dp * (cell.h - 1)
        Box(
            Modifier.height(height)
                .then(if (cell.w == 1) Modifier.width(look.smallSize) else Modifier.fillMaxWidth().padding(horizontal = 6.dp))
        ) {
            when {
                small ->
                    Box(
                        Modifier.fillMaxSize().then(look.tileSurface(look.smallShape)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiraEditIcon(t, look.iconTint, 24.dp)
                    }
                cell.h == 1 ->
                    Row(
                        Modifier.fillMaxSize().then(look.tileSurface(look.largeShape)).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MiraEditIcon(t, look.iconTint, 24.dp)
                        Text(label, color = look.fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                cell.w == 1 ->
                    // tall: icon in the middle, as a tall capsule
                    Box(
                        Modifier.fillMaxSize().then(look.tileSurface(look.largeShape)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiraEditIcon(t, look.iconTint, 26.dp)
                    }
                else ->
                    // large: icon at the top, the name at the bottom
                    Column(
                        Modifier.fillMaxSize().then(look.tileSurface(look.bigShape)).padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MiraEditIcon(t, look.iconTint, 28.dp)
                        Text(label, color = look.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
            }
            if (badge != null) {
                Box(
                    Modifier.offset((-4).dp, (-4).dp)
                        .size(22.dp)
                        .background(badgeColor, CircleShape)
                        .protoTap(onTap = onBadge),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp)
                }
            }
            if (onResize != null) {
                MiraResizeHandle(
                    look.accent,
                    look.onAccent,
                    onResize,
                    Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp),
                )
            }
        }
        if (small || cell.w == 1) {
            Text(
                label,
                color = look.fg,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 82.dp),
            )
            if (showApp) {
                t.appName?.text?.takeIf { it.isNotBlank() && it != label }?.let {
                    Text(it, color = look.fg2, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 82.dp).alpha(0.9f))
                }
            }
        }
    }
}
