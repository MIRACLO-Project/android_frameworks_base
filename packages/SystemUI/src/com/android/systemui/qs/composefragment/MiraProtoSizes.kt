/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import android.app.ActivityManager
import android.content.Context
import android.media.session.MediaController
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ---------------------------------------------------------------- tile sizes per style

/** A tile's size in grid cells: [w] columns by [h] rows. */
internal data class MiraCell(val w: Int, val h: Int) {
    val isSmall: Boolean
        get() = w == 1 && h == 1

    override fun toString() = "${w}x$h"

    companion object {
        val S11 = MiraCell(1, 1)
        val S21 = MiraCell(2, 1)
        val S12 = MiraCell(1, 2)
        val S22 = MiraCell(2, 2)
        val S24 = MiraCell(2, 4)

        fun parse(s: String): MiraCell? {
            val p = s.split("x")
            val w = p.getOrNull(0)?.toIntOrNull() ?: return null
            val h = p.getOrNull(1)?.toIntOrNull() ?: return null
            return if (w in 1..4 && h in 1..4) MiraCell(w, h) else null
        }
    }
}

/**
 * Each style keeps the size of every tile and widget in Settings.Secure
 * "miraclo_qs_sizes_<style>" as "key=WxH,key=WxH". A key without an entry uses the default the
 * style gives (Android's own small or large for tiles).
 */
internal class MiraTileSizes(private val context: Context, style: Int) {
    private val key = "miraclo_qs_sizes_$style"
    private var map by mutableStateOf(emptyMap<String, MiraCell>())

    fun load() {
        val raw =
            runCatching {
                    Settings.Secure.getStringForUser(
                        context.contentResolver, key, ActivityManager.getCurrentUser())
                }
                .getOrNull()
        map =
            raw.orEmpty()
                .split(",")
                .mapNotNull { e ->
                    val i = e.indexOf('=')
                    if (i <= 0) null
                    else MiraCell.parse(e.substring(i + 1))?.let { e.substring(0, i) to it }
                }
                .toMap()
    }

    /** The size of [id]; [default] when unset or when the saved size is not one of [allowed]. */
    fun of(id: String, default: MiraCell, allowed: List<MiraCell>? = null): MiraCell {
        val s = map[id] ?: return default
        return if (allowed == null || s in allowed) s else default
    }

    fun set(id: String, cell: MiraCell) {
        map = map + (id to cell)
        runCatching {
            Settings.Secure.putStringForUser(
                context.contentResolver,
                key,
                map.entries.joinToString(",") { "${it.key}=${it.value}" },
                ActivityManager.getCurrentUser(),
            )
        }
    }
}

@Composable
internal fun rememberMiraTileSizes(style: Int): MiraTileSizes {
    val context = LocalContext.current
    return remember(style) { MiraTileSizes(context, style).also { it.load() } }
}

/** The next size in [sizes] after [now] (wraps around). */
internal fun miraNextSize(sizes: List<MiraCell>, now: MiraCell): MiraCell {
    val i = sizes.indexOf(now)
    return sizes[(i + 1).mod(sizes.size)]
}

// ---------------------------------------------------------------- dense grid packing

/**
 * Places each size in the first free spot where it fits, left to right and top to bottom (as
 * Control Center does). Returns the (row, column) of each and the number of rows used.
 */
internal fun miraPack(sizes: List<MiraCell>, cols: Int): Pair<List<Pair<Int, Int>>, Int> {
    val rows = mutableListOf<BooleanArray>()
    fun free(r: Int, c: Int) = r >= rows.size || !rows[r][c]
    fun fits(r: Int, c: Int, w: Int, h: Int): Boolean {
        if (c + w > cols) return false
        for (y in r until r + h) for (x in c until c + w) if (!free(y, x)) return false
        return true
    }
    val spots =
        sizes.map { s ->
            val w = s.w.coerceIn(1, cols)
            val h = s.h.coerceAtLeast(1)
            var r = 0
            var spot: Pair<Int, Int>? = null
            while (spot == null) {
                for (c in 0..(cols - w)) if (fits(r, c, w, h)) { spot = r to c; break }
                if (spot == null) r++
            }
            val found = spot!!
            val (fr, fc) = found
            while (rows.size < fr + h) rows.add(BooleanArray(cols))
            for (y in fr until fr + h) for (x in fc until fc + w) rows[y][x] = true
            found
        }
    return spots to rows.size
}

/** Splits [sizes] into pages of [cols] by [maxRows] cells, packing each page densely. */
internal fun miraPages(sizes: List<MiraCell>, cols: Int, maxRows: Int): List<List<Int>> {
    val pages = mutableListOf<List<Int>>()
    var cur = mutableListOf<Int>()
    sizes.forEachIndexed { i, s ->
        val trial = (cur + i).map { sizes[it] }
        if (cur.isNotEmpty() && miraPack(trial, cols).second > maxRows) {
            pages += cur
            cur = mutableListOf()
        }
        cur += i
    }
    if (cur.isNotEmpty() || pages.isEmpty()) pages += cur
    return pages
}

/**
 * A grid of [cols] equal columns and [cellHeight] rows; each child takes the cells its size says
 * and goes into the first free spot ([miraPack]).
 */
@Composable
internal fun MiraCellGrid(
    sizes: List<MiraCell>,
    cols: Int,
    cellHeight: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val g = gap.roundToPx()
        val ch = cellHeight.roundToPx()
        val width = constraints.maxWidth
        val cw = ((width - g * (cols - 1)) / cols).coerceAtLeast(1)
        val (spots, rows) = miraPack(sizes.take(measurables.size), cols)
        val placeables =
            measurables.mapIndexed { i, m ->
                val s = sizes.getOrElse(i) { MiraCell.S11 }
                val w = s.w.coerceIn(1, cols)
                m.measure(Constraints.fixed(w * cw + (w - 1) * g, s.h * ch + (s.h - 1) * g))
            }
        val height = (rows * ch + (rows - 1).coerceAtLeast(0) * g).coerceAtLeast(0)
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEachIndexed { i, p ->
                val (r, c) = spots.getOrElse(i) { 0 to 0 }
                p.place(c * (cw + g), r * (ch + g))
            }
        }
    }
}

/** The corner handle of edit mode: tap to change a tile's size. */
internal val MiraResizeGlyph: ImageVector =
    MiraCLOIcons.icon("M21,11V3h-8l3.29,3.29l-10,10L3,13v8h8l-3.29,-3.29l10,-10L21,11z")

@Composable
internal fun MiraResizeHandle(bg: Color, fg: Color, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp).background(bg, CircleShape).protoTap(onTap = onTap),
        contentAlignment = Alignment.Center,
    ) {
        MiraProtoGlyph(MiraResizeGlyph, fg, 13.dp)
    }
}

// ---------------------------------------------------------------- media position and seeking

/** Position, length and seeking of the playing media session. */
internal class MiraProgress(val position: Long, val duration: Long, val seek: ((Long) -> Unit)?) {
    val fraction: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/**
 * Reads the position of [media] twice a second (also YouTube in picture-in-picture, through its
 * controller) and seeks through the session's transport controls.
 */
@Composable
internal fun rememberMiraProgress(media: MiraProtoMedia): MiraProgress {
    val controller: MediaController? = media.controller
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(controller?.sessionToken) {
        if (controller == null) {
            pos = 0L
            dur = 0L
            return@LaunchedEffect
        }
        while (isActive) {
            runCatching {
                dur =
                    controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
                        ?: 0L
                val st = controller.playbackState
                pos =
                    if (st == null) 0L
                    else if (st.state == android.media.session.PlaybackState.STATE_PLAYING) {
                        val since = android.os.SystemClock.elapsedRealtime() - st.lastPositionUpdateTime
                        st.position + (since * st.playbackSpeed).toLong()
                    } else st.position
                if (dur > 0) pos = pos.coerceIn(0L, dur)
            }
            delay(500)
        }
    }
    val seekable =
        controller != null &&
            dur > 0 &&
            ((controller.playbackState?.actions ?: 0L) and
                android.media.session.PlaybackState.ACTION_SEEK_TO) != 0L
    val seek: ((Long) -> Unit)? =
        if (seekable && controller != null) {
            { ms: Long ->
                pos = ms
                runCatching { controller.transportControls.seekTo(ms) }
                Unit
            }
        } else {
            null
        }
    return MiraProgress(pos, dur, seek)
}

internal fun miraTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

// ---------------------------------------------------------------- the wavy seek bar

/**
 * Material 3 Expressive's wavy bar: the played part is a moving wave while playing and flattens
 * when paused; the rest is a flat track after a small gap, with a dot at the end. Drag or tap to
 * seek when [onSeek] is set.
 */
@Composable
internal fun MiraWavyBar(
    fraction: Float,
    playing: Boolean,
    active: Color,
    track: Color,
    modifier: Modifier = Modifier,
    height: Dp = 18.dp,
    stroke: Dp = 4.dp,
    onSeek: ((Float) -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: fraction).coerceIn(0f, 1f)
    // the wave's height eases in and out with play and pause
    val amp = remember { androidx.compose.animation.core.Animatable(if (playing) 1f else 0f) }
    LaunchedEffect(playing) {
        amp.animateTo(if (playing) 1f else 0f, androidx.compose.animation.core.tween(450))
    }
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            androidx.compose.runtime.withFrameMillis { t ->
                if (last != 0L) phase = (phase + (t - last) / 1000f * 1.1f) % 1f
                last = t
            }
        }
    }
    val input =
        if (onSeek == null) Modifier
        else
            Modifier.pointerInput(onSeek) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    fun at(p: Offset) = (p.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    dragging = at(down.position)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        dragging = at(ch.position)
                        ch.consume()
                    }
                    dragging?.let { onSeek(it) }
                    dragging = null
                }
            }
    Canvas(modifier.fillMaxWidth().height(height).then(input)) {
        val sw = stroke.toPx()
        val cy = size.height / 2f
        val start = sw / 2f
        val end = size.width - sw / 2f
        val x = start + (end - start) * shown
        val wave = 3.dp.toPx() * amp.value
        val length = 36.dp.toPx()
        val gap = 6.dp.toPx()
        // played part
        if (x > start) {
            val path = Path()
            var px = start
            path.moveTo(px, cy + wave * kotlin.math.sin(((px / length) - phase) * 2f * Math.PI.toFloat()))
            while (px < x) {
                px = (px + 2f).coerceAtMost(x)
                path.lineTo(px, cy + wave * kotlin.math.sin(((px / length) - phase) * 2f * Math.PI.toFloat()))
            }
            drawPath(path, active, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
        // track after a small gap, and the end dot
        val ts = (x + gap).coerceAtMost(end)
        if (ts < end) {
            drawLine(track, Offset(ts, cy), Offset(end, cy), strokeWidth = sw, cap = StrokeCap.Round)
        }
        drawCircle(active, radius = sw / 2f, center = Offset(end, cy))
        // the thumb while dragging
        if (dragging != null) drawCircle(active, radius = sw * 1.4f, center = Offset(x, cy))
    }
}

/** A wavy, draggable position bar for [media], with the times underneath when [times] is set. */
@Composable
internal fun MiraMediaSeekBar(
    media: MiraProtoMedia,
    active: Color,
    track: Color,
    modifier: Modifier = Modifier,
    times: Color? = null,
    seekable: Boolean = true,
) {
    val p = rememberMiraProgress(media)
    Column(modifier) {
        MiraWavyBar(
            fraction = p.fraction,
            playing = media.playing,
            active = active,
            track = track,
            onSeek = p.seek?.takeIf { seekable }?.let { s -> { f: Float -> s((f * p.duration).toLong()) } },
        )
        if (times != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(miraTime(p.position), color = times, fontSize = 12.sp)
                Text("−" + miraTime((p.duration - p.position).coerceAtLeast(0L)), color = times, fontSize = 12.sp)
            }
        }
    }
}
