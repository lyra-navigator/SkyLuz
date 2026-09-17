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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.challenge.FindGameViewModel
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Viewport

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

/** A point on the great-circle ring of [radiusDeg] around [center] at angle [t] ∈ [0, 2π). */
private fun tipRingPoint(
    center: RaDec,
    radiusDeg: Double,
    t: Double,
): Vector3 {
    val c = center.toGeocentricVector()
    val helper =
        if (kotlin.math.abs(c.z) < 0.9) Vector3.UNIT_Z else Vector3(1.0, 0.0, 0.0)
    val u = (helper cross c).normalized()
    val v = (c cross u).normalized()
    val rad = Math.toRadians(radiusDeg)
    return (c + u * (rad * kotlin.math.cos(t)) + v * (rad * kotlin.math.sin(t))).normalized()
}

/**
 * The Tip's "look here" highlight: a cyan ring hugging the target zone on the celestial
 * sphere, plus a soft fill — projected through the shared [SkyProjection], so it tracks the
 * slew and stays on the sphere at any zoom.
 */
@Composable
fun TipZoneOverlay(
    center: RaDec,
    radiusDeg: Double,
    camera: SkyCamera,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
) {
    // 64 ring samples, computed once per (center, radius).
    val ringDirections =
        remember(center, radiusDeg) {
            (0..64).map { i -> tipRingPoint(center, radiusDeg, 2.0 * Math.PI * i / 64) }
        }
    Canvas(modifier = modifier.fillMaxSize()) {
        if (widthPx <= 0 || heightPx <= 0) return@Canvas
        val projection = SkyProjection(camera, Viewport(widthPx, heightPx, 1f))
        val pts =
            ringDirections.mapNotNull { d ->
                projection.worldToScreen(d)?.let { Offset(it.xPx, it.yPx) }
            }
        if (pts.size < 3) return@Canvas
        val color = Color(0xFF8FD0FF)
        val stroke = Stroke(width = 5f, cap = StrokeCap.Round)
        for (i in 0 until pts.size - 1) {
            drawLine(color, pts[i], pts[i + 1], strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        // Soft fill of the (projected) zone polygon — a hint of "look in here".
        val path = Path()
        pts.forEachIndexed { idx, p -> if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        drawPath(path, color = color.copy(alpha = 0.12f))
    }
}