/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.draw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.R
import kotlinx.coroutines.launch

/**
 * The constellation draw-mode chrome (custom-constellations.md §2): a top bar of actions
 * (undo / end stroke / cancel / save) while the sky itself takes the taps. The map routes
 * taps to the draw VM while the mode is on; camera gestures stay live.
 */
@Composable
fun DrawModeChrome(
    viewModel: ConstellationDrawViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showNameDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(top = 48.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            // Cancel: discard the whole drawing and leave draw mode.
            FilledTonalIconButton(onClick = onExit) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel drawing")
            }
            Text(
                text = "Tap stars to link them",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            // Undo last point.
            FilledTonalIconButton(
                onClick = { viewModel.undo() },
                enabled = state.openStroke.isNotEmpty() || state.closedStrokes.isNotEmpty(),
            ) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = "Undo")
            }
            // Save: primary action, enabled once anything drawable exists. Saving closes the
            // open stroke implicitly (strokes() includes it).
            FilledTonalIconButton(
                onClick = { showNameDialog = true },
                enabled = state.canSave,
            ) {
                Icon(
                    painterResource(R.drawable.ic_draw_constellation),
                    contentDescription = "Save constellation",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (showNameDialog) {
        SaveNameDialog(
            onDismiss = { showNameDialog = false },
            onSave = { name ->
                showNameDialog = false
                scope.launch { viewModel.save(name) }
            },
        )
    }
}

@Composable
private fun SaveNameDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this constellation") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}