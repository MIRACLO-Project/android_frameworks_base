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

    val editHost = LocalMiraEditHost.current
    if (editHost != null) {
        MiraStyledEditor(
            MiraEditLook(
                fg = c.fg, fg2 = c.fg2, accent = ColorOSBlue, onAccent = Color.White, divider = c.dash,
                tileSurface = { shape -> Modifier.protoGlass(shape, c.glass, c.edge, c.hi) },
                sizeNames = "Round" to "Pill",
                sizes = ColorOSSizes,
                sizeLabels = listOf("Round", "Pill", "Large"),
                bigShape = RoundedCornerShape(28.dp),
            ),
            editHost,
            sizeStore,
            blockLayout,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        if (!editing) {
            // Header: edit and settings circles, the carrier name underneath.
            ColorOSCircle(c, x = 26.dp, top = 44.dp, size = 36.dp, onTap = env.styleEdit) {
                MiraProtoGlyph(MiraCLOIcons.Edit, c.fg, 18.dp)
            }
            ColorOSCircle(
                c,
                x = (390 - 26 - 36).dp,
                top = 44.dp,
                size = 36.dp,
                onTap = { env.open(miraProtoIntent(Settings.ACTION_SETTINGS)) },
            ) {
                MiraProtoGlyph(MiraCLOIcons.Settings, c.fg, 20.dp)
            }
            Text(
                miraProtoCarrier(),
                color = c.fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.offset(38.dp, 104.dp),
            )

            // The widgets, in the order set in edit mode (the pills, media and sliders, My Devices
            // and the ringer, the tiles); media and My Devices can be hidden there.
            Column(Modifier.offset(0.dp, 142.dp).width(390.dp)) {
                blockLayout.shown.forEachIndexed { index, block ->
                    if (index > 0) Spacer(Modifier.height(if (block == CO_TILES) 22.dp else 16.dp))
                    androidx.compose.runtime.key(block) {
                    when (block) {
                        CO_PILLS ->
                            // The two pills: the first two tiles set to Pill in edit mode.
                            Row(
                                Modifier.padding(start = 38.dp).width(314.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                pills.getOrNull(0)?.let { ColorOSWidePill(c, it, Modifier.weight(1f)) }
                                    ?: Spacer(Modifier.weight(1f))
                                pills.getOrNull(1)?.let { ColorOSWidePill(c, it, Modifier.weight(1f)) }
                                    ?: Spacer(Modifier.weight(1f))
                            }
                        CO_MEDIA -> {
                            // Media card and the tall sliders, the album's colours glowing softly
                            // behind them.
                            val colorOSMedia = env.media("No media", "Tap to open a music app")
                            Box(Modifier.padding(start = 38.dp).width(314.dp).height(145.dp)) {
                                ColorOSArtGlow(colorOSMedia, Modifier.fillMaxSize())
                                Row(
                                    Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    ColorOSMediaCard(c, colorOSMedia)
                                    ColorOSTallSlider(c, env.brightness, MiraCLOIcons.LightMode, Color(0xFF4A4A4F))
                                    ColorOSTallSlider(c, env.volume, MiraCLOIcons.Headphones, ColorOSBlue)
                                }
                            }
                        }
                        CO_DEVICES ->
                            // My Devices and the ringer.
                            Row(
                                Modifier.padding(start = 38.dp).width(314.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Row(
                                    Modifier.weight(1f)
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
                                ColorOSRinger(c, env, Modifier.weight(1f))
                            }
                        CO_TILES -> ColorOSTilePages(c, env, visible.filter { it !in pills }, ::cellOf)
                        else -> {}
                    }
                    }
                }
            }
        } else {
            ColorOSEdit(c, env, layout, reserved, onDone = { editing = false })
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
            modifier = Modifier.padding(start = 30.dp).width(330.dp).height(291.dp),
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
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.offset(x, top).size(size).protoGlass(CircleShape, c.glass, c.edge, c.hi).protoTap(onTap = onTap),
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
    Box(modifier) {
        Box(
            Modifier.offset((-25).dp, (-15).dp)
                .size(200.dp, 175.dp)
                .blur(38.dp, edge)
                .background(colors.first.copy(alpha = 0.55f), CircleShape)
        )
        Box(
            Modifier.offset(150.dp, (-25).dp)
                .size(170.dp, 150.dp)
                .blur(38.dp, edge)
                .background(colors.second.copy(alpha = 0.5f), CircleShape)
        )
    }
}

@Composable
private fun ColorOSMediaCard(c: MiraColorOSInk, media: MiraProtoMedia) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.width(149.dp)
            .fillMaxHeight()
            .protoGlass(shape, c.glass, c.edge, c.hi)
            .protoTap(enabled = media.open != null) { media.open?.invoke() }
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
                if (!media.idle) {
                    // the wavy position bar: drag to seek
                    val progress = rememberMiraProgress(media)
                    MiraWavyBar(
                        fraction = progress.fraction,
                        playing = media.playing,
                        active = c.fg,
                        track = c.fg.copy(alpha = 0.25f),
                        height = 12.dp,
                        stroke = 3.dp,
                        onSeek = progress.seek?.let { s -> { f: Float -> s((f * progress.duration).toLong()) } },
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
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
) {
    val local = rememberProtoLocal(level.value)
    val v = local.floatValue
    Box(
        Modifier.width(66.dp)
            .fillMaxHeight()
            .protoGlass(RoundedCornerShape(33.dp), c.glass, c.edge, c.hi)
            .protoSliderInput({ v }, vertical = true, onLocal = { local.floatValue = it }, onSet = level.set)
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
private fun ColorOSRinger(c: MiraColorOSInk, env: MiraProtoEnv, modifier: Modifier) {
    val (mode, setMode) = rememberProtoRinger(env.audio)
    val modes =
        listOf(
            Triple(AudioManager.RINGER_MODE_NORMAL, MiraProtoIcons.Bell, ColorOSBlue),
            Triple(AudioManager.RINGER_MODE_VIBRATE, MiraProtoIcons.Vibrate, Color(0xFFFF3B30)),
            Triple(AudioManager.RINGER_MODE_SILENT, MiraProtoIcons.BellOff, Color(0xFFFF3B30)),
        )
    Row(
        modifier.height(64.dp).protoGlass(RoundedCornerShape(32.dp), c.glass, c.edge, c.hi).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEach { (m, icon, col) ->
            val sel = mode == m
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(48.dp)
                        .background(if (sel) (if (c.dark) Color.White else col) else Color.Transparent, CircleShape)
                        .protoTap { setMode(m) },
                    contentAlignment = Alignment.Center,
                ) {
                    MiraProtoGlyph(
                        icon,
                        if (sel) (if (c.dark) col else Color.White) else c.fg2,
                        if (sel) 24.dp else 20.dp,
                    )
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
