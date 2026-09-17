/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.challenge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The running Find-mode HUD (drawn over the map while the session is on). */
@Composable
fun FindModeChrome(
    session: FindGameViewModel.Session,
    onReveal: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(top = 48.dp).padding(horizontal = 16.dp),
    ) {
        Text(
            "Find: ${session.figureName}",
            style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(
            progress = { session.progress.fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(
            "${session.progress.coveredVertices}/${session.progress.totalVertices} stars · ${session.taps.size} taps",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (session.progress.complete) {
            Text(
                "Found it!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = onReveal) { Text("Reveal the real figure") }
            Button(onClick = onCancel) { Text("Stop") }
        }
    }
}