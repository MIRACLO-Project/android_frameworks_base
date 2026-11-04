/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.miraclo

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog

/**
 * Power menu › Advanced: Restart System UI, Reboot to recovery, Reboot to bootloader.
 * System UI restarts by itself after its process ends (it is a persistent process); apps keep
 * running. The reboots use the power service (REBOOT and RECOVERY permissions).
 */
object MiraCLOAdvancedPower {
    private const val TAG = "MiraCLOAdvancedPower"

    @JvmStatic
    fun show(context: Context) {
        try {
            val dark =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            val d = context.resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            val fg = if (dark) 0xFFE3E3E8.toInt() else 0xFF1B1B1F.toInt()
            val fg2 = if (dark) 0xFFA9ABB3.toInt() else 0xFF5E5F66.toInt()
            val rowBg = if (dark) 0xFF2A2E37.toInt() else 0xFFEDEEF4.toInt()
            val accent = if (dark) 0xFFA8C7FA.toInt() else 0xFF0B57D0.toInt()
            val onAccent = if (dark) 0xFF0A305F.toInt() else 0xFFFFFFFF.toInt()

            lateinit var dialog: AlertDialog
            val list =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(18), dp(8), dp(18), dp(8))
                }
            fun row(icon: Int, title: String, sub: String, run: () -> Unit) {
                val r =
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        background =
                            GradientDrawable().apply {
                                cornerRadius = dp(20).toFloat()
                                setColor(rowBg)
                            }
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            dialog.dismiss()
                            Handler(Looper.getMainLooper()).postDelayed({ run() }, 250)
                        }
                    }
                val iv =
                    ImageView(context).apply {
                        setImageResource(icon)
                        setColorFilter(onAccent)
                        setPadding(dp(9), dp(9), dp(9), dp(9))
                        background =
                            GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(accent)
                            }
                    }
                r.addView(iv, LinearLayout.LayoutParams(dp(38), dp(38)))
                val texts =
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), 0, 0, 0)
                    }
                texts.addView(
                    TextView(context).apply {
                        text = title
                        setTextColor(fg)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    }
                )
                texts.addView(
                    TextView(context).apply {
                        text = sub
                        setTextColor(fg2)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    }
                )
                r.addView(texts)
                list.addView(
                    r,
                    LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                        .apply { bottomMargin = dp(6) },
                )
            }
            row(R.drawable.ic_miraclo_power_sysui, "Restart System UI", "Status bar, shade and lock screen") {
                Log.i(TAG, "Restarting System UI from the power menu")
                Process.killProcess(Process.myPid())
            }
            row(R.drawable.ic_miraclo_power_recovery, "Reboot to recovery", "Restarts into recovery mode") {
                reboot(context, "recovery")
            }
            row(R.drawable.ic_miraclo_power_bootloader, "Reboot to bootloader", "Restarts into fastboot") {
                reboot(context, "bootloader")
            }

            dialog =
                AlertDialog.Builder(context, R.style.Theme_SystemUI_Dialog)
                    .setTitle("Advanced")
                    .setView(list)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
            SystemUIDialog.applyFlags(dialog)
            SystemUIDialog.setShowForAllUsers(dialog, true)
            SystemUIDialog.registerDismissListener(dialog)
            dialog.show()
        } catch (e: Exception) {
            Log.w(TAG, "Could not show the Advanced menu", e)
        }
    }

    private fun reboot(context: Context, reason: String) {
        try {
            context.getSystemService(PowerManager::class.java)?.reboot(reason)
        } catch (e: Exception) {
            Log.w(TAG, "Could not reboot to $reason", e)
        }
    }
}
