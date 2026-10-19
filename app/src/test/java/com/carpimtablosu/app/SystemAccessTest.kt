package com.carpimtablosu.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.carpimtablosu.app.lock.SystemAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemAccessTest {
    private val app: App = ApplicationProvider.getApplicationContext()

    private fun info(pkg: String, label: String) = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = pkg
            name = "$pkg.Main"
            applicationInfo = ApplicationInfo().apply { packageName = pkg }
        }
        nonLocalizedLabel = label
    }

    private fun register(intent: Intent, vararg infos: ResolveInfo) {
        val pm = shadowOf(app.packageManager)
        infos.forEach { pm.addResolveInfoForIntent(intent, it) }
    }

    @Test fun tvAppsAreListedAndSystemAppsAreNot() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        register(launcher, info("com.example.game", "Oyun"), info(app.packageName, "Çarpım"))
        register(
            leanback,
            info("com.google.android.youtube.tv", "YouTube"),
            info("com.oem.tvlauncher", "Üretici Ana Ekranı"),
            info("com.android.tv.settings", "Ayarlar"),
        )
        // Üreticinin ana ekranı sabit listede yok; HOME kaydından algılanmalı
        register(home, info("com.oem.tvlauncher", "Üretici Ana Ekranı"))
        register(Intent(Settings.ACTION_SETTINGS), info("com.oem.settings", "Ayarlar"))

        val pkgs = SystemAccess.launchableApps(app).map { it.pkg }
        assertTrue(pkgs.contains("com.google.android.youtube.tv"))
        assertTrue(pkgs.contains("com.example.game"))
        assertFalse(pkgs.contains("com.oem.tvlauncher"))
        assertFalse(pkgs.contains("com.android.tv.settings"))
        assertFalse(pkgs.contains(app.packageName))

        val never = SystemAccess.neverLockPackages(app)
        assertTrue(never.contains("com.oem.tvlauncher"))
        assertTrue(never.contains("com.oem.settings"))
        assertTrue(never.contains("com.android.systemui"))
    }

    @Test fun tvIsDetectedFromUiMode() {
        assertFalse(Platform.isTv(app))
    }

    @Config(qualifiers = "television")
    @Test fun tvIsDetectedOnTelevision() {
        assertTrue(Platform.isTv(app))
    }

    @Suppress("unused")
    private val unused = ComponentName("a", "b")
}
