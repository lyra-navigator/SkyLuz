/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.challenge

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.CustomFigureRepository
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.ui.objectinfo.IdentifyGeometry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Constellations tab (custom-constellations.md §4b): the starter challenges with
 * completion tracking, and a running challenge session whose taps are scored live. Reuses
 * the draw-mode snap (bias: a tap inside the challenge tolerance counts as its solution
 * star), and a completed challenge saves into "My constellations" with the challenge's name.
 * Progress persists across stop/app-restart (ChallengeProgress): Start resumes from there.
 */
class ChallengeTabViewModel(
    private val context: Context,
    private val catalog: suspend () -> CatalogRepository,
    private val customFigures: suspend () -> CustomFigureRepository,
) : ViewModel() {
    /** All pack challenges with their locked/unlocked/complete status. */
    data class Entry(
        val challenge: Challenge,
        val complete: Boolean,
        val locked: Boolean,
        /** How many of the challenge's vertices the player has found so far (persisted). */
        val covered: Int = 0,
        val total: Int = 0,
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val completed = mutableSetOf<String>()

    /** A running challenge session: taps so far + live progress + per-tap hit/miss. */
    data class Session(
        val challenge: Challenge,
        val taps: List<RaDec>,
        val progress: ChallengeScorer.Progress,
        /** Indices of solution vertices already covered (persisted between sessions). */
        val coveredIndices: Set<Int> = emptySet(),
        /** True per tap when it covered a NEW solution vertex (a hit). */
        val hitFlags: List<Boolean> = emptyList(),
    ) {
        val hits: Int get() = hitFlags.count { it }
        val misses: Int get() = hitFlags.size - hits
    }

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val challenges = ChallengePackLoader.load(context)
            // Completion is per-session (MVP): pack state persists via saves in
            // My constellations; a persistent completion store rides the saved-figure names.
            _entries.value =
                challenges.map { challenge ->
                    val locked =
                        challenge.unlocksAfter?.let { it !in completed } == true
                    val key = progressKey(challenge.id)
                    val covered = ChallengeProgress.coveredIndices(context, key)
                    Entry(
                        challenge = challenge,
                        complete = challenge.id in completed || ChallengeProgress.isComplete(context, key),
                        locked = locked,
                        covered = covered.size,
                        total = challenge.vertices.size,
                    )
                }
        }
    }

    fun start(challenge: Challenge) {
        // Resume: restore the persisted covered set (Catalyst: progress must survive stop).
        val covered = ChallengeProgress.coveredIndices(context, progressKey(challenge.id)).toIntArray()
        val vertices = challenge.vertices
        val taps = covered.filter { it < vertices.size }.map { vertices[it] }
        _session.value =
            Session(
                challenge,
                taps,
                ChallengeScorer.Progress(covered.size, vertices.size, taps.size),
                coveredIndices = covered.toSet(),
            )
    }

    /** Replay from zero: clears the stored progress, then starts fresh. */
    fun restart(challenge: Challenge) {
        ChallengeProgress.clear(context, progressKey(challenge.id))
        completed.remove(challenge.id)
        refresh()
        start(challenge)
    }

    /** The challenge's Tip slew target: its real-star centroid. */
    fun tipTarget(challenge: Challenge): RaDec {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        for (v in challenge.vertices) {
            val d = v.toGeocentricVector()
            x += d.x; y += d.y; z += d.z
        }
        return RaDec.fromGeocentricVector(com.google.android.stardroid.math.Vector3(x, y, z).normalized())
    }

    /** Tip zoom: angular radius from centroid, clamped. */
    fun tipRadiusDeg(challenge: Challenge): Double {
        val c = tipTarget(challenge).toGeocentricVector()
        val maxSep =
            challenge.vertices.maxOf {
                com.google.android.stardroid.ui.objectinfo.IdentifyGeometry.angularSeparationDeg(
                    c,
                    it.toGeocentricVector(),
                )
            }
        return maxOf(8.0, minOf(40.0, maxSep + 4.0))
    }

    /** The map observes this: true while a challenge is being played on the sky. */
    val isPlaying: Boolean get() = _session.value != null

    fun cancel() {
        // Progress is already persisted per tap; stopping just ends the live session.
        _session.value = null
    }

    /**
     * A tap during a running session: biased snap to the solution vertex when within
     * tolerance, else the raw tap position. Progress updates AND persists; completion saves
     * the figure. Returns true when the tap was a HIT (covered a new solution vertex).
     */
    fun onTap(
        xPx: Float,
        yPx: Float,
        widthPx: Int,
        heightPx: Int,
        camera: SkyCamera,
    ): Boolean {
        val session = _session.value ?: return false
        val direction = IdentifyGeometry.screenToDirection(camera, widthPx, heightPx, xPx, yPx)
        val tapRaDec = RaDec.fromGeocentricVector(direction)
        // Tolerance scales with FOV like draw mode, floored at the challenge's own tolerance.
        val scaled = IdentifyGeometry.TAP_THRESHOLD_DEGREES * camera.fovDeg / IdentifyGeometry.MAX_FOV_DEG
        val tolerance = maxOf(scaled, session.challenge.toleranceDeg)
        val vertices = session.challenge.vertices
        val coveredBefore = session.progress.coveredVertices
        val biased = ChallengeScorer.biasedSnap(vertices, tapRaDec, tolerance)
        val newTaps = session.taps + (biased ?: tapRaDec)
        val progress = ChallengeScorer.progress(vertices, newTaps, tolerance)
        val isHit = progress.coveredVertices > coveredBefore
        // Which vertex index the hit landed on (persisted for resume + the lines overlay).
        var hitIndex = -1
        if (isHit) {
            val coveredSet = session.coveredIndices.toMutableSet()
            for ((i, v) in vertices.withIndex()) {
                if (i in coveredSet) continue
                if (biased != null && v == biased) {
                    hitIndex = i
                    coveredSet += i
                    break
                }
            }
            val finalSet =
                if (hitIndex >= 0) coveredSet else {
                    // Snap missed but score still advanced (raw tap inside tolerance): find
                    // the nearest uncovered vertex within tolerance.
                    val tapDir = tapRaDec.toGeocentricVector()
                    var best = -1
                    var bestSep = Double.MAX_VALUE
                    for ((i, v) in vertices.withIndex()) {
                        if (i in coveredSet) continue
                        val sep = IdentifyGeometry.angularSeparationDeg(tapDir, v.toGeocentricVector())
                        if (sep < tolerance && sep < bestSep) {
                            bestSep = sep; best = i
                        }
                    }
                    if (best >= 0) coveredSet + best else coveredSet
                }
            hitIndex = finalSet.firstOrNull { it !in session.coveredIndices } ?: -1
            persistCovered(session.challenge.id, finalSet, progress)
            _session.value =
                Session(session.challenge, newTaps, progress, finalSet, session.hitFlags + isHit)
        } else {
            _session.value =
                Session(session.challenge, newTaps, progress, session.coveredIndices, session.hitFlags + isHit)
        }
        if (progress.complete && !completed.contains(session.challenge.id)) {
            completed += session.challenge.id
            ChallengeProgress.setComplete(context, progressKey(session.challenge.id), true)
            viewModelScope.launch {
                customFigures().save(
                    figure = Figure(owner = CelestialObjectId(""), strokes = session.challenge.strokes),
                    name = session.challenge.name,
                )
                refresh()
            }
        }
        return isHit
    }

    private fun persistCovered(
        challengeId: String,
        indices: Set<Int>,
        progress: ChallengeScorer.Progress,
    ) {
        val key = progressKey(challengeId)
        ChallengeProgress.setCoveredIndices(context, key, indices)
        if (progress.complete) ChallengeProgress.setComplete(context, key, true)
    }

    private fun progressKey(challengeId: String) = "challenge:$challengeId"

    /** The drawing-in-progress state for the live preview layer (the taps as one stroke). */
    fun asDrawState(): com.google.android.stardroid.ui.draw.DrawState {
        val session = _session.value ?: return com.google.android.stardroid.ui.draw.DrawState()
        val points = session.taps.map { com.google.android.stardroid.ui.draw.DrawPoint(raDec = it, snappedTo = null) }
        return com.google.android.stardroid.ui.draw.DrawState(openStroke = points)
    }

    /** Example image for a challenge, as an asset URL for Compose's AsyncImage-free loading. */
    fun exampleUrl(challenge: Challenge): String = challenge.exampleAsset
}