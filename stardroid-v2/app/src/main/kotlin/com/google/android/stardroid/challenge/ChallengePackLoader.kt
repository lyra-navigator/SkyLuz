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
import com.google.android.stardroid.math.RaDec
import org.json.JSONObject

/**
 * Loads a challenge pack from assets (custom-constellations.md §4b). Packs are content, not
 * code: a malformed challenge entry is skipped rather than crashing the tab. Uses org.json
 * (Android-builtin) — no serialization dependency needed for a three-field format.
 */
object ChallengePackLoader {
    fun load(
        context: Context,
        assetPath: String = "challenges/skyluz_starter.json",
    ): List<Challenge> =
        try {
            val root = JSONObject(context.assets.open(assetPath).bufferedReader().readText())
            val challenges = root.getJSONArray("challenges")
            (0 until challenges.length()).mapNotNull { i ->
                try {
                    val dto = challenges.getJSONObject(i)
                    val centerArr = dto.getJSONArray("center")
                    val strokesArr = dto.getJSONArray("strokes")
                    val strokes =
                        (0 until strokesArr.length()).map { s ->
                            val stroke = strokesArr.getJSONArray(s)
                            (0 until stroke.length()).map { v ->
                                val vertex = stroke.getJSONArray(v)
                                RaDec(vertex.getDouble(0), vertex.getDouble(1))
                            }
                        }
                    Challenge(
                        id = dto.getString("id"),
                        name = dto.getString("name"),
                        center = RaDec(centerArr.getDouble(0), centerArr.getDouble(1)),
                        radiusDeg = dto.getDouble("radiusDeg"),
                        exampleAsset = "file:///android_asset/" + dto.getString("example"),
                        strokes = strokes,
                        toleranceDeg = dto.optDouble("toleranceDeg", Challenge.DEFAULT_TOLERANCE_DEG),
                        unlocksAfter = dto.optString("unlocksAfter").ifEmpty { null },
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
}