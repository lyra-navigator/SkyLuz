/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.draw

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.R
import com.google.android.stardroid.catalog.CustomFigure
import com.google.android.stardroid.catalog.CustomFigureRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the "My constellations" library screen (custom-constellations.md §4). */
class MyConstellationsViewModel(
    private val customFigures: suspend () -> CustomFigureRepository,
) : ViewModel() {
    val figures: StateFlow<List<CustomFigure>> =
        flow { emitAll(customFigures().figures()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setVisible(
        id: String,
        visible: Boolean,
    ) {
        viewModelScope.launch { customFigures().setVisible(id, visible) }
    }

    fun delete(id: String) {
        viewModelScope.launch { customFigures().delete(id) }
    }
}

/**
 * The user's saved constellations: name, star count, created date, per-figure visibility
 * (toggle hides/shows it on the sky) and delete. Export/import arrive with the challenges
 * slice; the list is the MVP of the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyConstellationsScreen(
    viewModel: MyConstellationsViewModel,
    onBack: () -> Unit,
) {
    val figures by viewModel.figures.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My constellations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (figures.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No constellations yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap the draw button on the sky map, tap stars to link them, and save.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(figures, key = { it.id }) { figure ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                figure.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (figure.visible) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                            Text(
                                "${figure.figure.strokes.sumOf { it.size }} stars · " +
                                    java.text.DateFormat.getDateInstance()
                                        .format(java.util.Date(figure.createdEpochMillis)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { viewModel.setVisible(figure.id, !figure.visible) }) {
                            Icon(
                                painterResource(if (figure.visible) R.drawable.ic_eye else R.drawable.ic_eye_off),
                                contentDescription = if (figure.visible) "Hide" else "Show",
                            )
                        }
                        IconButton(onClick = { viewModel.delete(figure.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}