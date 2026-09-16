/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The user's own constellation store (custom-constellations.md). Deliberately a **separate**
 * database from the catalog pack: the catalog is read-mostly and replaceable on app update
 * (its recovery is "delete and re-copy"), while these are the user's drawings — the exact
 * state that must survive every update. Plain Room, no bundled asset, ordinary migrations.
 */
@Database(
    entities = [
        CustomFigureEntity::class,
        CustomFigureVertexEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CustomFigureDatabase : RoomDatabase() {
    abstract fun customFigureDao(): CustomFigureDao

    companion object {
        const val DATABASE_NAME = "skyluz-custom-figures.db"

        fun create(context: Context): CustomFigureDatabase =
            Room.databaseBuilder(context, CustomFigureDatabase::class.java, DATABASE_NAME).build()
    }
}
