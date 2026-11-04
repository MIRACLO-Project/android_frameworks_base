/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.miraclo

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.settingslib.mobile.MobileMappings.Config
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mira Studio › Status bar icons › Mobile network: the network type label and the call badges.
 * Applied on top of the carrier's own settings to the icon config every mobile icon reads.
 */
object MiraCLOStatusIcons {
    /** 0 = "4G" (default), 1 = "LTE". */
    const val KEY_NET_LABEL = "miraclo_sb_net_label"
    /** 1 = show 4G+ / LTE+ on carrier aggregation (default), 0 = plain 4G / LTE. */
    const val KEY_NET_PLUS = "miraclo_sb_net_plus"
    /** 0 = no badge, 1 = "VoLTE" (default), 2 = "HD". */
    const val KEY_VOLTE = "miraclo_sb_volte"
    /** 1 = Wi-Fi calling badge (default), 0 = off. */
    const val KEY_VOWIFI = "miraclo_sb_vowifi"

    private val KEYS = listOf(KEY_NET_LABEL, KEY_NET_PLUS, KEY_VOLTE, KEY_VOWIFI)

    private val _volteStyle = MutableStateFlow(1)
    /** Which call badge to draw while VoLTE is registered (see [KEY_VOLTE]). */
    val volteStyle: StateFlow<Int> = _volteStyle

    private fun read(context: Context, key: String, def: Int): Int =
        Settings.Secure.getIntForUser(context.contentResolver, key, def, UserHandle.USER_CURRENT)

    /** Writes the user's choices into [config] (read fresh each time the config is rebuilt). */
    fun apply(context: Context, config: Config) {
        // Labels follow the carrier until chosen in Mira Studio; the call badges start off.
        val label = read(context, KEY_NET_LABEL, -1)
        if (label >= 0) {
            config.show4gForLte = label == 0
            config.show4glteForLte = false
        }
        val plus = read(context, KEY_NET_PLUS, -1)
        if (plus >= 0) config.hideLtePlus = plus == 0
        val volte = read(context, KEY_VOLTE, 0)
        config.showVolteIcon = volte != 0
        _volteStyle.value = volte
        config.showVowifiIcon = read(context, KEY_VOWIFI, 0) != 0
    }

    /** Emits each time one of the settings changes, so the icon config is rebuilt. */
    fun changes(context: Context): Flow<Unit> = conflatedCallbackFlow {
        val resolver = context.contentResolver
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
        KEYS.forEach {
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(it), false, observer, UserHandle.USER_ALL)
        }
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
}
