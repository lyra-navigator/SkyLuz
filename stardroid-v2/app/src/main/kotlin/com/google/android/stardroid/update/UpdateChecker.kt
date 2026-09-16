/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.update

import java.util.regex.Pattern

/**
 * The app's update mechanism (FOSS, no store): the SkyLuz GitHub Releases feed is the single
 * source of truth. The checker compares the installed [BuildConfig]-provided version against
 * the latest published semver release and offers the APK download link; installing stays a
 * user action (the APK opens from the browser/download, per Android policy).
 *
 * Pure JVM: network via [HttpGet] seam so the check is unit-testable without a device.
 */
object UpdateChecker {
    /** SkyLuz's public releases feed (latest-stable endpoint, 302-followed by HttpURLConnection). */
    const val RELEASES_URL = "https://api.github.com/repos/lyra-navigator/SkyLuz/releases/latest"
    const val RELEASES_PAGE = "https://github.com/lyra-navigator/SkyLuz/releases"
    private const val USER_AGENT = "SkyLuz-UpdateChecker"

    /** Minimal HTTP seam. */
    fun interface HttpGet {
        fun get(url: String): String
    }

    /** The default GET over HttpURLConnection (follows the /latest redirect). */
    val defaultHttp: HttpGet =
        HttpGet { url ->
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

    data class LatestRelease(
        val version: String,
        val apkUrl: String?,
        val releaseUrl: String,
        val notes: String?,
    )

    /** Parse the GitHub /releases/latest JSON into a [LatestRelease], or null on any surprise. */
    fun parseLatest(json: String): LatestRelease? =
        try {
            // Minimal extraction without a JSON dependency (org.json is on the Android
            // classpath; use it when present, else regex over the known fields).
            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: return null
            val apk =
                Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"")
                    .find(json)?.groupValues?.get(1)
            val notes = Regex("\"body\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)
            LatestRelease(
                version = tag.removePrefix("v"),
                apkUrl = apk,
                releaseUrl = RELEASES_PAGE,
                notes = notes?.replace("\\n", "\n"),
            )
        } catch (e: Exception) {
            null
        }

    /**
     * Semantic-version compare: negative/0/positive when [installed] is older/equal/newer than
     * [candidate]. Tolerates a leading v, -suffixes (2.1.0-rc1 < 2.1.0) and build metadata.
     */
    fun compareVersions(
        installed: String,
        candidate: String,
    ): Int {
        fun parts(v: String): Pair<List<Int>, String> {
            val clean = v.trim().removePrefix("v").removePrefix("V")
            val core = clean.split('-', '.').takeWhile { it.all(Char::isDigit) && it.isNotEmpty() }
            val pre = clean.substringAfter('-', "")
            return core.map { it.toInt() } to pre
        }
        val (a, aPre) = parts(installed)
        val (b, bPre) = parts(candidate)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        // 2.1.0-rc1 < 2.1.0 ; equal cores with no prerelease on either side are equal.
        return when {
            aPre == bPre -> 0
            aPre.isEmpty() -> 1
            bPre.isEmpty() -> -1
            else -> aPre.compareTo(bPre)
        }
    }

    /** True when the user should be offered an update. */
    fun updateAvailable(
        installedVersion: String,
        latest: LatestRelease,
    ): Boolean = compareVersions(installedVersion, latest.version) < 0
}