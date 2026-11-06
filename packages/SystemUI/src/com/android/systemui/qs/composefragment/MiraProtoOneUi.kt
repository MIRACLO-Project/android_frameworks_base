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
import android.app.ActivityManager
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import com.android.compose.theme.MiraMotion
import kotlin.math.abs
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One UI 9 palette, from the approved prototype (text is always white). */
internal class MiraOneUiInk(val dark: Boolean) {
    val base = if (dark) Color(0xFF162257) else Color(0xFF5F86E6)
    val scrim = if (dark) Color(0x61080C28) else Color(0x0FFFFFFF)
    val card = Color.White.copy(alpha = if (dark) 0.13f else 0.22f)
    val track = Color.White.copy(alpha = if (dark) 0.15f else 0.24f)
    val off = if (dark) Color(0x47080E28) else Color(0x29080E3C)
    val fg = Color.White
    val fg2 = Color.White.copy(alpha = 0.8f)
    val fg3 = Color.White.copy(alpha = 0.75f)
}

private val OneUiInkOn = Color(0xFF1D1D1F)
private val OneUiBlue = Color(0xFF3E7BFA)

/**
 * One UI 9 quick panel: carrier, then the time and date with edit, power and settings; Wi-Fi and
 * Bluetooth pills; the button block that expands from 8 to every button (with labels) from its
 * handle; brightness with Extra dim, media volume with Mute; the media pill; and the four cards.
 */
@Composable
internal fun MiraOneUiQs(env: MiraProtoEnv) {
    val c = remember(env.dark) { MiraOneUiInk(env.dark) }
    val layout = rememberMiraProtoLayout(MiraProtoStyle.ONEUI)
    var editing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val reserved = setOf("wifi", "bt")
    val blockLayout = rememberMiraBlockLayout(MiraProtoStyle.ONEUI, OneUiBlocks)

    if (editing) {
        MiraPortraitWindow { OneUiEdit(c, env, layout, reserved) { editing = false } }
        return
    }
    val editHost = LocalMiraEditHost.current
    if (editHost != null) {
        MiraPortraitWindow {
            MiraStyledEditor(
                MiraEditLook(
                    fg = c.fg, fg2 = c.fg2, accent = Color.White, onAccent = OneUiInkOn, divider = c.track,
                    tileSurface = { shape -> Modifier.background(c.card, shape) },
                    smallSize = 56.dp,
                    largeShape = RoundedCornerShape(36.dp),
                    sizeNames = "Button" to "Pill",
                ),
                editHost,
                blocks = blockLayout,
            )
        }
        return
    }
    // Tiles in Android's real order. Large tiles are One UI pills (two per row), small ones the
    // round buttons in the expandable block. Edit = Android's real editor (every tile, sizes).
    val host = LocalMiraTileHost.current
    val visible = env.tiles.filter { it.spec.spec !in MIRA_PROTO_EXCLUDED }
    val pills = visible.filter { host?.isSmall?.invoke(it) == false }
    Box(Modifier.fillMaxSize()) {
        Text(miraProtoCarrier(), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.offset(26.dp, 36.dp))
        Row(
            Modifier.offset(26.dp, 70.dp).width((390 - 26 - 14).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val now = env.clock
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(now.first, color = c.fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                Text(now.second, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 3.dp))
            }
            Row {
                OneUiHeaderButton(MiraCLOIcons.Edit, env.styleEdit)
                OneUiHeaderButton(MiraCLOIcons.Power, env.power)
                OneUiHeaderButton(MiraCLOIcons.Settings) { env.open(miraProtoIntent(Settings.ACTION_SETTINGS)) }
            }
        }

        // The widgets column; in landscape the tile block gets its own column on the right.
        val blocksColumn: @Composable (List<String>, androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp) -> Unit =
            { list, x, y, h ->
            Column(
                Modifier.offset(x, y).width(356.dp).height(h).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The widgets, in the order set in edit mode; sliders, media and the cards can be
                // hidden there.
                list.forEach { block ->
                    androidx.compose.runtime.key(block) {
                    when (block) {
                        OU_PILLS -> {
                            pills.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { OneUiWidePill(c, it, Modifier.weight(1f)) }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        OU_TILES -> {
                            // The expandable block: drag anywhere on it (not only the bar) to open or close it.
                            // It grows under the finger, rows fade in as they appear, and when let go it springs
                            // open or closed with the Animations spring (past halfway, or a flick). A tap on a
                            // tile still works: the drag only starts once the finger moves.
                            val all = visible.filter { it !in pills }
                            val context = LocalContext.current
                            val openScope = rememberCoroutineScope()
                            val open = remember { Animatable(if (expanded) 1f else 0f) }
                            val rowBottoms = remember { mutableStateMapOf<Int, Int>() }
                            var fullPx by remember { mutableIntStateOf(0) }
                            val tracker = remember { VelocityTracker() }
                            fun collapsedPx(): Int = rowBottoms[1] ?: rowBottoms[0] ?: fullPx
                            fun settle(target: Boolean) {
                                expanded = target
                                openScope.launch { open.animateTo(if (target) 1f else 0f, MiraMotion.spatialSpec(context)) }
                            }
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(c.card, RoundedCornerShape(34.dp))
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onDragStart = { tracker.resetTracking() },
                                            onDragEnd = {
                                                val v = tracker.calculateVelocity().y
                                                settle(if (abs(v) > 900f) v > 0f else open.value > 0.5f)
                                            },
                                            onDragCancel = { settle(open.value > 0.5f) },
                                        ) { change, dy ->
                                            change.consume()
                                            tracker.addPosition(change.uptimeMillis, change.position)
                                            val span = (fullPx - collapsedPx()).coerceAtLeast(1)
                                            val to = (open.value + dy / span).coerceIn(0f, 1f)
                                            openScope.launch { open.snapTo(to) }
                                        }
                                    }
                                    .padding(top = 18.dp, bottom = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.width(332.dp)
                                        .clipToBounds()
                                        .layout { m, cons ->
                                            val p = m.measure(cons.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                                            val low = collapsedPx().coerceAtMost(p.height)
                                            val h = (low + (p.height - low) * open.value).toInt()
                                            layout(p.width, h) { p.place(0, 0) }
                                        }
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth().onSizeChanged { fullPx = it.height },
                                        verticalArrangement = Arrangement.spacedBy(if (expanded) 12.dp else 16.dp),
                                    ) {
                                        all.chunked(4).forEachIndexed { r, row ->
                                            Row(
                                                Modifier.fillMaxWidth()
                                                    .onGloballyPositioned { rowBottoms[r] = (it.positionInParent().y + it.size.height).toInt() }
                                                    .graphicsLayer { alpha = if (r < 2) 1f else open.value }
                                            ) {
                                                row.forEach { tile ->
                                                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                                        OneUiButton(c, tile, expanded)
                                                    }
                                                }
                                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                            }
                                        }
                                    }
                                }
                                Box(
                                    Modifier.size(120.dp, 30.dp).protoTap { settle(!expanded) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.size(32.dp, 4.dp).background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(2.dp)))
                                }
                            }
                        }
                        OU_BRIGHT -> {
                            // Brightness and Extra dim.
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                OneUiSlider(c, env.brightness, MiraCLOIcons.LightMode, Color(0xFFFF7A00), 26.dp, Modifier.weight(1f), shadow = true)
                                val dim = env.tile("reduce_brightness")
                                if (dim != null) {
                                    val s = dim.protoState()
                                    OneUiRoundToggle(c, s.isOn, MiraCLOIcons.Moon) { dim.protoToggle() }
                                } else {
                                    val context = LocalContext.current
                                    var on by remember {
                                        mutableStateOf(
                                            runCatching {
                                                    Settings.Secure.getIntForUser(
                                                        context.contentResolver,
                                                        Settings.Secure.REDUCE_BRIGHT_COLORS_ACTIVATED,
                                                        0,
                                                        ActivityManager.getCurrentUser(),
                                                    ) != 0
                                                }
                                                .getOrDefault(false)
                                        )
                                    }
                                    OneUiRoundToggle(c, on, MiraCLOIcons.Moon) {
                                        on = !on
                                        runCatching {
                                            Settings.Secure.putIntForUser(
                                                context.contentResolver,
                                                Settings.Secure.REDUCE_BRIGHT_COLORS_ACTIVATED,
                                                if (on) 1 else 0,
                                                ActivityManager.getCurrentUser(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        OU_VOLUME -> {
                            // Media volume and Mute.
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                OneUiSlider(c, env.volume, MiraCLOIcons.MusicNote, Color(0xFF3A3A3C), 28.dp, Modifier.weight(1f))
                                val context = LocalContext.current
                                val am = remember(context) { context.getSystemService(AudioManager::class.java) }
                                val muted = env.volume.value == 0f
                                OneUiRoundToggle(c, muted, MiraCLOIcons.VolumeOff) {
                                    am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                                }
                            }
                        }
                        OU_MEDIA -> {
                            // Media pill.
                            val media = env.media("Play music", "")
                            Row(
                                Modifier.fillMaxWidth()
                                    .height(72.dp)
                                    .background(c.card, RoundedCornerShape(36.dp))
                                    .protoTap {
                                        media.open?.invoke()
                                            ?: env.open(
                                                android.content.Intent.makeMainSelectorActivity(
                                                        android.content.Intent.ACTION_MAIN,
                                                        android.content.Intent.CATEGORY_APP_MUSIC,
                                                    )
                                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                    }
                                    .padding(start = 32.dp, end = 22.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                                    MiraProtoGlyph(MiraCLOIcons.MusicNote, c.fg, 17.dp)
                                    Text(
                                        if (media.idle) "Play music" else listOf(media.song, media.artist).filter { it.isNotEmpty() }.joinToString(" · "),
                                        color = c.fg,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!media.idle) {
                                    MediaKey(if (media.playing) MiraProtoIcons.Pause else MiraProtoIcons.Play, 24.dp, c.fg, media.playPause)
                                }
                                MiraProtoGlyph(MiraCLOIcons.CastConnected, c.fg, 26.dp)
                            }
                        }
                        OU_CARDS -> {
                            if (!expanded) {
                                val cards =
                                    listOf(
                                        Triple(MiraCLOIcons.CastConnected, "Screen mirroring" to "Mirror screen", Settings.ACTION_CAST_SETTINGS),
                                        Triple(MiraProtoIcons.Nightlight, "Modes" to modesSummary(env), Settings.ACTION_ZEN_MODE_SETTINGS),
                                        Triple(MiraCLOIcons.Devices, "Nearby devices" to "", Settings.ACTION_BLUETOOTH_SETTINGS),
                                        Triple(MiraProtoIcons.Home, "Device control" to "Smart home", "android.settings.DEVICE_CONTROLS_SETTINGS"),
                                    )
                                cards.chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        row.forEach { (icon, text, action) ->
                                            Row(
                                                Modifier.weight(1f)
                                                    .height(72.dp)
                                                    .background(c.card, RoundedCornerShape(36.dp))
                                                    .protoTap { env.open(miraProtoIntent(action)) }
                                                    .padding(start = 11.dp, end = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                Box(Modifier.size(50.dp).background(c.off, CircleShape), contentAlignment = Alignment.Center) {
                                                    MiraProtoGlyph(icon, c.fg, 26.dp)
                                                }
                                                Column(Modifier.weight(1f)) {
                                                    Text(text.first, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    if (text.second.isNotEmpty()) {
                                                        Text(text.second, color = c.fg2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            }
        if (LocalMiraLandscape.current) {
            blocksColumn(blockLayout.shown.filter { it != OU_TILES }, 17.dp, 122.dp, (MIRA_LAND_HEIGHT - 122f).dp)
            if (OU_TILES in blockLayout.shown) {
                blocksColumn(listOf(OU_TILES), 427.dp, 36.dp, (MIRA_LAND_HEIGHT - 36f).dp)
            }
        } else {
            blocksColumn(blockLayout.shown, 17.dp, 122.dp, (866 - 122).dp)
        }
    }
}

private const val OU_PILLS = "pills"
private const val OU_TILES = "tiles"
private const val OU_BRIGHT = "brightness"
private const val OU_VOLUME = "volume"
private const val OU_MEDIA = "media"
private const val OU_CARDS = "cards"

/** The One UI widgets edit mode can move (sliders, media and the cards can also be hidden). */
private val OneUiBlocks =
    listOf(
        MiraEditBlock(OU_PILLS, "Large tiles", removable = false),
        MiraEditBlock(OU_TILES, "Tile area", removable = false),
        MiraEditBlock(OU_BRIGHT, "Brightness and Extra dim", removable = true),
        MiraEditBlock(OU_VOLUME, "Volume and mute", removable = true),
        MiraEditBlock(OU_MEDIA, "Media", removable = true),
        MiraEditBlock(OU_CARDS, "Shortcut cards", removable = true),
    )

@Composable
private fun modesSummary(env: MiraProtoEnv): String {
    val dnd = env.tile("dnd") ?: env.tile("modes_dnd") ?: return "Off"
    return if (dnd.protoState().isOn) "On" else "Off"
}

@Composable
private fun OneUiHeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onTap: () -> Unit) {
    val label =
        when (icon) {
            MiraCLOIcons.Edit -> "Edit"
            MiraCLOIcons.Power -> "Power menu"
            MiraCLOIcons.Settings -> "Settings"
            else -> null
        }
    Box(Modifier.size(44.dp).protoTap(onTap = onTap).protoLabel(label), contentAlignment = Alignment.Center) {
        MiraProtoGlyph(icon, Color.White, 23.dp)
    }
}

@Composable
private fun OneUiWidePill(c: MiraOneUiInk, tile: TileViewModel, modifier: Modifier) {
    // One UI pill; inside it Android's real large tile (icon bubble, name, status).
    val skin = remember(c) {
        MiraTileSkin(
            shape = RoundedCornerShape(36.dp),
            onBackground = Color.Transparent, offBackground = Color.Transparent,
            onContent = OneUiInkOn, offContent = Color.White,
            bubbleOn = Color.White, bubbleOff = c.off,
            label = c.fg,
            bubbleAlways = true,
            bubbleSize = 50.dp,
            bubbleInset = 8.dp,
        )
    }
    Box(modifier.height(72.dp).background(c.card, RoundedCornerShape(36.dp))) {
        MiraRealTile(tile, skin, iconOnly = false, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun OneUiButton(c: MiraOneUiInk, tile: TileViewModel, labels: Boolean) {
    val s = tile.protoState()
    val on = s.isOn
    val skin = remember(c) {
        MiraTileSkin(
            shape = CircleShape,
            onBackground = Color.Transparent, offBackground = Color.Transparent,
            onContent = OneUiInkOn, offContent = Color.White,
        )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(48.dp).background(if (on) Color.White else c.off, CircleShape)) {
            MiraRealTile(tile, skin, iconOnly = true, modifier = Modifier.fillMaxSize())
        }
        if (labels) {
            Text(
                s.label?.toString().orEmpty(),
                color = c.fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 80.dp),
            )
        }
    }
}

@Composable
private fun OneUiRoundToggle(
    c: MiraOneUiInk,
    on: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onTap: () -> Unit,
) {
    Box(
        Modifier.size(64.dp).background(if (on) Color.White else c.card, CircleShape).protoTap(onTap = onTap),
        contentAlignment = Alignment.Center,
    ) {
        MiraProtoGlyph(icon, if (on) OneUiInkOn else Color.White, 28.dp)
    }
}

/** One UI slider: the white fill always covers the round end with the icon, and grows from there. */
@Composable
private fun OneUiSlider(
    c: MiraOneUiInk,
    level: MiraProtoLevel,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    ink: Color,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    shadow: Boolean = false,
) {
    val local = rememberProtoLocal(level.value)
    val v = local.floatValue
    val inset = with(LocalDensity.current) { 32.dp.toPx() }
    Box(
        modifier
            .height(64.dp)
            .background(c.track, RoundedCornerShape(32.dp))
            .protoSliderInput({ v }, false, { local.floatValue = it }, level.set, inset = inset)
    ) {
        Box(
            Modifier.fillMaxHeight()
                .layout { m, cons ->
                    val h = cons.maxHeight
                    val w = (h + (cons.maxWidth - h) * v.coerceIn(0f, 1f)).toInt().coerceIn(0, cons.maxWidth)
                    val p = m.measure(Constraints.fixed(w, h))
                    layout(w, h) { p.place(0, 0) }
                }
                .then(if (shadow) Modifier.shadow(6.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f)) else Modifier)
                .background(Color.White, RoundedCornerShape(32.dp))
        )
        MiraProtoGlyph(
            icon,
            ink,
            iconSize,
            Modifier.align(Alignment.CenterStart).padding(start = if (iconSize > 26.dp) 20.dp else 22.dp),
        )
    }
}

/** One UI edit: the quick panel buttons (− to remove, drag to move) and the available ones. */
@Composable
private fun OneUiEdit(
    c: MiraOneUiInk,
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
    val density = LocalDensity.current
    val cellW = with(density) { (342.dp / 4).toPx() }
    val cellH = with(density) { 80.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.offset(24.dp, 48.dp).width(342.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Edit", color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Drag to move", color = c.fg3, fontSize = 12.sp)
        }
        Column(
            Modifier.offset(16.dp, 96.dp).width(358.dp).height((866 - 96 - 100).dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(c.card, RoundedCornerShape(30.dp))
                    .padding(start = 8.dp, top = 16.dp, end = 8.dp, bottom = 14.dp)
            ) {
                Text("Quick panel", color = c.fg3, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp))
                draft.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(80.dp)) {
                        row.forEach { spec ->
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
                                    OneUiEditButton(
                                        c,
                                        tile,
                                        add = false,
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
            Column(
                Modifier.fillMaxWidth()
                    .background(c.card, RoundedCornerShape(30.dp))
                    .padding(start = 8.dp, top = 16.dp, end = 8.dp, bottom = 14.dp)
            ) {
                Text("Available buttons", color = c.fg3, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp))
                removed.mapNotNull { bySpec[it] }.filter { it.protoKey() !in reserved }.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(80.dp)) {
                        row.forEach { tile ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                OneUiEditButton(c, tile, add = true, onBadge = {
                                    removed = removed - tile.spec.spec
                                    draft = draft + tile.spec.spec
                                })
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 34.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.weight(1f).height(52.dp).background(c.card, RoundedCornerShape(26.dp)).protoTap {
                    layout.reset()
                    draft = layout.shown(env.tiles, reserved).map { it.spec.spec }
                    removed = emptySet()
                },
                contentAlignment = Alignment.Center,
            ) {
                Text("Reset", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier.weight(1f).height(52.dp).background(OneUiBlue, RoundedCornerShape(26.dp)).protoTap {
                    layout.save(draft, removed)
                    onDone()
                },
                contentAlignment = Alignment.Center,
            ) {
                Text("Done", color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OneUiEditButton(
    c: MiraOneUiInk,
    tile: TileViewModel,
    add: Boolean,
    onBadge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = tile.protoState()
    Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(52.dp)) {
            Box(modifier.fillMaxSize().background(c.off, CircleShape), contentAlignment = Alignment.Center) {
                MiraProtoTileIcon(s, Color.White, 24.dp)
            }
            Box(
                Modifier.align(Alignment.TopEnd)
                    .offset(4.dp, (-4).dp)
                    .size(22.dp)
                    .background(if (add) OneUiBlue else Color(0xFFFF4A4A), CircleShape)
                    .protoTap(onTap = onBadge),
                contentAlignment = Alignment.Center,
            ) {
                MiraProtoGlyph(if (add) MiraCLOIcons.Add else MiraProtoIcons.Minus, Color.White, 16.dp)
            }
        }
        Text(s.label?.toString().orEmpty(), color = c.fg, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 80.dp))
    }
}

/** "3:16" and "Mon, Nov 2" for the One UI and Stock headers. */
internal fun miraProtoClock(context: android.content.Context): Pair<String, String> {
    val now = Date()
    val time = android.text.format.DateFormat.getTimeFormat(context).format(now).replace(Regex("\\s?[AaPp][Mm]$"), "")
    val date = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)
    return time to date
}
