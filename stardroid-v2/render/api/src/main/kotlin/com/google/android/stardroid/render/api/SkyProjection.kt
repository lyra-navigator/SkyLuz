/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.Vector3
import kotlin.math.tan

/** A point projected onto the surface: top-left-origin pixels plus a pseudo-[depth] in NDC z. */
data class ScreenPoint(val xPx: Float, val yPx: Float, val depth: Float)

/**
 * The shared, pure, CPU-side projection (D21). Building the view-projection matrix is CPU work in
 * every backend (even GLES1's `glFrustumf`/`glLoadMatrixf` compute on the CPU), so `:render:api`
 * owns the one implementation:
 * - the GL backend uploads [viewProjection] (via [Matrix4.toFloatArray]) and lets the GPU do the
 *   per-vertex multiply;
 * - the Compose search arrow calls [worldToScreen] on the CPU for its single target point.
 *
 * Both therefore use the byte-identical matrix, so the GL-drawn sky and the CPU-projected arrow
 * agree pixel-for-pixel — and this is unit-testable with no GL context.
 *
 * Screen pixels are **top-left origin** (y down), matching Android/Compose UI space; the y-flip
 * relative to GL's y-up NDC happens here, in [worldToScreen].
 */
class SkyProjection(private val camera: SkyCamera, private val viewport: Viewport) {
    /**
     * `perspective × view`; world (unit geocentric) → clip space. The matrix the GL backend loads.
     */
    val viewProjection: Matrix4 =
        Matrix4.perspective(
            viewport.widthPx.toDouble(),
            viewport.heightPx.toDouble(),
            camera.fovDeg,
        ) * Matrix4.view(camera.lineOfSight, camera.up)

    /**
     * Projects a unit geocentric direction [p] to a [ScreenPoint], or `null` if it is behind the
     * viewer. The returned pixel may lie outside the surface bounds (the caller decides what to do
     * with off-screen points).
     */
    fun worldToScreen(p: Vector3): ScreenPoint? {
        val vp = viewProjection
        val w = vp[3, 0] * p.x + vp[3, 1] * p.y + vp[3, 2] * p.z + vp[3, 3]
        if (w <= W_EPSILON) return null // at or behind the eye plane
        val clipX = vp[0, 0] * p.x + vp[0, 1] * p.y + vp[0, 2] * p.z + vp[0, 3]
        val clipY = vp[1, 0] * p.x + vp[1, 1] * p.y + vp[1, 2] * p.z + vp[1, 3]
        val clipZ = vp[2, 0] * p.x + vp[2, 1] * p.y + vp[2, 2] * p.z + vp[2, 3]
        val ndcX = clipX / w
        val ndcY = clipY / w
        val ndcZ = clipZ / w
        val xPx = ((ndcX + 1.0) * 0.5 * viewport.widthPx).toFloat()
        val yPx = ((1.0 - ndcY) * 0.5 * viewport.heightPx).toFloat() // flip to top-left origin
        return ScreenPoint(xPx, yPx, ndcZ.toFloat())
    }

    private companion object {
        /** Reject points at or behind the eye plane (homogeneous w ≈ the look-direction dot). */
        const val W_EPSILON = 1e-9
    }

    /**
     * Inverse of [worldToScreen] (the constellation draw mode's tap → sky-direction step).
     * Returns the unit geocentric direction whose projection lands on the given pixel
     * (top-left origin, same convention as [worldToScreen]), or `null` for a degenerate
     * viewport. Shares the camera basis with [Matrix4.view], so directions round-trip
     * through [worldToScreen] to the same pixel: this is pure CPU math, unit-tested with
     * no GL context.
     */
    fun screenToDirection(
        xPx: Float,
        yPx: Float,
    ): Vector3? {
        val width = viewport.widthPx.toDouble()
        val height = viewport.heightPx.toDouble()
        if (width <= 0.0 || height <= 0.0) return null
        val ndcX = 2.0 * xPx / width - 1.0
        val ndcY = 1.0 - 2.0 * yPx / height
        // The same orthonormal basis Matrix4.view builds (D21): f = look, r = look × up,
        // u = r × f — so this agrees with the GL backend pixel-for-pixel.
        val f = camera.lineOfSight.normalized()
        val r = (f cross camera.up).normalized()
        if (r.length2 < 1e-12) return null // collinear look/up (invalid camera)
        val u = r cross f
        // Perspective mapping (Matrix4.perspective): ndc = dir·basis / (dir·f) scaled by
        // cot(fov/2) * shortPx / sidePx. Inverting with dir·f fixed at 1 gives the ray.
        val t = tan(camera.fovDeg * DEGREES_TO_RADIANS / 2.0)
        val shortPx = minOf(width, height)
        val tanX = t * width / shortPx
        val tanY = t * height / shortPx
        val dir = f + r * (ndcX * tanX) + u * (ndcY * tanY)
        return dir.normalized()
    }
}
