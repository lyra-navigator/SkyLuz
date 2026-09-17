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
 * player to tap its stars — EXACTLY the challenge interaction. The tap targets are ONLY the
 * vertices that coincide with real catalog stars (2.10.0 audit: 23 IAU vertices are v1
 * meridian-border corners in empty sky — un-tappable by design; the game must never require
 * them). Taps score live, each hit persists (ChallengeProgress), a stopped session keeps its
 * found stars, and Start resumes from there. The map forces the constellations layer off
 * while a session runs and restores it on exit.
 */
class FindGameViewModel(
    val context: Context,
    private val catalog: suspend () -> CatalogRepository,
) : ViewModel() {
    data class Pick(
        val figure: Figure,
        val name: String,
        /** Indices into [FindGame.vertices] that are real catalog stars — the tap targets. */
        val starVertexIndices: List<Int>,
    ) {
        val starCount: Int get() = starVertexIndices.size
    }

    data class Session(
        val figureName: String,
        val figure: Figure,
        val taps: List<RaDec>,
        val progress: ChallengeScorer.Progress,
        /** Vertex indices that are real catalog stars — the solution set. */
        val solutionIndices: List<Int>,
        /** Vertex indices already covered (persisted between sessions). */
        val coveredIndices: Set<Int> = emptySet(),
        /** True per tap when it covered a NEW solution vertex (a hit). */
        val hitFlags: List<Boolean> = emptyList(),
    ) {
        val hits: Int get() = hitFlags.count { it }
        val misses: Int get() = hitFlags.size - hits
    }

    /** The pick list: every IAU figure with its name + real-star solution set. */
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
            // The catalog stars: a figure vertex is a tap target only when one of these sits
            // on it (within [STAR_MATCH_DEG]). Meridian-border corners match nothing.
            val starPositions =
                catalog().layerObjects(LayerKind.STARS, LocaleSpec.ENGLISH).first()
                    .map { it.position }
            _picks.value =
                figures
                    .mapNotNull { figure ->
                        nameById[figure.owner.value]?.let {
                            Pick(figure, it, starVertexIndices(figure, starPositions))
                        }
                    }
                    .sortedBy { it.name }
        }
    }

    /** Vertex indices that coincide with a real catalog star (the only fair tap targets). */
    private fun starVertexIndices(
        figure: Figure,
        starPositions: List<RaDec>,
    ): List<Int> =
        FindGame.vertices(figure).mapIndexedNotNull { idx, vertex ->
            val v = vertex.toGeocentricVector()
            val matched =
                starPositions.any { star ->
                    IdentifyGeometry.angularSeparationDeg(v, star.toGeocentricVector()) <=
                        STAR_MATCH_DEG
                }
            if (matched) idx else null
        }

    fun start(pick: Pick) {
        viewModelScope.launch {
            val vertices = FindGame.vertices(pick.figure)
            val key = progressKey(pick.name)
            val covered = ChallengeProgress.coveredIndices(context, key).filter { it in pick.starVertexIndices }
            val taps = covered.sorted().map { vertices[it] }
            _session.value =
                Session(
                    pick.name,
                    pick.figure,
                    taps,
                    ChallengeScorer.Progress(covered.size, pick.starCount, taps.size),
                    pick.starVertexIndices,
                    covered.toSet(),
                )
        }
    }

    /** Replay from zero: clears the stored progress, then starts fresh. */
    fun restart(pick: Pick) {
        ChallengeProgress.clear(context, progressKey(pick.name))
        start(pick)
    }

    /** Restart the RUNNING session from zero (the HUD's Restart button). */
    fun restartCurrent() {
        val session = _session.value ?: return
        restart(Pick(session.figure, session.figureName, session.solutionIndices))
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
     * A tap during a running session: biased snap + scoring over the REAL-STAR solution set,
     * with per-tap hit/miss feedback. Hits persist immediately (resume-ready).
     * Returns true when the tap was a HIT.
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
        val solution = session.solutionIndices.map { vertices[it] }
        // Same tolerance law as challenges: scales with FOV but floored — at high zoom a
        // fixed 4°-scaled threshold would shrink until a tap on the drawn star dot missed.
        val tolerance =
            maxOf(
                TAP_TOLERANCE_DEG * camera.fovDeg / IdentifyGeometry.MAX_FOV_DEG,
                Challenge.DEFAULT_TOLERANCE_DEG,
            )
        val coveredBefore = session.progress.coveredVertices
        val biased = ChallengeScorer.biasedSnap(solution, tapRaDec, tolerance)
        val newTaps = session.taps + (biased ?: tapRaDec)
        val progress = ChallengeScorer.progress(solution, newTaps, tolerance)
        val isHit = progress.coveredVertices > coveredBefore
        var newCovered = session.coveredIndices
        if (isHit) {
            val coveredSet = session.coveredIndices.toMutableSet()
            var hitVertex = -1
            if (biased != null) {
                // biased snap IS a solution vertex (RaDec equality) — find its vertex index.
                val idx = vertices.indexOf(biased)
                if (idx >= 0) {
                    coveredSet += idx
                    hitVertex = idx
                }
            }
            if (hitVertex < 0) {
                // Snap missed but the raw tap advanced the score: persist the nearest
                // uncovered solution vertex within tolerance.
                var best = -1
                var bestSep = Double.MAX_VALUE
                for (si in session.solutionIndices) {
                    if (si in coveredSet) continue
                    val sep =
                        IdentifyGeometry.angularSeparationDeg(
                            tapRaDec.toGeocentricVector(),
                            vertices[si].toGeocentricVector(),
                        )
                    if (sep < tolerance && sep < bestSep) {
                        bestSep = sep; best = si
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
            Session(
                session.figureName,
                session.figure,
                newTaps,
                progress,
                session.solutionIndices,
                newCovered,
                session.hitFlags + isHit,
            )
        return isHit
    }

    private fun progressKey(name: String) = "find:$name"

    companion object {
        const val TAP_TOLERANCE_DEG = 4.0

        /** A vertex within this angle of a catalog star IS that star (v1 coords agree ~0.06°). */
        const val STAR_MATCH_DEG = 0.5
    }
}