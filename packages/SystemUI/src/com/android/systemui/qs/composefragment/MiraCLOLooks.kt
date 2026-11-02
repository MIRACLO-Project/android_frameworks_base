/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.animation.scene.ContentScope
import com.android.settingslib.volume.domain.interactor.AudioVolumeInteractor
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.qs.panels.ui.compose.GridLayout
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults
import com.android.systemui.qs.panels.ui.compose.infinitegrid.InfiniteGridLayout
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalMiraCLOHideTileLabels
import com.android.systemui.qs.panels.ui.compose.infinitegrid.MiraCLOLook
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.res.R

/**
 * Saved control layouts of the editable looks (Settings.Secure "miraclo_qs_layout_<look>"), kept
 * as Compose state and followed live. Main thread only.
 */
internal class MiraCLOLayoutStore(context: Context) {
    private val app = context.applicationContext ?: context
    private var watching = false

    var layouts by mutableStateOf<Map<Int, String?>>(emptyMap())
        private set

    fun watch() {
        if (watching) return
        watching = true
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) = refresh()
            }
        for (look in LOOKS) {
            try {
                app.contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(MiraCLOQsLayouts.key(look)),
                    false,
                    observer,
                    UserHandle.USER_ALL,
                )
            } catch (_: Exception) {}
        }
        refresh()
    }

    fun refresh() {
        layouts =
            LOOKS.associateWith { look ->
                try {
                    Settings.Secure.getStringForUser(
                        app.contentResolver,
                        MiraCLOQsLayouts.key(look),
                        ActivityManager.getCurrentUser(),
                    )
                } catch (_: Exception) {
                    null
                }
            }
    }

    fun save(look: Int, value: String?) {
        try {
            Settings.Secure.putStringForUser(
                app.contentResolver,
                MiraCLOQsLayouts.key(look),
                value,
                ActivityManager.getCurrentUser(),
            )
        } catch (_: Exception) {}
        layouts = layouts + (look to value)
    }

    private companion object {
        val LOOKS = listOf(MiraCLOLook.COLOROS, MiraCLOLook.IOS)
    }
}

/**
 * The body of the MiraCLO Quick Settings in the classic shade, for the selected look:
 * - ColorOS 17: the classic ColorOS layout, or the editable grid once it has been edited.
 * - iOS 27: the editable Control Center grid.
 * - One UI 9: the One UI Quick Panel.
 */
@Composable
internal fun ContentScope.MiraCLOLookBody(
    look: Int,
    store: MiraCLOLayoutStore,
    editor: MiraCLOQsEditor,
    ios: MiraCLOIosState,
    allTiles: List<TileViewModel>,
    gridLayout: GridLayout,
    infiniteGridLayout: InfiniteGridLayout,
    brightnessViewModel: BrightnessSliderViewModel,
    audioVolumeInteractor: AudioVolumeInteractor,
    showMedia: Boolean,
    media: @Composable (Modifier) -> Unit,
    mediaHost: @Composable (Modifier) -> Unit,
    listening: () -> Boolean,
    onOpen: (Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = remember(allTiles) { allTiles.miracloUsable() }
    when (look) {
        MiraCLOLook.ONEUI ->
            MiraCLOOneUiLayout(
                tiles = tiles,
                infiniteGridLayout = infiniteGridLayout,
                brightnessViewModel = brightnessViewModel,
                audioVolumeInteractor = audioVolumeInteractor,
                showMedia = showMedia,
                media = media,
                listening = listening,
                modifier = modifier,
            )
        else -> {
            val saved = remember(store.layouts, look) { MiraCLOQsLayouts.parse(store.layouts[look]) }
            if (look != MiraCLOLook.COLOROS || saved != null || editor.editing) {
                val layout = saved?.first ?: MiraCLOQsLayouts.defaults(look)
                val hidden = saved?.second ?: emptySet()
                val controls = remember(layout, hidden, tiles) { miracloResolve(layout, hidden, tiles) }
                val grid: @Composable () -> Unit = {
                    MiraCLOQsGrid(
                        look = look,
                        controls = controls,
                        hidden = hidden,
                        editor = editor,
                        tiles = tiles,
                        infiniteGridLayout = infiniteGridLayout,
                        brightnessViewModel = brightnessViewModel,
                        audioVolumeInteractor = audioVolumeInteractor,
                        showMedia = showMedia,
                        media = media,
                        listening = listening,
                        onSave = { items, removed ->
                            store.save(look, MiraCLOQsLayouts.serialize(items, removed))
                        },
                        onReset = { store.save(look, null) },
                    )
                }
                Box(modifier) {
                    mediaHost(Modifier.size(1.dp))
                    if (look == MiraCLOLook.IOS && !editor.editing) {
                        // iOS: the controls are the first of three pages, and the connectivity
                        // block grows into the full module on a long press.
                        LaunchedEffect(ios) {
                            snapshotFlow { listening() }.collect { if (!it) ios.reset() }
                        }
                        CompositionLocalProvider(LocalMiraCLOIosState provides ios) {
                            MiraCLOIosPages(
                                state = ios,
                                connectivity = remember(tiles) { connectivityPageTiles(tiles) },
                                infiniteGridLayout = infiniteGridLayout,
                                audioVolumeInteractor = audioVolumeInteractor,
                                showMedia = showMedia,
                                media = media,
                                listening = listening,
                                controls = grid,
                            )
                        }
                        if (ios.expanded) {
                            MiraCLOIosConnectivityModule(
                                tiles = remember(tiles) { connectivityTiles(tiles) },
                                infiniteGridLayout = infiniteGridLayout,
                                listening = listening,
                                onDismiss = { ios.expanded = false },
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    } else {
                        grid()
                    }
                }
            } else {
                Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    MiraCLOQuickSettingsLayout(
                        tiles = tiles,
                        gridLayout = gridLayout,
                        infiniteGridLayout = infiniteGridLayout,
                        brightnessViewModel = brightnessViewModel,
                        audioVolumeInteractor = audioVolumeInteractor,
                        showMedia = showMedia,
                        media = media,
                        mediaHost = mediaHost,
                        listening = listening,
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

/** Side margins of each look on the 390dp-wide mocks. */
internal fun miracloSideMargin(look: Int): Int =
    when (look) {
        MiraCLOLook.IOS -> 42
        MiraCLOLook.ONEUI -> 17
        else -> 38
    }

/** Opens the MiraCLO editor on the saved layout of [look], or on its default layout. */
internal fun miracloStartEdit(
    look: Int,
    store: MiraCLOLayoutStore,
    editor: MiraCLOQsEditor,
    tiles: List<TileViewModel>,
) {
    val saved = MiraCLOQsLayouts.parse(store.layouts[look])
    val hidden = saved?.second ?: emptySet()
    val usable = tiles.miracloUsable()
    editor.start(
        miracloResolve(saved?.first ?: MiraCLOQsLayouts.defaults(look), hidden, usable),
        hidden,
    )
}

/**
 * The tint each look lays over the shade blur behind Quick Settings, or an opaque panel when
 * Liquid Glass is off.
 */
internal fun miracloPanelTint(look: Int, liquid: Boolean, dark: Boolean): Color =
    when (look) {
        MiraCLOLook.IOS ->
            if (dark) Color(if (liquid) 0x59000000 else 0xF20E0F12.toInt())
            else Color(if (liquid) 0x26FFFFFF else 0xE6F2F2F7.toInt())
        MiraCLOLook.ONEUI ->
            if (dark) Color(if (liquid) 0x400A0E28 else 0xF0141A33.toInt())
            else Color(if (liquid) 0x10FFFFFF else 0xE65F86E6.toInt())
        else ->
            if (dark) Color(if (liquid) 0x8C06080C.toInt() else 0xFF0B0C0F.toInt())
            else Color(if (liquid) 0x40F0F2F7 else 0xE6F0F2F7.toInt())
    }

/** Tiles that cannot do anything on this device are left out of the MiraCLO looks. */
private val MIRACLO_HIDE_WHEN_UNAVAILABLE = setOf("wallet", "controls")

internal fun List<TileViewModel>.miracloUsable(): List<TileViewModel> = filterNot {
    it.spec.spec in MIRACLO_HIDE_WHEN_UNAVAILABLE &&
        it.currentState.state == android.service.quicksettings.Tile.STATE_UNAVAILABLE
}

/** Tiles that the One UI layout gives fixed places, by spec (aliases in order of preference). */
private val ONEUI_WIFI = listOf("internet", "wifi")
private val ONEUI_BT = listOf("bt", "bluetooth")
private val ONEUI_DIM = listOf("reduce_brightness")
private val ONEUI_CARDS = listOf(listOf("cast"), listOf("dnd", "modes"), listOf("controls"), listOf("hotspot"))

/** Small tiles shown while the One UI block is collapsed. */
private const val ONEUI_COLLAPSED_COUNT = 8

/**
 * One UI 9 Quick Panel.
 *
 * ```
 * [ Wi-Fi          ][ Bluetooth      ]
 * [ ( ) ( ) ( ) ( )                   ]  expandable block of round tiles; the handle shows all
 * [ ( ) ( ) ( ) ( )        ——         ]
 * [ ☀ brightness ===========   ] ( ☾ )  extra dim
 * [ ♪ volume =======           ] ( 🔔 ) ringer
 * [ ♪ media                           ]
 * [ Cast          ][ Modes          ]
 * [ Device control][ Hotspot        ]
 * ```
 */
@Composable
private fun ContentScope.MiraCLOOneUiLayout(
    tiles: List<TileViewModel>,
    infiniteGridLayout: InfiniteGridLayout,
    brightnessViewModel: BrightnessSliderViewModel,
    audioVolumeInteractor: AudioVolumeInteractor,
    showMedia: Boolean,
    media: @Composable (Modifier) -> Unit,
    listening: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    fun pick(specs: List<String>): TileViewModel? =
        specs.firstNotNullOfOrNull { s -> tiles.firstOrNull { it.spec.spec == s } }

    val wifi = pick(ONEUI_WIFI)
    val bt = pick(ONEUI_BT)
    val dim = pick(ONEUI_DIM)
    val cards = ONEUI_CARDS.mapNotNull { pick(it) }
    val fixed = listOfNotNull(wifi, bt, dim) + cards
    val small = remember(tiles, fixed) { tiles.filterNot { it in fixed } }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) small else small.take(ONEUI_COLLAPSED_COUNT)
    TileListener(fixed + shown, listening)

    val glass = miracloGlass()
    val card = RoundedCornerShape(34.dp)
    val tileHeight = CommonTileDefaults.TileHeight
    val gap = 10.dp

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = spacedBy(gap)) {
        if (wifi != null || bt != null) {
            Row(horizontalArrangement = spacedBy(gap)) {
                listOfNotNull(wifi, bt).forEach { tile ->
                    with(infiniteGridLayout) { MiraCLOWideTile(tile, listening, Modifier.weight(1f)) }
                }
                if (wifi == null || bt == null) Box(Modifier.weight(1f))
            }
        }

        // The expandable block of round tiles.
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .glassSurface(glass, card)
                    .animateContentSize()
                    .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompositionLocalProvider(LocalMiraCLOHideTileLabels provides true) {
                shown.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        row.forEach { tile ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(52.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.requiredSize(tileHeight).graphicsLayer {
                                            val s = 52.dp.toPx() / tileHeight.toPx()
                                            scaleX = s
                                            scaleY = s
                                        }
                                    ) {
                                        with(infiniteGridLayout) {
                                            MiraCLOWideTile(tile, listening, iconOnly = true)
                                        }
                                    }
                                }
                                if (expanded) {
                                    Text(
                                        text = tile.currentState.label?.toString() ?: "",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                                    )
                                }
                            }
                        }
                        repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            Box(
                modifier =
                    Modifier.width(120.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(50))
                        .semantics {
                            contentDescription = if (expanded) "Show fewer tiles" else "Show all tiles"
                        }
                        .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.55f))
                )
            }
        }

        // Brightness + extra dim, volume + ringer.
        Row(horizontalArrangement = spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MiraCLOBrightnessSlider(
                viewModel = brightnessViewModel,
                glass = glass,
                modifier = Modifier.weight(1f).height(tileHeight),
                vertical = false,
            )
            // Extra dim: the moon button flips Android's Extra dim (reduce bright colours).
            val context = LocalContext.current
            MiraCLOOneUiRoundButton(MiraCLOIcons.Moon, "Extra dim", glass, tileHeight) {
                val cr = context.contentResolver
                val on = Settings.Secure.getInt(cr, "reduce_bright_colors_activated", 0) != 0
                Settings.Secure.putInt(cr, "reduce_bright_colors_activated", if (on) 0 else 1)
            }
        }
        Row(horizontalArrangement = spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MiraCLOVolumeSlider(
                audioVolumeInteractor = audioVolumeInteractor,
                glass = glass,
                modifier = Modifier.weight(1f).height(tileHeight),
                vertical = false,
            )
            // Mute: mutes or unmutes media, like the One UI button next to the volume slider.
            val audio = LocalContext.current.getSystemService(AudioManager::class.java)
            MiraCLOOneUiRoundButton(MiraCLOIcons.VolumeOff, "Mute", glass, tileHeight) {
                audio?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE,
                    0,
                )
            }
        }

        // Media: the player card while something plays, otherwise a "Play music" pill.
        if (showMedia) {
            Box(Modifier.fillMaxWidth().height(tileHeight * 2 + gap).clip(MiraCLOMediaShape)) {
                media(Modifier.fillMaxSize())
            }
        } else {
            // A tap resumes the last player, as the pill does on One UI.
            val audio = LocalContext.current.getSystemService(AudioManager::class.java)
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(72.dp)
                        .glassSurface(glass, RoundedCornerShape(percent = 50))
                        .clickable(onClickLabel = "Play music") {
                            audio?.let { am ->
                                val code = KeyEvent.KEYCODE_MEDIA_PLAY
                                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                            }
                        }
                        .padding(start = 32.dp, end = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_volume_media),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Play music",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
                Icon(
                    MiraCLOIcons.CastConnected,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = spacedBy(gap)) {
                row.forEach { tile ->
                    with(infiniteGridLayout) { MiraCLOWideTile(tile, listening, Modifier.weight(1f)) }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** One UI: a round glass button with a white glyph (Extra dim, Mute). */
@Composable
private fun MiraCLOOneUiRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    glass: MiraCLOGlass,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier.size(size)
                .glassSurface(glass, androidx.compose.foundation.shape.CircleShape)
                .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
