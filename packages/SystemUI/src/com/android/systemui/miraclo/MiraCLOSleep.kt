/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.miraclo

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserHandle
import android.util.Log

/**
 * Double tap to lock (Mira Studio › Launcher › Gestures): the launcher asks System UI, which
 * holds the power permission, to turn the screen off as the power button would. Only apps
 * holding STATUS_BAR (the launcher) can send it.
 */
object MiraCLOSleep {
    const val ACTION = "com.android.systemui.miraclo.action.SLEEP"
    private const val TAG = "MiraCLOSleep"
    @Volatile private var registered = false

    @JvmStatic
    fun register(context: Context) {
        if (registered) return
        registered = true
        try {
            context.registerReceiverAsUser(
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        try {
                            c.getSystemService(PowerManager::class.java)
                                ?.goToSleep(
                                    SystemClock.uptimeMillis(),
                                    PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON,
                                    0,
                                )
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not turn the screen off", e)
                        }
                    }
                },
                UserHandle.ALL,
                IntentFilter(ACTION),
                Manifest.permission.STATUS_BAR,
                null,
                Context.RECEIVER_EXPORTED,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not register", e)
        }
    }
}
