/*
 * Copyright (C) 2026 MIRACLO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.composefragment

import com.android.systemui.qs.panels.ui.compose.infinitegrid.MiraTileSkin
import android.media.AudioManager
import android.media.session.MediaController
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** iOS 27 palette, from the approved prototype. */
internal class MiraIosInk(val dark: Boolean) {
    val base = if (dark) Color(0xFF10131A) else Color(0xFFE3E6EE)
    val scrim = if (dark) Color(0x6608080C) else Color(0x4DF5F6FA)
    val fg = if (dark) Color.White else Color(0xFF1C1C1E)
    val fg2 = if (dark) Color.White.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.55f)
    val fg3 = if (dark) Color.White.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.25f)
    val block = if (dark) Color(0x4D787A86) else Color.White.copy(alpha = 0.50f)
    val off = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.62f)
    val edge = if (dark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.95f)
    val hi = if (dark) Color.White.copy(alpha = 0.7f) else Color.White
    val dim = if (dark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.35f)
    val sheet = if (dark) Color(0xEB282A32) else Color(0xF0F6F6FA)
}

private val IosBlue = Color(0xFF0A84FF)
private val IosOnBg = Color(0xFFF4F4F6)

/** Connectivity toggles filled with their colour when on. */
private fun iosFill(key: String): Color? =
    when (key) {
        "wifi", "bt", "vpn" -> IosBlue
        "airplane" -> Color(0xFFFF9F0A)
        "data", "hotspot" -> Color(0xFF30D158)
        else -> if (key.contains("nearby") || key.contains("share")) IosBlue else null
    }

/** Other controls: a light circle with the glyph in their colour when on. */
private fun iosGlyph(key: String): Color =
    when (key) {
        "rotate", "record", "silent" -> Color(0xFFFF453A)
        "torch" -> Color(0xFF1C1C1E)
        "dark" -> Color.Black
        "saver" -> Color(0xFF30D158)
        "alarm", "eye" -> Color(0xFFFF9F0A)
        "dnd", "modes_dnd" -> Color(0xFF5E5CE6)
        else -> IosBlue
    }

/** Controls drawn as 2x1 pills with a label (as in iOS Control Center). */
private val IOS_PILLS = setOf("dnd", "modes_dnd", "hotspot", "location", "alarm", "cast")

private const val IOS_CONN = "@conn"
private const val IOS_MUSIC = "@music"
private const val IOS_SILENT = "@silent"
private const val IOS_BRIGHT = "@bright"
private const val IOS_VOL = "@vol"
private val IOS_WIDGETS = listOf(IOS_CONN, IOS_MUSIC, IOS_SILENT, IOS_BRIGHT, IOS_VOL)

/** What can go in the connectivity box, in the order the picker lists them. */
private val IOS_CONN_CANDIDATES = listOf("airplane", "data", "wifi", "bt", "hotspot", "share", "nfc", "saver")
private val IOS_CONN_DEFAULT = listOf("airplane", "data", "wifi", "bt", "hotspot", "share")
private const val IOS_CONN_MAX = 6

private fun iosConnName(id: String) =
    when (id) {
        "airplane" -> "Airplane mode"
        "data" -> "Mobile data"
        "wifi" -> "Wi-Fi"
        "bt" -> "Bluetooth"
        "hotspot" -> "Hotspot"
        "share" -> "Quick Share"
        "nfc" -> "NFC"
        "saver" -> "Data Saver"
        else -> id
    }

/** True when Android's tile [spec] is the connectivity control [id]. */
private fun iosConnMatches(id: String, spec: String): Boolean {
    val s = spec.lowercase()
    return when (id) {
        "share" -> s.contains("nearby") || s.contains("quickshare")
        "wifi" -> s == "internet" || s == "wifi"
        "data" -> s == "cell"
        else -> s == id
    }
}

private fun iosConnFind(tiles: List<TileViewModel>, id: String): TileViewModel? =
    tiles.firstOrNull { iosConnMatches(id, it.spec.spec) }

/**
 * The connectivity box's controls, picked in edit mode (up to six, in order), kept in
 * Settings.Secure "miraclo_ios_conn". The first three are the big circles; from the fifth on they
 * share the last corner as mini icons.
 */
private class IosConnPicks(private val context: android.content.Context) {
    var keys by mutableStateOf(IOS_CONN_DEFAULT)
        private set

    fun load() {
        val raw =
            runCatching {
                    android.provider.Settings.Secure.getStringForUser(
                        context.contentResolver, KEY, android.app.ActivityManager.getCurrentUser())
                }
                .getOrNull()
        keys =
            if (raw == null) IOS_CONN_DEFAULT
            else raw.split(",").filter { it in IOS_CONN_CANDIDATES }.distinct().take(IOS_CONN_MAX)
    }

    fun set(list: List<String>) {
        keys = list.distinct().take(IOS_CONN_MAX)
        runCatching {
            android.provider.Settings.Secure.putStringForUser(
                context.contentResolver,
                KEY,
                keys.joinToString(","),
                android.app.ActivityManager.getCurrentUser(),
            )
        }
    }

    companion object {
        const val KEY = "miraclo_ios_conn"
    }
}

@Composable
private fun rememberIosConnPicks(): IosConnPicks {
    val context = LocalContext.current
    return remember { IosConnPicks(context).also { it.load() } }
}

/** The tiles in the connectivity box, in the order picked. */
private class IosConn(val all: List<TileViewModel>)

private fun iosConn(tiles: List<TileViewModel>, keys: List<String>): IosConn =
    IosConn(keys.mapNotNull { iosConnFind(tiles, it) }.distinct())

/** The sizes of a tile: circle, capsule with name, tall, large. */
private val IOS_TILE_SIZES = listOf(MiraCell.S11, MiraCell.S21, MiraCell.S12, MiraCell.S22)

private fun iosAllowed(key: String): List<MiraCell> =
    when (key) {
        IOS_CONN, IOS_MUSIC -> listOf(MiraCell.S22, MiraCell.S24)
        IOS_BRIGHT, IOS_VOL -> listOf(MiraCell.S12, MiraCell.S21, MiraCell.S22)
        IOS_SILENT -> listOf(MiraCell.S11, MiraCell.S21)
        else -> IOS_TILE_SIZES
    }

/** The size of each control in cells (width x height), as set with the corner handle. */
private fun iosCell(
    key: String,
    tiles: List<TileViewModel>,
    isLarge: (TileViewModel) -> Boolean,
    store: MiraTileSizes,
): MiraCell =
    when (key) {
        IOS_CONN, IOS_MUSIC -> store.of(key, MiraCell.S22, iosAllowed(key))
        IOS_BRIGHT, IOS_VOL -> store.of(key, MiraCell.S12, iosAllowed(key))
        IOS_SILENT -> store.of(key, MiraCell.S11, iosAllowed(key))
        else -> {
            val t = tiles.firstOrNull { it.spec.spec == key }
            miraTileCell(key, t == null || !isLarge(t), store, IOS_TILE_SIZES)
        }
    }

/** Something the "Add a Control" sheet can add. */
private class IosAddItem(
    val key: String,
    val label: String,
    val tile: TileViewModel? = null,
    val edit: EditTileViewModel? = null,
    val add: () -> Unit,
)

/**
 * iOS 27 Control Center: add and power at the top, three pages swiped up and down (controls,
 * Now Playing, connectivity) with the page column at the side. Holding the connectivity block,
 * or tapping its mini icons, opens the full connectivity module.
 *
 * "+" is edit mode, as Control Center: the controls jiggle; hold and drag to move, the corner
 * handle changes the size, − removes, and "Add a Control" lists every tile Android has (system
 * and app tiles) and the widgets. Android's real editor runs underneath, so tiles added or
 * removed here are added or removed for real.
 */
@Composable
internal fun MiraIosQs(env: MiraProtoEnv) {
    val c = remember(env.dark) { MiraIosInk(env.dark) }
    val editHost = LocalMiraEditHost.current
    val layout = rememberMiraProtoLayout(MiraProtoStyle.IOS)
    val sizeStore = rememberMiraTileSizes(MiraProtoStyle.IOS)
    val picks = rememberIosConnPicks()
    val conn = iosConn(env.tiles, picks.keys)
    val connKeys = conn.all.map { it.spec.spec }.toSet()
    val editing = editHost != null
    var sheet by remember { mutableStateOf(false) }
    var connSheet by remember { mutableStateOf(false) }
    var module by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (!editing) {
            sheet = false
            connSheet = false
        }
    }
    val scope = rememberCoroutineScope()
    val host = LocalMiraTileHost.current
    val isLarge: (TileViewModel) -> Boolean = { t ->
        host?.isSmall?.invoke(t)?.not() ?: (t.protoKey() in IOS_PILLS)
    }

    // Controls: the widgets keep the slots you gave them; the tiles follow Android's real order.
    val tileSpecs =
        env.tiles
            .filter {
                it.spec.spec !in MIRA_PROTO_EXCLUDED && it.spec.spec !in connKeys &&
                    it.spec.spec !in layout.hidden
            }
            .map { it.spec.spec }
    val saved = layout.order
    val items =
        if (saved.isEmpty()) {
            (listOf(IOS_CONN, IOS_MUSIC, IOS_SILENT) + tileSpecs.take(1) + listOf(IOS_BRIGHT, IOS_VOL) +
                    tileSpecs.drop(1))
                .filter { it !in layout.hidden }
        } else {
            val queue = ArrayDeque(tileSpecs)
            val out = mutableListOf<String>()
            saved.forEach { k ->
                if (k in IOS_WIDGETS) { if (k !in layout.hidden) out += k }
                else queue.removeFirstOrNull()?.let { out += it }
            }
            out + queue
        }
    val cells = items.map { iosCell(it, env.tiles, isLarge, sizeStore) }
    // Controls spread over as many pages as they need (no more overlapping the next page).
    val pageIdx = miraPages(cells, cols = 4, maxRows = IOS_ROWS)
    val controlPages = pageIdx.map { page -> page.map { items[it] } }
    val cp = controlPages.size
    val pager = rememberPagerState(0) { cp + 2 }

    fun resize(key: String) {
        val now = iosCell(key, env.tiles, isLarge, sizeStore)
        val next = miraNextSize(iosAllowed(key), now)
        if (key in IOS_WIDGETS) {
            sizeStore.set(key, next)
            return
        }
        if (!next.isSmall) sizeStore.set(key, next)
        val tile = env.tiles.firstOrNull { it.spec.spec == key } ?: return
        if (next.isSmall == isLarge(tile)) editHost?.resize?.invoke(tile.spec, next.isSmall)
    }

    fun remove(key: String) {
        val tile = env.tiles.firstOrNull { it.spec.spec == key }
        val real = editHost?.current?.firstOrNull { it.tileSpec.spec == key }
        if (key !in IOS_WIDGETS && tile != null && real != null && real.isRemovable && editHost != null) {
            editHost.remove(tile.spec)
        } else {
            layout.save(items - key, layout.hidden + key)
        }
    }

    // A page's new order: the widgets keep their new slots, the tiles take Android's order in turn.
    fun move(page: List<String>, newPage: List<String>) {
        val start = items.indexOf(page.firstOrNull() ?: return)
        if (start < 0) return
        val newItems = items.toMutableList()
        newPage.forEachIndexed { i, k -> if (start + i < newItems.size) newItems[start + i] = k }
        layout.save(newItems, layout.hidden)
        val h = editHost ?: return
        val all = h.current.map { it.tileSpec }
        val bySpec = all.associateBy { it.spec }
        val shownTiles = newItems.filter { it !in IOS_WIDGETS && it in bySpec }
        val slots = all.indices.filter { all[it].spec in shownTiles }
        val reordered = all.toMutableList()
        slots.forEachIndexed { k, idx -> shownTiles.getOrNull(k)?.let { bySpec[it] }?.let { reordered[idx] = it } }
        if (reordered != all) h.setOrder(reordered)
    }

    Box(Modifier.fillMaxSize()) {
        // Tapping empty space closes a sheet, then leaves edit mode, like Control Center.
        Box(
            Modifier.fillMaxSize().pointerInput(editing, sheet, connSheet) {
                detectTapGestures {
                    if (sheet) sheet = false
                    else if (connSheet) connSheet = false
                    else if (editing) editHost?.done?.invoke()
                }
            }
        )
        IosCircle(
            c,
            30.dp,
            18.dp,
            onTap = {
                if (editing) sheet = true
                else {
                    env.styleEdit()
                    scope.launch { pager.scrollToPage(0) }
                }
            },
        ) {
            MiraProtoGlyph(MiraCLOIcons.Add, c.fg, 16.dp)
        }
        IosCircle(c, (390 - 30 - 34).dp, 18.dp, onTap = env.power) {
            MiraProtoGlyph(MiraCLOIcons.Power, c.fg, 16.dp)
        }
        Text(
            miraProtoCarrier(),
            color = c.fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.offset(44.dp, 82.dp),
        )
        if (editing) {
            Text(
                "HOLD TO MOVE · CORNER TO RESIZE · TAP EMPTY SPACE WHEN DONE",
                color = c.fg2,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(0.dp, 58.dp).width(390.dp),
            )
        }

        VerticalPager(
            state = pager,
            userScrollEnabled = true,
            modifier = Modifier.offset(42.dp, 124.dp).width(305.dp).height(640.dp),
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                when {
                    page < cp ->
                        IosControls(
                            c = c,
                            env = env,
                            items = controlPages[page],
                            cells = pageIdx[page].map { cells[it] },
                            tiles = env.tiles,
                            conn = conn,
                            editing = editing,
                            onOpenModule = { module = true },
                            onRemove = { key -> remove(key) },
                            onResize = { key -> resize(key) },
                            onMove = { newPage -> move(controlPages[page], newPage) },
                            onConnEdit = { connSheet = true },
                        )
                    page == cp -> IosNowPlaying(c, env)
                    else -> IosConnectivityPage(c, conn.all)
                }
            }
        }

        // The page column: tap to jump.
        Column(
            Modifier.offset(354.dp, 300.dp).width(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (List(cp) { MiraProtoIcons.Favorite } + listOf(MiraCLOIcons.MusicNote, MiraProtoIcons.Tower)).forEachIndexed { i, icon ->
                Box(
                    Modifier.size(32.dp)
                        .alpha(if (pager.currentPage == i) 1f else 0.4f)
                        .protoTap { scope.launch { pager.animateScrollToPage(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    MiraProtoGlyph(icon, c.fg, 15.dp)
                }
            }
        }

        if (module) {
            Box(
                Modifier.fillMaxSize().background(c.dim).pointerInput(Unit) {
                    detectTapGestures { module = false }
                }
            )
            // The expanded box: solid, so every name and state is easy to read.
            Box(
                Modifier.offset(42.dp, 190.dp)
                    .width(306.dp)
                    .background(c.sheet, RoundedCornerShape(40.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(start = 18.dp, top = 26.dp, end = 18.dp, bottom = 22.dp)
            ) {
                IosConnGrid(c, conn.all, circle = 64.dp, icon = 30.dp, rowGap = 22.dp)
            }
        }

        if (editing) {
            Box(
                Modifier.offset(115.dp, 772.dp)
                    .size(160.dp, 44.dp)
                    .protoGlass(RoundedCornerShape(22.dp), c.off, c.edge)
                    .protoTap { sheet = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("Add a Control", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (sheet && editHost != null) {
            // Every widget you removed, then every tile Android has that is not here yet.
            val pool = mutableListOf<IosAddItem>()
            IOS_WIDGETS.filter { it in layout.hidden }.forEach { k ->
                pool += IosAddItem(k, iosWidgetLabel(k), add = { layout.save(items + k, layout.hidden - k) })
            }
            env.tiles
                .filter { it.spec.spec in layout.hidden && it.spec.spec !in MIRA_PROTO_EXCLUDED }
                .forEach { t ->
                    pool += IosAddItem(t.spec.spec, "", tile = t, add = { layout.save(items + t.spec.spec, layout.hidden - t.spec.spec) })
                }
            editHost.available.forEach { e ->
                pool += IosAddItem(e.tileSpec.spec, e.label.text, edit = e, add = { editHost.add(e.tileSpec) })
            }
            Column(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(520.dp)
                    .background(c.sheet, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Add a Control", color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier.size(32.dp).background(c.off, CircleShape).protoTap { sheet = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        MiraProtoGlyph(MiraProtoIcons.Close, c.fg, 18.dp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (pool.isEmpty()) {
                    Text("Every control is already in Control Center.", color = c.fg2, fontSize = 14.sp)
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    pool.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                            row.forEach { item ->
                                Column(
                                    Modifier.weight(1f).protoTap(onTap = item.add),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        Modifier.size(56.dp).background(c.off, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        when {
                                            item.tile != null -> MiraProtoTileIcon(item.tile.protoState(), c.fg, 26.dp)
                                            item.edit != null -> MiraEditIcon(item.edit, c.fg, 26.dp)
                                            else -> MiraProtoGlyph(iosWidgetIcon(item.key), c.fg, 26.dp)
                                        }
                                    }
                                    Text(
                                        item.tile?.protoState()?.label?.toString() ?: item.label,
                                        color = c.fg,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.widthIn(max = 80.dp),
                                    )
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (connSheet && editHost != null) {
            IosConnSheet(c, env, picks, editHost, onClose = { connSheet = false })
        }
    }
}

/** Edit mode › connectivity box: pick what goes in it, up to six, in order. */
@Composable
private fun BoxScope.IosConnSheet(
    c: MiraIosInk,
    env: MiraProtoEnv,
    picks: IosConnPicks,
    editHost: MiraEditHost,
    onClose: () -> Unit,
) {
    val keys = picks.keys
    val main = if (keys.size <= 4) keys.size else 3
    Column(
        Modifier.align(Alignment.BottomStart)
            .fillMaxWidth()
            .background(c.sheet, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Connectivity", color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Up to $IOS_CONN_MAX, in order. The first three are big; from the fifth on they share the last corner.",
                    color = c.fg2,
                    fontSize = 12.sp,
                )
            }
            Box(
                Modifier.size(32.dp).background(c.off, CircleShape).protoTap(onTap = onClose),
                contentAlignment = Alignment.Center,
            ) {
                MiraProtoGlyph(MiraProtoIcons.Close, c.fg, 18.dp)
            }
        }
        Spacer(Modifier.height(14.dp))
        IOS_CONN_CANDIDATES.forEach { id ->
            val tile = iosConnFind(env.tiles, id)
            val addable = editHost.available.firstOrNull { iosConnMatches(id, it.tileSpec.spec) }
            if (tile == null && addable == null) return@forEach // this phone has no such tile
            val at = keys.indexOf(id)
            val inBox = at >= 0
            Row(
                Modifier.fillMaxWidth()
                    .height(52.dp)
                    .background(if (inBox) c.off else Color.Transparent, RoundedCornerShape(26.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    when {
                        tile != null -> MiraProtoTileIcon(tile.protoState(), iosFill(tile.protoKey()) ?: c.fg, 22.dp)
                        addable != null -> MiraEditIcon(addable, c.fg, 22.dp)
                    }
                }
                Text(iosConnName(id), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (inBox) {
                    Text(if (at < main) "Big ${at + 1}" else "Mini", color = c.fg2, fontSize = 12.sp)
                }
                val canAdd = inBox || keys.size < IOS_CONN_MAX
                Box(
                    Modifier.size(28.dp)
                        .alpha(if (canAdd) 1f else 0.35f)
                        .background(if (inBox) Color(0xFF8E8E93) else IosBlue, CircleShape)
                        .protoTap(enabled = canAdd) {
                            if (inBox) picks.set(keys - id)
                            else {
                                if (tile == null && addable != null) editHost.add(addable.tileSpec)
                                picks.set(keys + id)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    MiraProtoGlyph(if (inBox) MiraProtoIcons.Minus else MiraCLOIcons.Add, Color.White, 16.dp)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun iosWidgetLabel(key: String) =
    when (key) {
        IOS_CONN -> "Connectivity"
        IOS_MUSIC -> "Now Playing"
        IOS_SILENT -> "Silent Mode"
        IOS_BRIGHT -> "Brightness"
        IOS_VOL -> "Volume"
        else -> key
    }

private fun iosWidgetIcon(key: String) =
    when (key) {
        IOS_MUSIC -> MiraCLOIcons.MusicNote
        IOS_SILENT -> MiraProtoIcons.Bell
        IOS_BRIGHT -> MiraCLOIcons.LightMode
        IOS_VOL -> MiraCLOIcons.VolumeUp
        else -> MiraProtoIcons.Tower
    }

@Composable
private fun IosCircle(c: MiraIosInk, x: Dp, top: Dp, onTap: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.offset(x, top).size(34.dp).protoGlass(CircleShape, c.off, c.edge, c.hi).protoTap(onTap = onTap),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val IOS_ROWS = 8 // 8 rows of 65dp + 15dp gaps fit one 640dp page

/**
 * A page of controls: a 4-column grid of 65dp cells with 15dp gaps, each control taking the cells
 * its size says, packed densely as Control Center does. In edit mode they jiggle, hold and drag
 * moves one (the others make room), the corner handle resizes and − removes.
 */
@Composable
private fun IosControls(
    c: MiraIosInk,
    env: MiraProtoEnv,
    items: List<String>,
    cells: List<MiraCell>,
    tiles: List<TileViewModel>,
    conn: IosConn,
    editing: Boolean,
    onOpenModule: () -> Unit,
    onRemove: (String) -> Unit,
    onResize: (String) -> Unit,
    onMove: (List<String>) -> Unit,
    onConnEdit: () -> Unit,
) {
    val jiggle by
        rememberInfiniteTransition(label = "jiggle")
            .animateFloat(
                initialValue = -1.2f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(tween(140), RepeatMode.Reverse),
                label = "jiggle",
            )
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    val centres = remember { mutableStateMapOf<String, Offset>() }
    val cellBy = items.zip(cells).toMap()
    val shown = (dragOrder ?: items).filter { it in cellBy }
    MiraCellGrid(
        sizes = shown.map { cellBy[it] ?: MiraCell.S11 },
        cols = 4,
        cellHeight = 65.dp,
        gap = 15.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        shown.forEach { key ->
            val cell = cellBy[key] ?: MiraCell.S11
            val isDragged = dragging == key
            Box(
                Modifier.zIndex(if (isDragged) 2f else 0f)
                    .onGloballyPositioned { centres[key] = it.boundsInRoot().center }
                    .graphicsLayer {
                        rotationZ = if (editing && !isDragged) jiggle else 0f
                        if (isDragged) {
                            translationX = dragDelta.x
                            translationY = dragDelta.y
                            scaleX = 1.06f
                            scaleY = 1.06f
                        }
                    }
                    .then(
                        if (!editing) Modifier
                        else
                            Modifier.pointerInput(key, items) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragging = key
                                        dragDelta = Offset.Zero
                                        dragOrder = items
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDelta += amount
                                        val me = centres[key] ?: return@detectDragGesturesAfterLongPress
                                        val p = me + dragDelta
                                        val list = dragOrder ?: items
                                        val target = centres.entries
                                            .filter { it.key != key && it.key in list }
                                            .minByOrNull { (it.value - p).getDistanceSquared() }
                                        if (target != null && (target.value - p).getDistance() < 50f * density) {
                                            val to = list.indexOf(target.key)
                                            if (to >= 0 && list.indexOf(key) != to) {
                                                val before = centres[key]
                                                dragOrder = list.protoMove(key, to)
                                                val after = centres[target.key]
                                                if (before != null && after != null) dragDelta += before - after
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        dragOrder?.let { if (it != items) onMove(it) }
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
                            }
                    )
            ) {
                IosControl(c, env, key, cell, tiles, conn, editing, onOpenModule)
                if (editing) {
                    Box(
                        Modifier.offset((-7).dp, (-7).dp)
                            .size(24.dp)
                            .background(Color(0xFF8E8E93), CircleShape)
                            .protoTap { onRemove(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        MiraProtoGlyph(MiraProtoIcons.Minus, Color.White, 16.dp)
                    }
                    if (iosAllowed(key).size > 1) {
                        MiraResizeHandle(
                            Color.White,
                            Color(0xFF1C1C1E),
                            { onResize(key) },
                            Modifier.align(Alignment.BottomEnd).offset(5.dp, 5.dp),
                        )
                    }
                    if (key == IOS_CONN) {
                        // pick what goes in the box
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .offset(5.dp, (-7).dp)
                                .size(24.dp)
                                .background(IosBlue, CircleShape)
                                .protoTap(onTap = onConnEdit),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiraProtoGlyph(MiraCLOIcons.Edit, Color.White, 13.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IosControl(
    c: MiraIosInk,
    env: MiraProtoEnv,
    key: String,
    cell: MiraCell,
    tiles: List<TileViewModel>,
    conn: IosConn,
    editing: Boolean,
    onOpenModule: () -> Unit,
) {
    val rim = if (editing) c.fg3 else c.edge
    when (key) {
        IOS_CONN -> IosConnBlock(c, conn, cell, rim, editing, onOpenModule)
        IOS_MUSIC -> IosMusic(c, env, cell, rim, editing)
        IOS_BRIGHT -> IosSlider(c, env.brightness, MiraCLOIcons.LightMode, Color(0xFFFFC400), cell, rim, editing)
        IOS_VOL -> IosSlider(c, env.volume, MiraCLOIcons.VolumeUp, IosBlue, cell, rim, editing)
        IOS_SILENT -> {
            val (mode, setMode) = rememberProtoRinger(env.audio)
            val on = mode == AudioManager.RINGER_MODE_SILENT
            val wide = cell.w > 1
            Row(
                Modifier.fillMaxSize()
                    .protoGlass(if (wide) RoundedCornerShape(33.dp) else CircleShape, if (on) IosOnBg else c.off, rim, c.hi, dashed = editing)
                    .protoTap(enabled = !editing) {
                        setMode(if (on) AudioManager.RINGER_MODE_NORMAL else AudioManager.RINGER_MODE_SILENT)
                    },
                horizontalArrangement = if (wide) Arrangement.spacedBy(10.dp) else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (wide) Spacer(Modifier.width(8.dp))
                MiraProtoGlyph(if (on) MiraProtoIcons.BellOff else MiraProtoIcons.Bell, if (on) iosGlyph("silent") else c.fg, 28.dp)
                if (wide) {
                    Column {
                        Text("Silent", color = if (on) Color(0xFF1C1C1E) else c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (on) "On" else "Off", color = if (on) Color(0xFF1C1C1E).copy(alpha = 0.6f) else c.fg2, fontSize = 12.sp)
                    }
                }
            }
        }
        else -> {
            val tile = tiles.firstOrNull { it.spec.spec == key } ?: return
            val s = tile.protoState()
            val on = s.isOn
            val k = tile.protoKey()
            val fill = iosFill(k)
            val dnd = k == "dnd" || k == "modes_dnd"
            when {
                cell == MiraCell.S21 -> {
                    // Capsule: an iOS pill with Android's real large tile inside (icon bubble, name, status).
                    val skin = remember(k, c) {
                        MiraTileSkin(
                            shape = RoundedCornerShape(33.dp),
                            onBackground = Color.Transparent, offBackground = Color.Transparent,
                            onContent = if (dnd) Color.White else iosGlyph(k), offContent = c.fg,
                            bubbleOn = if (dnd) Color(0xFF5E5CE6) else IosOnBg, bubbleOff = c.off,
                            label = c.fg,
                        )
                    }
                    Box(Modifier.fillMaxSize().protoGlass(RoundedCornerShape(33.dp), c.block, rim, c.hi, dashed = editing)) {
                        if (editing) IosStaticTile(s, c.fg, withLabel = true)
                        else MiraRealTile(tile, skin, iconOnly = false, modifier = Modifier.fillMaxSize())
                    }
                }
                cell == MiraCell.S22 -> {
                    // Large: the icon in its circle at the top, the name and state at the bottom.
                    Column(
                        Modifier.fillMaxSize()
                            .protoGlass(RoundedCornerShape(36.dp), c.block, rim, c.hi, dashed = editing)
                            .protoTap(enabled = !editing) { tile.protoToggle() }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(
                            Modifier.size(54.dp).background(if (on) (fill ?: IosOnBg) else c.off, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            val skin = remember(k, c) {
                                MiraTileSkin(
                                    shape = CircleShape,
                                    onBackground = Color.Transparent, offBackground = Color.Transparent,
                                    onContent = if (fill != null) Color.White else iosGlyph(k), offContent = c.fg,
                                )
                            }
                            if (editing) MiraProtoTileIcon(s, if (on && fill != null) Color.White else if (on) iosGlyph(k) else c.fg, 26.dp)
                            else MiraRealTile(tile, skin, iconOnly = true, modifier = Modifier.fillMaxSize())
                        }
                        Column {
                            Text(s.label?.toString().orEmpty(), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                s.secondaryLabel?.toString() ?: if (on) "On" else "Off",
                                color = c.fg2,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                else -> {
                    // Circle (1x1) or tall capsule (1x2) with Android's real tile inside.
                    val shape = if (cell.h > 1) RoundedCornerShape(33.dp) else CircleShape
                    val skin = remember(k, c, cell) {
                        MiraTileSkin(
                            shape = shape,
                            onBackground = Color.Transparent, offBackground = Color.Transparent,
                            onContent = if (fill != null) Color.White else iosGlyph(k), offContent = c.fg,
                        )
                    }
                    Box(
                        Modifier.fillMaxSize()
                            .protoGlass(shape, if (on) (fill ?: IosOnBg) else c.off, rim, c.hi, dashed = editing)
                    ) {
                        if (editing) IosStaticTile(s, if (on && fill != null) Color.White else if (on) iosGlyph(k) else c.fg, withLabel = false)
                        else MiraRealTile(tile, skin, iconOnly = true, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** A tile drawn without its touches (edit mode moves and resizes it instead). */
@Composable
private fun IosStaticTile(s: QSTile.State, tint: Color, withLabel: Boolean) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = if (withLabel) 16.dp else 0.dp),
        horizontalArrangement = if (withLabel) Arrangement.spacedBy(10.dp) else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiraProtoTileIcon(s, tint, 26.dp)
        if (withLabel) {
            Text(s.label?.toString().orEmpty(), color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun IosToggle(tile: TileViewModel, c: MiraIosInk, size: Dp, icon: Dp, enabled: Boolean, onTap: (() -> Unit)? = null) {
    val s = tile.protoState()
    val on = s.isOn
    val k = tile.protoKey()
    val fill = iosFill(k)
    val tint = if (on) (if (fill != null) Color.White else iosGlyph(k)) else c.fg
    Box(
        Modifier.size(size)
            .background(if (on) (fill ?: IosOnBg) else c.off, CircleShape)
            .then(if (onTap != null) Modifier.protoTap(enabled = enabled, onTap = onTap) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (onTap != null || !enabled) {
            MiraProtoTileIcon(s, tint, icon)
        } else {
            val skin = remember(k, c) {
                MiraTileSkin(
                    shape = CircleShape,
                    onBackground = Color.Transparent, offBackground = Color.Transparent,
                    onContent = if (fill != null) Color.White else iosGlyph(k), offContent = c.fg,
                )
            }
            MiraRealTile(tile, skin, iconOnly = true, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * The connectivity box. 2x2: up to four circles; with five or six picked, three circles and the
 * rest as mini icons (no background) in the last corner, which open the full box. 2x4: every
 * pick with its name and state. Touch and hold anywhere opens the full box.
 */
@Composable
private fun IosConnBlock(c: MiraIosInk, conn: IosConn, cell: MiraCell, rim: Color, editing: Boolean, onOpen: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .protoGlass(RoundedCornerShape(36.dp), c.block, rim, c.hi, dashed = editing)
            .pointerInput(editing) {
                if (!editing) detectTapGestures(onLongPress = { onOpen() })
            }
            .padding(12.dp)
    ) {
        val picks = conn.all
        val mainCount = if (picks.size <= 4) picks.size else 3
        val mini = picks.drop(mainCount).take(4)
        if (cell.h >= 4) {
            IosConnGrid(c, picks, circle = 50.dp, icon = 24.dp, rowGap = 10.dp, enabled = !editing)
        } else Column(Modifier.fillMaxSize()) {
            listOf(0, 2).forEach { start ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (i in start until start + 2) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            when {
                                i < mainCount -> IosToggle(picks[i], c, 54.dp, 26.dp, !editing)
                                i == 3 && mini.isNotEmpty() -> IosMiniCluster(c, mini, !editing, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The mini icons in the box's last corner: no background; a tap opens the full box. */
@Composable
private fun IosMiniCluster(c: MiraIosInk, tiles: List<TileViewModel>, enabled: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier.size(54.dp).protoTap(enabled = enabled, onTap = onOpen),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { t ->
                    val s = t.protoState()
                    val k = t.protoKey()
                    MiraProtoTileIcon(s, if (s.isOn) (iosFill(k) ?: iosGlyph(k)) else c.fg2, 20.dp)
                }
            }
        }
    }
}

@Composable
private fun IosMusic(c: MiraIosInk, env: MiraProtoEnv, cell: MiraCell, rim: Color, editing: Boolean) {
    val media = env.media("Not Playing", "")
    val tall = cell.h >= 4
    Column(
        Modifier.fillMaxSize()
            .protoGlass(RoundedCornerShape(36.dp), c.block, rim, c.hi, dashed = editing)
            .protoTap(enabled = !editing && media.open != null) { media.open?.invoke() }
            .padding(start = 13.dp, top = 13.dp, end = 13.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiraProtoArt(media, if (tall) 92.dp else 52.dp, if (tall) 16.dp else 12.dp, c.off, Color(0xFF9A3A2A))
            Box(Modifier.size(34.dp).background(c.off, CircleShape), contentAlignment = Alignment.Center) {
                MiraProtoGlyph(MiraCLOIcons.Airplay, c.fg, 18.dp)
            }
        }
        Column {
            Text(media.song, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (media.artist.isNotEmpty()) {
                Text(media.artist, color = c.fg2, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (tall && !media.idle) {
            MiraMediaSeekBar(media, c.fg, c.off, times = c.fg2, seekable = !editing)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp).alpha(if (media.idle) 0.35f else 1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaKey(MiraProtoIcons.FastRewind, 24.dp, c.fg, media.prev.takeIf { !editing }, box = 28.dp)
            MediaKey(if (media.playing) MiraProtoIcons.Pause else MiraProtoIcons.Play, 26.dp, c.fg, media.playPause.takeIf { !editing }, box = 30.dp)
            MediaKey(MiraProtoIcons.FastForward, 24.dp, c.fg, media.next.takeIf { !editing }, box = 28.dp)
        }
    }
}

@Composable
private fun IosSlider(
    c: MiraIosInk,
    level: MiraProtoLevel,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    ink: Color,
    cell: MiraCell,
    rim: Color,
    editing: Boolean,
) {
    val local = rememberProtoLocal(level.value)
    val v = local.floatValue
    // 1x2 and 2x2 fill from the bottom; 2x1 fills from the left.
    val vertical = cell.h >= cell.w
    Box(
        Modifier.fillMaxSize()
            .protoGlass(RoundedCornerShape(33.dp), c.off, rim, c.hi, dashed = editing)
            .then(
                if (editing) Modifier
                else Modifier.protoSliderInput({ v }, vertical, { local.floatValue = it }, level.set)
            )
    ) {
        if (vertical) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(v.coerceIn(0.001f, 1f)).background(IosOnBg)
            )
        } else {
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(v.coerceIn(0.001f, 1f)).background(IosOnBg)
            )
        }
        MiraProtoGlyph(
            icon,
            if (v > 0.2f) ink else c.fg,
            30.dp,
            if (vertical) Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 16.dp)
            else Modifier.align(Alignment.CenterStart).padding(start = 18.dp),
        )
    }
}

@Composable
private fun IosNowPlaying(c: MiraIosInk, env: MiraProtoEnv) {
    val media = env.media("Not Playing", "")
    val vol = env.volume
    val localVol = rememberProtoLocal(vol.value)
    Column(
        Modifier.width(305.dp)
            .height(465.dp)
            .protoGlass(RoundedCornerShape(40.dp), c.block, c.edge, c.hi)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.protoTap(enabled = media.open != null) { media.open?.invoke() }) {
                MiraProtoArt(media, 150.dp, 18.dp, c.off, Color(0xFF9A3A2A))
            }
            Box(Modifier.size(40.dp).background(c.off, CircleShape), contentAlignment = Alignment.Center) {
                MiraProtoGlyph(MiraCLOIcons.Airplay, c.fg, 22.dp)
            }
        }
        Column {
            Text(media.song, color = c.fg, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(media.artist, color = c.fg2, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // The wavy position bar: real position and length; drag it to seek.
        MiraMediaSeekBar(
            media,
            c.fg,
            c.off,
            modifier = Modifier.alpha(if (media.idle) 0.35f else 1f),
            times = c.fg2,
        )
        Row(
            Modifier.fillMaxWidth().alpha(if (media.idle) 0.35f else 1f),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaKey(MiraProtoIcons.FastRewind, 40.dp, c.fg, media.prev, box = 48.dp)
            MediaKey(if (media.playing) MiraProtoIcons.Pause else MiraProtoIcons.Play, 52.dp, c.fg, media.playPause, box = 60.dp)
            MediaKey(MiraProtoIcons.FastForward, 40.dp, c.fg, media.next, box = 48.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiraProtoGlyph(MiraCLOIcons.VolumeOff, c.fg2, 18.dp)
            Box(
                Modifier.weight(1f)
                    .height(18.dp)
                    .protoSliderInput({ localVol.floatValue }, false, { localVol.floatValue = it }, vol.set),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(c.off, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(localVol.floatValue).height(6.dp).background(c.fg, RoundedCornerShape(3.dp)))
                }
            }
            MiraProtoGlyph(MiraCLOIcons.VolumeUp, c.fg2, 18.dp)
        }
    }
}

@Composable
private fun IosConnectivityPage(c: MiraIosInk, all: List<TileViewModel>) {
    Box(
        Modifier.width(305.dp)
            .protoGlass(RoundedCornerShape(40.dp), c.block, c.edge, c.hi)
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 20.dp)
    ) {
        IosConnGrid(c, all, circle = 58.dp, icon = 28.dp, rowGap = 20.dp)
    }
}

@Composable
private fun IosConnGrid(
    c: MiraIosInk,
    tiles: List<TileViewModel>,
    circle: Dp,
    icon: Dp,
    rowGap: Dp,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { t ->
                    val s = t.protoState()
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (circle > 60.dp) 8.dp else 7.dp),
                    ) {
                        IosToggle(t, c, circle, icon, enabled)
                        Text(s.label?.toString().orEmpty(), color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            s.secondaryLabel?.toString() ?: if (s.isOn) "On" else "Off",
                            color = c.fg2,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 130.dp),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
