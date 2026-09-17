/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.challenge

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
 * player to tap its stars. Taps score live against the figure's vertices. The map forces the
 * constellations layer off while the session runs; "Reveal" turns it back on with the figure
 * highlighted by the player's taps still on screen.
 */
class FindGameViewModel(
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
    )

    /** The pick list: every IAU figure with its name. */
    private val _picks = MutableStateFlow<List<Pick>>(emptyList())
    val picks: MutableStateFlow<List<Pick>> = _picks

    private val _session = MutableStateFlow<Session?>(null)
    val session: MutableStateFlow<Session?> = _session

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
        _session.value =
            Session(pick.name, pick.figure, emptyList(), ChallengeScorer.Progress(0, FindGame.vertices(pick.figure).size, 0))
    }

    fun cancel() {
        _session.value = null
    }

    /** User closed the picker with the ✕ (the map also restores the IAU lines layer). */
    fun cancelRequest() {
        _session.value = null
    }

    /** Reveal: end the session (the map re-enables the IAU lines so the figure shows). */
    fun reveal() {
        _session.value = null
    }

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
        val tolerance = TAP_TOLERANCE_DEG * camera.fovDeg / IdentifyGeometry.MAX_FOV_DEG
        val biased = ChallengeScorer.biasedSnap(FindGame.vertices(session.figure), tapRaDec, tolerance)
        val newTaps = session.taps + (biased ?: tapRaDec)
        val progress = FindGame.score(session.figure, newTaps, tolerance)
        _session.value = session.copy(taps = newTaps, progress = progress)
    }

    companion object {
        const val TAP_TOLERANCE_DEG = 4.0
    }
}