/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.miraclo

import android.view.View

/**
 * MiraCLO split shade: keeps the notification list out of view while Quick Settings shows on
 * its own. A pull from the Quick Settings side moves the list off screen before the shade opens;
 * closing the shade brings it back. Main thread only.
 */
class MiraCLOShadePanels(private val stackView: () -> View?) {

    /** Called when a pull starts with the shade closed. */
    fun onPull(quickSettings: Boolean) {
        val stack = stackView() ?: return
        stack.translationX = if (quickSettings) -width(stack) else 0f
    }

    /** Called once the shade has closed. */
    fun onClosed() {
        stackView()?.translationX = 0f
    }

    private fun width(view: View): Float =
        (if (view.width > 0) view.width else view.resources.displayMetrics.widthPixels).toFloat()
}
