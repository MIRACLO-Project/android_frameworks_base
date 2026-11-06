/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.miraclo

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.PowerManager
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The Mira power menu: a full screen over a dim blur, two slide bars (Power off, Restart) and a
 * row of round buttons (Emergency, Lockdown, Advanced, Cancel). Slide a bar to the end, or tap
 * it, to run it. Advanced turns the same screen to three more bars (Restart System UI, Reboot to
 * recovery, Reboot to bootloader) at once, with Back to return.
 * Settings.Secure "miraclo_power_menu": 1 (default) Mira, 0 Android's own menu.
 */
object MiraCLOPowerMenu {
    const val KEY = "miraclo_power_menu"

    /** What the buttons do; supplied by the power menu that opens this one. */
    interface Actions {
        fun powerOff()
        fun restart()
        fun emergency()
        fun lockdown()
        fun advanced()
        /** Called once when the menu has gone, whatever closed it. */
        fun onHidden()
        val showLockdown: Boolean
        val showEmergency: Boolean
    }

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        try {
            Settings.Secure.getIntForUser(
                context.contentResolver, KEY, 1, ActivityManager.getCurrentUser()) != 0
        } catch (_: Exception) {
            true
        }

    @JvmStatic
    fun show(context: Context, actions: Actions): Dialog {
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        val view = MenuView(context, actions) { run -> close(dialog, run) }
        dialog.setContentView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY)
        dialog.window?.addPrivateFlags(WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setWindowAnimations(0)
            setDecorFitsSystemWindows(false)
            attributes =
                attributes.apply {
                    title = "MiraPowerMenu"
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    fitInsetsTypes = 0
                    flags = flags
                    blurBehindRadius = (24 * context.resources.displayMetrics.density).toInt()
                }
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnCancelListener { view.leave(null) }
        dialog.setOnDismissListener { actions.onHidden() }
        dialog.show()
        view.enter()
        return dialog
    }

    private fun close(dialog: Dialog, run: (() -> Unit)?) {
        if (dialog.isShowing) dialog.dismiss()
        run?.invoke()
    }

    private class MenuView(
        context: Context,
        private val actions: Actions,
        private val finish: ((() -> Unit)?) -> Unit,
    ) : View(context) {
        private val d = resources.displayMetrics.density
        private fun dp(v: Float) = v * d
        private val ease = PathInterpolator(0.2f, 0.9f, 0.2f, 1f)
        private val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8C08090E.toInt() }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        private val trackRim =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1f)
                color = 0x40FFFFFF
            }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glyph =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = dp(2.2f)
            }
        private val sans = Typeface.create("mira-sans", Typeface.NORMAL)
        private val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dp(17f)
                typeface = Typeface.create(sans, 600, false)
                textAlign = Paint.Align.CENTER
            }
        private val small =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xD9FFFFFF.toInt()
                textSize = dp(13f)
                typeface = sans
                textAlign = Paint.Align.CENTER
            }

        private val barH = dp(68f)
        private val knob = dp(56f)
        private val mainBars = listOf(Bar.POWER, Bar.RESTART)
        private val advBars = listOf(Bar.SYSTEM_UI, Bar.RECOVERY, Bar.BOOTLOADER)
        private val barRects = Array(3) { RectF() }
        private val drag = FloatArray(3)
        /** 0 = the main page, 1 = Advanced; animates between them. */
        private var adv = 0f
        private var onAdvanced = false
        private val bars: List<Bar>
            get() = if (onAdvanced) advBars else mainBars
        private var active = -1
        private var downX = 0f
        private var downY = 0f
        private var moved = false
        private var shown = 0f
        private var leaving = false

        private enum class Bar { POWER, RESTART, SYSTEM_UI, RECOVERY, BOOTLOADER }
        private enum class Button { EMERGENCY, LOCKDOWN, ADVANCED, CANCEL, BACK }

        private val buttons =
            buildList {
                if (actions.showEmergency) add(Button.EMERGENCY)
                if (actions.showLockdown) add(Button.LOCKDOWN)
                add(Button.ADVANCED)
                add(Button.CANCEL)
            }
        private val buttonCentres = Array(buttons.size) { FloatArray(2) }
        private val backCentre = FloatArray(2)
        private var pressedButton = -1

        init {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        fun enter() {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 380
                interpolator = ease
                addUpdateListener {
                    shown = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun leave(run: (() -> Unit)?) {
            if (leaving) return
            leaving = true
            ValueAnimator.ofFloat(shown, 0f).apply {
                duration = 220
                interpolator = ease
                addUpdateListener {
                    shown = it.animatedValue as Float
                    invalidate()
                }
                addListener(
                    object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            finish(run)
                        }
                    }
                )
                start()
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            val w = width.toFloat()
            val h = height.toFloat()
            val side = dp(26f)
            val barW = min(w - side * 2, dp(440f))
            val left = (w - barW) / 2f
            val top = h * 0.30f
            barRects.forEachIndexed { i, rect ->
                val y = top + i * (barH + dp(16f))
                rect.set(left, y, left + barW, y + barH)
            }
            backCentre[0] = w / 2f
            backCentre[1] = h - dp(150f)
            val gap = min(dp(92f), w / (buttons.size + 0.5f))
            val start = w / 2f - gap * (buttons.size - 1) / 2f
            val cy = h - dp(150f)
            buttons.indices.forEach { i ->
                buttonCentres[i][0] = start + gap * i
                buttonCentres[i][1] = cy
            }
        }

        private fun barText(bar: Bar): String =
            when (bar) {
                Bar.POWER -> context.getString(com.android.internal.R.string.global_action_power_off)
                Bar.RESTART -> context.getString(com.android.internal.R.string.global_action_restart)
                Bar.SYSTEM_UI -> "Restart System UI"
                Bar.RECOVERY -> "Recovery"
                Bar.BOOTLOADER -> "Bootloader"
            }

        private fun drawBars(canvas: Canvas, list: List<Bar>, a: Float, dx: Float, lift: Float) {
            if (a <= 0.01f) return
            list.forEachIndexed { i, bar ->
                val r = barRects[i]
                canvas.save()
                canvas.translate(dx, lift * (1f + i * 0.4f))
                val alpha = (255 * a).toInt()
                track.alpha = (0x33 * a).toInt()
                trackRim.alpha = (0x40 * a).toInt()
                canvas.drawRoundRect(r, barH / 2f, barH / 2f, track)
                canvas.drawRoundRect(r, barH / 2f, barH / 2f, trackRim)
                val travel = r.width() - knob - dp(12f)
                val mine = (list === bars)
                val p = if (mine) (drag[i] / max(1f, travel)).coerceIn(0f, 1f) else 0f
                label.alpha = (alpha * (1f - p)).toInt()
                canvas.drawText(barText(bar), r.centerX() + knob / 2f, r.centerY() - (label.descent() + label.ascent()) / 2f, label)
                val kx = r.left + dp(6f) + knob / 2f + p * travel
                val ky = r.centerY()
                fill.color = if (bar == Bar.POWER) 0xFFFF453A.toInt() else Color.WHITE
                fill.alpha = alpha
                canvas.drawCircle(kx, ky, knob / 2f, fill)
                glyph.color = if (bar == Bar.POWER) Color.WHITE else 0xFF111317.toInt()
                glyph.alpha = alpha
                when (bar) {
                    Bar.POWER -> drawPower(canvas, kx, ky)
                    Bar.RESTART -> drawRestart(canvas, kx, ky)
                    Bar.SYSTEM_UI -> drawPhone(canvas, kx, ky)
                    Bar.RECOVERY -> drawWrench(canvas, kx, ky)
                    Bar.BOOTLOADER -> drawPrompt(canvas, kx, ky)
                }
                canvas.restore()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val a = shown
            scrim.alpha = (0x8C * a).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
            val lift = (1f - a) * dp(40f)
            val slide = dp(60f)
            val mainA = a * (1f - adv)
            val advA = a * adv
            drawBars(canvas, mainBars, mainA, -slide * adv, lift)
            drawBars(canvas, advBars, advA, slide * (1f - adv), lift)
            // Title of the Advanced page, above its bars.
            if (advA > 0.01f) {
                label.alpha = (255 * advA).toInt()
                canvas.drawText(
                    context.getString(com.android.systemui.res.R.string.miraclo_power_advanced),
                    width / 2f + slide * (1f - adv),
                    barRects[0].top - dp(28f),
                    label,
                )
            }
            val hintY = barRects[if (adv > 0.5f) 2 else 1].bottom + dp(30f) + lift
            small.alpha = (0x99 * a).toInt()
            canvas.drawText(
                context.getString(com.android.systemui.res.R.string.miraclo_power_hint),
                width / 2f,
                hintY,
                small,
            )
            // Main page buttons.
            buttons.forEachIndexed { i, b ->
                val cx = buttonCentres[i][0] - slide * adv
                val cy = buttonCentres[i][1] + lift * 1.6f
                val ba = mainA
                if (ba <= 0.01f) return@forEachIndexed
                fill.color = if (i == pressedButton) 0x59FFFFFF else 0x2EFFFFFF
                fill.alpha = (Color.alpha(fill.color) * ba).toInt()
                canvas.drawCircle(cx, cy, dp(28f), fill)
                glyph.color = if (b == Button.EMERGENCY) 0xFFFF453A.toInt() else Color.WHITE
                glyph.alpha = (255 * ba).toInt()
                when (b) {
                    Button.EMERGENCY -> drawEmergency(canvas, cx, cy)
                    Button.LOCKDOWN -> drawLock(canvas, cx, cy)
                    Button.ADVANCED -> drawMore(canvas, cx, cy)
                    Button.CANCEL -> drawCancel(canvas, cx, cy)
                    Button.BACK -> {}
                }
                small.alpha = (0xD9 * ba).toInt()
                canvas.drawText(buttonLabel(b), cx, cy + dp(28f) + dp(20f), small)
            }
            // Advanced page: Back.
            if (advA > 0.01f) {
                val cx = backCentre[0] + slide * (1f - adv)
                val cy = backCentre[1] + lift * 1.6f
                fill.color = if (backPressed) 0x59FFFFFF else 0x2EFFFFFF
                fill.alpha = (Color.alpha(fill.color) * advA).toInt()
                canvas.drawCircle(cx, cy, dp(28f), fill)
                glyph.color = Color.WHITE
                glyph.alpha = (255 * advA).toInt()
                drawBack(canvas, cx, cy)
                small.alpha = (0xD9 * advA).toInt()
                canvas.drawText(buttonLabel(Button.BACK), cx, cy + dp(28f) + dp(20f), small)
            }
        }

        private var backPressed = false

        private fun showAdvanced(on: Boolean) {
            onAdvanced = on
            drag.fill(0f)
            ValueAnimator.ofFloat(adv, if (on) 1f else 0f).apply {
                duration = 300
                interpolator = ease
                addUpdateListener {
                    adv = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun buttonLabel(b: Button): String =
            when (b) {
                Button.EMERGENCY -> context.getString(com.android.internal.R.string.global_action_emergency)
                Button.LOCKDOWN -> context.getString(com.android.internal.R.string.global_action_lockdown)
                Button.ADVANCED -> context.getString(com.android.systemui.res.R.string.miraclo_power_advanced)
                Button.CANCEL -> context.getString(android.R.string.cancel)
                Button.BACK -> "Back"
            }

        // Glyphs, drawn on a 24-unit grid centred on (cx, cy).
        private fun g(v: Float) = v * d
        private val path = Path()

        private fun drawPower(c: Canvas, cx: Float, cy: Float) {
            c.drawLine(cx, cy - g(9f), cx, cy - g(1f), glyph)
            val r = g(7.5f)
            c.drawArc(cx - r, cy - r + g(1f), cx + r, cy + r + g(1f), -55f, 290f, false, glyph)
        }

        private fun drawRestart(c: Canvas, cx: Float, cy: Float) {
            val r = g(7.5f)
            c.drawArc(cx - r, cy - r, cx + r, cy + r, -40f, 300f, false, glyph)
            path.reset()
            path.moveTo(cx + g(7.5f), cy - g(9f))
            path.lineTo(cx + g(7.5f), cy - g(3.5f))
            path.lineTo(cx + g(2f), cy - g(3.5f))
            c.drawPath(path, glyph)
        }

        private fun drawEmergency(c: Canvas, cx: Float, cy: Float) {
            for (k in 0 until 3) {
                c.save()
                c.rotate(k * 60f, cx, cy)
                c.drawLine(cx, cy - g(8f), cx, cy + g(8f), glyph)
                c.restore()
            }
        }

        private fun drawLock(c: Canvas, cx: Float, cy: Float) {
            c.drawRoundRect(cx - g(7f), cy - g(1f), cx + g(7f), cy + g(9f), g(2.5f), g(2.5f), glyph)
            c.drawArc(cx - g(4.5f), cy - g(9f), cx + g(4.5f), cy + g(1f), 180f, 180f, false, glyph)
        }

        private fun drawMore(c: Canvas, cx: Float, cy: Float) {
            val dot = glyph.strokeWidth
            glyph.strokeWidth = g(3.2f)
            for (k in -1..1) c.drawPoint(cx + k * g(6f), cy, glyph)
            glyph.strokeWidth = dot
        }

        private fun drawPhone(c: Canvas, cx: Float, cy: Float) {
            c.drawRoundRect(cx - g(5.5f), cy - g(9f), cx + g(5.5f), cy + g(9f), g(2.5f), g(2.5f), glyph)
            c.drawLine(cx - g(2f), cy + g(5.5f), cx + g(2f), cy + g(5.5f), glyph)
        }

        private fun drawWrench(c: Canvas, cx: Float, cy: Float) {
            // A circular arrow around a plus: restart into repair.
            val r = g(8f)
            c.drawArc(cx - r, cy - r, cx + r, cy + r, -60f, 300f, false, glyph)
            c.drawLine(cx, cy - g(3.5f), cx, cy + g(3.5f), glyph)
            c.drawLine(cx - g(3.5f), cy, cx + g(3.5f), cy, glyph)
        }

        private fun drawPrompt(c: Canvas, cx: Float, cy: Float) {
            path.reset()
            path.moveTo(cx - g(7f), cy - g(5f))
            path.lineTo(cx - g(2f), cy)
            path.lineTo(cx - g(7f), cy + g(5f))
            c.drawPath(path, glyph)
            c.drawLine(cx, cy + g(6f), cx + g(7f), cy + g(6f), glyph)
        }

        private fun drawBack(c: Canvas, cx: Float, cy: Float) {
            path.reset()
            path.moveTo(cx + g(1f), cy - g(6f))
            path.lineTo(cx - g(5f), cy)
            path.lineTo(cx + g(1f), cy + g(6f))
            c.drawPath(path, glyph)
            c.drawLine(cx - g(5f), cy, cx + g(7f), cy, glyph)
        }

        private fun drawCancel(c: Canvas, cx: Float, cy: Float) {
            c.drawLine(cx - g(6f), cy - g(6f), cx + g(6f), cy + g(6f), glyph)
            c.drawLine(cx + g(6f), cy - g(6f), cx - g(6f), cy + g(6f), glyph)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (leaving) return true
            val n = bars.size
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    moved = false
                    active = (0 until n).firstOrNull { barRects[it].contains(event.x, event.y) } ?: -1
                    pressedButton = -1
                    backPressed = false
                    if (onAdvanced) {
                        backPressed = abs(backCentre[0] - event.x) < dp(36f) && abs(backCentre[1] - event.y) < dp(36f)
                    } else {
                        pressedButton =
                            buttonCentres.indexOfFirst {
                                abs(it[0] - event.x) < dp(36f) && abs(it[1] - event.y) < dp(36f)
                            }
                    }
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (active >= 0) {
                        val dx = event.x - downX
                        if (abs(dx) > dp(8f)) moved = true
                        val r = barRects[active]
                        drag[active] = max(0f, dx).coerceAtMost(r.width() - knob - dp(12f))
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val i = active
                    if (i >= 0) {
                        val r = barRects[i]
                        val travel = r.width() - knob - dp(12f)
                        if (!moved || drag[i] >= travel * 0.92f) {
                            run(bars[i])
                        } else {
                            springBack(i)
                        }
                    } else if (backPressed) {
                        backPressed = false
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        showAdvanced(false)
                    } else if (pressedButton >= 0) {
                        val b = buttons[pressedButton]
                        pressedButton = -1
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        when (b) {
                            Button.EMERGENCY -> leave { actions.emergency() }
                            Button.LOCKDOWN -> leave { actions.lockdown() }
                            // Straight to the Advanced page, on this same screen.
                            Button.ADVANCED -> showAdvanced(true)
                            Button.CANCEL -> leave(null)
                            Button.BACK -> showAdvanced(false)
                        }
                    } else if (!moved) {
                        if (onAdvanced) showAdvanced(false) else leave(null)
                    }
                    active = -1
                    invalidate()
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (active >= 0) springBack(active)
                    active = -1
                    pressedButton = -1
                    backPressed = false
                    invalidate()
                }
            }
            return true
        }

        private fun springBack(i: Int) {
            ValueAnimator.ofFloat(drag[i], 0f).apply {
                duration = 260
                interpolator = ease
                addUpdateListener {
                    drag[i] = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun run(bar: Bar) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            try {
                context.getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } catch (_: Exception) {}
            when (bar) {
                Bar.POWER -> leave { actions.powerOff() }
                Bar.RESTART -> leave { actions.restart() }
                Bar.SYSTEM_UI ->
                    leave {
                        // System UI is a persistent process: Android starts it again at once.
                        android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed({ Process.killProcess(Process.myPid()) }, 150)
                    }
                Bar.RECOVERY -> leave { reboot("recovery") }
                Bar.BOOTLOADER -> leave { reboot("bootloader") }
            }
        }

        private fun reboot(reason: String) {
            val pm = context.getSystemService(PowerManager::class.java) ?: return
            // reboot() does not return; keep it off the main thread.
            Thread({
                try {
                    pm.reboot(reason)
                } catch (e: Exception) {
                    android.util.Log.w("MiraCLOPowerMenu", "Could not reboot to $reason", e)
                }
            }, "MiraReboot").start()
        }
    }
}
