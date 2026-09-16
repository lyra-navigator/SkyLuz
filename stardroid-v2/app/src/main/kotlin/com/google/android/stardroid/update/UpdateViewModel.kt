/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State of the in-app update check (settings screen row). */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** An update is offered: version + the APK (or release-page) link. */
    data class Available(
        val release: UpdateChecker.LatestRelease,
    ) : UpdateState

    /** Running the latest published release. */
    data object UpToDate : UpdateState

    /** Network/parse failure — the row offers a manual link instead of an error dead-end. */
    data class Error(
        val message: String,
    ) : UpdateState
}

/**
 * Backs the "Check for updates" row (Settings). Checks GitHub Releases for a newer semver
 * than the installed [BuildConfig.VERSION_NAME]; nothing auto-installs — the user opens the
 * link and sideloads, per FOSS-store norms.
 */
class UpdateViewModel(
    private val http: UpdateChecker.HttpGet = UpdateChecker.defaultHttp,
    private val installedVersion: String = BuildConfig.VERSION_NAME,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun check() {
        if (_state.value is UpdateState.Checking) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            _state.value =
                try {
                    val json =
                        withContext(Dispatchers.IO) {
                            http.get(UpdateChecker.RELEASES_URL)
                        }
                    val latest = UpdateChecker.parseLatest(json)
                    when {
                        latest == null -> UpdateState.Error("Could not read releases")
                        UpdateChecker.updateAvailable(installedVersion, latest) -> UpdateState.Available(latest)
                        else -> UpdateState.UpToDate
                    }
                } catch (e: Exception) {
                    UpdateState.Error(e.message ?: "Network error")
                }
        }
    }

    /** Opens the APK (or release page) in the user's browser. */
    fun openDownload(onOpen: (String) -> Unit) {
        val s = _state.value
        if (s is UpdateState.Available) {
            onOpen(s.release.apkUrl ?: s.release.releaseUrl)
        }
    }
}