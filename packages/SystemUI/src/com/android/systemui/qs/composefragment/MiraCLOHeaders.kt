/*
 * Copyright (C) 2026 MIRACLO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.composefragment

import android.content.Intent
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.animation.Expandable
import com.android.systemui.animation.Expandable as SysuiExpandable
import com.android.systemui.qs.panels.ui.compose.infinitegrid.MiraCLOLook
import kotlinx.coroutines.delay

/** Icons for the MIRACLO Quick Settings headers, from the Material icon set (Apache 2.0). */
internal object MiraCLOIcons {
    val Edit =
        icon(
            "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 " +
                "0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"
        )
    val Add = icon("M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val LightMode =
        icon(
            "M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5s5,-2.24 5,-5S14.76,7 12,7zM2,13h2c0.55,0 1," +
                "-0.45 1,-1s-0.45,-1 -1,-1H2c-0.55,0 -1,0.45 -1,1S1.45,13 2,13zM20,13h2c0.55,0 1," +
                "-0.45 1,-1s-0.45,-1 -1,-1h-2c-0.55,0 -1,0.45 -1,1S19.45,13 20,13zM11,2v2c0,0.55 " +
                "0.45,1 1,1s1,-0.45 1,-1V2c0,-0.55 -0.45,-1 -1,-1S11,1.45 11,2zM11,20v2c0,0.55 " +
                "0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1S11,19.45 11,20zM5.99,4.58c-0.39," +
                "-0.39 -1.03,-0.39 -1.41,0c-0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03," +
                "0.39 1.41,0s0.39,-1.03 0,-1.41L5.99,4.58zM18.36,16.95c-0.39,-0.39 -1.03,-0.39 " +
                "-1.41,0c-0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0c0.39," +
                "-0.39 0.39,-1.03 0,-1.41L18.36,16.95zM19.42,5.99c0.39,-0.39 0.39,-1.03 0,-1.41c" +
                "-0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 " +
                "1.41,0L19.42,5.99zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41c-0.39,-0.39 -1.03," +
                "-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0L7.05,18.36z"
        )
    val Headphones =
        icon(
            "M12,1c-4.97,0 -9,4.03 -9,9v7c0,1.66 1.34,3 3,3h3v-8H5v-2c0,-3.87 3.13,-7 7,-7s7," +
                "3.13 7,7v2h-4v8h3c1.66,0 3,-1.34 3,-3v-7c0,-4.97 -4.03,-9 -9,-9z"
        )
    val VolumeUp =
        icon(
            "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5," +
                "-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01," +
                "-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"
        )
    val MusicNote =
        icon(
            "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4," +
                "-1.79 4,-4V7h4V3h-6z"
        )
    val Moon =
        icon(
            "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9s9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36c" +
                "-0.98,1.37 -2.58,2.26 -4.4,2.26c-2.98,0 -5.4,-2.42 -5.4,-5.4c0,-1.81 0.89,-3.42 " +
                "2.26,-4.4C12.92,3.04 12.46,3 12,3z"
        )
    val VolumeOff =
        icon(
            "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05," +
                "-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0," +
                "-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4" +
                "l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 " +
                "3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z"
        )
    val CastConnected =
        icon(
            "M1,18v3h3c0,-1.66 -1.34,-3 -3,-3zM1,14v2c2.76,0 5,2.24 5,5h2c0,-3.87 -3.13,-7 -7," +
                "-7zM19,7H5v1.63c3.96,1.28 7.09,4.41 8.37,8.37H19V7zM1,10v2c4.97,0 9,4.03 9,9h2c0," +
                "-6.08 -4.93,-11 -11,-11zM21,3H3c-1.1,0 -2,0.9 -2,2v3h2V5h18v14h-7v2h7c1.1,0 2,-0.9 " +
                "2,-2V5c0,-1.1 -0.9,-2 -2,-2z"
        )
    val Airplay =
        icon(
            "M6,22h12l-6,-6zM21,3H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h4v-2H3V5h18v12h-4v2h4c1.1," +
                "0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z"
        )
    val Devices =
        icon(
            "M4,6h18V4H4c-1.1,0 -2,0.9 -2,2v11H0v3h14v-3H4V6zM23,8h-6c-0.55,0 -1,0.45 -1,1v10c0," +
                "0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1V9c0,-0.55 -0.45,-1 -1,-1zM22,17h-4v-7h4v7z"
        )
    val Power =
        icon(
            "M13,3h-2v10h2L13,3zM17.83,5.17l-1.42,1.42C17.99,7.86 19,9.81 19,12c0,3.87 -3.13,7 " +
                "-7,7s-7,-3.13 -7,-7c0,-2.19 1.01,-4.14 2.58,-5.42L6.17,5.17C4.23,6.82 3,9.26 " +
                "3,12c0,4.97 4.03,9 9,9s9,-4.03 9,-9c0,-2.74 -1.23,-5.18 -3.17,-6.83z"
        )
    val Settings =
        icon(
            "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03," +
                "-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 " +
                "-0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 " +
                "-0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 " +
                "8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 " +
                "2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03," +
                "1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39," +
                "-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24," +
                "0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22," +
                "0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12," +
                "15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 " +
                "12,15.6z"
        )

    fun icon(path: String): ImageVector =
        ImageVector.Builder(
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}

/** Where each look's header starts, in dp from the top of the screen (390dp-wide mocks). */
internal fun miracloHeaderTop(look: Int): Int =
    when (look) {
        MiraCLOLook.IOS -> 18
        MiraCLOLook.ONEUI -> 36
        else -> 44
    }

/** The carrier name for the header row (no status icons: the looks don't need them). */
@Composable
private fun miracloCarrier(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
                context.getSystemService(TelephonyManager::class.java)?.networkOperatorName
            }
            .getOrNull()
            .orEmpty()
    }
}

/**
 * The header each MIRACLO look draws at the top of Quick Settings, in place of the stock shade
 * header and footer. Its height is fixed so the controls start exactly where the mocks put them:
 * - ColorOS: edit and settings circles (36dp) at the edges, the carrier name underneath.
 * - iOS: add and power circles (34dp), the carrier name underneath.
 * - One UI: the carrier name, then the time and date with edit, power and settings buttons.
 */
@Composable
internal fun MiraCLOStyleHeader(
    look: Int,
    onEdit: () -> Unit,
    onPower: ((SysuiExpandable) -> Unit)?,
    onOpen: (Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSettings = { onOpen(Intent(Settings.ACTION_SETTINGS)) }
    val carrier = miracloCarrier()
    val fg = MaterialTheme.colorScheme.onSurface
    when (look) {
        MiraCLOLook.ONEUI -> MiraCLOOneUiHeader(carrier, onEdit, onPower, onSettings, modifier)
        MiraCLOLook.IOS ->
            Box(modifier.fillMaxWidth().height(106.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MiraCLOGlassButton(MiraCLOIcons.Add, "Edit controls", 34.dp, 16.dp) { onEdit() }
                    MiraCLOGlassPowerButton(onPower, 34.dp, 16.dp)
                }
                Text(
                    carrier,
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 44.dp, top = 62.dp),
                )
            }
        else ->
            Box(modifier.fillMaxWidth().height(98.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MiraCLOGlassButton(MiraCLOIcons.Edit, "Edit controls", 36.dp, 18.dp) { onEdit() }
                    MiraCLOGlassButton(MiraCLOIcons.Settings, "Settings", 36.dp, 20.dp) {
                        onSettings()
                    }
                }
                Text(
                    carrier,
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 38.dp, top = 60.dp),
                )
            }
    }
}

@Composable
private fun MiraCLOGlassButton(
    icon: ImageVector,
    label: String,
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    val glass = miracloGlass()
    Box(
        modifier =
            Modifier.size(size)
                .glassSurface(glass, CircleShape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = glass.iconOff, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun MiraCLOGlassPowerButton(
    onPower: ((SysuiExpandable) -> Unit)?,
    size: Dp,
    iconSize: Dp,
) {
    if (onPower == null) {
        Spacer(Modifier.width(size))
        return
    }
    val glass = miracloGlass()
    Expandable(
        color = Color.Transparent,
        shape = CircleShape,
        onClick = onPower,
        modifier = Modifier.size(size).glassSurface(glass, CircleShape),
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(
                MiraCLOIcons.Power,
                contentDescription = "Power menu",
                tint = glass.iconOff,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * One UI: the carrier name, then the time (26sp bold) and date (17sp) on the left with plain
 * edit, power and settings buttons (44dp) on the right. White in light and dark, as on One UI.
 */
@Composable
private fun MiraCLOOneUiHeader(
    carrier: String,
    onEdit: () -> Unit,
    onPower: ((SysuiExpandable) -> Unit)?,
    onSettings: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    val time =
        remember(now) {
            DateFormat.format(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", now)
                .toString()
        }
    val date = remember(now) { DateFormat.format("EEE, MMM d", now).toString() }
    val text = Color.White
    Box(modifier.fillMaxWidth().height(86.dp)) {
        Text(
            carrier,
            color = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(start = 26.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 14.dp, top = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, color = text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
            Text(
                date,
                color = text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            MiraCLOPlainButton(MiraCLOIcons.Edit, "Edit", text, onEdit)
            if (onPower != null) {
                Expandable(color = Color.Transparent, shape = CircleShape, onClick = onPower) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            MiraCLOIcons.Power,
                            contentDescription = "Power menu",
                            tint = text,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }
            }
            MiraCLOPlainButton(MiraCLOIcons.Settings, "Settings", text, onSettings)
        }
    }
}

@Composable
private fun MiraCLOPlainButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(23.dp))
    }
}
