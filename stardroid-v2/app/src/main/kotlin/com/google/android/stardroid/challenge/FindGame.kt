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
}