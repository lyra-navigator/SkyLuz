/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.draw

import com.google.android.stardroid.catalog.CatalogObject
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.CustomFigure
import com.google.android.stardroid.catalog.CustomFigureRepository
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.TypeCode
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Viewport
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private val CAM = SkyCamera(lineOfSight = Vector3.UNIT_X, up = Vector3.UNIT_Z, fovDeg = 90.0)
private val VIEWPORT = Viewport(widthPx = 600, heightPx = 600, density = 1f)

/**
 * Test stars near RA 0 (the camera's look direction +X) so they sit inside the 90° FOV; taps
 * are placed via the app's own SkyProjection.worldToScreen, so the helper can never disagree
 * with IdentifyGeometry's inverse.
 */
private val STAR_A = RaDec(0.5, 5.0)
private val STAR_B = RaDec(1.0, 5.0)
private val STAR_C = RaDec(0.5, 6.0)

private fun catalogObject(
    id: String,
    position: RaDec,
): CatalogObject =
    CatalogObject(
        id = CelestialObjectId(id),
        layerKind = LayerKind.STARS,
        type = TypeCode("star"),
        position = position,
        magnitude = 1.0,
        colorIndex = null,
        name = id.substringAfter('/').uppercase(),
        nameIsPrimary = true,
        searchFovDeg = null,
    )

class ConstellationDrawViewModelTest {
    private val stars =
        listOf(
            catalogObject("star/a", STAR_A),
            catalogObject("star/b", STAR_B),
            catalogObject("star/c", STAR_C),
        )

    private class FakeCustomRepo : CustomFigureRepository {
        val saved = mutableListOf<Pair<Figure, String>>()
        private val flow = MutableStateFlow<List<CustomFigure>>(emptyList())

        override fun figures(): Flow<List<CustomFigure>> = flow

        override suspend fun save(
            figure: Figure,
            name: String,
        ): String {
            saved += figure to name
            return "custom/test-${saved.size}"
        }

        override suspend fun setVisible(
            id: String,
            visible: Boolean,
        ) {}

        override suspend fun delete(id: String) {}
    }

    private class FakeCatalog(
        private val stars: List<CatalogObject>,
    ) : CatalogRepository {
        val objects = MutableStateFlow<List<CatalogObject>>(stars)

        override fun layerObjects(
            kind: LayerKind,
            locale: LocaleSpec,
        ): Flow<List<CatalogObject>> = objects

        override fun figures(
            kind: LayerKind,
            culture: String,
        ): Flow<List<Figure>> = flowOf(emptyList())

        override fun meteorShowers(locale: LocaleSpec): Flow<List<com.google.android.stardroid.catalog.MeteorShower>> = flowOf(emptyList())

        override suspend fun searchByPrefix(
            prefix: String,
            locale: LocaleSpec,
            limit: Int,
        ): List<com.google.android.stardroid.catalog.SearchHit> = emptyList()

        override suspend fun objectInfo(
            id: CelestialObjectId,
            locale: LocaleSpec,
        ): com.google.android.stardroid.catalog.ObjectInfo? = null

        override suspend fun infoCardObjectIds(): Set<CelestialObjectId> = emptySet()

        override suspend fun galleryItems(locale: LocaleSpec): List<com.google.android.stardroid.catalog.GalleryItem> = emptyList()
    }

    private fun vmWith(
        repo: FakeCustomRepo = FakeCustomRepo(),
        catalog: FakeCatalog = FakeCatalog(stars),
    ): ConstellationDrawViewModel = ConstellationDrawViewModel(customFigures = { repo }, catalog = { catalog })

    private suspend fun ConstellationDrawViewModel.tap(
        raDec: RaDec,
    ): DrawPoint? {
        // Tap exactly on the star's projected pixel, via the app's own projection — so this
        // helper can never disagree with IdentifyGeometry's inverse.
        val p = SkyProjection(CAM, VIEWPORT).worldToScreen(raDec.toGeocentricVector())!!
        return onTap(p.xPx, p.yPx, VIEWPORT.widthPx, VIEWPORT.heightPx, CAM)
    }

    @Test
    fun tapNearStar_snapsToCatalogPosition() = runTest {
        val vm = vmWith()
        // 0.05° off star A's exact position: well within the 5° tolerance at 90° fov.
        val p = vm.tap(RaDec(STAR_A.raDeg + 0.05, STAR_A.decDeg + 0.02))!!
        assertThat(p.snappedTo).isEqualTo(CelestialObjectId("star/a"))
        assertThat(p.raDec).isEqualTo(STAR_A)
    }

    @Test
    fun tapInEmptySky_recordsFreePoint() = runTest {
        val vm = vmWith()
        // In the FOV (looking down +X = RA 0/dec 0) but far (>5°) from A/B/C — 20° up.
        val target = RaDec(0.0, 20.0)
        val p = vm.tap(target)!!
        assertThat(p.snappedTo).isNull()
        assertThat(p.raDec.raDeg).isWithin(1e-6).of(target.raDeg)
        assertThat(p.raDec.decDeg).isWithin(1e-6).of(target.decDeg)
    }

    @Test
    fun tapFirstStarAgain_closesStroke_withoutNewPoint() = runTest {
        val vm = vmWith()
        vm.tap(STAR_A) // A opens
        vm.tap(STAR_B) // B
        val result = vm.tap(STAR_A) // A again closes
        assertThat(result).isNull()
        assertThat(vm.drawState.openStroke).isEmpty()
        assertThat(vm.drawState.closedStrokes).hasSize(1)
        assertThat(vm.drawState.closedStrokes.first().first().snappedTo).isEqualTo(CelestialObjectId("star/a"))
        assertThat(vm.drawState.canSave).isTrue()
    }

    @Test
    fun singlePointStroke_closesWithoutBecomingLine() = runTest {
        val vm = vmWith()
        vm.tap(STAR_A) // A
        vm.tap(STAR_A) // A again, but only one point so far: stroke aborts
        assertThat(vm.drawState.openStroke).isEmpty()
        assertThat(vm.drawState.closedStrokes).isEmpty()
    }

    @Test
    fun endStrokeAndMultiStroke_figureCarriesBothStrokes() = runTest {
        val vm = vmWith()
        vm.tap(STAR_A)
        vm.tap(STAR_B)
        vm.endStroke()
        vm.tap(STAR_C) // C starts the second stroke
        vm.tap(STAR_B) // B ends it
        vm.endStroke()
        val strokes = vm.strokes()
        assertThat(strokes).hasSize(2)
        assertThat(strokes[0]).isEqualTo(listOf(STAR_A, STAR_B))
        assertThat(strokes[1]).isEqualTo(listOf(STAR_C, STAR_B))
    }

    @Test
    fun undoRemovesLastPoint_acrossStrokeBoundary() = runTest {
        val vm = vmWith()
        vm.tap(STAR_A)
        vm.tap(STAR_B)
        vm.endStroke()
        assertThat(vm.drawState.closedStrokes).hasSize(1)
        vm.undo() // removes B; a one-point stroke is dropped
        assertThat(vm.drawState.closedStrokes).isEmpty()
        vm.undo() // empty already; no crash
        assertThat(vm.drawState.openStroke).isEmpty()
    }

    @Test
    fun save_persistsFigure_andResetsState() = runTest {
        val repo = FakeCustomRepo()
        val vm = vmWith(repo)
        vm.tap(STAR_A)
        vm.tap(STAR_B)
        vm.tap(STAR_A) // close the loop
        val id = vm.save("My shape")!!
        assertThat(id).isEqualTo("custom/test-1")
        val (figure, name) = repo.saved.single()
        assertThat(name).isEqualTo("My shape")
        assertThat(figure.strokes).isEqualTo(listOf(listOf(STAR_A, STAR_B)))
        assertThat(vm.drawState.canSave).isFalse()
        assertThat(vm.drawState.openStroke).isEmpty()
    }

    @Test
    fun saveWithNothingDrawable_returnsNull() = runTest {
        val vm = vmWith()
        assertThat(vm.save("nothing")).isNull()
        vm.tap(STAR_A) // one open point only
        assertThat(vm.save("still nothing")).isNull()
    }

    @Test
    fun reset_clearsEverything() = runTest {
        val vm = vmWith()
        vm.tap(STAR_A)
        vm.tap(STAR_B)
        vm.reset()
        assertThat(vm.drawState.openStroke).isEmpty()
        assertThat(vm.drawState.canSave).isFalse()
    }
}