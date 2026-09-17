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

/**
 * Persistent play progress (Catalyst, 2.7.0 feedback: "when i close it goes back to 0/6"):
 * which solution vertices each challenge / find figure has covered, plus completion. A
 * stopped session keeps its found stars; Start resumes from there. SharedPreferences is the
 * right weight — a handful of small strings, no schema, synchronous reads for list rows.
 */
object ChallengeProgress {
    private const val PREFS = "skyluz_play_progress"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Covered solution-vertex indices for a play key ("challenge:<id>" / "find:<name>"). */
    fun coveredIndices(context: Context, key: String): Set<Int> =
        prefs(context)
            .getString("covered:$key", null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    fun setCoveredIndices(
        context: Context,
        key: String,
        indices: Set<Int>,
    ) {
        prefs(context).edit().putString("covered:$key", indices.sorted().joinToString(",")).apply()
    }

    fun isComplete(context: Context, key: String): Boolean =
        prefs(context).getBoolean("complete:$key", false)

    fun setComplete(
        context: Context,
        key: String,
        complete: Boolean,
    ) {
        prefs(context).edit().putBoolean("complete:$key", complete).apply()
    }

    /** Clears a key's stored progress (used when a replay starts from zero). */
    fun clear(context: Context, key: String) {
        prefs(context).edit().remove("covered:$key").remove("complete:$key").apply()
    }
}