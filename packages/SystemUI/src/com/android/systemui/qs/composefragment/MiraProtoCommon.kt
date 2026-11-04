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

import com.android.systemui.qs.panels.ui.compose.infinitegrid.Tile
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.systemui.animation.Expandable
import com.android.compose.animation.rememberExpandableController
import com.android.compose.animation.miracloExpandable
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.service.quicksettings.Tile
import android.telephony.TelephonyManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.settingslib.volume.domain.interactor.AudioVolumeInteractor
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.systemui.brightness.domain.model.GammaBrightness
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.brightness.ui.viewmodel.Drag
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * MIRACLO Quick Settings for the separate (dual) shade, drawn exactly as the approved prototypes
 * (Stock, ColorOS, iOS and One UI, each 390dp wide). Rules:
 * - Only the Stock style follows Material You. ColorOS, iOS and One UI use their own fixed
 *   colours from the prototypes and only switch between their own dark and light palettes with
 *   the system dark theme.
 * - The real blur sits behind a fixed tint at the prototype's opacity, so the panel looks the
 *   same on any wallpaper.
 */

/**
 * The prototypes are drawn on a 390 x 800 dp portrait phone. Portrait: 390 design dp map to the
 * screen width (unchanged). Landscape: the panel sits on the right half at its normal size, like
 * the notification shade on the left, and scrolls (it no longer shrinks to fit the height).
 */
@Composable
internal fun MiraProtoFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val outer = LocalDensity.current
        val landscape =
            constraints.hasBoundedHeight && constraints.maxWidth > constraints.maxHeight
        if (landscape) {
            val panelPx = kotlin.math.min(constraints.maxWidth / 2f, MIRA_PROTO_WIDTH * outer.density)
            val scaled = Density(panelPx / MIRA_PROTO_WIDTH, outer.fontScale)
            Box(
                Modifier.fillMaxSize()
                    .padding(end = androidx.compose.ui.unit.Dp(12f)),
                contentAlignment = androidx.compose.ui.Alignment.TopEnd,
            ) {
                CompositionLocalProvider(LocalDensity provides scaled) {
                    Box(
                        Modifier.fillMaxHeight()
                            .width(androidx.compose.ui.unit.Dp(MIRA_PROTO_WIDTH))
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    ) {
                        Box(
                            Modifier.width(androidx.compose.ui.unit.Dp(MIRA_PROTO_WIDTH))
                                .height(androidx.compose.ui.unit.Dp(MIRA_PROTO_HEIGHT)),
                            content = content,
                        )
                    }
                }
            }
        } else {
            val byWidth = constraints.maxWidth / MIRA_PROTO_WIDTH
            val byHeight = constraints.maxHeight / MIRA_PROTO_HEIGHT
            val k = if (constraints.hasBoundedHeight) kotlin.math.min(byWidth, byHeight) else byWidth
            val scaled = Density(k, outer.fontScale)
            CompositionLocalProvider(LocalDensity provides scaled) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                    Box(
                        Modifier.fillMaxHeight()
                            .width(androidx.compose.ui.unit.Dp(MIRA_PROTO_WIDTH)),
                        content = content,
                    )
                }
            }
        }
    }
}

internal const val MIRA_PROTO_WIDTH = 390f
internal const val MIRA_PROTO_HEIGHT = 800f

/** Settings.Secure "miraclo_qs_style": 0 Stock, 1 ColorOS, 2 iOS, 4 One UI. */
internal object MiraProtoStyle {
    const val STOCK = 0
    const val COLOROS = 1
    const val IOS = 2
    const val ONEUI = 4
}

// ---------------------------------------------------------------- tiles

internal val QSTile.State.isOn: Boolean
    get() = state == Tile.STATE_ACTIVE

/** The tile's live state. */
@Composable
internal fun TileViewModel.protoState(): QSTile.State {
    val s by state.collectAsStateWithLifecycle(initialValue = currentState)
    return s
}

/** The tile's own icon, tinted. */
@Composable
internal fun MiraProtoTileIcon(state: QSTile.State, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = state.icon ?: state.iconSupplier?.get()
    val drawable = remember(icon) { runCatching { icon?.getDrawable(context)?.mutate() }.getOrNull() }
    if (drawable != null) {
        Icon(
            painter = rememberDrawablePainter(drawable),
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(size),
        )
    }
}

/** A tap toggles the tile (Internet and Bluetooth toggle instead of opening their dialog). */
internal fun TileViewModel.protoToggle(from: Expandable? = null) {
    if (currentState.handlesSecondaryClick) toggleClick() else mainClick(from)
}

/** Opens the tile's details (dialog or panel), as the chevron does on ColorOS. */
internal fun TileViewModel.protoDetails(from: Expandable? = null) = mainClick(from)

/** Touch and hold opens the tile's settings page. */
internal fun TileViewModel.protoSettings(from: Expandable? = null) = settingsClick(from)

/**
 * The tile's own launch anchor: Android's Expandable laid invisibly over the element (transparent,
 * same bounds), so dialogs and settings open from the tile with the system animation. The design
 * is untouched.
 */
@Composable
internal fun Modifier.protoAnchor(key: Any, shape: Shape = RoundedCornerShape(20.dp)): Pair<Modifier, Expandable> {
    val expandable = remember(key) { Expandable(mutableSetOf()) }
    val controller = rememberExpandableController(color = Color.Transparent, shape = shape)
    return this.miracloExpandable(expandable, controller) to expandable
}

/** Tap and long-press for a tile control, launching from the tile itself. */
@Composable
internal fun Modifier.protoTileGestures(
    tile: TileViewModel,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
): Modifier {
    val (anchored, from) = protoAnchor(tile.spec, shape)
    return if (!enabled) anchored
    else
        anchored.pointerInput(tile.spec) {
            detectTapGestures(
                onTap = { tile.protoToggle(from) },
                onLongPress = { tile.protoSettings(from) },
            )
        }
}

/** A tap that opens the tile's details, launching from the tapped element. */
@Composable
internal fun Modifier.protoDetailsTap(
    tile: TileViewModel,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier {
    val (anchored, from) = protoAnchor("details:" + tile.spec, shape)
    return if (!enabled) this
    else anchored.pointerInput(tile.spec) { detectTapGestures(onTap = { tile.protoDetails(from) }) }
}

internal fun Modifier.protoTap(enabled: Boolean = true, onTap: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) }

/** Normalised tile spec ("internet" and "wifi" are both Wi-Fi, and so on). */
internal fun TileViewModel.protoKey(): String =
    when (val s = spec.spec) {
        "internet", "wifi" -> "wifi"
        "cell" -> "data"
        "flashlight" -> "torch"
        "rotation" -> "rotate"
        "screenrecord" -> "record"
        "night" -> "eye"
        "qr_code_scanner" -> "qr"
        else -> s
    }

/** Tiles never shown by the MIRACLO styles (they live elsewhere). */
internal val MIRA_PROTO_EXCLUDED = setOf("wallet", "controls", "device_controls")

/** Tiles that open details from their label chevron (ColorOS / One UI). */
internal val MIRA_PROTO_DETAILS = setOf("data", "dnd", "hotspot", "record", "cast", "wifi", "bt", "modes_dnd")

// ---------------------------------------------------------------- order and hidden tiles per style

/**
 * Each style keeps its own tile order and removed tiles in Settings.Secure
 * "miraclo_qs_proto_<style>" as "spec,spec|hidden,hidden". Tiles added later in the system are
 * appended at the end.
 */
internal class MiraProtoLayout(private val context: Context, private val style: Int) {
    private val key = "miraclo_qs_proto_$style"
    var order by mutableStateOf(emptyList<String>())
        private set
    var hidden by mutableStateOf(emptySet<String>())
        private set
    var loaded = false
        private set

    fun load() {
        val raw =
            runCatching {
                    Settings.Secure.getStringForUser(
                        context.contentResolver, key, ActivityManager.getCurrentUser())
                }
                .getOrNull()
        val parts = raw?.split("|").orEmpty()
        order = parts.getOrNull(0)?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        hidden = parts.getOrNull(1)?.split(",")?.filter { it.isNotBlank() }?.toSet().orEmpty()
        loaded = true
    }

    fun save(newOrder: List<String>, newHidden: Set<String>) {
        order = newOrder
        hidden = newHidden
        runCatching {
            Settings.Secure.putStringForUser(
                context.contentResolver,
                key,
                newOrder.joinToString(",") + "|" + newHidden.joinToString(","),
                ActivityManager.getCurrentUser(),
            )
        }
    }

    fun reset() {
        order = emptyList()
        hidden = emptySet()
        runCatching {
            Settings.Secure.putStringForUser(
                context.contentResolver, key, null, ActivityManager.getCurrentUser())
        }
    }

    /** The tiles to show, in order, without [reserved] ones (drawn elsewhere by the style). */
    fun shown(tiles: List<TileViewModel>, reserved: Set<String>): List<TileViewModel> {
        val usable =
            tiles.filter {
                it.spec.spec !in MIRA_PROTO_EXCLUDED && it.protoKey() !in reserved
            }
        val bySpec = usable.associateBy { it.spec.spec }
        val ordered = order.mapNotNull { bySpec[it] }
        val rest = usable.filter { it.spec.spec !in order }
        return (ordered + rest).filter { it.spec.spec !in hidden }
    }

    /** Removed tiles that can be added back. */
    fun available(tiles: List<TileViewModel>, reserved: Set<String>): List<TileViewModel> =
        tiles.filter {
            it.spec.spec !in MIRA_PROTO_EXCLUDED &&
                it.protoKey() !in reserved &&
                it.spec.spec in hidden
        }
}

@Composable
internal fun rememberMiraProtoLayout(style: Int): MiraProtoLayout {
    val context = LocalContext.current
    return remember(style) { MiraProtoLayout(context, style).also { it.load() } }
}

/** Reorders [list] so [item] sits at [to]. */
internal fun <T> List<T>.protoMove(item: T, to: Int): List<T> {
    val from = indexOf(item)
    if (from < 0 || to == from) return this
    val out = toMutableList()
    out.removeAt(from)
    out.add(to.coerceIn(0, out.size), item)
    return out
}

// ---------------------------------------------------------------- glass surfaces

/**
 * A prototype glass surface: fixed fill, 1dp rim and the soft top-left highlight
 * (inset 1.5px 1.5px 1px -1px), with optional dashed rim in edit mode.
 */
internal fun Modifier.protoGlass(
    shape: Shape,
    fill: Color,
    rim: Color,
    highlight: Color = Color.Transparent,
    dashed: Boolean = false,
    rimWidth: Dp = 1.dp,
): Modifier =
    clip(shape)
        .background(fill, shape)
        .drawWithContent {
            drawContent()
            if (highlight.alpha > 0f) {
                val outline = shape.createOutline(size.copy(width = size.width - 1.5f, height = size.height - 1.5f), layoutDirection, this)
                translate(1.5f, 1.5f) { drawOutline(outline, highlight, style = Stroke(width = 1f)) }
            }
            if (rim.alpha > 0f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                val w = rimWidth.toPx()
                drawOutline(
                    outline,
                    rim,
                    style =
                        Stroke(
                            width = w,
                            pathEffect =
                                if (dashed)
                                    androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                                else null,
                        ),
                )
            }
        }

// ---------------------------------------------------------------- carrier

@Composable
internal fun miraProtoCarrier(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
                context.getSystemService(TelephonyManager::class.java)?.networkOperatorName
            }
            .getOrNull()
            .orEmpty()
            .lowercase()
    }
}

// ---------------------------------------------------------------- brightness and volume

/** Brightness as 0..1 with a setter; [set] (fraction, finished). */
internal class MiraProtoLevel(val value: Float, val set: (Float, Boolean) -> Unit)

@Composable
internal fun rememberProtoBrightness(viewModel: BrightnessSliderViewModel): MiraProtoLevel {
    val gamma = viewModel.currentBrightness.value
    val min = viewModel.minBrightness.value
    val max = viewModel.maxBrightness.value
    val scope = rememberCoroutineScope()
    val span = (max - min).coerceAtLeast(1)
    val fraction = if (gamma < 0) 0.5f else ((gamma - min).toFloat() / span).coerceIn(0f, 1f)
    fun toGamma(f: Float) = GammaBrightness((min + f * span).toInt().coerceIn(min, max))
    return MiraProtoLevel(fraction) { f, done ->
        scope.launch {
            viewModel.onDrag(if (done) Drag.Stopped(toGamma(f)) else Drag.Dragging(toGamma(f)))
        }
    }
}

@Composable
internal fun rememberProtoVolume(audio: AudioVolumeInteractor): MiraProtoLevel {
    val stream = remember { AudioStream(AudioManager.STREAM_MUSIC) }
    val model by
        remember(audio) { audio.getAudioStream(stream) }
            .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val current = model
    val min = current?.minVolume ?: 0
    val max = current?.maxVolume ?: 1
    val span = (max - min).coerceAtLeast(1)
    val fraction = current?.let { ((it.volume - min).toFloat() / span).coerceIn(0f, 1f) } ?: 0f
    return MiraProtoLevel(fraction) { f, _ ->
        scope.launch { audio.setVolume(stream, (min + Math.round(f * span)).coerceIn(min, max)) }
    }
}

/**
 * Drag handling shared by the prototype sliders: [vertical] fills from the bottom, otherwise
 * from the left; [inset] is the part of the track the fill always covers (One UI's round end).
 */
internal fun Modifier.protoSliderInput(
    level: () -> Float,
    vertical: Boolean,
    onLocal: (Float) -> Unit,
    onSet: (Float, Boolean) -> Unit,
    inset: Float = 0f,
): Modifier =
    pointerInput(vertical) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            fun at(p: Offset): Float =
                if (vertical) (1f - p.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                else ((p.x - inset) / (size.width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
            var v = at(down.position)
            onLocal(v)
            onSet(v, false)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                v = at(change.position)
                onLocal(v)
                onSet(v, false)
                change.consume()
            }
            onSet(v, true)
        }
    }

/** Keeps a slider's local value while dragging, following the system value otherwise. */
@Composable
internal fun rememberProtoLocal(value: Float): androidx.compose.runtime.MutableFloatState {
    val local = remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { local.floatValue = value }
    return local
}

// ---------------------------------------------------------------- ringer

@Composable
internal fun rememberProtoRinger(audio: AudioVolumeInteractor): Pair<Int, (Int) -> Unit> {
    val context = LocalContext.current
    val am = remember(context) { context.getSystemService(AudioManager::class.java) }
    val mode by audio.ringerMode.collectAsStateWithLifecycle()
    val set: (Int) -> Unit = { m ->
        am?.setRingerModeInternal(m)
        Unit
    }
    return mode.value to set
}

// ---------------------------------------------------------------- media

/** What the prototypes show for media: the playing session, or the greyed idle card. */
internal class MiraProtoMedia(
    val song: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val idle: Boolean,
    val prev: (() -> Unit)?,
    val playPause: (() -> Unit)?,
    val next: (() -> Unit)?,
    val open: (() -> Unit)?,
    /** The session's controller, for its position and seeking (null when idle). */
    val controller: android.media.session.MediaController? = null,
)

@Composable
internal fun rememberProtoMedia(
    data: MediaData?,
    onOpen: (android.app.PendingIntent) -> Unit,
    idleSong: String,
    idleArtist: String,
): MiraProtoMedia {
    val context = LocalContext.current
    val art by
        produceState<ImageBitmap?>(initialValue = null, data?.artwork) {
            value =
                data?.artwork?.let { icon ->
                    withContext(Dispatchers.IO) {
                        runCatching { icon.loadDrawable(context)?.toBitmap()?.asImageBitmap() }
                            .getOrNull()
                    }
                }
        }
    // MIRACLO: media playing without a notification (YouTube in picture-in-picture) is read
    // from the system's media sessions.
    val session = rememberMiraActiveSession()
    val dataController =
        remember(data?.token) {
            data?.token?.let { tk ->
                runCatching { android.media.session.MediaController(context, tk) }.getOrNull()
            }
        }
    if (session != null && (data == null || (data.isPlaying != true && session.playing))) {
        val c = session.controller
        val t = c.transportControls
        return MiraProtoMedia(
            song = session.title,
            artist = session.artist,
            art = session.art,
            playing = session.playing,
            idle = false,
            prev = { runCatching { t.skipToPrevious() } },
            playPause = { runCatching { if (session.playing) t.pause() else t.play() } },
            next = { runCatching { t.skipToNext() } },
            open = c.sessionActivity?.let { i -> { onOpen(i) } },
            controller = c,
        )
    }
    if (data == null) {
        return MiraProtoMedia(idleSong, idleArtist, null, false, true, null, null, null, null)
    }
    val actions = data.semanticActions
    return MiraProtoMedia(
        song = data.song?.toString().orEmpty(),
        artist = data.artist?.toString().orEmpty(),
        art = art,
        playing = data.isPlaying == true,
        idle = false,
        prev = actions?.prevOrCustom?.action?.let { r -> { r.run() } },
        playPause = actions?.playOrPause?.action?.let { r -> { r.run() } },
        next = actions?.nextOrCustom?.action?.let { r -> { r.run() } },
        open = data.clickIntent?.let { i -> { onOpen(i) } },
        controller = dataController,
    )
}

/** Album art or the empty square the prototypes show. */
@Composable
internal fun MiraProtoArt(media: MiraProtoMedia, size: Dp, radius: Dp, empty: Color, fallback: Color) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(radius)
    Box(Modifier.size(size).clip(shape).background(if (media.idle) empty else fallback)) {
        media.art?.let {
            Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---------------------------------------------------------------- glyphs

/** Glyphs the prototypes draw that are not tile icons (Material icons, Apache 2.0). */
internal object MiraProtoIcons {
    private fun i(p: String): ImageVector = MiraCLOIcons.icon(p)
    val Play = i("M8,5v14l11,-7z")
    val Pause = i("M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    val SkipPrevious = i("M6,6h2v12H6zM9.5,12l8.5,6V6z")
    val SkipNext = i("M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val FastRewind = i("M11,18V6l-8.5,6 8.5,6zM11.5,12l8.5,6V6l-8.5,6z")
    val FastForward = i("M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z")
    val Bell =
        i(
            "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4" +
                "c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1" +
                "h16v-1l-2,-2z")
    val Vibrate =
        i(
            "M0,15h2V9H0v6zM3,17h2V7H3v10zM22,9v6h2V9h-2zM19,17h2V7h-2v10zM16.5,3h-9C6.67,3 6,3.67 6," +
                "4.5v15c0,0.83 0.67,1.5 1.5,1.5h9c0.83,0 1.5,-0.67 1.5,-1.5v-15c0,-0.83 -0.67,-1.5 -1.5," +
                "-1.5zM16,19H8V5h8v14z")
    val BellOff =
        i(
            "M20,18.69L7.84,6.14 5.27,3.49 4,4.76l2.8,2.8v0.01c-0.52,0.99 -0.8,2.16 -0.8,3.42v5l-2,2v1" +
                "h13.73l2,2L21,19.72l-1,-1.03zM12,22c1.11,0 2,-0.89 2,-2h-4c0,1.11 0.89,2 2,2zM18,14.68V11" +
                "c0,-3.08 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68" +
                "c-0.15,0.03 -0.29,0.08 -0.42,0.12l-0.31,0.12L18,14.68z")
    val Favorite =
        i(
            "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5," +
                "2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12," +
                "21.35z")
    val Tower =
        i(
            "M7.3,14.7l1.2,-1.2c-1,-1 -1.5,-2.3 -1.5,-3.5s0.5,-2.6 1.5,-3.5L7.3,5.3c-1.3,1.3 -2,3 -2," +
                "4.7S6,13.4 7.3,14.7zM19.1,2.9l-1.2,1.2c1.6,1.6 2.4,3.8 2.4,5.9s-0.8,4.3 -2.4,5.9l1.2,1.2" +
                "c2,-2 2.9,-4.5 2.9,-7.1S21,4.9 19.1,2.9zM6.1,4.1L4.9,2.9C3,4.9 2,7.4 2,10s1,5.1 2.9,7.1" +
                "l1.2,-1.2C4.5,14.3 3.7,12.1 3.7,10S4.5,5.7 6.1,4.1zM16.7,14.7c1.3,-1.3 2,-3 2,-4.7" +
                "s-0.7,-3.4 -2,-4.7l-1.2,1.2c1,1 1.5,2.3 1.5,3.5s-0.5,2.6 -1.5,3.5L16.7,14.7zM14.5,10" +
                "c0,-1.38 -1.12,-2.5 -2.5,-2.5S9.5,8.62 9.5,10c0,0.76 0.34,1.42 0.87,1.88L7,22h2l0.67,-2" +
                "h4.67L15,22h2l-3.37,-10.12C14.16,11.42 14.5,10.76 14.5,10zM10.33,18L12,13l1.67,5H10.33z")
    val Close =
        i(
            "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19," +
                "17.59 13.41,12z")
    val Minus = i("M19,13H5v-2h14v2z")
    val Home = i("M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    val Nightlight =
        i(
            "M12.34,2.02C6.59,1.82 2,6.42 2,12c0,5.52 4.48,10 10,10c3.71,0 6.93,-2.02 8.66,-5.02" +
                "C13.15,16.73 8.57,8.55 12.34,2.02z")
}

/** A prototype glyph. */
@Composable
internal fun MiraProtoGlyph(icon: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Opens a Settings screen from Quick Settings. */
internal fun miraProtoIntent(action: String): Intent =
    Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal val MiraProtoCircle = CircleShape


/** What the styles need to place Android's real tiles (set by [MiraCLOOverlayQs.ProtoContent]). */
internal class MiraTileHost(
    val scope: com.android.compose.animation.scene.ContentScope,
    val haptics: com.android.systemui.haptics.msdl.qs.TileHapticsViewModel.Factory,
    val details: com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel,
    val coroutineScope: kotlinx.coroutines.CoroutineScope,
    /** Small (icon) or large, as the user set the tile in edit mode. */
    val isSmall: (TileViewModel) -> Boolean = { true },
)

internal val LocalMiraTileHost = androidx.compose.runtime.compositionLocalOf<MiraTileHost?> { null }

/**
 * Android's real QS tile, drawn with a style's [skin]. Tap, long-press, dialogs, haptics and
 * accessibility are the stock tile's. Falls back to nothing if the host is missing.
 */
@Composable
internal fun MiraRealTile(
    tile: TileViewModel,
    skin: com.android.systemui.qs.panels.ui.compose.infinitegrid.MiraTileSkin,
    iconOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val host = LocalMiraTileHost.current ?: return
    val bounce = remember(tile.spec) {
        com.android.systemui.qs.panels.ui.compose.BounceableInfo(
            com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel(), null, null, false)
    }
    CompositionLocalProvider(
        com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalMiraTileSkin provides skin
    ) {
        with(host.scope) {
            Tile(
                tile = tile,
                iconOnly = iconOnly,
                squishiness = { 1f },
                coroutineScope = host.coroutineScope,
                bounceableInfo = bounce,
                tileHapticsViewModelFactory = host.haptics,
                modifier = modifier,
                detailsViewModel = host.details,
            )
        }
    }
}
