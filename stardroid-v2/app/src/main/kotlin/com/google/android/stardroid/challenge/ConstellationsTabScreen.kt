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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.R

/**
 * The Constellations tab (custom-constellations.md §4b): starter challenges with example
 * art, tap-to-play; a running session shows live progress. Find mode lands here next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstellationsTabScreen(
    viewModel: ChallengeTabViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Constellations") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (session != null) viewModel.cancel() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val active = session
        if (active != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Find the ${active.challenge.name}! Tap its stars on the sky.",
                    style = MaterialTheme.typography.titleMedium,
                )
                val context = LocalContext.current
                ChallengeExampleImage(challenge = active.challenge, modifier = Modifier.size(140.dp))
                LinearProgressIndicator(
                    progress = { active.progress.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${active.progress.coveredVertices}/${active.progress.totalVertices} stars found")
                if (active.progress.complete) {
                    Text(
                        "Complete! Saved to My constellations.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(onClick = { viewModel.cancel() }) { Text("Done") }
                } else {
                    Button(onClick = { viewModel.cancel() }) { Text("Give up") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries.size) { i ->
                    val entry = entries[i]
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp),
                        ) {
                            ChallengeExampleImage(challenge = entry.challenge, modifier = Modifier.size(72.dp))
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    entry.challenge.name + if (entry.challenge.unlocksAfter != null) " ⭐" else "",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    if (entry.complete) "Complete ✓" else "Find it in Orion's region",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (entry.locked) {
                                Icon(Icons.Filled.Lock, contentDescription = "Locked")
                            } else {
                                Button(onClick = { viewModel.start(entry.challenge) }) {
                                    Text(if (entry.complete) "Again" else "Start")
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