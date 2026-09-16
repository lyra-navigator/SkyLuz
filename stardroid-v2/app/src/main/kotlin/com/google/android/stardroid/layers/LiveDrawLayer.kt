/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.ui.draw.DrawPoint
import com.google.android.stardroid.ui.draw.DrawState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext

/**
 * Wraps a base [SkyLayer] with the constellation draw mode's in-progress preview: the draft
 * strokes (bright gold) render alongside the saved figures, so taps are visible the moment
 * they land. Id/depth/parameters come from the wrapped [CustomFiguresLayer].
 */
class LiveDrawLayer(
    private val delegate: CustomFiguresLayer,
    drawState: Flow<DrawState>,
    mapContext: CoroutineContext = Dispatchers.Default,
) : SkyLayer {
    override val id = delegate.id
    override val depth: Int = delegate.depth
    override val parameters get() = delegate.parameters

    private val scenes: Flow<LayerScene> =
        combine(delegate.scenes(), drawState, LiveDrawLayer::mergeScenes).flowOn(mapContext)

    override fun scenes(): Flow<LayerScene> = scenes

    companion object {
        internal fun mergeScenes(
            saved: LayerScene,
            drawing: DrawState,
        ): LayerScene {
            val draft = draftLines(drawing)
            return if (draft.isEmpty()) {
                saved
            } else {
                LayerScene(depth = saved.depth, lines = saved.lines + draft)
            }
        }

        /** Segments of the drawing in progress: open stroke, then the closed ones. */
        internal fun segments(d: DrawState): List<List<DrawPoint>> =
            d.openStroke.windowed(2) + d.closedStrokes.flatMap { it.windowed(2) }

        internal fun draftLines(d: DrawState): List<LinePrimitive> =
            segments(d)
                .filter { it[0].raDec != it[1].raDec }
                .map { (a, b) ->
                    LinePrimitive(
                        listOf(a.raDec.toGeocentricVector(), b.raDec.toGeocentricVector()),
                        DRAFT_COLOR,
                        DRAFT_WIDTH_DP,
                    )
                }

        /** Warm gold, clearly distinct from both IAU lines and saved-figure violet. */
        private val DRAFT_COLOR = Rgba(1f, 0.9f, 0.55f, 0.95f)
        private const val DRAFT_WIDTH_DP = 2.4
    }
}