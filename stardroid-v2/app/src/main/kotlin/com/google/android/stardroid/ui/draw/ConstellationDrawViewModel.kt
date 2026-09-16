/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.draw

import com.google.android.stardroid.catalog.CatalogObject
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.CustomFigureRepository
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.ui.objectinfo.IdentifyGeometry
import kotlinx.coroutines.flow.first

/** One recorded tap: where the user aimed (snapped to the star when one is in tolerance). */
data class DrawPoint(
    val raDec: RaDec,
    /** The catalog star the point snapped to, or null for a free (between-stars) point. */
    val snappedTo: CelestialObjectId?,
)

/** Immutable snapshot of the drawing in progress, for the Compose layer. */
data class DrawState(
    /** Strokes the user has finished (closed by tapping the first star again or "new stroke"). */
    val closedStrokes: List<List<DrawPoint>> = emptyList(),
    /** The stroke under construction; empty when no stroke is open. */
    val openStroke: List<DrawPoint> = emptyList(),
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = (closedStrokes.isNotEmpty() || openStroke.size >= 2) && !saving
}

/**
 * The constellation draw mode (custom-constellations.md §2). Taps become sky directions via the
 * identify path's proven inverse projection, snap to the nearest catalog star within tolerance,
 * and append to the open stroke; re-tapping the open stroke's first star closes the stroke
 * (loop gesture, no duplicate vertex). Saving persists through [CustomFigureRepository], which
 * the custom-figures layer renders automatically.
 *
 * Free of Compose and Android so the geometry + state machine is plain unit-testable.
 */
class ConstellationDrawViewModel(
    private val customFigures: suspend () -> CustomFigureRepository,
    private val catalog: suspend () -> CatalogRepository,
) {
    private var state = DrawState()

    /** The latest snapshot; the map screen reads this for its overlay. */
    val drawState: DrawState get() = state

    private fun update(transform: (DrawState) -> DrawState) {
        state = transform(state)
    }

    /**
     * A tap at screen pixel ([xPx], [yPx]) on a [widthPx]×[heightPx] view of [camera].
     * Returns the recorded point, or null when the tap only closed the open stroke.
     */
    suspend fun onTap(
        xPx: Float,
        yPx: Float,
        widthPx: Int,
        heightPx: Int,
        camera: SkyCamera,
    ): DrawPoint? {
        if (state.saving) return null
        val direction = IdentifyGeometry.screenToDirection(camera, widthPx, heightPx, xPx, yPx)
        val tapRaDec = RaDec.fromGeocentricVector(direction)
        val snapped = nearestStar(tapRaDec, drawToleranceDeg(camera.fovDeg))
        val pointRaDec = snapped?.position ?: tapRaDec
        val tapStarId = snapped?.id
        val point = DrawPoint(raDec = pointRaDec, snappedTo = tapStarId)

        // Tapping the open stroke's first star closes it (loop gesture; the closing vertex is
        // the first one, so nothing is appended). With only one point recorded, the same tap
        // ABORTS the stroke — a lone point is discarded rather than left dangling.
        val open = state.openStroke
        if (open.isNotEmpty() && tapStarId != null && tapStarId == open.first().snappedTo) {
            if (open.size >= 2) {
                closeStroke()
            } else {
                update { it.copy(openStroke = emptyList()) }
            }
            return null
        }
        update { it.copy(openStroke = it.openStroke + point) }
        return point
    }

    /** Drop the last recorded point: from the open stroke, or from the newest closed one. */
    fun undo() {
        update { s ->
            when {
                s.openStroke.isNotEmpty() -> s.copy(openStroke = s.openStroke.dropLast(1))
                s.closedStrokes.isNotEmpty() -> {
                    val last = s.closedStrokes.last()
                    val trimmed = last.dropLast(1)
                    s.copy(closedStrokes = s.closedStrokes.dropLast(1) + listOfNotNull(trimmed.takeIf { it.size >= 2 }))
                }
                else -> s
            }
        }
    }

    /** Finish the open stroke (if it has a line's worth of points) without starting a new one. */
    fun endStroke() {
        update { s ->
            if (s.openStroke.size >= 2) {
                s.copy(closedStrokes = s.closedStrokes + listOf(s.openStroke), openStroke = emptyList())
            } else {
                s
            }
        }
    }

    /** Clears the drawing in progress entirely. */
    fun reset() {
        state = DrawState()
    }

    /** Persists the finished figure; returns the stored id, or null when there is nothing to save. */
    suspend fun save(name: String): String? {
        if (!state.canSave) return null
        val strokes = strokes()
        if (strokes.isEmpty()) return null
        update { it.copy(saving = true) }
        return try {
            val id =
                customFigures().save(
                    figure = Figure(owner = CelestialObjectId(""), strokes = strokes),
                    name = name,
                )
            state = DrawState()
            id
        } finally {
            update { it.copy(saving = false) }
        }
    }

    /** All strokes, closed ones first, the open one last. */
    fun strokes(): List<List<RaDec>> =
        (state.closedStrokes + listOf(state.openStroke))
            .filter { it.size >= 2 }
            .map { stroke -> stroke.map { it.raDec } }

    private fun closeStroke() {
        update { s ->
            if (s.openStroke.size >= 2) {
                s.copy(closedStrokes = s.closedStrokes + listOf(s.openStroke), openStroke = emptyList())
            } else {
                s.copy(openStroke = emptyList())
            }
        }
    }

    // --- catalog snap support ---------------------------------------------------------------

    private data class SnapStar(
        val id: CelestialObjectId,
        val position: RaDec,
    )

    private var starsCache: List<SnapStar>? = null

    private suspend fun stars(): List<SnapStar> {
        starsCache?.let { return it }
        val objects =
            catalog().layerObjects(
                LayerKind.STARS,
                com.google.android.stardroid.catalog.LocaleSpec.ENGLISH,
            )
        // First emission is enough: a draw session is short, and a stale-but-close snap beats
        // no snap. (layerObjects re-emits on locale change; English is the stable request.)
        val snapshot: List<CatalogObject> = objects.first()
        starsCache = snapshot.map { SnapStar(it.id, it.position) }
        return starsCache!!
    }

    private suspend fun nearestStar(
        tap: RaDec,
        toleranceDeg: Double,
    ): SnapStar? {
        val tapDir = tap.toGeocentricVector()
        return stars()
            .map { it to IdentifyGeometry.angularSeparationDeg(tapDir, it.position.toGeocentricVector()) }
            .filter { (_, sep) -> sep < toleranceDeg }
            .minByOrNull { (_, sep) -> sep }
            ?.first
    }

    /** v1-style FOV-scaled tolerance for star snapping (identify's semantics, no label reach). */
    private fun drawToleranceDeg(fovDeg: Double): Double =
        (SNAP_TOLERANCE_DEGREES * fovDeg / IdentifyGeometry.MAX_FOV_DEG).coerceAtLeast(MIN_SNAP_TOLERANCE_DEG)

    companion object {
        /** v1's default tap tolerance, reused as the draw-mode snap radius at widest zoom. */
        const val SNAP_TOLERANCE_DEGREES = 5.0

        /** The tolerance floor at high zoom, matching identify. */
        const val MIN_SNAP_TOLERANCE_DEG = 0.5
    }
}