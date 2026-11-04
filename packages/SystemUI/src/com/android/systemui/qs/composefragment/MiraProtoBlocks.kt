/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One of a style's widgets (pills, media, sliders, the tile area...) that edit mode can move. */
internal class MiraEditBlock(val id: String, val label: String, val removable: Boolean)

/**
 * The order of a style's widgets, and which are hidden, kept in Settings.Secure
 * "miraclo_qs_blocks_<style>" as "id,id|hidden,hidden". Widgets the style adds later go at the end.
 */
internal class MiraBlockLayout(
    private val context: Context,
    style: Int,
    val blocks: List<MiraEditBlock>,
) {
    private val key = "miraclo_qs_blocks_$style"
    var order by mutableStateOf(blocks.map { it.id })
        private set
    var hidden by mutableStateOf(emptySet<String>())
        private set

    fun load() {
        val raw =
            runCatching {
                    Settings.Secure.getStringForUser(
                        context.contentResolver, key, ActivityManager.getCurrentUser())
                }
                .getOrNull()
        val ids = blocks.map { it.id }
        val parts = raw?.split("|").orEmpty()
        val saved = parts.getOrNull(0)?.split(",")?.filter { it in ids }.orEmpty().distinct()
        order = saved + ids.filter { it !in saved }
        hidden =
            parts.getOrNull(1)
                ?.split(",")
                ?.filter { id -> blocks.any { it.id == id && it.removable } }
                ?.toSet()
                .orEmpty()
    }

    private fun save() {
        runCatching {
            Settings.Secure.putStringForUser(
                context.contentResolver,
                key,
                order.joinToString(",") + "|" + hidden.joinToString(","),
                ActivityManager.getCurrentUser(),
            )
        }
    }

    /** The widgets to draw, in order. */
    val shown: List<String>
        get() = order.filter { it !in hidden }

    fun block(id: String): MiraEditBlock? = blocks.firstOrNull { it.id == id }

    /** Moves [id] one place up ([delta] = -1) or down (+1) among the shown widgets. */
    fun move(id: String, delta: Int) {
        val list = order.toMutableList()
        val from = list.indexOf(id)
        if (from < 0) return
        var to = from + delta
        // skip hidden ones so every press visibly moves the widget
        while (to in list.indices && list[to] in hidden) to += delta
        if (to !in list.indices) return
        list.removeAt(from)
        list.add(to, id)
        order = list
        save()
    }

    fun setHidden(id: String, hide: Boolean) {
        if (block(id)?.removable != true) return
        hidden = if (hide) hidden + id else hidden - id
        save()
    }
}

@Composable
internal fun rememberMiraBlockLayout(style: Int, blocks: List<MiraEditBlock>): MiraBlockLayout {
    val context = LocalContext.current
    return remember(style) { MiraBlockLayout(context, style, blocks).also { it.load() } }
}

private val MiraArrowUp: ImageVector =
    MiraCLOIcons.icon("M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6 -6,6z")
private val MiraArrowDown: ImageVector =
    MiraCLOIcons.icon("M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6 1.41,-1.41z")

/**
 * Edit mode › the style's widgets: move each up or down, − hides one (media, sliders...), + brings
 * it back. Drawn above the tiles in the styled editor.
 */
@Composable
internal fun MiraBlockEditor(look: MiraEditLook, layout: MiraBlockLayout, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Layout",
            color = look.fg2,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
        )
        val shown = layout.shown
        layout.order.forEach { id ->
            val b = layout.block(id) ?: return@forEach
            val isHidden = id in layout.hidden
            val at = shown.indexOf(id)
            Row(
                Modifier.fillMaxWidth()
                    .height(48.dp)
                    .then(look.tileSurface(RoundedCornerShape(24.dp)))
                    .padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    b.label + if (isHidden) " · hidden" else "",
                    color = look.fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).alpha(if (isHidden) 0.5f else 1f),
                )
                if (!isHidden) {
                    MiraBlockButton(MiraArrowUp, look.fg, enabled = at > 0) { layout.move(id, -1) }
                    MiraBlockButton(MiraArrowDown, look.fg, enabled = at in 0 until shown.size - 1) {
                        layout.move(id, 1)
                    }
                }
                if (b.removable) {
                    Box(
                        Modifier.size(28.dp)
                            .background(if (isHidden) look.accent else Color(0xFF8E8E93), CircleShape)
                            .protoTap { layout.setHidden(id, !isHidden) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isHidden) "+" else "−",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiraBlockButton(icon: ImageVector, tint: Color, enabled: Boolean, onTap: () -> Unit) {
    Box(
        Modifier.size(34.dp).alpha(if (enabled) 1f else 0.3f).protoTap(enabled = enabled, onTap = onTap),
        contentAlignment = Alignment.Center,
    ) {
        MiraProtoGlyph(icon, tint, 22.dp)
    }
}
