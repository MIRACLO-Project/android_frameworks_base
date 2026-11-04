/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.miraclo

import android.os.SystemClock
import android.view.MotionEvent

/** The last touch the shade window saw (written by the scene container, main thread). */
object MiraCLOShadeTouch {
    @Volatile var fingerDown = false
        private set
    @Volatile var lastEventAt = 0L
        private set

    @JvmStatic
    fun onEvent(e: MotionEvent) {
        lastEventAt = SystemClock.uptimeMillis()
        fingerDown =
            e.actionMasked != MotionEvent.ACTION_UP && e.actionMasked != MotionEvent.ACTION_CANCEL
    }
}
