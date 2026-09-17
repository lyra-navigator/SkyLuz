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
import com.google.android.stardroid.ui.draw.DrawPoint
import com.google.android.stardroid.ui.draw.DrawState
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
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val completed = mutableSetOf<String>()

    /** A running challenge session: taps so far + live progress. */
    data class Session(
        val challenge: Challenge,
        val taps: List<RaDec>,
        val progress: ChallengeScorer.Progress,
    )

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
                    Entry(challenge, complete = challenge.id in completed, locked = locked)
                }
        }
    }

    fun start(challenge: Challenge) {
        _session.value = Session(challenge, emptyList(), ChallengeScorer.Progress(0, challenge.vertices.size, 0))
    }

    /** The map observes this: true while a challenge is being played on the sky. */
    val isPlaying: Boolean get() = _session.value != null

    fun cancel() {
        _session.value = null
    }

    /**
     * A tap during a running session: biased snap to the solution vertex when within
     * tolerance, else the raw tap position. Progress updates; completion saves the figure.
     */
    fun onTap(
        xPx: Float,
        yPx: Float,
        widthPx: Int,
        heightPx: Int,
        camera: SkyCamera,
    ) {
        val session = _session.value ?: return
        val direction = IdentifyGeometry.screenToDirection(camera, widthPx, heightPx, xPx, yPx)
        val tapRaDec = RaDec.fromGeocentricVector(direction)
        // Tolerance scales with FOV like draw mode, floored at the challenge's own tolerance.
        val scaled = IdentifyGeometry.TAP_THRESHOLD_DEGREES * camera.fovDeg / IdentifyGeometry.MAX_FOV_DEG
        val tolerance = maxOf(scaled, session.challenge.toleranceDeg)
        val biased = ChallengeScorer.biasedSnap(session.challenge.vertices, tapRaDec, tolerance)
        val newTaps = session.taps + (biased ?: tapRaDec)
        val progress = ChallengeScorer.progress(session.challenge.vertices, newTaps, tolerance)
        _session.value = Session(session.challenge, newTaps, progress)
        if (progress.complete && !completed.contains(session.challenge.id)) {
            completed += session.challenge.id
            viewModelScope.launch {
                customFigures().save(
                    figure = Figure(owner = CelestialObjectId(""), strokes = session.challenge.strokes),
                    name = session.challenge.name,
                )
                refresh()
            }
        }
    }

    /** The drawing-in-progress state for the live preview layer (the taps as one stroke). */
    fun asDrawState(): DrawState {
        val session = _session.value ?: return DrawState()
        val points = session.taps.map { DrawPoint(raDec = it, snappedTo = null) }
        return DrawState(openStroke = points)
    }

    /** Example image for a challenge, as an asset URL for Compose's AsyncImage-free loading. */
    fun exampleUrl(challenge: Challenge): String = challenge.exampleAsset
}