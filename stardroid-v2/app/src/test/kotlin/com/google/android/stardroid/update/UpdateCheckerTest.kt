/*
 * Copyright (c) 2026 The Digital Fleet (SkyLuz fork).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.update

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class UpdateCheckerTest {
    private val json =
        """
        {
          "url": "https://api.github.com/repos/lyra-navigator/SkyLuz/releases/1",
          "tag_name": "v2.2.0",
          "body": "Fixes and the\\nchallenge tab.",
          "assets": [
            {"name": "skyluz-2.2.0.apk",
             "browser_download_url": "https://github.com/lyra-navigator/SkyLuz/releases/download/v2.2.0/skyluz-2.2.0.apk"}
          ]
        }
        """.trimIndent()

    @Test
    fun parseLatest_extractsTagApkAndNotes() {
        val r = UpdateChecker.parseLatest(json)!!
        assertThat(r.version).isEqualTo("2.2.0")
        assertThat(r.apkUrl).contains("skyluz-2.2.0.apk")
        assertThat(r.notes).contains("challenge")
    }

    @Test
    fun parseLatest_garbage_returnsNull() {
        assertThat(UpdateChecker.parseLatest("not json at all")).isNull()
        assertThat(UpdateChecker.parseLatest("{\"other\":1}")).isNull()
    }

    @Test
    fun semverCompare_ordersCorrectly() {
        assertThat(UpdateChecker.compareVersions("2.1.0", "2.2.0")).isLessThan(0)
        assertThat(UpdateChecker.compareVersions("2.1.0", "2.1.0")).isEqualTo(0)
        assertThat(UpdateChecker.compareVersions("2.1.1", "2.1.0")).isGreaterThan(0)
        assertThat(UpdateChecker.compareVersions("2.1.0", "2.10.0")).isLessThan(0)
        assertThat(UpdateChecker.compareVersions("v2.1.0", "2.1.0")).isEqualTo(0)
        assertThat(UpdateChecker.compareVersions("2.1.0-rc1", "2.1.0")).isLessThan(0)
        assertThat(UpdateChecker.compareVersions("2.2.0", "2.1.9")).isGreaterThan(0)
    }

    @Test
    fun updateAvailable_trueOnlyForNewer() {
        val r = UpdateChecker.LatestRelease("2.2.0", null, "", null)
        assertThat(UpdateChecker.updateAvailable("2.1.0", r)).isTrue()
        assertThat(UpdateChecker.updateAvailable("2.2.0", r)).isFalse()
        assertThat(UpdateChecker.updateAvailable("2.3.0", r)).isFalse()
    }
}