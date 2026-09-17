/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.challenge.ChallengeScorer
import com.google.android.stardroid.challenge.FindGameViewModel
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Viewport
import com.google.android.stardroid.math.RaDec

/**
 * The instant, GL-independent overlay for constellation play modes: projects each recorded
 * tap through the app's own [SkyProjection] and draws it — a glowing dot per tap, gold lines
 * linking the draw-mode stroke. Compose recomposes on every VM state change, so feedback is
 * guaranteed-visible on every device (the GL scene-cache path can lag or drop on some GPUs).
 */
@Composable
fun TapOverlay(
    points: List<RaDec>,
    camera: SkyCamera,
    widthPx: Int,
    heightPx: Int,
    color: Color,
    modifier: Modifier = Modifier,
    connect: Boolean = false,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (points.isEmpty() || widthPx <= 0 || heightPx <= 0) return@Canvas
        val projection = SkyProjection(camera, Viewport(widthPx, heightPx, 1f))
        val screen =
            points.mapNotNull { raDec ->
                projection.worldToScreen(raDec.toGeocentricVector())?.let { Offset(it.xPx, it.yPx) }
            }
        if (connect && screen.size >= 2) {
            val stroke = Stroke(width = 5f, cap = StrokeCap.Round)
            for (i in 0 until screen.size - 1) {
                drawLine(color, screen[i], screen[i + 1], strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
        val r = 14f
        screen.forEach { p ->
            drawCircle(color.copy(alpha = 0.35f), radius = r * 1.9f, center = p)
            drawCircle(color, radius = r, center = p)
            drawCircle(Color.White, radius = r * 0.35f, center = p)
        }
    }
}

/** Draw mode's draft: gold dots + linking lines over the map. */
@Composable
fun DrawTapOverlay(
    viewModel: ConstellationDrawViewModel,
    camera: SkyCamera,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val all = state.openStroke + state.closedStrokes.flatten()
    TapOverlay(
        points = all.map { it.raDec },
        camera = camera,
        widthPx = widthPx,
        heightPx = heightPx,
        color = Color(0xFFFFD94D),
        connect = true,
        modifier = modifier,
    )
}

/** Find mode's taps: cyan markers (no connecting lines). */
@Composable
fun FindTapOverlay(
    viewModel: FindGameViewModel,
    camera: SkyCamera,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    TapOverlay(
        points = session?.taps.orEmpty(),
        camera = camera,
        widthPx = widthPx,
        heightPx = heightPx,
        color = Color(0xFF8FD0FF),
        connect = false,
        modifier = modifier,
    )
}