/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the figures GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.challenge

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.ui.objectinfo.IdentifyGeometry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Find mode (custom-constellations.md §4b): hide a real IAU constellation's lines and ask the
 * player to tap its stars — EXACTLY the challenge interaction (Catalyst, 2.7.0 feedback #1).
 * Taps score live, each hit persists (ChallengeProgress), a stopped session keeps its found
 * stars, and Start resumes from there. The map forces the constellations layer off while a
 * session runs and restores it on exit.
 */
class FindGameViewModel(
    val context: Context,
    private val catalog: suspend () -> CatalogRepository,
) : ViewModel() {
    data class Pick(
        val figure: Figure,
        val name: String,
    )

    data class Session(
        val figureName: String,
        val figure: Figure,
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

    /** The pick list: every IAU figure with its name. */
    private val _picks = MutableStateFlow<List<Pick>>(emptyList())
    val picks: StateFlow<List<Pick>> = _picks.asStateFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun loadFigures() {
        if (_picks.value.isNotEmpty()) return
        viewModelScope.launch {
            val figures = catalog().figures(LayerKind.CONSTELLATIONS).first()
            val names = catalog().layerObjects(LayerKind.CONSTELLATIONS, LocaleSpec.ENGLISH).first()
            val nameById = names.associate { it.id.value to it.name }
            _picks.value =
                figures
                    .mapNotNull { figure ->
                        nameById[figure.owner.value]?.let { Pick(figure, it) }
                    }
                    .sortedBy { it.name }
        }
    }

    fun start(pick: Pick) {
        val key = progressKey(pick.name)
        val vertices = FindGame.vertices(pick.figure)
        val covered = ChallengeProgress.coveredIndices(context, key).filter { it < vertices.size }
        _session.value =
            Session(
                pick.name,
                pick.figure,
                covered.map { vertices[it] },
                ChallengeScorer.Progress(covered.size, vertices.size, covered.size),
                coveredIndices = covered.toSet(),
            )
    }

    /** Replay from zero: clears the stored progress, then starts fresh. */
    fun restart(pick: Pick) {
        ChallengeProgress.clear(context, progressKey(pick.name))
        start(pick)
    }

    fun cancel() {
        // Progress persists per tap; stopping just ends the live session.
        _session.value = null
    }

    /** Kept for compatibility with earlier wiring (find-mode ✕). */
    fun cancelRequest() {
        cancel()
    }

    /** Reveal: end the session (the map re-enables the IAU lines so the figure shows). */
    fun reveal() {
        cancel()
    }

    /**
     * A tap during a running session: biased snap + scoring, with per-tap hit/miss feedback.
     * Hits persist immediately (resume-ready). Returns true when the tap was a HIT.
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
        val vertices = FindGame.vertices(session.figure)
        // Same tolerance law as challenges (2.9.1): scales with FOV but floored — at high
        // zoom a fixed 4°-scaled threshold would shrink until a tap on the drawn star dot
        // itself missed.
        val tolerance =
            maxOf(
                TAP_TOLERANCE_DEG * camera.fovDeg / IdentifyGeometry.MAX_FOV_DEG,
                Challenge.DEFAULT_TOLERANCE_DEG,
            )
        val coveredBefore = session.progress.coveredVertices
        val biased = ChallengeScorer.biasedSnap(vertices, tapRaDec, tolerance)
        val newTaps = session.taps + (biased ?: tapRaDec)
        val progress = FindGame.score(session.figure, newTaps, tolerance)
        val isHit = progress.coveredVertices > coveredBefore
        var newCovered = session.coveredIndices
        if (isHit) {
            val coveredSet = session.coveredIndices.toMutableSet()
            if (biased != null) {
                for ((i, v) in vertices.withIndex()) {
                    if (i in coveredSet) continue
                    if (v == biased) {
                        coveredSet += i
                        break
                    }
                }
            }
            if (coveredSet == session.coveredIndices) {
                // Snap missed but score advanced: persist the nearest uncovered in-tolerance vertex.
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
                if (best >= 0) coveredSet += best
            }
            newCovered = coveredSet
            val key = progressKey(session.figureName)
            ChallengeProgress.setCoveredIndices(context, key, newCovered)
            if (progress.complete) ChallengeProgress.setComplete(context, key, true)
        }
        _session.value =
            Session(session.figureName, session.figure, newTaps, progress, newCovered, session.hitFlags + isHit)
        return isHit
    }

    private fun progressKey(name: String) = "find:$name"

    companion object {
        const val TAP_TOLERANCE_DEG = 4.0
    }
}