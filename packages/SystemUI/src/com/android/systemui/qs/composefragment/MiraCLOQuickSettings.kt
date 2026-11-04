/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import com.android.systemui.miraclo.LocalMiraCLOGlow
import com.android.systemui.miraclo.miracloCornerGlow
import com.android.systemui.miraclo.LocalMiraCLORefraction
import com.android.systemui.miraclo.miracloRefraction
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.settingslib.volume.domain.interactor.AudioVolumeInteractor
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.systemui.brightness.domain.model.GammaBrightness
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.brightness.ui.viewmodel.Drag
import com.android.systemui.qs.panels.ui.compose.GridLayout
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults
import com.android.systemui.qs.panels.ui.compose.infinitegrid.InfiniteGridLayout
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalMiraCLOLiquidGlass
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalMiraCLOLook
import com.android.systemui.qs.panels.ui.compose.infinitegrid.MiraCLOLook
import com.android.systemui.qs.panels.ui.compose.infinitegrid.MiraCLOLiquidHighlight
import com.android.systemui.qs.panels.ui.compose.infinitegrid.MiraCLOLiquidRim
import com.android.systemui.qs.panels.ui.compose.infinitegrid.MiraCLOSolidTile
import com.android.systemui.qs.panels.ui.compose.infinitegrid.MiraCLOColorOSGap
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.res.R
import kotlinx.coroutines.launch

/** Tile specs that MIRACLO pins to the fixed wide slots, in order of preference. */
private val MIRACLO_WIFI_SPECS = listOf("internet", "wifi")
private val MIRACLO_BT_SPECS = listOf("bt", "bluetooth")

private val MiraCLOGap = MiraCLOColorOSGap

/** Sun-icon colour at full brightness (the orange of the 🔆 emoji). */
private val MiraCLOBrightnessFull = Color(0xFFFF9F0A)

/** Slider position treated as "full" (allows for rounding of the brightness value). */
private const val MIRACLO_FULL_THRESHOLD = 0.995f

/** Matches the media card's own corner radius (Media.kt clips cards to 32dp). */
internal val MiraCLOMediaShape = RoundedCornerShape(32.dp)

/**
 * MIRACLO (ColorOS 17 style) expanded Quick Settings.
 *
 * ```
 * [ Wi-Fi         ][ Bluetooth     ]
 * [ Media        ][ ☀ ][ 🔊 ]          tall brightness + media volume sliders
 * [ My devices    ][ 🔔  📳  🔕     ]   Bluetooth devices + ringer switch
 * ( ) ( ) ( ) ( )                       every other tile: round, label below
 * ```
 *
 * The fixed tiles are the real tile view models, so they behave exactly like their stock
 * versions. Anything missing on a device (e.g. no mobile data tile) simply leaves its slot out.
 */
@Composable
internal fun ContentScope.MiraCLOQuickSettingsLayout(
    tiles: List<TileViewModel>,
    gridLayout: GridLayout,
    infiniteGridLayout: InfiniteGridLayout,
    brightnessViewModel: BrightnessSliderViewModel,
    audioVolumeInteractor: AudioVolumeInteractor,
    showMedia: Boolean,
    media: @Composable (Modifier) -> Unit,
    mediaHost: @Composable (Modifier) -> Unit = {},
    listening: () -> Boolean,
    modifier: Modifier = Modifier,
    onOpen: (Intent) -> Unit = {},
) {
    fun pick(specs: List<String>): TileViewModel? =
        specs.firstNotNullOfOrNull { spec -> tiles.firstOrNull { it.spec.spec == spec } }

    val wifi = pick(MIRACLO_WIFI_SPECS)
    val bt = pick(MIRACLO_BT_SPECS)
    val fixed = listOfNotNull(wifi, bt)
    val rest = remember(tiles, fixed) { tiles.filterNot { it in fixed } }

    // The fixed tiles are not part of a grid, so make them listen ourselves.
    TileListener(fixed, listening)

    // Follows the ColorOS tile height (66dp) set in CommonTileDefaults for this style.
    val tileHeight = CommonTileDefaults.TileHeight
    val glass = miracloGlass()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = spacedBy(MiraCLOGap)) {
        // Row 1: Wi-Fi + Bluetooth
        if (wifi != null || bt != null) {
            Row(horizontalArrangement = spacedBy(MiraCLOGap)) {
                listOfNotNull(wifi, bt).forEach { tile ->
                    with(infiniteGridLayout) {
                        MiraCLOWideTile(tile, listening, Modifier.weight(1f))
                    }
                }
                if (wifi == null || bt == null) Box(Modifier.weight(1f))
            }
        }

        // Row 2: media + tall brightness + tall volume
        Row(
            horizontalArrangement = spacedBy(MiraCLOGap),
            modifier = Modifier.height(tileHeight * 2 + MiraCLOGap),
        ) {
            Box(
                modifier =
                    Modifier.weight(2f)
                        .fillMaxHeight()
                        // While media plays, the player card is the whole slot: no glass behind
                        // it, same corner radius as the card (32dp) so there is a single outline.
                        .then(
                            if (showMedia) Modifier
                            else Modifier.glassSurface(glass, MiraCLOMediaShape)
                        )
                        // MIRACLO Liquid Glass glow: a faint light in the card's bottom corner.
                        .miracloCornerGlow(glass.liquid && LocalMiraCLOGlow.current),
                contentAlignment = Alignment.Center,
            ) {
                mediaHost(Modifier.matchParentSize())
                if (showMedia) {
                    media(Modifier.fillMaxSize())
                } else {
                    Text(
                        text = "No media playing",
                        color = glass.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            MiraCLOBrightnessSlider(
                viewModel = brightnessViewModel,
                glass = glass,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            MiraCLOVolumeSlider(
                audioVolumeInteractor = audioVolumeInteractor,
                glass = glass,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        // Row 3: My devices (Bluetooth devices) + ringer switch
        Row(horizontalArrangement = spacedBy(MiraCLOGap)) {
            MiraCLOMyDevices(
                glass = glass,
                height = tileHeight,
                onClick = { onOpen(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                modifier = Modifier.weight(1f),
            )
            MiraCLORingerSwitch(
                audioVolumeInteractor = audioVolumeInteractor,
                glass = glass,
                height = tileHeight,
                modifier = Modifier.weight(1f),
            )
        }

        // Everything else: round icon tiles with labels (the grid handles paging + listening)
        // ColorOS 17: the round tiles start 22dp under the cards and spread 8dp wider on each
        // side than the cards above them.
        if (rest.isNotEmpty()) {
            with(gridLayout) {
                TileGrid(
                    tiles = rest,
                    modifier =
                        Modifier.padding(top = 6.dp).layout { measurable, constraints ->
                            val extra = 16.dp.roundToPx()
                            val width = constraints.maxWidth + extra
                            val placeable =
                                measurable.measure(
                                    constraints.copy(minWidth = width, maxWidth = width)
                                )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.place(-extra / 2, 0)
                            }
                        },
                    listening = listening,
                    enableRevealEffect = false,
                )
            }
        }
    }
}

/**
 * ColorOS 17 "My devices": a glass pill with an icon bubble that opens the Bluetooth device list.
 */
@Composable
internal fun MiraCLOMyDevices(
    glass: MiraCLOGlass,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier =
            modifier
                .height(height)
                .glassSurface(glass, shape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "My Devices" }
                .padding(horizontal = 8.dp),
        horizontalArrangement = spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(height - 18.dp)
                    .clip(CircleShape)
                    .background(
                        if (glass.dark) Color.White.copy(alpha = 0.12f)
                        else Color.Black.copy(alpha = 0.06f)
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                MiraCLOIcons.Devices,
                contentDescription = null,
                tint = glass.iconOff,
                modifier = Modifier.size(24.dp),
            )
        }
        Column {
            Text(
                text = "My Devices",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = "Tap to add",
                color = glass.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

/** Colours shared by the MIRACLO glass cards, matching the tile glass in Tile.kt. */
internal data class MiraCLOGlass(
    val fill: Color,
    val iconOff: Color,
    val accent: Color,
    val secondaryText: Color,
    val dark: Boolean,
    val liquid: Boolean,
    val look: Int = MiraCLOLook.COLOROS,
)

@Composable
internal fun miracloGlass(): MiraCLOGlass {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = Color(0xFF1677FF)
    val liquid = LocalMiraCLOLiquidGlass.current
    val look = LocalMiraCLOLook.current
    // Each look has its own material, matching the tiles in Tile.kt.
    val fill =
        when (look) {
            MiraCLOLook.IOS ->
                if (dark) Color(0xFF787A86).copy(alpha = if (liquid) 0.30f else 0.36f)
                else Color.White.copy(alpha = if (liquid) 0.50f else 0.70f)
            MiraCLOLook.ONEUI -> Color.White.copy(alpha = if (dark) 0.13f else 0.22f)
            else ->
                if (dark) (if (liquid) Color.White.copy(alpha = 0.10f) else MiraCLOSolidTile)
                else Color.White.copy(alpha = if (liquid) 0.55f else 0.72f)
        }
    return MiraCLOGlass(
        fill = fill,
        iconOff = onSurface,
        accent = accent,
        secondaryText = onSurface.copy(alpha = 0.70f),
        dark = dark,
        liquid = liquid,
        look = look,
    )
}

/** The glass rim. Created once: building a new Brush per frame showed up during QS expansion. */
private val MiraCLOGlassRim =
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f)))

@Composable
internal fun Modifier.glassSurface(glass: MiraCLOGlass, shape: Shape): Modifier =
    if (glass.look == MiraCLOLook.ONEUI) {
        // One UI: flat tinted cards, no rim.
        this.clip(shape).background(glass.fill, shape)
    } else if (glass.look == MiraCLOLook.COLOROS && glass.liquid) {
        // ColorOS: frosted card lit along its contour.
        // ColorOS: frosted glass, a thin bright rim and a soft light along the top-left edge.
        this.clip(shape)
            .background(glass.fill, shape)
            .background(MiraCLOLiquidHighlight, shape)
            .border(1.dp, Color.White.copy(alpha = if (glass.dark) 0.18f else 0.95f), shape)
    } else if (glass.liquid && LocalMiraCLORefraction.current) {
        // SurfaceFlinger draws the refracting glass and its rim; only the tint is drawn here.
        this.miracloRefraction(shape, true).clip(shape).background(glass.fill, shape)
    } else if (glass.liquid) {
        this.clip(shape)
            .background(glass.fill, shape)
            .background(MiraCLOLiquidHighlight, shape)
            .border(1.3.dp, MiraCLOLiquidRim, shape)
    } else if (glass.dark) {
        // Dark, no Liquid Glass: flat solid surface, no rim.
        this.clip(shape).background(glass.fill, shape)
    } else {
        this.clip(shape).background(glass.fill, shape).border(1.dp, MiraCLOGlassRim, shape)
    }

/**
 * A tall pill slider: the white fill rises from the bottom. Dragging is relative to where the
 * finger lands, like ColorOS. While dragging, the shade is asked not to steal the gesture.
 */
@Composable
private fun MiraCLOVerticalSlider(
    fraction: Float,
    @DrawableRes iconRes: Int,
    iconTint: Color,
    label: String,
    glass: MiraCLOGlass,
    onChange: (Float) -> Unit,
    onStop: (Float) -> Unit,
    modifier: Modifier = Modifier,
    fullIconTint: Color? = null,
    vertical: Boolean = true,
    oneUiIconTint: Color = Color(0xFF3A3A3C),
    vector: ImageVector? = null,
    vectorTint: Color = Color(0xFF4A4A4F),
) {
    // One UI: the white fill always covers a round end with the icon on it, and grows from there.
    val oneUi = glass.look == MiraCLOLook.ONEUI && !vertical
    val view = LocalView.current
    var local by remember { mutableFloatStateOf(fraction) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(fraction, dragging) { if (!dragging) local = fraction }
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnStop by rememberUpdatedState(onStop)
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier =
            modifier
                .glassSurface(glass, shape)
                .semantics { contentDescription = label }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        dragging = true
                        down.consume()
                        val start = local
                        // MIRACLO: tall sliders fill upwards, wide ones (4x1) left to right.
                        val length =
                            (if (vertical) size.height else size.width).coerceAtLeast(1)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta =
                                if (vertical) (down.position.y - change.position.y) / length
                                else (change.position.x - down.position.x) / length
                            local = (start + delta).coerceIn(0f, 1f)
                            currentOnChange(local)
                            change.consume()
                        }
                        currentOnStop(local)
                        dragging = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
    ) {
        Box(
            modifier =
                if (vertical) {
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(local.coerceIn(0.001f, 1f))
                        .background(Color.White)
                } else if (oneUi) {
                    Modifier.align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .layout { measurable, constraints ->
                            val h = constraints.maxHeight
                            val w =
                                (h + (constraints.maxWidth - h) * local.coerceIn(0f, 1f))
                                    .toInt()
                                    .coerceIn(0, constraints.maxWidth)
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(w, h) { placeable.place(0, 0) }
                        }
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White)
                } else {
                    Modifier.align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(local.coerceIn(0.001f, 1f))
                        .background(Color.White)
                }
        )
        // MIRACLO: the looks use their own glyphs (ColorOS: a grey sun and blue headphones; the
        // icon sits on the white fill, or on the glass when the level is low).
        if (vector != null && glass.look != MiraCLOLook.ONEUI) {
            Icon(
                vector,
                contentDescription = null,
                tint = if (local > 0.16f) vectorTint else glass.iconOff,
                modifier =
                    if (vertical) {
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).size(26.dp)
                    } else {
                        Modifier.align(Alignment.CenterStart).padding(start = 20.dp).size(24.dp)
                    },
            )
            return@Box
        }
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint =
                when {
                    oneUi -> oneUiIconTint
                    fullIconTint != null && local >= MIRACLO_FULL_THRESHOLD -> fullIconTint
                    local > 0.18f -> iconTint
                    else -> glass.iconOff
                },
            modifier =
                if (vertical) {
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(26.dp)
                } else {
                    Modifier.align(Alignment.CenterStart).padding(start = 20.dp).size(24.dp)
                },
        )
    }
}

@Composable
internal fun MiraCLOBrightnessSlider(
    viewModel: BrightnessSliderViewModel,
    glass: MiraCLOGlass,
    modifier: Modifier = Modifier,
    vertical: Boolean = true,
) {
    val gamma = viewModel.currentBrightness.value
    val min = viewModel.minBrightness.value
    val max = viewModel.maxBrightness.value
    val scope = rememberCoroutineScope()
    val span = (max - min).coerceAtLeast(1)
    val fraction =
        if (gamma < 0) 0.5f else ((gamma - min).toFloat() / span).coerceIn(0f, 1f)
    fun toGamma(f: Float) = GammaBrightness((min + f * span).toInt().coerceIn(min, max))

    MiraCLOVerticalSlider(
        fraction = fraction,
        iconRes = BrightnessSliderViewModel.getIconForPercentage(fraction * 100f),
        iconTint = Color(0xFF1A1A1A),
        // At full brightness only the sun icon turns orange, like the 🔆 emoji.
        fullIconTint = MiraCLOBrightnessFull,
        oneUiIconTint = Color(0xFFFF7A00),
        vector = MiraCLOIcons.LightMode,
        vectorTint = if (glass.look == MiraCLOLook.IOS) Color(0xFFFFC400) else Color(0xFF4A4A4F),
        label = "Brightness",
        glass = glass,
        onChange = { f -> scope.launch { viewModel.onDrag(Drag.Dragging(toGamma(f))) } },
        onStop = { f -> scope.launch { viewModel.onDrag(Drag.Stopped(toGamma(f))) } },
        modifier = modifier,
        vertical = vertical,
    )
}

@Composable
internal fun MiraCLOVolumeSlider(
    audioVolumeInteractor: AudioVolumeInteractor,
    glass: MiraCLOGlass,
    modifier: Modifier = Modifier,
    vertical: Boolean = true,
) {
    val stream = remember { AudioStream(AudioManager.STREAM_MUSIC) }
    val model by
        remember(audioVolumeInteractor) { audioVolumeInteractor.getAudioStream(stream) }
            .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val current = model
    val min = current?.minVolume ?: 0
    val max = current?.maxVolume ?: 1
    val span = (max - min).coerceAtLeast(1)
    val fraction = current?.let { ((it.volume - min).toFloat() / span).coerceIn(0f, 1f) } ?: 0f
    fun toVolume(f: Float) = (min + Math.round(f * span)).coerceIn(min, max)

    MiraCLOVerticalSlider(
        fraction = fraction,
        iconRes =
            if (current?.isMuted == true || fraction == 0f) R.drawable.ic_volume_media_mute
            else R.drawable.ic_volume_media,
        iconTint = glass.accent,
        vector = if (glass.look == MiraCLOLook.IOS) MiraCLOIcons.VolumeUp else MiraCLOIcons.Headphones,
        vectorTint = Color(0xFF1A7CFF),
        label = "Media volume",
        glass = glass,
        onChange = { f -> scope.launch { audioVolumeInteractor.setVolume(stream, toVolume(f)) } },
        onStop = { f -> scope.launch { audioVolumeInteractor.setVolume(stream, toVolume(f)) } },
        modifier = modifier,
        vertical = vertical,
    )
}

/**
 * ColorOS-style colour for each sound mode: ring = blue, vibrate = orange, silent = red.
 * Light panel: coloured bubble with a white icon. Dark panel: white bubble with a coloured icon,
 * the same pairing the active QS tiles use.
 */
internal fun miracloRingerColor(mode: Int): Color =
    when (mode) {
        AudioManager.RINGER_MODE_NORMAL -> Color(0xFF1A7CFF)
        AudioManager.RINGER_MODE_VIBRATE -> Color(0xFFFF3B30)
        else -> Color(0xFFFF3B30)
    }

@Composable
internal fun MiraCLORingerSwitch(
    audioVolumeInteractor: AudioVolumeInteractor,
    glass: MiraCLOGlass,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val ringerMode by audioVolumeInteractor.ringerMode.collectAsStateWithLifecycle()
    val modes =
        listOf(
            Triple(AudioManager.RINGER_MODE_NORMAL, R.drawable.ic_volume_ringer, "Ring"),
            Triple(AudioManager.RINGER_MODE_VIBRATE, R.drawable.ic_volume_ringer_vibrate, "Vibrate"),
            Triple(AudioManager.RINGER_MODE_SILENT, R.drawable.ic_volume_ringer_mute, "Silent"),
        )
    val shape = RoundedCornerShape(percent = 50)
    val bubble = height - 16.dp

    Row(
        modifier =
            modifier.height(height).glassSurface(glass, shape).padding(horizontal = 4.dp),
        horizontalArrangement = spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEach { (mode, icon, label) ->
            val selected = ringerMode.value == mode
            val modeColor = miracloRingerColor(mode)
            Box(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier.size(bubble)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !selected -> Color.Transparent
                                    glass.dark -> Color.White
                                    else -> modeColor
                                }
                            )
                            .semantics { contentDescription = label }
                            .pointerInput(mode) {
                                awaitEachGesture {
                                    awaitFirstDown().consume()
                                    val up = waitForUpOrCancellation()
                                    if (up != null) {
                                        up.consume()
                                        audioManager?.setRingerModeInternal(mode)
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint =
                            when {
                                !selected -> glass.secondaryText
                                glass.dark -> modeColor
                                else -> Color.White
                            },
                        modifier = Modifier.size(if (selected) 24.dp else 20.dp),
                    )
                }
            }
        }
    }
}
