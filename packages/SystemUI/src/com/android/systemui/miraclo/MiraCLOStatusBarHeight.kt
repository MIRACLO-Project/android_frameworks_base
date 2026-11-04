/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.miraclo

import android.app.ActivityManager
import android.content.Context
import android.content.om.FabricatedOverlay
import android.content.om.OverlayManager
import android.content.om.OverlayManagerTransaction
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.util.TypedValue

/**
 * Status bar height from Mira Studio (Top Bar › Icons › Height), applied live as a small runtime
 * overlay on the framework's status bar height, the same mechanism the theme engine uses for
 * colours. 0 or the panel's own height (34 dp) removes the overlay. The island centres on the
 * camera, not on the bar, so a taller bar never moves it off the camera.
 */
object MiraCLOStatusBarHeight {
    const val KEY = "miraclo_sb_height"          // dp, 34..44; 0 = default
    private const val TAG = "MiraCLOSbHeight"
    private const val NAME = "miraclo_statusbar_height"
    private const val DEFAULT_DP = 34
    private var started = false
    private var applied = -1

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = apply(app)
            }
        app.contentResolver.registerContentObserver(Settings.Secure.getUriFor(KEY), false, observer)
        apply(app)
    }

    private fun apply(context: Context) {
        val dp = Settings.Secure.getInt(context.contentResolver, KEY, 0)
        val want = if (dp <= DEFAULT_DP || dp > 44) 0 else dp
        if (want == applied) return
        try {
            val om = context.getSystemService(OverlayManager::class.java) ?: return
            val overlay = FabricatedOverlay.Builder("com.android.systemui", NAME, "android").build()
            val user = ActivityManager.getCurrentUser()
            val tx = OverlayManagerTransaction.Builder()
            if (want == 0) {
                tx.unregisterFabricatedOverlay(overlay.identifier)
            } else {
                val px = want * context.resources.displayMetrics.density
                val value = TypedValue.createComplexDimension(px, TypedValue.COMPLEX_UNIT_PX)
                for (n in listOf("status_bar_height_portrait", "status_bar_height_default")) {
                    overlay.setResourceValue("android:dimen/$n", TypedValue.TYPE_DIMENSION, value, null)
                }
                tx.registerFabricatedOverlay(overlay)
                tx.setEnabled(overlay.identifier, true, user)
                if (user != UserHandle.USER_SYSTEM) tx.setEnabled(overlay.identifier, true, UserHandle.USER_SYSTEM)
            }
            om.commit(tx.build())
            applied = want
        } catch (e: Exception) {
            // Never let a bad value take SystemUI down: keep the panel's own height.
            Log.w(TAG, "Could not apply status bar height $want dp", e)
        }
    }
}
