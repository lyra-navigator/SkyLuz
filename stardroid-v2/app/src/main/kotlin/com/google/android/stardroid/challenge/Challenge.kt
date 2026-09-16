/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.challenge

import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.ui.objectinfo.IdentifyGeometry

/**
 * A starter challenge ("constellations to find", custom-constellations.md §4b): a shape drawn
 * among REAL catalog stars. [solution] lists the strokes the player must reproduce; each vertex
 * is a real star position. [exampleAsset] is a shipped example picture; [center]/[radiusDeg]
 * aim the map at the right field. Bonus challenges unlock after their base is complete.
 */
data class Challenge(
    val id: String,
    val name: String,
    val center: RaDec,
    val radiusDeg: Double,
    val exampleAsset: String,
    /** Solution strokes of real star positions (J2000 degrees). */
    val strokes: List<List<RaDec>>,
    val toleranceDeg: Double = DEFAULT_TOLERANCE_DEG,
    /** Set when this challenge unlocks only after another one is complete. */
    val unlocksAfter: String? = null,
) {
    /** Distinct vertices the player must cover. */
    val vertices: List<RaDec> by lazy { strokes.flatten().distinct() }

    companion object {
        /** Kid-friendly default: a generous target on the phone's sky. */
        const val DEFAULT_TOLERANCE_DEG = 3.0
    }
}

/**
 * Pure scoring for challenge + find modes: which solution vertices a set of tapped positions
 * covers (each tap covers at most its nearest vertex, within tolerance), so "complete" is
 * honest even when the player taps extra stars. No Android deps — unit-testable.
 */
object ChallengeScorer {
    data class Progress(
        val coveredVertices: Int,
        val totalVertices: Int,
        val taps: Int,
    ) {
        val complete: Boolean get() = totalVertices > 0 && coveredVertices == totalVertices
        val fraction: Float get() = if (totalVertices == 0) 0f else coveredVertices.toFloat() / totalVertices
    }

    fun progress(
        solutionVertices: List<RaDec>,
        taps: List<RaDec>,
        toleranceDeg: Double,
    ): Progress {
        if (solutionVertices.isEmpty()) return Progress(0, 0, taps.size)
        val covered = BooleanArray(solutionVertices.size)
        var coveredCount = 0
        for (tap in taps) {
            val tapDir = tap.toGeocentricVector()
            var bestIdx = -1
            var bestSep = Double.MAX_VALUE
            for ((i, vertex) in solutionVertices.withIndex()) {
                if (covered[i]) continue
                val sep =
                    IdentifyGeometry.angularSeparationDeg(tapDir, vertex.toGeocentricVector())
                if (sep < toleranceDeg && sep < bestSep) {
                    bestSep = sep
                    bestIdx = i
                }
            }
            if (bestIdx >= 0) {
                covered[bestIdx] = true
                coveredCount++
            }
        }
        return Progress(coveredCount, solutionVertices.size, taps.size)
    }

    /** The vertex a tap should snap to during a challenge (bias), or null when free. */
    fun biasedSnap(
        solutionVertices: List<RaDec>,
        tap: RaDec,
        toleranceDeg: Double,
    ): RaDec? {
        val tapDir = tap.toGeocentricVector()
        var best: RaDec? = null
        var bestSep = toleranceDeg
        for (vertex in solutionVertices) {
            val sep = IdentifyGeometry.angularSeparationDeg(tapDir, vertex.toGeocentricVector())
            if (sep < bestSep) {
                bestSep = sep
                best = vertex
            }
        }
        return best
    }
}