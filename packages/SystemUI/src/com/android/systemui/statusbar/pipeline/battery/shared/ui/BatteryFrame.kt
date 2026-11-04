/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.battery.shared.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addSvg
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Instead of reading from xml, we can define the battery here with a single Path */
object BatteryFrame {
    val pathSpec =
        PathSpec(
            path =
                Path().apply {
                    addSvg(
                        "M17.5 0H2C0.895431 0 0 0.895431 0 2V10C0 11.1046 0.89543 12 2 12H17.5C18.6046 12 19.5 11.1046 19.5 10V8H19.9231C20.5178 8 21 7.51785 21 6.92308V5.07692C21 4.48215 20.5178 4 19.9231 4H19.5V2C19.5 0.895431 18.6046 0 17.5 0Z"
                    )
                },
            viewportHeight = 12.dp,
            viewportWidth = 21.dp,
        )

    /**
     * MIRACLO: the battery shape of the status bar icon style (0 Android 17, 1 ColorOS, 2 iOS,
     * 3 One UI), set from System UI's resources (the style's overlay). Read while measuring and
     * drawing, so a change redraws every battery.
     */
    private val miracloStyleState = androidx.compose.runtime.mutableIntStateOf(0)
    var miracloStyle: Int
        get() = miracloStyleState.intValue
        set(value) {
            miracloStyleState.intValue = value.coerceIn(0, 3)
        }

    // body width and corner radius per style (13 high)
    private val miracloWidths = floatArrayOf(24f, 26f, 27f, 30f)
    private val miracloRadii = floatArrayOf(4f, 4f, 4.6f, 6.5f)

    private fun miracloBody(w: Float, r: Float) =
        PathSpec(
            path =
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, 0f, w, 13f),
                            topLeft = CornerRadius(r),
                            topRight = CornerRadius(r),
                            bottomRight = CornerRadius(r),
                            bottomLeft = CornerRadius(r),
                        )
                    )
                },
            viewportWidth = w.dp,
            viewportHeight = 13.dp,
        )

    private fun miracloCap(w: Float, h: Float) =
        PathSpec(
            path =
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, 0f, w, h),
                            topLeft = CornerRadius.Zero,
                            topRight = CornerRadius(w),
                            bottomRight = CornerRadius(w),
                            bottomLeft = CornerRadius.Zero,
                        )
                    )
                },
            viewportWidth = w.dp,
            viewportHeight = h.dp,
        )

    private val miracloBodies = Array(4) { miracloBody(miracloWidths[it], miracloRadii[it]) }

    /** The body of the current style (Android 17's is the first). */
    val bodyPathSpec: PathSpec
        get() = miracloBodies[miracloStyle]

    /** True when the style draws the cap (One UI's battery is a plain pill). */
    val hasCap: Boolean
        get() = miracloStyle != 3

    private val stockBodyPathSpec: PathSpec =
        PathSpec(
            path =
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, 0f, 24f, 13f),
                            topLeft = CornerRadius(4f),
                            topRight = CornerRadius(4f),
                            bottomRight = CornerRadius(4f),
                            bottomLeft = CornerRadius(4f),
                        )
                    )
                },
            viewportWidth = 24.dp,
            viewportHeight = 13.dp,
        )

    private val miracloCaps by lazy {
        arrayOf(stockCapPathSpec, miracloCap(2.2f, 6f), miracloCap(2f, 5f), stockCapPathSpec)
    }

    /** The cap of the current style. */
    val capPathSpec: PathSpec
        get() = miracloCaps[miracloStyle]

    private val stockCapPathSpec: PathSpec =
        PathSpec(
            path =
                Path().apply {
                    addSvg(
                        "M0.3333,-0.0037L0,-0.0037L0,6.0234L0.3333,6.0234C0.9777,6.0234 1.5,5.5011 1.5,4.8567L1.5,1.163C1.5,0.5187 0.9777,-0.0037 0.3333,-0.0037Z"
                    )
                },
            viewportWidth = 1.5.dp,
            viewportHeight = 6.03.dp,
        )

    /** The width of the drawable that is usable for inside elements */
    val innerWidth: Float
        get() = miracloWidths[miracloStyle]

    /** The height of the drawable that is usable for inside elements */
    const val innerHeight = 13f

    /** Corner radius for the battery body */
    val cornerRadius: Float
        get() = miracloRadii[miracloStyle]
}

/**
 * Encapsulates both the [Path] and the drawn dimensions of the internal SVG path. Use [scaleTo] to
 * determine the appropriate scale factor (x and y) to fit the frame into its container
 */
data class PathSpec(val path: Path, val viewportWidth: Dp, val viewportHeight: Dp) {
    /** Return the appropriate scale to achieve FIT_CENTER-type scaling */
    fun scaleTo(w: Float, h: Float): Float {
        // FIT_CENTER for the path, this determines how much we need to scale up to fit the bounds
        // without skewing
        val xScale = w / viewportWidth.value
        val yScale = h / viewportHeight.value

        return min(xScale, yScale)
    }

    fun scaledSize(scale: Float): Size =
        Size(width = viewportWidth.value * scale, height = viewportHeight.value * scale)
}
