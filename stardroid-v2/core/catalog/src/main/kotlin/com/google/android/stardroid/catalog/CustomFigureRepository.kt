/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.catalog

import kotlinx.coroutines.flow.Flow

/**
 * A user-drawn constellation (custom-constellations.md). `figure` carries the strokes exactly
 * like an IAU figure; the metadata (name, visibility, creation time) is the library's business.
 */
data class CustomFigure(
    val id: String,
    val name: String,
    val createdEpochMillis: Long,
    val visible: Boolean,
    val figure: Figure,
)

/** The user's own constellation store — a dedicated user-owned DB, never the replaceable catalog pack. */
interface CustomFigureRepository {
    /** All saved figures, newest first; re-emits on every change (the layer renders from this). */
    fun figures(): Flow<List<CustomFigure>>

    /** Saves (or renames/updates) a figure; returns the id actually stored. */
    suspend fun save(
        figure: Figure,
        name: String,
    ): String

    suspend fun setVisible(
        id: String,
        visible: Boolean,
    )

    suspend fun delete(id: String)
}
