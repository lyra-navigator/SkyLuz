/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.catalog.CustomFigureRepository
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.render.api.Rgba
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/**
 * Draws the user's own constellations (custom-constellations.md) as polylines, styled a touch
 * brighter than the IAU figure lines so a personal drawing reads as "mine" next to the official
 * figures. Ignores the clock and the locale: pure geometric content from the user-owned store.
 */
class CustomFiguresLayer(
    private val customFigures: CustomFigureRepository,
    override val depth: Int = 55, // just above the IAU figure lines, below horizon
    private val mapContext: CoroutineContext = Dispatchers.Default,
) : SkyLayer {
    override val id: LayerId = LAYER_ID

    override fun scenes(): Flow<LayerScene> =
        customFigures
            .figures()
            .map { figures ->
                val lines =
                    figures
                        .filter { it.visible }
                        .flatMap { cf ->
                            cf.figure.strokes.map { stroke ->
                                LinePrimitive(
                                    stroke.map { it.toGeocentricVector() },
                                    LINE_COLOR,
                                    LINE_WIDTH_DP,
                                )
                            }
                        }
                LayerScene(depth = depth, lines = lines)
            }.flowOn(mapContext)

    companion object {
        val LAYER_ID = LayerId("custom/figures")
        private val LINE_COLOR = Rgba(0xE6 / 255f, 0xB0 / 255f, 0xFF / 255f, 0xD9 / 255f)
        private const val LINE_WIDTH_DP = 1.8
    }
}
