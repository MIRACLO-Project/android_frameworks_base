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
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import android.app.ActivityManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.round
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/** ColorOS 17 palette, from the approved prototype. */
internal class MiraColorOSInk(val dark: Boolean) {
    val base = if (dark) Color(0xFF0C0F15) else Color(0xFFDFE3EA)
    val scrim = if (dark) Color(0x8506080C) else Color(0x73F0F2F7)
    val fg = if (dark) Color.White else Color(0xFF111214)
    val fg2 = if (dark) Color.White.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.56f)
    val fg3 = if (dark) Color.White.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.25f)
    val glass = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
    val edge = if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.95f)
    val hi = if (dark) Color.White.copy(alpha = 0.5f) else Color.White
    val bubble = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.05f)
    val artGlow = if (dark) Color(0x663CC86E) else Color(0x4D3CC86E)
    val dash = if (dark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.22f)
}

/** Each ColorOS tile's colour when on. */
internal fun miraColorOSAccent(key: String): Color =
    when (key) {
        "data", "saver" -> Color(0xFF2FB457)
        "airplane" -> Color(0xFFFF9500)
        "rotate", "record" -> Color(0xFFFF3B30)
        "dnd", "modes_dnd" -> Color(0xFF5E5CE6)
        "torch", "eye" -> Color(0xFFE69500)
        "hotspot" -> Color(0xFFC99400)
        "dark" -> Color(0xFF111214)
        else -> Color(0xFF1A7CFF)
    }

private val ColorOSBlue = Color(0xFF1A7CFF)

/**
 * ColorOS 17 Quick Settings: edit and settings at the top, Wi-Fi and Bluetooth pills, the media
 * card with the tall brightness and volume sliders, My Devices and the ringer, then the round
 * tiles in pages of 12 (3 rows of 4) that swipe sideways while the top stays put.
 */
@Composable
internal fun MiraColorOSQs(env: MiraProtoEnv) {
    val c = remember(env.dark) { MiraColorOSInk(env.dark) }
    val layout = rememberMiraProtoLayout(MiraProtoStyle.COLOROS)
    var editing by remember { mutableStateOf(false) }
    val reserved = setOf("wifi", "bt")

    val host = LocalMiraTileHost.current
    val visible = env.tiles.filter { it.spec.spec !in MIRA_PROTO_EXCLUDED }
    // Sizes: Round (1x1), Pill (2x1) and Large (2x2), kept for this style.
    val sizeStore = rememberMiraTileSizes(MiraProtoStyle.COLOROS)
    fun cellOf(t: TileViewModel): MiraCell =
        miraTileCell(t.spec.spec, host?.isSmall?.invoke(t) != false, sizeStore, ColorOSSizes)
    val pills = visible.filter { cellOf(it) == MiraCell.S21 }.take(2)
    val blockLayout = rememberMiraBlockLayout(MiraProtoStyle.COLOROS, ColorOSBlocks)
    // MIRACLO: the top block as ColorOS keeps it, a fixed 2 x 4 grid of slots. Until it is first
    // edited it shows the old layout (the two pills, media and sliders, My Devices and the ringer).
    val grid = rememberCoXGrid {
        buildMap {
            pills.getOrNull(0)?.let { put(0, CoXGrid.tile(it.spec.spec)) }
            pills.getOrNull(1)?.let { put(1, CoXGrid.tile(it.spec.spec)) }
            if (CO_MEDIA !in blockLayout.hidden) {
                put(2, CO_MEDIA)
                put(3, CO_SLIDERS)
            }
            if (CO_DEVICES !in blockLayout.hidden) {
                put(6, CO_DEVICES)
                put(7, CO_RINGER)
            }
        }
    }

    val editHost = LocalMiraEditHost.current
    if (editHost != null) {
        // MIRACLO: edit in place. The panel keeps its look; every item gets a − badge, touch and
        // hold drags it, empty pill slots and the add sheet bring tiles and widgets back.
        MiraPortraitWindow { CoXEditInPlace(c, env, editHost, sizeStore, grid) }
        return
    }

    // MIRACLO add-ons (the panel's look is unchanged): only the brightness slider stays while it
    // is dragged, the ringer names its new mode, the media card grows into the music player, a
    // pull past the end stretches the rows and the panel settles with a small spring when opened.
    val fx = remember { CoXFx() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val others by animateFloatAsState(if (fx.brightOnly) 0f else 1f, tween(220), label = "coxBrightOnly")
    LaunchedEffect(others) { MiraQsBrightOnly.fade = 1f - others }
    DisposableEffect(Unit) { onDispose { MiraQsBrightOnly.fade = 0f } }
    val playerT = remember { Animatable(0f) }
    LaunchedEffect(fx.player) {
        playerT.animateTo(
            if (fx.player) 1f else 0f,
            if (fx.player) spring(dampingRatio = 0.74f, stiffness = 340f)
            else spring(dampingRatio = 1f, stiffness = 520f),
        )
        if (!fx.player) fx.playerShown = false
    }
    val stretchMax = with(density) { 110.dp.toPx() }
    val stretchScale = with(density) { 260.dp.toPx() }
    fun rub(i: Int): Float {
        val k = when (i) {
            0 -> 0.18f
            1 -> 0.4f
            else -> (0.45f + 0.2f * (i - 2)).coerceAtMost(1.1f)
        }
        return stretchMax * (1f - exp(-fx.pull / stretchScale)) * k
    }
    val colorOSMedia = env.media("No media", "Tap to open a music app")

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { fx.root = it.boundsInRoot().topLeft }
            .coXRubber(fx, scope)
            .coXSettle(env.openFraction)
    ) {
        Box(
            Modifier.fillMaxSize()
                .then(
                    if (fx.playerShown)
                        Modifier.blur(18.dp * playerT.value.coerceIn(0f, 1f)).graphicsLayer {
                            alpha = 1f - 0.5f * playerT.value.coerceIn(0f, 1f)
                        }
                    else Modifier
                )
        ) {
        if (!editing) {
            val liveBySpec = visible.associateBy { it.spec.spec }
            val inGrid = grid.tileSpecs()
            if (LocalMiraLandscape.current) {
                // Landscape: settings and edit at the far left, the slots in the left half, the
                // tile pages (3 rows of 4, sideways) in the right half; all on one screen.
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = others }) {
                    ColorOSCircle(
                        c,
                        x = 0.dp,
                        top = 46.dp,
                        size = 36.dp,
                        onTap = { env.open(miraProtoIntent(Settings.ACTION_SETTINGS)) },
    label = "Settings",) {
                        MiraProtoGlyph(MiraCLOIcons.Settings, c.fg, 20.dp)
                    }
                    ColorOSCircle(c, x = 0.dp, top = 92.dp, size = 36.dp, onTap = env.styleEdit, label = "Edit") {
                        MiraProtoGlyph(MiraCLOIcons.Edit, c.fg, 18.dp)
                    }
                }
                Box(Modifier.offset(12.dp, 46.dp)) {
                    CoXGridView(c, env, grid, liveBySpec, colorOSMedia, fx, others)
                }
                Box(Modifier.offset(400.dp, 40.dp).graphicsLayer { alpha = others }) {
                    ColorOSTilePages(c, env, visible.filter { it.spec.spec !in inGrid }, ::cellOf)
                }
            } else {
                // Header: edit and settings circles, the carrier name underneath.
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = others; translationY = rub(0) }) {
                    ColorOSCircle(c, x = 26.dp, top = 44.dp, size = 36.dp, onTap = env.styleEdit, label = "Edit") {
                        MiraProtoGlyph(MiraCLOIcons.Edit, c.fg, 18.dp)
                    }
                    ColorOSCircle(
                        c,
                        x = (390 - 26 - 36).dp,
                        top = 44.dp,
                        size = 36.dp,
                        onTap = { env.open(miraProtoIntent(Settings.ACTION_SETTINGS)) },
    label = "Settings",) {
                        MiraProtoGlyph(MiraCLOIcons.Settings, c.fg, 20.dp)
                    }
                    CoXCarrier(c, miraProtoCarrier(), fx)
                }

                // The top block (ColorOS slots) and the round tiles under it.
                Column(Modifier.offset(0.dp, 142.dp).width(390.dp)) {
                    Box(Modifier.graphicsLayer { translationY = rub(1) }) {
                        CoXGridView(c, env, grid, liveBySpec, colorOSMedia, fx, others)
                    }
                    Spacer(Modifier.height(22.dp))
                    Box(
                        Modifier.graphicsLayer {
                            translationY = rub(2)
                            alpha = others
                        }
                    ) {
                        ColorOSTilePages(c, env, visible.filter { it.spec.spec !in inGrid }, ::cellOf)
                    }
                }
            }
        } else {
            MiraPortraitWindow { ColorOSEdit(c, env, layout, reserved, onDone = { editing = false }) }
        }
        }
        if (fx.playerShown) {
            MiraPortraitWindow {
                CoXPlayer(c, env, colorOSMedia, fx, playerT.value, onClose = { fx.player = false })
            }
        }
    }
}

/** ColorOS My Devices pill (unchanged look), shared by the panel and edit mode. */
@Composable
private fun ColorOSDevicesPill(c: MiraColorOSInk, env: MiraProtoEnv, modifier: Modifier) {
    Row(
        modifier
            .height(64.dp)
            .protoGlass(RoundedCornerShape(32.dp), c.glass, c.edge, c.hi)
            .protoTap {
                env.open(miraProtoIntent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.size(46.dp).background(c.bubble, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MiraProtoGlyph(MiraCLOIcons.Devices, c.fg, 24.dp)
        }
        Column {
            Text("My Devices", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("Tap to add", color = c.fg2, fontSize = 12.sp)
        }
    }
}

private const val CO_PILLS = "pills"
private const val CO_MEDIA = "media"
private const val CO_DEVICES = "devices"
private const val CO_TILES = "tiles"

/** The ColorOS widgets edit mode can move (media and My Devices can also be hidden). */
private val ColorOSBlocks =
    listOf(
        MiraEditBlock(CO_PILLS, "Wide tiles", removable = false),
        MiraEditBlock(CO_MEDIA, "Media and sliders", removable = true),
        MiraEditBlock(CO_DEVICES, "My Devices and ringer", removable = true),
        MiraEditBlock(CO_TILES, "Tiles", removable = false),
    )

/** Paged tiles: round, pill or large, packed four columns by three rows a page, with the dots. */
@Composable
private fun ColorOSTilePages(
    c: MiraColorOSInk,
    env: MiraProtoEnv,
    tiles: List<TileViewModel>,
    cellOf: (TileViewModel) -> MiraCell,
) {
    val cells = tiles.map { cellOf(it) }
    val pages = miraPages(cells, cols = 4, maxRows = 3).map { page -> page.map { tiles[it] } }
    val pager = rememberPagerState(0) { pages.size }
    Column(Modifier.width(390.dp)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.width(390.dp).height(291.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 30.dp),
            pageSpacing = 60.dp,
            verticalAlignment = Alignment.Top,
        ) { p ->
            val page = pages.getOrElse(p) { emptyList() }
            MiraCellGrid(
                sizes = page.map { cellOf(it) },
                cols = 4,
                cellHeight = 95.dp,
                gap = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                page.forEach { tile ->
                    when (cellOf(tile)) {
                        MiraCell.S21 ->
                            Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.TopCenter) {
                                ColorOSWidePill(c, tile, Modifier.fillMaxWidth())
                            }
                        MiraCell.S22 ->
                            Box(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 22.dp)) {
                                ColorOSLargeTile(c, tile, env.dark)
                            }
                        else ->
                            Box(contentAlignment = Alignment.TopCenter) {
                                ColorOSRoundTile(c, tile, env.dark)
                            }
                    }
                }
            }
        }
        if (pages.size > 1) {
            val scope = rememberCoroutineScope()
            Row(
                Modifier.padding(top = 5.dp).width(390.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier.size(14.dp).protoTap { scope.launch { pager.animateScrollToPage(i) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(6.dp)
                                .background(if (i == pager.currentPage) c.fg else c.fg3, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

/** The sizes a ColorOS tile can take: Round, Pill and Large. */
private val ColorOSSizes = listOf(MiraCell.S11, MiraCell.S21, MiraCell.S22)

/** ColorOS Large (2x2): a glass card with the tile's icon bubble on top and its name and state. */
@Composable
private fun ColorOSLargeTile(c: MiraColorOSInk, tile: TileViewModel, dark: Boolean) {
    val s = tile.protoState()
    val on = s.isOn
    val key = tile.protoKey()
    val col = miraColorOSAccent(key)
    Column(
        Modifier.fillMaxSize()
            .protoGlass(RoundedCornerShape(28.dp), c.glass, c.edge, c.hi)
            .protoTap { tile.protoToggle() }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(52.dp)
                .protoGlass(
                    CircleShape,
                    if (on) (if (dark) Color.White else col) else c.bubble,
                    Color.Transparent,
                    Color.Transparent,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val skin = remember(dark, col, c) {
                MiraTileSkin(
                    shape = CircleShape,
                    onBackground = Color.Transparent, offBackground = Color.Transparent,
                    onContent = if (dark) col else Color.White, offContent = c.fg,
                )
            }
            MiraRealTile(tile, skin, iconOnly = true, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.protoDetailsTap(tile, enabled = key in MIRA_PROTO_DETAILS)) {
            Text(
                s.label?.toString().orEmpty(),
                color = c.fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                s.secondaryLabel?.toString() ?: if (on) "On" else "Off",
                color = c.fg2,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColorOSCircle(
    c: MiraColorOSInk,
    x: Dp,
    top: Dp,
    size: Dp,
    onTap: () -> Unit,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.offset(x, top).size(size).protoGlass(CircleShape, c.glass, c.edge, c.hi).protoTap(onTap = onTap)
            .protoLabel(label),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ColorOSWidePill(c: MiraColorOSInk, tile: TileViewModel, modifier: Modifier) {
    // ColorOS glass pill; inside it Android's real large tile (icon bubble, name, status).
    val skin = remember(c) {
        MiraTileSkin(
            shape = RoundedCornerShape(32.dp),
            onBackground = Color.Transparent, offBackground = Color.Transparent,
            onContent = if (c.dark) ColorOSBlue else Color.White, offContent = c.fg,
            bubbleOn = if (c.dark) Color.White else ColorOSBlue, bubbleOff = c.bubble,
            label = c.fg,
            bubbleAlways = true,
            bubbleSize = 45.dp,
            bubbleInset = 3.dp,
        )
    }
    Box(modifier.height(64.dp).protoGlass(RoundedCornerShape(32.dp), c.glass, c.edge, c.hi)) {
        MiraRealTile(tile, skin, iconOnly = false, modifier = Modifier.fillMaxSize())
    }
}

/** Two colours from the album art (top-left and bottom-right), made vivid; null without art. */
private fun colorOSArtColors(art: androidx.compose.ui.graphics.ImageBitmap): Pair<Color, Color>? =
    runCatching {
        var bmp = art.asAndroidBitmap()
        if (bmp.config == android.graphics.Bitmap.Config.HARDWARE) {
            bmp = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        }
        val small = android.graphics.Bitmap.createScaledBitmap(bmp, 2, 2, true)
        fun vivid(p: Int): Color {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(p, hsv)
            // grey art keeps a soft neutral glow instead of an invented colour
            if (hsv[1] >= 0.12f) hsv[1] = (hsv[1] * 1.5f).coerceIn(0.45f, 1f)
            hsv[2] = hsv[2].coerceIn(0.6f, 1f)
            return Color(android.graphics.Color.HSVToColor(hsv))
        }
        vivid(small.getPixel(0, 0)) to vivid(small.getPixel(1, 1))
    }.getOrNull()

/**
 * ColorOS: the playing song's colours glow softly behind the media card and the sliders, through
 * their glass. They follow the album art; nothing glows when nothing plays.
 */
@Composable
private fun ColorOSArtGlow(media: MiraProtoMedia, modifier: Modifier) {
    val art = media.art
    val colors = remember(art) { art?.let { colorOSArtColors(it) } }
    if (media.idle || colors == null) return
    val edge = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
    // MIRACLO: one still glow, the same playing or paused. Nothing behind the glass card may
    // move: the card samples what is behind it, and a moving glow showed as square corners.
    val v = 0.8f
    Box(modifier) {
        Box(
            Modifier.offset((-25).dp, (-15).dp)
                .size(200.dp, 175.dp)
                .blur(38.dp, edge)
                .background(colors.first.copy(alpha = 0.55f * v), CircleShape)
        )
        Box(
            Modifier.offset(150.dp, (-25).dp)
                .size(170.dp, 150.dp)
                .blur(38.dp, edge)
                .background(colors.second.copy(alpha = 0.5f * v), CircleShape)
        )
    }
}

@Composable
private fun ColorOSMediaCard(c: MiraColorOSInk, media: MiraProtoMedia, onTap: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.width(149.dp)
            .fillMaxHeight()
            .protoGlass(shape, c.glass, c.edge, c.hi)
            .protoTap(enabled = onTap != null || media.open != null) {
                if (onTap != null) onTap() else media.open?.invoke()
            }
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiraProtoArt(media, 44.dp, 10.dp, c.bubble, Color(0xFF4D7F99))
                MiraProtoGlyph(MiraCLOIcons.Headphones, c.fg, 24.dp)
            }
            Column {
                Text(media.song, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(media.artist, color = c.fg2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // MIRACLO: no position bar in the small tile; the player opened from it has one.
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp).alpha(if (media.idle) 0.35f else 1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaKey(MiraProtoIcons.SkipPrevious, 18.dp, c.fg, media.prev)
                MediaKey(if (media.playing) MiraProtoIcons.Pause else MiraProtoIcons.Play, 20.dp, c.fg, media.playPause)
                MediaKey(MiraProtoIcons.SkipNext, 18.dp, c.fg, media.next)
            }
        }
    }
}

@Composable
internal fun MediaKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: Dp,
    tint: Color,
    action: (() -> Unit)?,
    box: Dp = 32.dp,
) {
    Box(
        Modifier.size(box).protoTap(enabled = action != null) { action?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        MiraProtoGlyph(icon, tint, size)
    }
}

@Composable
private fun ColorOSTallSlider(
    c: MiraColorOSInk,
    level: MiraProtoLevel,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    ink: Color,
    onActive: (Boolean) -> Unit = {},
) {
    val local = rememberProtoLocal(level.value)
    val v = local.floatValue
    Box(
        Modifier.width(66.dp)
            .fillMaxHeight()
            .protoGlass(RoundedCornerShape(33.dp), c.glass, c.edge, c.hi)
            .protoSliderInput(
                { v },
                vertical = true,
                onLocal = { local.floatValue = it },
                onSet = { f, done ->
                    level.set(f, done)
                    onActive(!done)
                },
            )
    ) {
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(v.coerceIn(0.001f, 1f))
                .background(Color.White)
        )
        MiraProtoGlyph(
            icon,
            if (v > 0.16f) ink else c.fg,
            26.dp,
            Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 14.dp),
        )
    }
}

@Composable
private fun ColorOSRinger(
    c: MiraColorOSInk,
    env: MiraProtoEnv,
    modifier: Modifier,
    onMode: (String) -> Unit = {},
) {
    val (mode, setMode) = rememberProtoRinger(env.audio)
    val modes =
        listOf(
            Triple(AudioManager.RINGER_MODE_NORMAL, MiraProtoIcons.Bell, ColorOSBlue),
            Triple(AudioManager.RINGER_MODE_VIBRATE, MiraProtoIcons.Vibrate, Color(0xFFFF3B30)),
            Triple(AudioManager.RINGER_MODE_SILENT, MiraProtoIcons.BellOff, Color(0xFFFF3B30)),
        )
    val names = listOf("Ring mode", "Vibrate mode", "Silent mode")
    val idx = modes.indexOfFirst { it.first == mode }.coerceAtLeast(0)
    // The chosen mode: a filled circle in its colour with a white glyph, the same for Ring,
    // Vibrate and Silent, in light and dark.
    val thumbColor by animateColorAsState(modes[idx].third, label = "coxRingerThumb")
    BoxWithConstraints(
        modifier.height(64.dp).protoGlass(RoundedCornerShape(32.dp), c.glass, c.edge, c.hi).padding(horizontal = 4.dp)
    ) {
        // MIRACLO: the white circle is one thumb that slides to the new mode, stretching as it
        // travels and settling with a small spring.
        val seg = constraints.maxWidth / 3f
        val knobDp = with(LocalDensity.current) { minOf(48.dp, (seg / density).dp - 2.dp) }
        val knob = with(LocalDensity.current) { knobDp.toPx() }
        val x = remember { Animatable(0f) }
        var placed by remember { mutableStateOf(false) }
        LaunchedEffect(idx, seg) {
            val target = seg * idx + (seg - knob) / 2f
            if (!placed) {
                x.snapTo(target)
                placed = true
            } else {
                x.animateTo(target, spring(dampingRatio = 0.58f, stiffness = 520f))
            }
        }
        Box(
            Modifier.align(Alignment.CenterStart)
                .offset { IntOffset(x.value.roundToInt(), 0) }
                .size(knobDp)
                .graphicsLayer {
                    val v = (abs(x.velocity) / 2600f).coerceAtMost(0.42f)
                    scaleX = 1f + v
                    scaleY = 1f - v * 0.35f
                    alpha = if (placed) 1f else 0f
                }
                .background(thumbColor, CircleShape)
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            modes.forEachIndexed { i, (m, icon, col) ->
                val sel = i == idx
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(48.dp).protoTap {
                            if (!sel) {
                                setMode(m)
                                onMode(names[i])
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        MiraProtoGlyph(icon, if (sel) Color.White else c.fg2, if (sel) 24.dp else 20.dp)
                    }
                }
            }
        }
    }
}

/** The up/down chevron after a ColorOS tile label that opens its details. */
@Composable
private fun DetailChevron(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.padding(top = 2.dp).size(8.dp, 11.dp)) {
        val sx = size.width / 8f
        val sy = size.height / 12f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(1.5f * sx, 4.5f * sy); lineTo(4f * sx, 2f * sy); lineTo(6.5f * sx, 4.5f * sy)
            moveTo(1.5f * sx, 7.5f * sy); lineTo(4f * sx, 10f * sy); lineTo(6.5f * sx, 7.5f * sy)
        }
        drawPath(
            p,
            tint,
            style =
                androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.4f * sx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
        )
    }
}

@Composable
private fun ColorOSRoundTile(c: MiraColorOSInk, tile: TileViewModel, dark: Boolean) {
    val s = tile.protoState()
    val on = s.isOn
    val key = tile.protoKey()
    val col = miraColorOSAccent(key)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier.size(64.dp)
                .then(
                    if (on)
                        Modifier.shadow(
                            if (dark) 16.dp else 14.dp,
                            CircleShape,
                            ambientColor = if (dark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.14f),
                            spotColor = if (dark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.14f),
                        )
                    else Modifier
                )
                .protoGlass(
                    CircleShape,
                    if (on) (if (dark) Color.White else col) else c.glass,
                    if (on) Color.Transparent else c.edge,
                    if (on) Color.Transparent else c.hi,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val skin = remember(dark, col, c) {
                MiraTileSkin(
                    shape = CircleShape,
                    onBackground = Color.Transparent, offBackground = Color.Transparent,
                    onContent = if (dark) col else Color.White, offContent = c.fg,
                )
            }
            MiraRealTile(tile, skin, iconOnly = true, modifier = Modifier.fillMaxSize())
        }
        Row(
            Modifier.widthIn(max = 82.dp).protoDetailsTap(tile, enabled = key in MIRA_PROTO_DETAILS),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                s.label?.toString().orEmpty(),
                color = c.fg,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 13.8.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (key in MIRA_PROTO_DETAILS) DetailChevron(c.fg)
        }
    }
}

/**
 * Drag to reorder inside a 4-column grid of [cellW] x [cellH] cells. The dragged item follows
 * the finger and the list reorders as it crosses cells.
 */
internal fun Modifier.protoReorder(
    key: String,
    index: () -> Int,
    count: () -> Int,
    cellW: Float,
    cellH: Float,
    onDrag: (String?) -> Unit,
    onOffset: (IntOffset) -> Unit,
    onMove: (String, Int) -> Unit,
): Modifier =
    pointerInput(key) {
        var dx = 0f
        var dy = 0f
        detectDragGestures(
            onDragStart = {
                dx = 0f
                dy = 0f
                onDrag(key)
            },
            onDragEnd = {
                onDrag(null)
                onOffset(IntOffset.Zero)
            },
            onDragCancel = {
                onDrag(null)
                onOffset(IntOffset.Zero)
            },
        ) { change, amount ->
            change.consume()
            dx += amount.x
            dy += amount.y
            val from = index()
            val col = from % 4
            val stepX = (dx / cellW).roundToInt().coerceIn(-col, 3 - col)
            val stepY = (dy / cellH).roundToInt()
            val to = (from + stepX + stepY * 4).coerceIn(0, count() - 1)
            if (to != from) {
                onMove(key, to)
                val mx = (to % 4) - col
                val my = (to / 4) - (from / 4)
                dx -= mx * cellW
                dy -= my * cellH
            }
            onOffset(IntOffset(dx.roundToInt(), dy.roundToInt()))
        }
    }

/** ColorOS 17 edit: only the round tiles move; − removes, the list underneath adds. */
@Composable
private fun ColorOSEdit(
    c: MiraColorOSInk,
    env: MiraProtoEnv,
    layout: MiraProtoLayout,
    reserved: Set<String>,
    onDone: () -> Unit,
) {
    var draft by remember { mutableStateOf(layout.shown(env.tiles, reserved).map { it.spec.spec }) }
    var removed by remember { mutableStateOf(layout.hidden) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(IntOffset.Zero) }
    val bySpec = env.tiles.associateBy { it.spec.spec }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cellW = with(density) { (330.dp / 4).toPx() }
    val cellH = with(density) { 88.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.offset(20.dp, 44.dp).width(350.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.height(38.dp)
                    .protoGlass(RoundedCornerShape(19.dp), c.glass, c.edge)
                    .protoTap {
                        layout.reset()
                        draft = layout.shown(env.tiles, reserved).map { it.spec.spec }
                        removed = emptySet()
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Reset", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Edit", color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Box(
                Modifier.height(38.dp)
                    .background(ColorOSBlue, RoundedCornerShape(19.dp))
                    .protoTap {
                        layout.save(draft, removed)
                        onDone()
                    }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Done", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Drag to move · tap − to remove",
            color = c.fg2,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(0.dp, 100.dp).width(390.dp),
        )
        Column(
            Modifier.offset(0.dp, 130.dp).fillMaxWidth().height((866 - 130).dp).verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.padding(start = 30.dp).width(330.dp)) {
                draft.chunked(4).forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth().height(88.dp)) {
                        row.forEachIndexed { ci, spec ->
                            val tile = bySpec[spec]
                            val isDragged = dragging == spec
                            Box(
                                Modifier.weight(1f)
                                    .zIndex(if (isDragged) 2f else 0f)
                                    .offset { if (isDragged) dragOffset else IntOffset.Zero }
                                    .alpha(if (isDragged) 0.4f else 1f),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                if (tile != null) {
                                    ColorOSEditTile(
                                        c,
                                        tile,
                                        badge = "−",
                                        badgeColor = Color(0xFF8E8E93),
                                        dashed = true,
                                        onBadge = {
                                            draft = draft - spec
                                            removed = removed + spec
                                        },
                                        modifier =
                                            Modifier.protoReorder(
                                                key = spec,
                                                index = { draft.indexOf(spec) },
                                                count = { draft.size },
                                                cellW = cellW,
                                                cellH = cellH,
                                                onDrag = { dragging = it },
                                                onOffset = { dragOffset = it },
                                                onMove = { k, to -> draft = draft.protoMove(k, to) },
                                            ),
                                    )
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(c.dash))
            Text(
                "Tap to add",
                color = c.fg2,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 26.dp, top = 15.dp),
            )
            Spacer(Modifier.height(14.dp))
            val pool = removed.mapNotNull { bySpec[it] }.filter { it.protoKey() !in reserved }
            Column(Modifier.padding(start = 30.dp).width(330.dp)) {
                pool.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(88.dp)) {
                        row.forEach { tile ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                ColorOSEditTile(
                                    c,
                                    tile,
                                    badge = "+",
                                    badgeColor = ColorOSBlue,
                                    dashed = false,
                                    onBadge = {
                                        removed = removed - tile.spec.spec
                                        draft = draft + tile.spec.spec
                                    },
                                    wholeTap = true,
                                )
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ColorOSEditTile(
    c: MiraColorOSInk,
    tile: TileViewModel,
    badge: String,
    badgeColor: Color,
    dashed: Boolean,
    onBadge: () -> Unit,
    modifier: Modifier = Modifier,
    wholeTap: Boolean = false,
) {
    val s = tile.protoState()
    Column(
        Modifier.then(if (wholeTap) Modifier.protoTap(onTap = onBadge) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(60.dp)) {
            Box(
                modifier
                    .fillMaxSize()
                    .protoGlass(CircleShape, c.glass, if (dashed) c.dash else c.edge, dashed = dashed),
                contentAlignment = Alignment.Center,
            ) {
                MiraProtoTileIcon(s, c.fg, 25.dp)
            }
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
        Text(
            s.label?.toString().orEmpty(),
            color = c.fg,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 80.dp),
        )
    }
}

// =============================================================================================
// MIRACLO ColorOS add-ons. Everything below is added on top of the panel above; the panel's
// look is unchanged.
//  1. Edit in place: − badges, touch-and-hold drag with springy reflow, empty pill slots that a
//     round tile grows into (and pills that shrink back into round tiles), widgets moved and
//     hidden in place, and the add sheet with recommended widgets and tile categories.
//  2. Music: the media card grows into the player (art, title, wavy seek bar, controls, volume,
//     media output), the glow breathes while playing, and only the brightness slider stays
//     while it is dragged.
//  3. Motion: rubber-band pull past the end, a small spring when the panel finishes opening and
//     the ringer's liquid thumb with the mode named at the top.
// =============================================================================================

/** While the brightness slider is dragged everything else fades; the shade's tint follows. */
internal object MiraQsBrightOnly {
    var fade by mutableFloatStateOf(0f)
}

private class CoXFx {
    var brightOnly by mutableStateOf(false)
    var mode by mutableStateOf<String?>(null)
    var modeN by mutableStateOf(0)
    var player by mutableStateOf(false)
    var playerShown by mutableStateOf(false)
    var pull by mutableFloatStateOf(0f)
    var card by mutableStateOf(Rect.Zero)
    var root by mutableStateOf(Offset.Zero)

    fun showMode(text: String) {
        mode = text
        modeN++
    }

    fun openPlayer() {
        playerShown = true
        player = true
    }
}

private object CoXIcons {
    val Layout: androidx.compose.ui.graphics.vector.ImageVector =
        MiraCLOIcons.icon("M3,3h8v10H3V3zM13,3h8v6h-8V3zM13,11h8v10h-8V11zM3,15h8v6H3V15z")
    val Add: androidx.compose.ui.graphics.vector.ImageVector =
        MiraCLOIcons.icon("M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val Open: androidx.compose.ui.graphics.vector.ImageVector =
        MiraCLOIcons.icon(
            "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7z" +
                "M14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z"
        )
}

/** The carrier name; for 1.5 s after the ringer changes, the new mode takes its place. */
@Composable
private fun CoXCarrier(c: MiraColorOSInk, carrier: String, fx: CoXFx) {
    var last by remember { mutableStateOf("") }
    val shown = fx.mode
    if (shown != null && shown != last) last = shown
    LaunchedEffect(fx.modeN) {
        if (fx.mode != null) {
            delay(1500)
            fx.mode = null
        }
    }
    val m by animateFloatAsState(
        if (shown != null) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "coxMode",
    )
    Box(Modifier.offset(38.dp, 104.dp)) {
        Text(
            carrier,
            color = c.fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - m
                translationY = -8.dp.toPx() * m
            },
        )
        Text(
            last,
            color = c.fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.graphicsLayer {
                alpha = m
                translationY = 8.dp.toPx() * (1f - m)
            },
        )
    }
}

/**
 * Pulling down past the end stretches the rows like rubber (lower rows more) and they spring back
 * on release. Upward and sideways drags, and drags a control takes, are left alone.
 */
private fun Modifier.coXRubber(fx: CoXFx, scope: CoroutineScope): Modifier =
    pointerInput(fx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (fx.playerShown) return@awaitEachGesture
            var dx = 0f
            var dy = 0f
            var active = false
            val slop = viewConfiguration.touchSlop
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                val d = ch.positionChange()
                if (!active) {
                    if (ch.isConsumed) break
                    dx += d.x
                    dy += d.y
                    if (dy > slop && dy > abs(dx) * 1.2f) active = true
                    else if (dy < -slop || abs(dx) > slop) break
                }
                if (active) {
                    ch.consume()
                    fx.pull = (fx.pull + d.y).coerceAtLeast(0f)
                }
            }
            if (active) {
                val from = fx.pull
                scope.launch {
                    animate(
                        initialValue = from,
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.48f, stiffness = 320f),
                    ) { v, _ -> fx.pull = v }
                }
            }
        }
    }

/**
 * The panel follows the pull with a slight scale and, when it finishes opening, settles with a
 * small overshoot. [open] is the shade's open progress (0 closed, 1 open).
 */
private fun Modifier.coXSettle(open: () -> Float): Modifier = composed {
    val s = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        var was = open()
        snapshotFlow { open() }.collect { f ->
            if (was < 0.95f && f >= 0.999f) {
                launch {
                    s.snapTo(1f)
                    s.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 240f), initialVelocity = 0.32f)
                }
            }
            was = f
        }
    }
    graphicsLayer {
        val f = open().coerceIn(0f, 1f)
        val k = s.value * (0.95f + 0.05f * f)
        scaleX = k
        scaleY = k
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
}

/** Items slide to their new place on a spring when the layout changes (edit mode). */
private fun Modifier.coXPlace(enabled: Boolean = true): Modifier = composed {
    val scope = rememberCoroutineScope()
    val on by rememberUpdatedState(enabled)
    var target by remember { mutableStateOf<IntOffset?>(null) }
    var anim by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this.onPlaced { target = it.positionInParent().round() }
        .offset {
            val t = target ?: return@offset IntOffset.Zero
            val a = anim ?: Animatable(t, IntOffset.VectorConverter).also { anim = it }
            if (!on) {
                if (a.value != t) scope.launch { a.snapTo(t) }
                return@offset IntOffset.Zero
            }
            if (a.targetValue != t) {
                scope.launch { a.animateTo(t, spring(dampingRatio = 0.66f, stiffness = 420f)) }
            }
            a.value - t
        }
}

// ---------------------------------------------------------------- 2. the music player

/** Opens Android's media output switcher for the playing app. */
private fun coXMediaOutput(context: Context, pkg: String?) {
    runCatching {
        context.sendBroadcast(
            Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                .setPackage(context.packageName)
                .putExtra("package_name", pkg.orEmpty())
        )
    }
}

/** A title that scrolls slowly when it is too long to fit. */
@Composable
private fun CoXMarquee(text: String, color: Color, size: TextUnit, modifier: Modifier = Modifier) {
    val sc = rememberScrollState()
    LaunchedEffect(text, sc.maxValue) {
        sc.scrollTo(0)
        if (sc.maxValue <= 0) return@LaunchedEffect
        while (true) {
            delay(1600)
            sc.animateScrollTo(sc.maxValue, tween(sc.maxValue * 14 + 400, easing = LinearEasing))
            delay(1200)
            sc.scrollTo(0)
        }
    }
    Text(
        text,
        color = color,
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.horizontalScroll(sc, enabled = false),
    )
}

/** A wide glass volume slider for the player. */
@Composable
private fun CoXHSlider(c: MiraColorOSInk, level: MiraProtoLevel, modifier: Modifier = Modifier) {
    val local = rememberProtoLocal(level.value)
    val v = local.floatValue
    Box(
        modifier.height(44.dp)
            .protoGlass(RoundedCornerShape(22.dp), c.glass, c.edge, c.hi)
            .protoSliderInput({ v }, vertical = false, onLocal = { local.floatValue = it }, onSet = level.set)
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(v.coerceIn(0.001f, 1f)).background(Color.White))
        MiraProtoGlyph(
            MiraCLOIcons.Headphones,
            if (v > 0.12f) ColorOSBlue else c.fg,
            20.dp,
            Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
        )
    }
}

/** The album's colours, large and soft, behind the player. */
@Composable
private fun CoXPlayerGlow(media: MiraProtoMedia, modifier: Modifier) {
    val art = media.art
    val colors = remember(art) { art?.let { colorOSArtColors(it) } }
    if (colors == null) return
    val edge = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
    val pulse = rememberInfiniteTransition(label = "coxPlayerGlow")
    val k by pulse.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "coxPlayerGlowK",
    )
    val v = if (media.playing) k else 0.8f
    Box(modifier) {
        Box(
            Modifier.offset((-40).dp, (-30).dp)
                .size(300.dp, 320.dp)
                .blur(60.dp, edge)
                .background(colors.first.copy(alpha = 0.55f * v), CircleShape)
        )
        Box(
            Modifier.offset(110.dp, 300.dp)
                .size(280.dp, 300.dp)
                .blur(60.dp, edge)
                .background(colors.second.copy(alpha = 0.5f * v), CircleShape)
        )
    }
}

/**
 * The music player, grown out of the media card: the card's frame springs open to the full
 * player and back. Art (tap the corner to open the app), the scrolling title, the wavy seek bar,
 * previous / play / next, volume and Android's media output switcher.
 */
@Composable
private fun CoXPlayer(
    c: MiraColorOSInk,
    env: MiraProtoEnv,
    media: MiraProtoMedia,
    fx: CoXFx,
    t: Float,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val tx = with(density) { 20.dp.toPx() }
    val ty = with(density) { 118.dp.toPx() }
    val tw = with(density) { 350.dp.toPx() }
    val th = with(density) { 610.dp.toPx() }
    val from = fx.card.translate(-fx.root.x, -fx.root.y)
    val ok = from.width > 0f && from.height > 0f
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = t.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.2f))
                .protoTap(onTap = onClose)
        )
        Box(
            Modifier.offset(20.dp, 118.dp).size(350.dp, 610.dp).graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                if (ok) {
                    val r = 1f - t
                    translationX = (from.left - tx) * r
                    translationY = (from.top - ty) * r
                    scaleX = from.width / tw + (1f - from.width / tw) * t
                    scaleY = from.height / th + (1f - from.height / th) * t
                }
                alpha = (t * 2.2f).coerceIn(0f, 1f)
            }
        ) {
            CoXPlayerGlow(media, Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxSize()
                    .protoGlass(RoundedCornerShape(36.dp), c.base.copy(alpha = 0.55f), c.edge, c.hi)
                    .protoTap {}
                    .padding(22.dp)
                    .graphicsLayer { alpha = ((t - 0.3f) / 0.6f).coerceIn(0f, 1f) }
            ) {
                Box(
                    Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(26.dp)).background(c.bubble)
                ) {
                    media.art?.let {
                        Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    if (media.open != null) {
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(34.dp)
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                                .protoTap {
                                    media.open?.invoke()
                                    onClose()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            MiraProtoGlyph(CoXIcons.Open, Color.White, 18.dp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                CoXMarquee(media.song, c.fg, 21.sp, Modifier.fillMaxWidth())
                Text(media.artist, color = c.fg2, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                MiraMediaSeekBar(media, active = c.fg, track = c.fg.copy(alpha = 0.25f), times = c.fg2)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaKey(MiraProtoIcons.SkipPrevious, 30.dp, c.fg, media.prev, box = 52.dp)
                    Box(
                        Modifier.size(68.dp).protoGlass(CircleShape, c.glass, c.edge, c.hi),
                        contentAlignment = Alignment.Center,
                    ) {
                        MediaKey(
                            if (media.playing) MiraProtoIcons.Pause else MiraProtoIcons.Play,
                            34.dp,
                            c.fg,
                            media.playPause,
                            box = 68.dp,
                        )
                    }
                    MediaKey(MiraProtoIcons.SkipNext, 30.dp, c.fg, media.next, box = 52.dp)
                }
                Spacer(Modifier.height(12.dp))
                CoXHSlider(c, env.volume, Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Row(
                        Modifier.height(38.dp)
                            .protoGlass(RoundedCornerShape(19.dp), c.glass, c.edge, c.hi)
                            .protoTap { coXMediaOutput(context, media.controller?.packageName) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MiraProtoGlyph(MiraCLOIcons.Headphones, c.fg, 18.dp)
                        Text("Media output", color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 1. edit in place

private class CoXToastState {
    var text by mutableStateOf<String?>(null)
    var n by mutableStateOf(0)

    fun show(s: String) {
        text = s
        n++
    }
}

@Composable
private fun CoXToast(c: MiraColorOSInk, state: CoXToastState, modifier: Modifier) {
    var last by remember { mutableStateOf("") }
    state.text?.let { if (it != last) last = it }
    LaunchedEffect(state.n) {
        if (state.text != null) {
            delay(1600)
            state.text = null
        }
    }
    val a by animateFloatAsState(
        if (state.text != null) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "coxToast",
    )
    if (a > 0.01f) {
        Box(
            modifier.graphicsLayer {
                alpha = a.coerceIn(0f, 1f)
                scaleX = 0.9f + 0.1f * a
                scaleY = 0.9f + 0.1f * a
                translationY = 16.dp.toPx() * (1f - a)
            }
                .protoGlass(RoundedCornerShape(20.dp), c.base.copy(alpha = 0.92f), c.edge, c.hi)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(last, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The grey − badge of edit mode; it pops in on a spring. */
@Composable
private fun CoXBadge(onTap: () -> Unit, modifier: Modifier = Modifier) {
    val s = remember { Animatable(0f) }
    LaunchedEffect(Unit) { s.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 520f)) }
    Box(
        modifier
            .graphicsLayer {
                scaleX = s.value
                scaleY = s.value
            }
            .size(22.dp)
            .background(Color(0xFF8E8E93), CircleShape)
            .protoTap(onTap = onTap),
        contentAlignment = Alignment.Center,
    ) {
        MiraProtoGlyph(MiraProtoIcons.Minus, Color.White, 14.dp)
    }
}

/** A tile being dragged: where it started (root px) and how far the finger has moved. */
private class CoXDrag(val spec: TileSpec, val pill: Boolean, val start: Rect) {
    var delta by mutableStateOf(Offset.Zero)
    val at: Offset
        get() = start.center + delta
}

/** The add sheet's sections, by tile spec. */
private fun coXCategory(spec: String): String =
    when {
        spec.startsWith("custom(") -> "Apps"
        spec in setOf("internet", "wifi", "cell", "bt", "airplane", "hotspot", "nfc", "cast", "saver") -> "Connection"
        spec in setOf("dark", "night", "rotation", "color_correction", "inversion", "reduce_brightness", "font_scaling", "onehanded", "hearing_devices") -> "Display"
        spec in setOf("dnd", "modes_dnd", "alarm") -> "Sound and focus"
        spec in setOf("location", "mictoggle", "cameratoggle", "work") -> "Privacy"
        spec in setOf("flashlight", "screenrecord", "qr_code_scanner", "battery", "record_issue", "dream", "screenshot") -> "Tools"
        else -> "More"
    }

private val CoXCategoryOrder = listOf("Connection", "Display", "Sound and focus", "Tools", "Privacy", "Apps", "More")

/**
 * Edit mode drawn in place, as in ColorOS 17: the same panel, a little smaller.
 *  - the top block is one outlined container with a fixed 2 x 4 grid of pill-sized slots;
 *    − on an item leaves its slot(s) empty (dashed +) and nothing else moves
 *  - touch and hold, then drag: drop an item on another slot to move it (same-size items swap);
 *    drop a round tile on an empty slot (or on a pill) and it becomes a pill there; drag a pill
 *    down onto the tiles and it becomes a round tile
 *  - + (top left), the layout button or an empty slot opens the add sheet
 * Android's own edit mode runs underneath, so tile changes are saved at once.
 */
@Composable
private fun CoXEditInPlace(
    c: MiraColorOSInk,
    env: MiraProtoEnv,
    host: MiraEditHost,
    store: MiraTileSizes,
    grid: CoXGrid,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val base = host.current.map { it.tileSpec }
    var dragOrder by remember { mutableStateOf<List<TileSpec>?>(null) }
    val order = (dragOrder ?: base).filter { it in base }
    val live = env.tiles.associateBy { it.spec }
    val liveBySpec = env.tiles.associateBy { it.spec.spec }
    val editBy = host.tiles.associateBy { it.tileSpec }
    fun cell(s: TileSpec): MiraCell = miraTileCell(s.spec, host.isSmall(s), store, ColorOSSizes)
    val inGrid = grid.tileSpecs()
    val rest = order.filter { it.spec !in inGrid }

    var drag by remember { mutableStateOf<CoXDrag2?>(null) }
    var rootTL by remember { mutableStateOf(Offset.Zero) }
    val centres = remember { mutableStateMapOf<TileSpec, Offset>() }
    val slotRects = remember { mutableStateMapOf<Int, Rect>() }
    var gridRect by remember { mutableStateOf(Rect.Zero) }
    var sheet by remember { mutableStateOf(false) }
    var sheetSlot by remember { mutableStateOf<Int?>(null) }
    val toast = remember { CoXToastState() }
    val near = with(density) { 46.dp.toPx() }
    val media = env.media("No media", "Tap to open a music app")

    // the panel shrinks a little into edit mode, on a spring
    val enter = remember { Animatable(1f) }
    LaunchedEffect(Unit) { enter.animateTo(0.965f, spring(dampingRatio = 0.62f, stiffness = 380f)) }

    /** The slot under [p] (root px), or null. */
    fun slotAt(p: Offset): Int? = slotRects.entries.firstOrNull { it.value.contains(p) }?.key

    /** The top slot a [rows]-tall item lands on when dropped on [slot]. */
    fun topFor(id: String, slot: Int): Int =
        if (grid.rows(id) == 2 && slot >= 6) slot - 2 else slot

    fun toRound(s: TileSpec, at: Int?) {
        grid.remove(CoXGrid.tile(s.spec))
        if (cell(s) != MiraCell.S11) store.set(s.spec, MiraCell.S11)
        if (!host.isSmall(s)) host.resize(s, true)
        if (at != null) host.setOrder(base.protoMove(s, at))
    }

    fun dropFromGrid(d: CoXDrag2) {
        val p = d.at
        val slot = slotAt(p)
        val spec = CoXGrid.specOf(d.id)
        when {
            slot != null -> {
                if (!grid.move(d.id, topFor(d.id, slot))) toast.show("Insufficient space. Remove a tile first.")
            }
            spec != null && p.y > gridRect.bottom -> {
                val s = base.firstOrNull { it.spec == spec } ?: return
                val target = centres.entries
                    .filter { it.key in rest }
                    .minByOrNull { (it.value - p).getDistanceSquared() }
                toRound(s, target?.let { base.indexOf(it.key) })
            }
        }
    }

    fun dropRound(s: TileSpec, d: CoXDrag2, o: List<TileSpec>?) {
        val slot = slotAt(d.at)
        if (slot != null) {
            val id = CoXGrid.tile(s.spec)
            val there = grid.occupant(slot)
            when {
                there == null -> grid.put(id, slot)
                CoXGrid.specOf(there.second) != null && there.first == slot -> {
                    // a round tile dropped on a pill: they change places
                    grid.remove(there.second)
                    grid.put(id, slot)
                }
                else -> toast.show("Insufficient space. Remove a tile first.")
            }
        } else if (o != null && o != base) {
            host.setOrder(o)
        }
    }

    val pagesIdx = miraPages(rest.map { cell(it) }, cols = 4, maxRows = 3)
    val pages = pagesIdx.map { p -> p.map { rest[it] } }
    val pager = rememberPagerState(0) { pages.size }

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { rootTL = it.boundsInRoot().topLeft }
            // Empty space in edit mode does nothing (before, a tap there closed the whole panel).
            .pointerInput(Unit) { detectTapGestures {} }
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = enter.value
                scaleY = enter.value
                transformOrigin = TransformOrigin(0.5f, 0.06f)
            }
        ) {
            // Header, as in ColorOS: + on the left; the layout button and Done on the right.
            Box(
                Modifier.offset(26.dp, 44.dp)
                    .size(56.dp, 36.dp)
                    .protoGlass(RoundedCornerShape(18.dp), c.glass, c.edge, c.hi)
                    .protoTap {
                        sheetSlot = null
                        sheet = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                MiraProtoGlyph(CoXIcons.Add, c.fg, 22.dp)
            }
            ColorOSCircle(c, x = (390 - 26 - 84 - 10 - 36).dp, top = 44.dp, size = 36.dp, onTap = {
                sheetSlot = null
                sheet = true
            }) {
                MiraProtoGlyph(CoXIcons.Layout, c.fg, 20.dp)
            }
            Box(
                Modifier.offset((390 - 26 - 84).dp, 44.dp)
                    .size(84.dp, 36.dp)
                    .protoGlass(RoundedCornerShape(18.dp), c.glass, c.edge, c.hi)
                    .protoTap(onTap = host.done),
                contentAlignment = Alignment.Center,
            ) {
                Text("Done", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            // The top block: one outlined container, 2 x 4 pill slots.
            Box(
                Modifier.offset(28.dp, 104.dp)
                    .size(334.dp, CoXGridHeight + 20.dp)
                    .border(1.dp, c.edge, RoundedCornerShape(34.dp))
                    .onGloballyPositioned { gridRect = it.boundsInRoot() }
            ) {
                Box(Modifier.offset(10.dp, 10.dp).size(314.dp, CoXGridHeight)) {
                    // the slots underneath: dashed + where nothing is
                    for (s in 0..7) {
                        val (x, y) = coXSlotXY(s)
                        val empty = grid.occupant(s) == null
                        val d = drag
                        val hover = d != null && empty && slotRects[s]?.contains(d.at) == true
                        Box(
                            Modifier.offset(x, y)
                                .size(CoXColW, CoXRowH)
                                .onGloballyPositioned { slotRects[s] = it.boundsInRoot() }
                        ) {
                            if (empty) {
                                Box(
                                    Modifier.fillMaxSize()
                                        .graphicsLayer {
                                            val k = if (hover) 1.05f else 1f
                                            scaleX = k
                                            scaleY = k
                                        }
                                        .protoGlass(
                                            RoundedCornerShape(32.dp),
                                            if (hover) c.glass else Color.Transparent,
                                            c.dash,
                                            dashed = true,
                                            rimWidth = 1.5.dp,
                                        )
                                        .protoTap {
                                            sheetSlot = s
                                            sheet = true
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MiraProtoGlyph(CoXIcons.Add, c.fg2, 22.dp)
                                }
                            }
                        }
                    }
                    // the items, each in its own slot(s)
                    grid.items.forEach { (top, id) ->
                        val (x, y) = coXSlotXY(top)
                        androidx.compose.runtime.key(id) {
                            var rect by remember { mutableStateOf(Rect.Zero) }
                            val isDragged = drag?.id == id
                            val spec = CoXGrid.specOf(id)
                            val tileSpec = spec?.let { sp -> base.firstOrNull { it.spec == sp } }
                            Box(
                                Modifier.offset(x, y)
                                    .size(CoXColW, if (grid.rows(id) == 2) CoXRowH * 2 + CoXGap else CoXRowH)
                                    .onGloballyPositioned { rect = it.boundsInRoot() }
                            ) {
                                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (isDragged) 0f else 1f }) {
                                    CoXGridItem(c, env, id, liveBySpec, media, fx = null, others = 1f)
                                }
                                if (isDragged) {
                                    Box(
                                        Modifier.fillMaxSize()
                                            .protoGlass(RoundedCornerShape(32.dp), Color.Transparent, c.dash, dashed = true)
                                    )
                                }
                                Box(
                                    Modifier.matchParentSize()
                                        // taps do nothing in edit mode (no toggling)
                                        .pointerInput(id) { detectTapGestures {} }
                                        .pointerInput(id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { drag = CoXDrag2(id, true, rect) },
                                                onDrag = { ch, a ->
                                                    ch.consume()
                                                    drag?.let { it.delta = it.delta + a }
                                                },
                                                onDragEnd = {
                                                    val dd = drag
                                                    drag = null
                                                    if (dd != null) dropFromGrid(dd)
                                                },
                                                onDragCancel = { drag = null },
                                            )
                                        }
                                )
                                if (tileSpec == null || editBy[tileSpec]?.isRemovable != false) {
                                    CoXBadge(
                                        onTap = {
                                            grid.remove(id)
                                            if (tileSpec != null) host.remove(tileSpec)
                                        },
                                        modifier = Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // The round tiles, paged, under the container.
            Column(Modifier.offset(0.dp, 104.dp + CoXGridHeight + 20.dp + 18.dp).width(390.dp)) {
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = drag == null,
                    modifier = Modifier.width(390.dp).height(291.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 30.dp),
                    pageSpacing = 60.dp,
                    verticalAlignment = Alignment.Top,
                ) { pi ->
                    val page = pages.getOrElse(pi) { emptyList() }
                    MiraCellGrid(
                        sizes = page.map { cell(it) },
                        cols = 4,
                        cellHeight = 95.dp,
                        gap = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        page.forEach { s ->
                            androidx.compose.runtime.key(s) {
                                CoXEditTile(
                                    c = c,
                                    env = env,
                                    spec = s,
                                    cell = cell(s),
                                    live = live[s],
                                    edit = editBy[s],
                                    dragged = drag?.id == CoXGrid.tile(s.spec),
                                    onCentre = { r -> centres[s] = r.center },
                                    onRemove = { host.remove(s) },
                                    onStart = { r ->
                                        dragOrder = base
                                        drag = CoXDrag2(CoXGrid.tile(s.spec), false, r)
                                    },
                                    onDrag = { a ->
                                        val d = drag ?: return@CoXEditTile
                                        d.delta = d.delta + a
                                        val p = d.at
                                        // over the container: no reordering, the slot lights up instead
                                        if (p.y < gridRect.bottom) return@CoXEditTile
                                        val list = dragOrder ?: base
                                        val onPage = pages.getOrElse(pager.currentPage) { emptyList() }
                                        val target = centres.entries
                                            .filter { it.key != s && it.key in onPage && it.key in list }
                                            .minByOrNull { (it.value - p).getDistanceSquared() }
                                        if (target != null && (target.value - p).getDistance() < near) {
                                            val to = list.indexOf(target.key)
                                            if (to >= 0 && list.indexOf(s) != to) dragOrder = list.protoMove(s, to)
                                        }
                                    },
                                    onEnd = {
                                        val d = drag
                                        val o = dragOrder
                                        drag = null
                                        dragOrder = null
                                        if (d != null) dropRound(s, d, o)
                                    },
                                    onCancel = {
                                        drag = null
                                        dragOrder = null
                                    },
                                )
                            }
                        }
                    }
                }
                if (pages.size > 1) {
                    Row(
                        Modifier.padding(top = 5.dp).width(390.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        repeat(pages.size) { i ->
                            Box(
                                Modifier.size(14.dp).protoTap { scope.launch { pager.animateScrollToPage(i) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(6.dp)
                                        .background(if (i == pager.currentPage) c.fg else c.fg3, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }

        // The dragged item follows the finger above everything, lifted a little.
        drag?.let { d ->
            val w = with(density) { d.start.width.toDp() }
            val h = with(density) { d.start.height.toDp() }
            Box(
                Modifier.offset {
                    IntOffset(
                        (d.start.left + d.delta.x - rootTL.x).roundToInt(),
                        (d.start.top + d.delta.y - rootTL.y).roundToInt(),
                    )
                }
                    .size(w, h)
                    .zIndex(5f)
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        alpha = 0.96f
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                val spec = CoXGrid.specOf(d.id)
                val t = spec?.let { liveBySpec[it] }
                when {
                    d.fromGrid -> CoXGridItem(c, env, d.id, liveBySpec, media, fx = null, others = 1f)
                    t == null -> {}
                    else -> {
                        val ts = t.spec
                        when (cell(ts)) {
                            MiraCell.S22 -> ColorOSLargeTile(c, t, env.dark)
                            MiraCell.S21 -> ColorOSWidePill(c, t, Modifier.fillMaxWidth())
                            else -> ColorOSRoundTile(c, t, env.dark)
                        }
                    }
                }
            }
        }

        if (sheet) {
            CoXAddSheet(
                c = c,
                host = host,
                grid = grid,
                slot = sheetSlot,
                toast = toast,
                onPick = { t ->
                    val slot = sheetSlot
                    if (slot != null && grid.occupant(slot) == null) {
                        // from an empty slot: the tile goes into that slot as a pill
                        host.add(t.tileSpec)
                        grid.put(CoXGrid.tile(t.tileSpec.spec), slot)
                        true
                    } else {
                        host.add(t.tileSpec)
                        toast.show(t.label.text + " added")
                        false
                    }
                },
                onClose = {
                    sheet = false
                    sheetSlot = null
                },
            )
        }
        CoXToast(c, toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

/** A widget row in edit mode: dashed frame, − to hide, touch and hold to move it. */
@Composable
private fun CoXEditBlock(
    c: MiraColorOSInk,
    id: String,
    shift: () -> Float,
    dragged: Boolean,
    onRect: (Rect) -> Unit,
    onRemove: () -> Unit,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit,
    content: @Composable () -> Unit,
) {
    val lift by animateFloatAsState(
        if (dragged) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "coxLift",
    )
    Box(
        Modifier.width(390.dp)
            .onGloballyPositioned { onRect(it.boundsInRoot()) }
            .coXPlace(enabled = !dragged)
            .zIndex(if (dragged) 3f else 0f)
            .graphicsLayer {
                translationY = shift()
                val k = 1f + 0.03f * lift
                scaleX = k
                scaleY = k
            }
    ) {
        content()
        Box(
            Modifier.matchParentSize()
                .padding(horizontal = 30.dp)
                .drawBehind {
                    val pad = 6.dp.toPx()
                    drawRoundRect(
                        color = c.dash,
                        topLeft = Offset(0f, -pad),
                        size = Size(size.width, size.height + pad * 2f),
                        cornerRadius = CornerRadius(36.dp.toPx()),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                            ),
                        ),
                    )
                }
                .pointerInput(id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onStart() },
                        onDrag = { ch, a ->
                            ch.consume()
                            onDrag(a.y)
                        },
                        onDragEnd = { onEnd() },
                        onDragCancel = { onEnd() },
                    )
                }
        )
        CoXBadge(onTap = onRemove, modifier = Modifier.align(Alignment.TopStart).offset(22.dp, (-12).dp))
    }
}

/** One tile in edit mode: its real look, − to remove, touch and hold to drag. */
@Composable
private fun CoXEditTile(
    c: MiraColorOSInk,
    env: MiraProtoEnv,
    spec: TileSpec,
    cell: MiraCell,
    live: TileViewModel?,
    edit: EditTileViewModel?,
    dragged: Boolean,
    onCentre: (Rect) -> Unit,
    onRemove: () -> Unit,
    onStart: (Rect) -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    var rect by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier.onGloballyPositioned {
            rect = it.boundsInRoot()
            onCentre(rect)
        }
            .coXPlace(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.graphicsLayer { alpha = if (dragged) 0f else 1f },
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                live == null ->
                    Box(Modifier.size(64.dp).protoGlass(CircleShape, c.glass, c.edge, c.hi), contentAlignment = Alignment.Center) {
                        if (edit != null) MiraEditIcon(edit, c.fg, 24.dp)
                    }
                cell == MiraCell.S21 ->
                    Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.TopCenter) {
                        ColorOSWidePill(c, live, Modifier.fillMaxWidth())
                    }
                cell == MiraCell.S22 ->
                    Box(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 22.dp)) {
                        ColorOSLargeTile(c, live, env.dark)
                    }
                else -> ColorOSRoundTile(c, live, env.dark)
            }
        }
        if (dragged) {
            Box(
                Modifier.size(64.dp).protoGlass(CircleShape, Color.Transparent, c.dash, dashed = true, rimWidth = 1.5.dp)
            )
        }
        Box(
            Modifier.matchParentSize().pointerInput(spec) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onStart(rect) },
                    onDrag = { ch, a ->
                        ch.consume()
                        onDrag(a)
                    },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onCancel() },
                )
            }
        )
        if (edit?.isRemovable != false) {
            CoXBadge(
                onTap = onRemove,
                modifier = Modifier.align(Alignment.TopCenter).offset((-27).dp, (-4).dp),
            )
        }
    }
}

/**
 * The add sheet: rises on a spring, grows taller as you scroll. Recommended first (Media control,
 * Brightness & volume, My Devices, Sound mode), then every tile Android has, by section. Things
 * already on the panel are dimmed and say so when tapped. A widget goes into the slot that opened
 * the sheet when it fits there, otherwise into the first place it fits.
 */
@Composable
private fun CoXAddSheet(
    c: MiraColorOSInk,
    host: MiraEditHost,
    grid: CoXGrid,
    slot: Int?,
    toast: CoXToastState,
    onPick: (EditTileViewModel) -> Boolean,
    onClose: () -> Unit,
) {
    val y = remember { Animatable(1f) }
    val scroll = rememberScrollState()
    val tall by animateFloatAsState(
        if (scroll.value > 4) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "coxSheetTall",
    )
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { y.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 360f)) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            y.animateTo(1f, tween(240))
            onClose()
        }
    }
    fun addWidget(id: String, name: String) {
        if (grid.has(id)) {
            toast.show("$name is already added")
            return
        }
        val top = grid.firstFit(id, slot)
        if (top == null) {
            toast.show("Insufficient space. Remove a tile first.")
            return
        }
        grid.put(id, top)
        close()
    }
    val all = host.tiles.filter { it.tileSpec.spec !in MIRA_PROTO_EXCLUDED }
    val groups = all.groupBy { coXCategory(it.tileSpec.spec) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = 1f - y.value }
                .background(Color.Black.copy(alpha = 0.3f))
                .protoTap { close() }
        )
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(560.dp + 200.dp * tall)
                .graphicsLayer { translationY = size.height * y.value }
                .protoGlass(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp), c.base.copy(alpha = 0.96f), c.edge, c.hi)
                .protoTap {}
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(38.dp, 5.dp).background(c.fg3, RoundedCornerShape(3.dp)))
            }
            Text(
                "Tap a tile to add",
                color = c.fg,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            )
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(horizontal = 22.dp)) {
                Text(
                    "Recommended",
                    color = c.fg2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
                // Media control (a card) and Brightness & volume (two sliders), side by side.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(CO_MEDIA to "Media control", CO_SLIDERS to "Brightness & volume").forEach { (id, name) ->
                        val added = grid.has(id)
                        Column(
                            Modifier.weight(1f).alpha(if (added) 0.38f else 1f).protoTap { addWidget(id, name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().height(126.dp), contentAlignment = Alignment.Center) {
                                if (id == CO_MEDIA) {
                                    Column(
                                        Modifier.width(126.dp)
                                            .fillMaxHeight()
                                            .protoGlass(RoundedCornerShape(26.dp), c.glass, c.edge, c.hi)
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Box(Modifier.size(34.dp).background(c.bubble, RoundedCornerShape(9.dp)))
                                        Box(Modifier.size(54.dp, 8.dp).background(c.bubble, RoundedCornerShape(4.dp)))
                                        Box(Modifier.size(80.dp, 8.dp).background(c.bubble, RoundedCornerShape(4.dp)))
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        listOf(MiraCLOIcons.LightMode, MiraCLOIcons.Headphones).forEach { icon ->
                                            Box(
                                                Modifier.size(48.dp, 126.dp)
                                                    .protoGlass(RoundedCornerShape(24.dp), c.glass, c.edge, c.hi),
                                                contentAlignment = Alignment.BottomCenter,
                                            ) {
                                                Box(
                                                    Modifier.fillMaxWidth()
                                                        .height(70.dp)
                                                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                                                )
                                                MiraProtoGlyph(icon, Color(0xFF4A4A4F), 20.dp, Modifier.padding(bottom = 12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Text(name, color = c.fg, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        }
                    }
                }
                // My Devices and Sound mode, as wide bars.
                listOf(CO_DEVICES to "My Devices", CO_RINGER to "Sound mode").forEach { (id, name) ->
                    val added = grid.has(id)
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(top = 14.dp)
                            .alpha(if (added) 0.38f else 1f)
                            .protoTap { addWidget(id, name) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .height(56.dp)
                                .protoGlass(RoundedCornerShape(28.dp), c.glass, c.edge, c.hi)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MiraProtoGlyph(
                                if (id == CO_DEVICES) MiraCLOIcons.Devices else MiraProtoIcons.Bell,
                                c.fg,
                                24.dp,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(70.dp, 8.dp).background(c.bubble, RoundedCornerShape(4.dp)))
                                Box(Modifier.size(110.dp, 8.dp).background(c.bubble, RoundedCornerShape(4.dp)))
                            }
                        }
                        Text(name, color = c.fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                CoXCategoryOrder.forEach { cat ->
                    val list = groups[cat].orEmpty()
                    if (list.isNotEmpty()) {
                        Text(
                            cat,
                            color = c.fg2,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 22.dp, bottom = 12.dp),
                        )
                        list.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                row.forEach { t ->
                                    val label = t.label.text
                                    Column(
                                        Modifier.weight(1f)
                                            .alpha(if (t.isCurrent) 0.38f else 1f)
                                            .protoTap {
                                                if (t.isCurrent) toast.show("$label is already added")
                                                else if (onPick(t)) close()
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Box(
                                            Modifier.size(56.dp).protoGlass(CircleShape, c.glass, c.edge, c.hi),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            MiraEditIcon(t, c.fg, 24.dp)
                                        }
                                        Text(
                                            label,
                                            color = c.fg,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 80.dp),
                                        )
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- the ColorOS top block (cc2)

private const val CO_SLIDERS = "sliders"
private const val CO_RINGER = "ringer"

private val CoXColW = 149.dp
private val CoXRowH = 64.dp
private val CoXGap = 16.dp
private val CoXGridHeight = CoXRowH * 4 + CoXGap * 3

/** Where slot [s] (row * 2 + column) sits inside the 314 dp wide block. */
private fun coXSlotXY(s: Int): Pair<Dp, Dp> =
    (CoXColW + CoXGap) * (s % 2) to (CoXRowH + CoXGap) * (s / 2)

/** An item being dragged: its id, whether it came from the top block, and where it started. */
private class CoXDrag2(val id: String, val fromGrid: Boolean, val start: Rect) {
    var delta by mutableStateOf(Offset.Zero)
    val at: Offset
        get() = start.center + delta
}

/**
 * The ColorOS top block: a fixed 2 x 4 grid of pill-sized slots (slot = row * 2 + column). Each
 * item sits at its top slot: "t:<spec>" a tile as a pill, or the media card and the brightness +
 * volume sliders (one column, two rows each), My Devices or the ringer (one slot each).
 * Kept in Settings.Secure "miraclo_qs_cogrid" as "slot=id;slot=id"; until it is first edited the
 * layout comes from [default] (the old pills and rows, so nothing changes on update).
 */
private class CoXGrid(private val context: Context) {
    private var saved by mutableStateOf<Map<Int, String>?>(null)
    var default: () -> Map<Int, String> = { emptyMap() }

    val items: Map<Int, String>
        get() = saved ?: default()

    fun load() {
        val raw =
            runCatching {
                    Settings.Secure.getStringForUser(
                        context.contentResolver, KEY, ActivityManager.getCurrentUser())
                }
                .getOrNull()
        saved =
            raw?.let { r ->
                r.split(";")
                    .mapNotNull { e ->
                        val i = e.indexOf('=')
                        val s = if (i > 0) e.substring(0, i).toIntOrNull() else null
                        if (s == null || s !in 0..7) null else s to e.substring(i + 1)
                    }
                    .toMap()
            }
    }

    private fun commit(m: Map<Int, String>) {
        saved = m
        runCatching {
            Settings.Secure.putStringForUser(
                context.contentResolver,
                KEY,
                m.entries.joinToString(";") { "${it.key}=${it.value}" },
                ActivityManager.getCurrentUser(),
            )
        }
    }

    fun rows(id: String) = if (id == CO_MEDIA || id == CO_SLIDERS) 2 else 1

    private fun cells(top: Int, id: String) = if (rows(id) == 2) listOf(top, top + 2) else listOf(top)

    /** What covers [slot]: its top slot and id. */
    fun occupant(slot: Int): Pair<Int, String>? =
        items.entries.firstOrNull { slot in cells(it.key, it.value) }?.let { it.key to it.value }

    fun fits(id: String, top: Int, ignore: String? = null): Boolean {
        if (top !in 0..7) return false
        val cs = cells(top, id)
        if (cs.any { it > 7 }) return false
        return cs.all { s -> occupant(s).let { it == null || it.second == ignore } }
    }

    /** Where [id] can go: [prefer] (or the slot above it, for a two-row item) first. */
    fun firstFit(id: String, prefer: Int? = null): Int? {
        if (prefer != null) {
            if (fits(id, prefer)) return prefer
            if (rows(id) == 2 && prefer >= 2 && fits(id, prefer - 2)) return prefer - 2
        }
        return (0..7).firstOrNull { fits(id, it) }
    }

    fun has(id: String) = items.values.contains(id)

    fun topOf(id: String) = items.entries.firstOrNull { it.value == id }?.key

    fun put(id: String, top: Int) {
        commit(items.filterValues { it != id } + (top to id))
    }

    fun remove(id: String) {
        commit(items.filterValues { it != id })
    }

    /** Moves [id] to [top]; an item of the same size there swaps places with it. */
    fun move(id: String, top: Int): Boolean {
        val from = topOf(id) ?: return false
        if (from == top) return true
        if (fits(id, top, ignore = id)) {
            put(id, top)
            return true
        }
        val other = occupant(top) ?: return false
        if (other.first != top || rows(other.second) != rows(id)) return false
        val m = items.toMutableMap()
        m.remove(from)
        m.remove(top)
        m[top] = id
        m[from] = other.second
        commit(m)
        return true
    }

    /** Tiles shown as pills here (they are left out of the round tiles). */
    fun tileSpecs(): Set<String> = items.values.mapNotNull { specOf(it) }.toSet()

    companion object {
        const val KEY = "miraclo_qs_cogrid"

        fun tile(spec: String) = "t:$spec"

        fun specOf(id: String): String? = if (id.startsWith("t:")) id.substring(2) else null
    }
}

@Composable
private fun rememberCoXGrid(default: () -> Map<Int, String>): CoXGrid {
    val context = LocalContext.current
    val grid = remember { CoXGrid(context).also { it.load() } }
    grid.default = default
    return grid
}

/** One item of the top block, drawn the same in the panel and in edit mode. */
@Composable
private fun CoXGridItem(
    c: MiraColorOSInk,
    env: MiraProtoEnv,
    id: String,
    live: Map<String, TileViewModel>,
    media: MiraProtoMedia,
    fx: CoXFx?,
    others: Float,
) {
    val spec = CoXGrid.specOf(id)
    when {
        spec != null -> {
            val t = live[spec]
            if (t != null) {
                Box(Modifier.graphicsLayer { alpha = others }) {
                    ColorOSWidePill(c, t, Modifier.fillMaxWidth())
                }
            }
        }
        id == CO_MEDIA ->
            Box(
                Modifier.fillMaxSize()
                    .then(if (fx != null) Modifier.onGloballyPositioned { fx.card = it.boundsInRoot() } else Modifier)
                    .graphicsLayer { alpha = others }
            ) {
                ColorOSArtGlow(media, Modifier.fillMaxSize())
                ColorOSMediaCard(
                    c,
                    media,
                    onTap = if (fx == null || media.idle) null else ({ fx.openPlayer() }),
                )
            }
        id == CO_SLIDERS ->
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ColorOSTallSlider(
                    c,
                    env.brightness,
                    MiraCLOIcons.LightMode,
                    Color(0xFF4A4A4F),
                    onActive = { fx?.brightOnly = it },
                )
                Box(Modifier.graphicsLayer { alpha = others }) {
                    ColorOSTallSlider(c, env.volume, MiraCLOIcons.Headphones, ColorOSBlue)
                }
            }
        id == CO_DEVICES ->
            Box(Modifier.graphicsLayer { alpha = others }) {
                ColorOSDevicesPill(c, env, Modifier.fillMaxWidth())
            }
        id == CO_RINGER ->
            Box(Modifier.graphicsLayer { alpha = others }) {
                ColorOSRinger(c, env, Modifier.fillMaxWidth(), onMode = { fx?.showMode(it) })
            }
    }
}

/** The top block in the panel: empty rows close up, empty slots in a used row stay blank. */
@Composable
private fun CoXGridView(
    c: MiraColorOSInk,
    env: MiraProtoEnv,
    grid: CoXGrid,
    live: Map<String, TileViewModel>,
    media: MiraProtoMedia,
    fx: CoXFx,
    others: Float,
) {
    val items = grid.items.filter { (_, id) -> CoXGrid.specOf(id)?.let { it in live } ?: true }
    val used = (0..3).filter { r ->
        (0..1).any { col ->
            val s = r * 2 + col
            items.entries.any { (top, id) -> s == top || (grid.rows(id) == 2 && s == top + 2) }
        }
    }
    if (used.isEmpty()) return
    val rowIndex = used.withIndex().associate { (i, r) -> r to i }
    Box(
        Modifier.padding(start = 38.dp)
            .size(314.dp, CoXRowH * used.size + CoXGap * (used.size - 1))
    ) {
        items.forEach { (top, id) ->
            val i = rowIndex[top / 2] ?: return@forEach
            androidx.compose.runtime.key(id) {
                Box(
                    Modifier.offset((CoXColW + CoXGap) * (top % 2), (CoXRowH + CoXGap) * i)
                        .size(CoXColW, if (grid.rows(id) == 2) CoXRowH * 2 + CoXGap else CoXRowH)
                ) {
                    CoXGridItem(c, env, id, live, media, fx, others)
                }
            }
        }
    }
}
