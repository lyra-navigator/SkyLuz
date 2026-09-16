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
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ChallengeScorerTest {
    private val a = RaDec(10.0, 10.0)
    private val b = RaDec(11.0, 10.0)
    private val c = RaDec(10.0, 11.0)

    @Test
    fun tapsWithinTolerance_coverVertices() {
        val p = ChallengeScorer.progress(listOf(a, b, c), listOf(RaDec(10.05, 10.02), RaDec(11.04, 9.98)), 3.0)
        assertThat(p.coveredVertices).isEqualTo(2)
        assertThat(p.totalVertices).isEqualTo(3)
        assertThat(p.complete).isFalse()
    }

    @Test
    fun coveringEveryVertex_completes() {
        val p =
            ChallengeScorer.progress(
                listOf(a, b, c),
                listOf(RaDec(10.0, 10.0), RaDec(11.0, 10.0), RaDec(10.0, 11.0)),
                3.0,
            )
        assertThat(p.complete).isTrue()
        assertThat(p.fraction).isWithin(1e-6f).of(1f)
    }

    @Test
    fun aTapCoversAtMostItsNearestVertex_greedyTakesNextNearest() {
        // Two taps both near A: the first covers A; the second covers the next-nearest
        // UNCOVERED vertex (B, ~1° away) rather than being wasted — greedy by design,
        // so a stray double-tap never blocks completion.
        val p = ChallengeScorer.progress(listOf(a, b), listOf(a, RaDec(10.02, 10.02)), 3.0)
        assertThat(p.coveredVertices).isEqualTo(2)
        assertThat(p.taps).isEqualTo(2)
    }

    @Test
    fun tapFarFromEverything_coversNothing() {
        val p = ChallengeScorer.progress(listOf(a, b), listOf(RaDec(40.0, 40.0)), 3.0)
        assertThat(p.coveredVertices).isEqualTo(0)
        assertThat(p.taps).isEqualTo(1)
    }

    @Test
    fun sharedVertexCountedOnce() {
        // Strokes sharing a vertex (a corner) count it once.
        val strokes = listOf(listOf(a, b), listOf(b, c))
        val vertices = strokes.flatten().distinct()
        val p = ChallengeScorer.progress(vertices, listOf(b), 3.0)
        assertThat(p.coveredVertices).isEqualTo(1)
        assertThat(p.totalVertices).isEqualTo(3)
    }

    @Test
    fun outOfToleranceTaps_coverNothing() {
        val p = ChallengeScorer.progress(listOf(a, b, c), listOf(RaDec(40.0, 40.0)), 3.0)
        assertThat(p.coveredVertices).isEqualTo(0)
    }

    @Test
    fun biasedSnap_returnsNearestVertexWithinTolerance() {
        val near = ChallengeScorer.biasedSnap(listOf(a, b, c), RaDec(10.04, 10.03), 3.0)
        assertThat(near).isEqualTo(a)
        val far = ChallengeScorer.biasedSnap(listOf(a, b, c), RaDec(40.0, 40.0), 3.0)
        assertThat(far).isNull()
    }
}