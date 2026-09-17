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
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The Constellations tab, Catalyst's 2.7.0 shape — challenges AND find in the SAME screen
 * (feedback #1: "the interactions should be the same"). ONE screen per figure: art, info,
 * progress counter, and a single action button that is **Start** when idle and **Stop** while
 * the session runs. Start arms the session and closes the tab (back to the sky instantly);
 * the map shows the HUD + Tip while the session is live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstellationsTabScreen(
    viewModel: ChallengeTabViewModel,
    findViewModel: FindGameViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val findPicks by findViewModel.picks.collectAsStateWithLifecycle()
    val findSession by findViewModel.session.collectAsStateWithLifecycle()
    var openDetail by rememberSaveable { mutableStateOf<String?>(null) }
    // "challenge:<id>" rows come from the pack; "find:<name>" rows from the IAU catalog.
    val openIsFind = openDetail?.startsWith("find:") == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (openDetail != null) "Constellation" else "Constellations") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (openDetail != null) openDetail = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val activeChallenge = session
        val activeFind = findSession
        when {
            openDetail != null -> {
                if (openIsFind) {
                    val name = openDetail!!.removePrefix("find:")
                    val pick = findPicks.firstOrNull { it.name == name }
                    if (pick == null) {
                        openDetail = null
                    } else {
                        val isActive = activeFind?.figureName == name
                        val covered = ChallengeProgress.coveredIndices(
                            findViewModel.findContext, "find:$name")
                        FigureDetail(
                            name = pick.name,
                            starCount = FindGame.vertices(pick.figure).size,
                            covered = covered.size,
                            exampleAsset = null,
                            instructions = "The lines are hidden on the sky. Tap each star you " +
                                "think belongs to " + pick.name + ". Progress is saved as you go.",
                            isActive = isActive,
                            onStart = {
                                findViewModel.start(pick)
                                onBack()
                            },
                            onStop = { findViewModel.cancel() },
                        )
                    }
                } else {
                    val id = openDetail!!
                    val entry = entries.firstOrNull { it.challenge.id == id }
                    if (entry == null) {
                        openDetail = null
                    } else {
                        val isActive = activeChallenge?.challenge?.id == id
                        FigureDetail(
                            name = entry.challenge.name,
                            starCount = entry.challenge.vertices.size,
                            covered = entry.covered,
                            exampleAsset = entry.challenge.exampleAsset,
                            instructions =
                                "Connect the stars of this shape — they're all real stars. " +
                                    "Start closes this screen: tap each star of the figure on the sky.",
                            isActive = isActive,
                            onStart = {
                                viewModel.start(entry.challenge)
                                onBack()
                            },
                            onStop = { viewModel.cancel() },
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val challengeRows = entries.size
                    val findRows = findPicks.size
                    items(challengeRows + findRows) { i ->
                        if (i < challengeRows) {
                            val entry = entries[i]
                            ProgressRow(
                                title = entry.challenge.name + if (entry.challenge.unlocksAfter != null) " ⭐" else "",
                                covered = entry.covered,
                                total = entry.total,
                                complete = entry.complete,
                                locked = entry.locked,
                                onClick = { openDetail = entry.challenge.id },
                            )
                        } else {
                            val pick = findPicks[i - challengeRows]
                            val key = "find:" + pick.name
                            val covered =
                                ChallengeProgress.coveredIndices(findViewModel.findContext, key).size
                            ProgressRow(
                                title = pick.name,
                                covered = covered,
                                total = FindGame.vertices(pick.figure).size,
                                complete = ChallengeProgress.isComplete(
                                    findViewModel.findContext, key),
                                locked = false,
                                onClick = { openDetail = key },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The unified detail (2.7.0 feedback #2): example art + instructions + progress + a single
 * Start/Stop button. Start closes the screen (the sky takes over); Stop ends the session.
 */
@Composable
private fun FigureDetail(
    name: String,
    starCount: Int,
    covered: Int,
    exampleAsset: String?,
    instructions: String,
    isActive: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (exampleAsset != null) {
            ChallengeExampleImage(exampleAsset = exampleAsset, modifier = Modifier.size(180.dp))
        }
        Text(name, style = MaterialTheme.typography.titleLarge)
        Text(
            "$covered / $starCount stars found" + (if (covered >= starCount && starCount > 0) " — Complete ✓" else ""),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(instructions, style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = { if (isActive) onStop() else onStart() },
            colors =
                if (isActive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isActive) "Stop" else "Start") }
    }
}

/** Browse row with live progress: "x/y stars found" (or Complete / Locked). */
@Composable
private fun ProgressRow(
    title: String,
    covered: Int,
    total: Int,
    complete: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        complete -> "Complete ✓"
                        locked -> "Locked"
                        total > 0 && covered > 0 -> "$covered/$total stars found"
                        else -> "Tap to see"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (locked) {
                Icon(Icons.Filled.Lock, contentDescription = "Locked")
            } else {
                Button(onClick = onClick) { Text("See") }
            }
        }
    }
}

/** The challenge's example picture, loaded straight from assets. */
@Composable
private fun ChallengeExampleImage(
    exampleAsset: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                runCatching {
                    val stream = ctx.assets.open(exampleAsset.removePrefix("file:///android_asset/"))
                    android.graphics.BitmapFactory.decodeStream(stream)?.let { setImageBitmap(it) }
                    stream.close()
                }
            }
        },
        modifier = modifier,
    )
}

/** The find VM's Android context — public constructor property, read directly by the tab UI. */
val FindGameViewModel.findContext: Context
    get() = context