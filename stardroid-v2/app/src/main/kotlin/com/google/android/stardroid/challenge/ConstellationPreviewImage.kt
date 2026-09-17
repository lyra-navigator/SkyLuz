/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.challenge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/**
 * A REAL picture of a constellation, rendered from its actual catalog star positions
 * (Catalyst, 2.8.0 feedback #1): the find "start" screen shows the true figure shape, not a
 * placeholder. Strokes are drawn as the constellation lines; vertices as stars with a glow.
 * No asset needed — the catalog geometry IS the picture.
 */
@Composable
fun ConstellationPreviewImage(
    strokes: List<List<com.google.android.stardroid.math.RaDec>>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF8FD0FF),
    starColor: Color = Color.White,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas
        // Collect the vertex directions, find the centroid, then flatten to a tangent plane:
        // a gnomonic projection around the figure's centroid gives an undistorted picture.
        val dirs =
            strokes.flatten().map { it.toGeocentricVector() }
        if (dirs.isEmpty()) return@Canvas
        var cx = 0.0; var cy = 0.0; var cz = 0.0
        for (d in dirs) { cx += d.x; cy += d.y; cz += d.z }
        val center = com.google.android.stardroid.math.Vector3(cx, cy, cz).normalized()
        // Orthonormal basis tangent to the sphere at the centroid.
        val helper =
            if (kotlin.math.abs(center.z) < 0.9) com.google.android.stardroid.math.Vector3.UNIT_Z
            else com.google.android.stardroid.math.Vector3(1.0, 0.0, 0.0)
        val u = (helper cross center).normalized()
        val v = (center cross u).normalized()
        fun project(dir: com.google.android.stardroid.math.Vector3): Offset? {
            val zAxis = (center dot dir)
            if (zAxis <= 0.02) return null // >~89° from centroid: not part of this picture
            val px = (dir dot u) / zAxis
            val py = (dir dot v) / zAxis
            return Offset(px.toFloat(), (-py).toFloat())
        }
        val pts = dirs.mapNotNull { dir -> project(dir) }
        if (pts.isEmpty()) return@Canvas
        val maxX = pts.maxOf { it.x }; val minX = pts.minOf { it.x }
        val maxY = pts.maxOf { it.y }; val minY = pts.minOf { it.y }
        val spanX = (maxX - minX).takeIf { it > 1e-9 } ?: 1f
        val spanY = (maxY - minY).takeIf { it > 1e-9 } ?: 1f
        val margin = 0.12f
        val scale = minOf((w * (1 - 2 * margin)) / spanX, (h * (1 - 2 * margin)) / spanY)
        fun toCanvas(p: Offset): Offset =
            Offset(
                w / 2 + (p.x - (maxX + minX) / 2) * scale,
                h / 2 + (p.y - (maxY + minY) / 2) * scale,
            )
        for (stroke in strokes) {
            var prev: Offset? = null
            for (vertex in stroke) {
                val cur = project(vertex.toGeocentricVector())?.let { toCanvas(it) }
                if (cur != null && prev != null) {
                    drawLine(lineColor, prev, cur, strokeWidth = 4f, cap = StrokeCap.Round)
                }
                prev = cur
            }
        }
        for (p in pts) {
            val c = toCanvas(p)
            drawCircle(starColor.copy(alpha = 0.3f), radius = 11f, center = c)
            drawCircle(starColor, radius = 6f, center = c)
        }
    }
}