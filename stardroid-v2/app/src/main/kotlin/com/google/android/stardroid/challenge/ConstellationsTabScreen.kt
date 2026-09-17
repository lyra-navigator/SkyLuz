/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.challenge

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Constellations tab (custom-constellations.md §4b), in the browse → detail → play shape
 * Catalyst specified: the list rows open a DETAIL screen ("See"); Start lives on the detail;
 * starting closes the screen so the sky is immediately playable; an active session shows the
 * stars-found counter and a Tip button (slew + zone highlight).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstellationsTabScreen(
    viewModel: ChallengeTabViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    var openDetail by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            session != null -> "In the sky — tap the stars"
                            openDetail != null -> "Constellation"
                            else -> "Constellations"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            session != null -> viewModel.cancel()
                            openDetail != null -> openDetail = null
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val active = session
        when {
            // Playing: progress + Tip + the example art; the sky is behind this panel.
            active != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(active.challenge.name, style = MaterialTheme.typography.titleLarge)
                    ChallengeExampleImage(challenge = active.challenge, modifier = Modifier.size(120.dp))
                    LinearProgressIndicator(
                        progress = { active.progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${active.progress.coveredVertices}/${active.progress.totalVertices} stars found")
                    if (active.progress.complete) {
                        Text(
                            "Complete! Saved to My constellations 🎉",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Button(onClick = { viewModel.cancel() }) { Text("Done") }
                    } else {
                        Button(onClick = { viewModel.cancel() }) { Text("Give up") }
                    }
                }
            }
            // Detail screen: example art + Start.
            openDetail != null -> {
                val entry = entries.firstOrNull { it.challenge.id == openDetail }
                if (entry == null) {
                    openDetail = null
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ChallengeExampleImage(challenge = entry.challenge, modifier = Modifier.size(180.dp))
                        Text(entry.challenge.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Connect the stars of this shape — they're all real stars in Orion's " +
                                "region. When you Start, the sky opens: tap each star of the figure.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (entry.complete) {
                            Text("Complete ✓", color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = {
                                viewModel.start(entry.challenge)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start") }
                    }
                }
            }
            // Browse list: See (not Start) per row.
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries.size) { i ->
                        val entry = entries[i]
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .clickable { openDetail = entry.challenge.id },
                        ) {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp),
                            ) {
                                ChallengeExampleImage(challenge = entry.challenge, modifier = Modifier.size(64.dp))
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(
                                        entry.challenge.name + if (entry.challenge.unlocksAfter != null) " ⭐" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        if (entry.complete) "Complete ✓" else "Tap to see",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (entry.locked) {
                                    Icon(Icons.Filled.Lock, contentDescription = "Locked")
                                } else {
                                    Button(onClick = { openDetail = entry.challenge.id }) { Text("See") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The challenge's example picture, loaded straight from assets. */
@Composable
private fun ChallengeExampleImage(
    challenge: Challenge,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                runCatching {
                    val stream = ctx.assets.open(challenge.exampleAsset.removePrefix("file:///android_asset/"))
                    android.graphics.BitmapFactory.decodeStream(stream)?.let { setImageBitmap(it) }
                    stream.close()
                }
            }
        },
        modifier = modifier,
    )
}