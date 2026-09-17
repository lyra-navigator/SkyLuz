/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.challenge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Find mode's full in-map experience (custom-constellations.md §4b): a pick sheet (choose any
 * real IAU constellation), then a top HUD with instructions + live progress + reveal/stop.
 * The map routes taps to the game while a session runs.
 */
@Composable
fun FindModeChrome(
    viewModel: FindGameViewModel,
    onExit: () -> Unit,
    onRevealLines: () -> Unit,
    onTip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picks by viewModel.picks.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()

    if (session == null) {
        // Picker: which constellation do you want to find?
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Find a constellation — pick one:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(onClick = onExit) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                }
                Text(
                    "The lines are hidden. Tap the stars you think belong to it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(picks.size) { i ->
                        val pick = picks[i]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.start(pick) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pick.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${FindGame.vertices(pick.figure).size} stars",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(onClick = { viewModel.start(pick) }) { Text("Find") }
                        }
                    }
                }
            }
        }
    } else {
        val active = session ?: return
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Find: ${active.figureName}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(onClick = {
                        onExit()
                        viewModel.cancel()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Stop")
                    }
                }
                Text(
                    "Tap the stars of ${active.figureName} on the sky. 'Reveal' shows the real figure.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { active.progress.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "${active.progress.coveredVertices}/${active.progress.totalVertices} stars found" +
                        (if (active.progress.complete) " — Found it! 🎉" else ""),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = onTip,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Tip: show me where") }
                if (active.progress.complete) {
                    Button(
                        onClick = {
                            onRevealLines()
                            viewModel.reveal()
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Reveal the figure")
                    }
                }
            }
        }
    }
}