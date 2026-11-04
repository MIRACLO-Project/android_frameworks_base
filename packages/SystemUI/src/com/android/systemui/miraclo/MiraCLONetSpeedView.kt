/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.miraclo

import android.content.Context
import android.database.ContentObserver
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import com.android.systemui.miraclo.island.IslandStatusBarSpace
import com.android.systemui.res.R

/**
 * Network speed in the status bar (Mira Studio › Top Bar › Icons › Network speed).
 *
 * Two copies live in the status bar: one after the clock ([side] = 0) and one before the system
 * icons ([side] = 1); only the copy on the chosen side shows. It follows the island's rules: it
 * fades out while the island shows anything. It takes the clock's colour, so it is always tinted
 * like the rest of the status bar. It only samples while it is visible on screen.
 */
class MiraCLONetSpeedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextView(context, attrs) {

    private val side: Int
    private val handler = Handler(Looper.getMainLooper())
    private var enabled = false
    private var position = 1
    private var style = 0
    private var arrows = true
    private var unit = 0
    private var size = 1
    private var hideIdle = true
    private var islandActive = false
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L
    private var running = false

    init {
        val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.tag))
        side = if (a.getString(0) == "start") 0 else 1
        a.recycle()
        gravity = Gravity.CENTER_VERTICAL or (if (side == 0) Gravity.START else Gravity.END)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        fontFeatureSettings = "tnum"
        includeFontPadding = false
        setSingleLine(false)
        visibility = GONE
    }

    private val observer =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = reload()
        }

    private val islandListener = Runnable {
        islandActive = IslandStatusBarSpace.isActive()
        animate().cancel()
        animate().alpha(if (islandActive) 0f else 1f).setDuration(if (islandActive) 120 else 180).start()
    }

    private val tick =
        object : Runnable {
            override fun run() {
                sample()
                if (running) handler.postDelayed(this, 1000L)
            }
        }

    // Take the clock's colour (light or dark status bar), checked cheaply before each draw.
    private val tint =
        ViewTreeObserver.OnPreDrawListener {
            val clock = rootView?.findViewById<TextView>(R.id.clock)
            if (clock != null && clock.currentTextColor != currentTextColor) {
                setTextColor(clock.currentTextColor)
            }
            true
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        for (k in KEYS) {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(k), false, observer)
        }
        IslandStatusBarSpace.addListener(islandListener)
        viewTreeObserver.addOnPreDrawListener(tint)
        islandListener.run()
        reload()
    }

    override fun onDetachedFromWindow() {
        context.contentResolver.unregisterContentObserver(observer)
        IslandStatusBarSpace.removeListener(islandListener)
        viewTreeObserver.removeOnPreDrawListener(tint)
        stop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(v: Int) {
        super.onWindowVisibilityChanged(v)
        if (v == View.VISIBLE) reload() else stop()
    }

    private fun get(key: String, def: Int) = Settings.Secure.getInt(context.contentResolver, key, def)

    private fun reload() {
        enabled = get(KEY_ON, 0) != 0
        position = get(KEY_POS, 1).coerceIn(0, 1)
        style = get(KEY_STYLE, 0).coerceIn(0, 2)
        arrows = get(KEY_ARROWS, 0) != 0
        unit = get(KEY_UNIT, 0).coerceIn(0, 2)
        size = get(KEY_SIZE, 1).coerceIn(0, 2)
        hideIdle = get(KEY_HIDE_IDLE, 0) != 0
        val textSp = when (size) { 0 -> 7.5f; 2 -> 10f; else -> 8.5f } * (if (style == 0) 1f else 1.25f)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        setLineSpacing(0f, 0.9f)
        maxLines = if (style == 0) 2 else 1
        val mine = enabled && position == side
        if (mine && windowVisibility == View.VISIBLE) start() else { stop(); visibility = GONE }
    }

    private fun start() {
        if (running) return
        running = true
        lastRx = -1L
        handler.post(tick)
    }

    private fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun sample() {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()
        if (rx < 0 || tx < 0) { visibility = GONE; return }
        if (lastRx < 0) { lastRx = rx; lastTx = tx; lastAt = now; return }
        val sec = ((now - lastAt).coerceAtLeast(1L)) / 1000f
        val down = ((rx - lastRx).coerceAtLeast(0L)) / sec
        val up = ((tx - lastTx).coerceAtLeast(0L)) / sec
        lastRx = rx; lastTx = tx; lastAt = now
        if (hideIdle && down + up < IDLE_BYTES) { visibility = GONE; return }
        text = when (style) {
            0 -> fmt(down, "↓") + "\n" + fmt(up, "↑")
            1 -> fmt(down, "↓") + "  " + fmt(up, "↑")
            else -> fmt(down + up, "⇅")
        }
        visibility = VISIBLE
    }

    /** Bytes per second → "2.4 MB/s", "512 KB/s", "19.2 Mb/s"; the arrow only if enabled. */
    private fun fmt(bytesPerSec: Float, arrow: String): String {
        val a = if (arrows) "$arrow " else ""
        val v: Float
        val u: String
        when (unit) {
            1 -> { // bits
                val b = bytesPerSec * 8
                if (b >= 1_000_000f) { v = b / 1_000_000f; u = "Mb/s" } else { v = b / 1000f; u = "Kb/s" }
            }
            2 -> { v = bytesPerSec / (1024f * 1024f); u = "MB/s" }
            else -> {
                if (bytesPerSec >= 1024f * 1024f) { v = bytesPerSec / (1024f * 1024f); u = "MB/s" }
                else { v = bytesPerSec / 1024f; u = "KB/s" }
            }
        }
        val n = if (v >= 100f || v == 0f) "%.0f".format(v) else if (v >= 10f) "%.1f".format(v) else "%.2f".format(v)
        return "$a$n $u"
    }

    companion object {
        const val KEY_ON = "miraclo_sb_speed"
        const val KEY_POS = "miraclo_sb_speed_pos"       // 0 left after the clock, 1 right
        const val KEY_STYLE = "miraclo_sb_speed_style"   // 0 two lines, 1 one line, 2 total only
        const val KEY_ARROWS = "miraclo_sb_speed_arrows"
        const val KEY_UNIT = "miraclo_sb_speed_unit"     // 0 auto bytes, 1 bits, 2 always MB/s
        const val KEY_SIZE = "miraclo_sb_speed_size"     // 0 small, 1 normal, 2 large
        const val KEY_HIDE_IDLE = "miraclo_sb_speed_hide_idle"
        private const val IDLE_BYTES = 1024f
        private val KEYS = listOf(KEY_ON, KEY_POS, KEY_STYLE, KEY_ARROWS, KEY_UNIT, KEY_SIZE, KEY_HIDE_IDLE)
    }
}
