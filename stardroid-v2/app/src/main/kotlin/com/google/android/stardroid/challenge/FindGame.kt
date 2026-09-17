/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.challenge

import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.ui.objectinfo.IdentifyGeometry

/**
 * Find mode (custom-constellations.md §4b): the map hides a real IAU constellation's lines;
 * the player taps the stars they think belong to it, and [score] compares the taps against
 * the figure's vertices. Reuses [ChallengeScorer] — a find is a challenge whose solution is
 * the real figure. Tap tolerance scales with FOV like draw mode (kid-generous by default).
 */
object FindGame {
    /** Figure vertices in J2000 degrees, deduplicated (shared corners count once). */
    fun vertices(figure: Figure): List<RaDec> = figure.strokes.flatten().distinct()

    fun score(
        figure: Figure,
        taps: List<RaDec>,
        toleranceDeg: Double = 5.0,
    ): ChallengeScorer.Progress = ChallengeScorer.progress(vertices(figure), taps, toleranceDeg)

    /** The figure's centroid (unit-vector mean, re-normalized) — the Tip slew target. */
    fun center(figure: Figure): RaDec {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        for (v in vertices(figure)) {
            val d = v.toGeocentricVector()
            x += d.x
            y += d.y
            z += d.z
        }
        return RaDec.fromGeocentricVector(Vector3(x, y, z).normalized())
    }

    /** Tip zoom: the figure's angular radius from its centroid, clamped to sane FOVs. */
    fun radiusDeg(figure: Figure): Double {
        val c = center(figure).toGeocentricVector()
        val maxSep =
            vertices(figure).maxOf { IdentifyGeometry.angularSeparationDeg(c, it.toGeocentricVector()) }
        return maxOf(5.0, minOf(40.0, maxSep + 3.0))
    }

    /** The Tip's tap tolerance: FOV-scaled like draw mode. */
    const val TAP_TOLERANCE_DEG = 4.0
}