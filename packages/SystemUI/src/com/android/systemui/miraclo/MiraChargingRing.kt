/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.miraclo

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import com.android.systemui.res.R

/**
 * The Mira charging moment: when the charger goes in while the lock screen is showing, a ring
 * fills to the battery level with the percentage, how fast it is charging and when it will be
 * full, then fades away. Settings.Secure "miraclo_charging_ring": 1 (default) on.
 */
object MiraChargingRing {
    const val KEY = "miraclo_charging_ring"
    private var started = false
    private var showing: View? = null

    @JvmStatic
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext ?: context
        app.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    Handler(Looper.getMainLooper()).postDelayed({ maybeShow(app) }, 250)
                }
            },
            IntentFilter(Intent.ACTION_POWER_CONNECTED),
            Context.RECEIVER_EXPORTED,
        )
    }

    private fun enabled(c: Context) =
        try {
            Settings.Secure.getIntForUser(c.contentResolver, KEY, 1, ActivityManager.getCurrentUser()) != 0
        } catch (_: Exception) {
            true
        }

    private fun maybeShow(c: Context) {
        if (showing != null || !enabled(c)) return
        val km = c.getSystemService(KeyguardManager::class.java) ?: return
        val pm = c.getSystemService(PowerManager::class.java) ?: return
        if (!km.isKeyguardLocked || !pm.isInteractive) return
        val sticky = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = (sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / scale).coerceIn(0, 100)
        val microA = sticky.getIntExtra("max_charging_current", 0).toLong()
        val microV = sticky.getIntExtra("max_charging_voltage", 5_000_000).toLong()
        val watts = microA * microV / 1_000_000_000_000.0
        val fast = watts >= 7.5
        val bm = c.getSystemService(BatteryManager::class.java)
        val remaining = try { bm?.computeChargeTimeRemaining() ?: -1L } catch (_: Exception) { -1L }
        val wm = c.getSystemService(WindowManager::class.java) ?: return
        val view = RingView(c, level, fast, remaining)
        val lp =
            WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                )
                .apply {
                    title = "MiraChargingRing"
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    fitInsetsTypes = 0
                }
        try {
            wm.addView(view, lp)
        } catch (_: Exception) {
            return
        }
        showing = view
        view.play {
            try { wm.removeView(view) } catch (_: Exception) {}
            showing = null
        }
    }

    private class RingView(context: Context, val level: Int, val fast: Boolean, val remainingMs: Long) :
        View(context) {
        private val d = resources.displayMetrics.density
        private val ease = PathInterpolator(0.2f, 0.9f, 0.2f, 1f)
        private val sans = Typeface.create("mira-sans", Typeface.NORMAL)
        private val dim = Paint().apply { color = Color.BLACK }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 12 * d
            strokeCap = Paint.Cap.ROUND
            color = 0x2EFFFFFF
        }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 12 * d
            strokeCap = Paint.Cap.ROUND
            color = 0xFF34C759.toInt()
        }
        private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46 * d
            typeface = Typeface.create(sans, 700, false)
            textAlign = Paint.Align.CENTER
        }
        private val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
        private val chipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13 * d
            typeface = Typeface.create(sans, 500, false)
            textAlign = Paint.Align.CENTER
        }
        private var shown = 0f
        private var fill = 0f

        fun play(done: () -> Unit) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1100
                interpolator = ease
                addUpdateListener {
                    val v = it.animatedValue as Float
                    shown = (v * 3f).coerceAtMost(1f)
                    fill = v
                    invalidate()
                }
                start()
            }
            postDelayed({
                ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = 320
                    addUpdateListener {
                        shown = it.animatedValue as Float
                        invalidate()
                    }
                    addListener(
                        object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) = done()
                        }
                    )
                    start()
                }
            }, 2800)
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            dim.alpha = (0xB3 * shown).toInt()
            c.drawRect(0f, 0f, w, h, dim)
            val cx = w / 2f
            val cy = h * 0.46f
            val r = 74 * d
            val a = (255 * shown).toInt()
            track.alpha = (0x2E * shown).toInt()
            arc.alpha = a
            c.drawCircle(cx, cy, r, track)
            c.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 360f * level / 100f * fill, false, arc)
            big.alpha = a
            c.drawText("${(level * fill).toInt()}%", cx, cy - (big.descent() + big.ascent()) / 2f, big)
            val labels = buildList {
                add(context.getString(if (fast) R.string.miraclo_charging_fast else R.string.miraclo_charging))
                if (remainingMs > 0) {
                    val min = (remainingMs / 60000L).coerceAtLeast(1L)
                    add(
                        if (min >= 60) context.getString(R.string.miraclo_charging_full_h, min / 60, min % 60)
                        else context.getString(R.string.miraclo_charging_full_m, min)
                    )
                }
            }
            val y = cy + r + 44 * d
            val widths = labels.map { chipText.measureText(it) + 28 * d }
            var x = cx - (widths.sum() + 8 * d * (labels.size - 1)) / 2f
            labels.forEachIndexed { i, s ->
                chip.alpha = (0x26 * shown).toInt()
                c.drawRoundRect(x, y - 16 * d, x + widths[i], y + 16 * d, 16 * d, 16 * d, chip)
                chipText.alpha = a
                c.drawText(s, x + widths[i] / 2f, y - (chipText.descent() + chipText.ascent()) / 2f, chipText)
                x += widths[i] + 8 * d
            }
        }
    }
}
