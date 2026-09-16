/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.CustomFigure
import com.google.android.stardroid.catalog.CustomFigureRepository
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.math.RaDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One user-drawn constellation header. Vertices live in [CustomFigureVertexEntity]. */
@Entity(tableName = "custom_figure")
data class CustomFigureEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_epoch_millis") val createdEpochMillis: Long,
    @ColumnInfo(name = "visible") val visible: Boolean,
    @ColumnInfo(name = "owner_object_id") val ownerObjectId: String,
)

/** One vertex of a custom figure polyline: stroke [stroke], position [seq] within it, J2000 degrees. */
@Entity(
    tableName = "custom_figure_vertex",
    primaryKeys = ["figure_id", "stroke", "seq"],
)
data class CustomFigureVertexEntity(
    @ColumnInfo(name = "figure_id") val figureId: String,
    val stroke: Int,
    val seq: Int,
    val ra: Double,
    val dec: Double,
)

data class CustomFigureRow(
    val id: String,
    val name: String,
    val createdEpochMillis: Long,
    val visible: Boolean,
    val ownerObjectId: String,
    val stroke: Int,
    val seq: Int,
    val ra: Double,
    val dec: Double,
)

@Dao
interface CustomFigureDao {
    @Query(
        "SELECT f.id AS id, f.name AS name, f.created_epoch_millis AS createdEpochMillis, " +
            "f.visible AS visible, f.owner_object_id AS ownerObjectId, " +
            "v.stroke AS stroke, v.seq AS seq, v.ra AS ra, v.dec AS dec " +
            "FROM custom_figure f " +
            "JOIN custom_figure_vertex v ON v.figure_id = f.id " +
            "ORDER BY f.created_epoch_millis DESC, f.id, v.stroke, v.seq",
    )
    fun rows(): Flow<List<CustomFigureRow>>

    @Upsert
    suspend fun upsertFigure(figure: CustomFigureEntity)

    @Query("DELETE FROM custom_figure_vertex WHERE figure_id = :figureId")
    suspend fun clearVertices(figureId: String)

    @Upsert
    suspend fun upsertVertices(vertices: List<CustomFigureVertexEntity>)

    @Query("UPDATE custom_figure SET visible = :visible WHERE id = :id")
    suspend fun setVisible(
        id: String,
        visible: Boolean,
    )

    @Query("DELETE FROM custom_figure WHERE id = :id")
    suspend fun deleteFigure(id: String)

    @Query("DELETE FROM custom_figure_vertex WHERE figure_id = :id")
    suspend fun deleteVertices(id: String)
}

/**
 * Room-backed [CustomFigureRepository] over the user's own database (never the replaceable
 * catalog pack — the same rule that keeps satellite elements in their own store).
 */
class RoomCustomFigureRepository(
    private val dao: CustomFigureDao,
) : CustomFigureRepository {
    override fun figures(): Flow<List<CustomFigure>> =
        dao.rows().map { rows ->
            rows
                .groupBy { it.id }
                .map { (_, group) ->
                    val head = group.first()
                    CustomFigure(
                        id = head.id,
                        name = head.name,
                        createdEpochMillis = head.createdEpochMillis,
                        visible = head.visible,
                        figure =
                            Figure(
                                owner = CelestialObjectId(head.ownerObjectId),
                                strokes =
                                    group
                                        .groupBy { it.stroke }
                                        .map { (_, stroke) -> stroke.map { RaDec(it.ra, it.dec) } },
                            ),
                    )
                }
        }.distinctUntilChanged()

    override suspend fun save(
        figure: Figure,
        name: String,
    ): String {
        val id = "custom/" + java.util.UUID.randomUUID().toString()
        val owner = figure.owner.value.ifEmpty { id }
        dao.upsertFigure(
            CustomFigureEntity(
                id = id,
                name = name,
                createdEpochMillis = System.currentTimeMillis(),
                visible = true,
                ownerObjectId = owner,
            ),
        )
        val vertices =
            figure.strokes.flatMapIndexed { stroke, points ->
                points.mapIndexed { seq, raDec ->
                    CustomFigureVertexEntity(
                        figureId = id,
                        stroke = stroke,
                        seq = seq,
                        ra = raDec.raDeg,
                        dec = raDec.decDeg,
                    )
                }
            }
        dao.upsertVertices(vertices)
        return id
    }

    override suspend fun setVisible(
        id: String,
        visible: Boolean,
    ) = dao.setVisible(id, visible)

    override suspend fun delete(id: String) {
        dao.deleteVertices(id)
        dao.deleteFigure(id)
    }
}
