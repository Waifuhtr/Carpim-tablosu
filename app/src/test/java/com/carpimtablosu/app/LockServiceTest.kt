package com.carpimtablosu.app

import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import com.carpimtablosu.app.lock.AppLockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Kilit servisinin uçtan uca davranışı: olay → karar → kilit ekranı. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockServiceTest {
    private val yt = "com.google.android.youtube"
    private lateinit var app: App
    private lateinit var service: AppLockService

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.repo.updateSettings { it.copy(onboarded = true, lockedApps = setOf(yt), locksEnabled = true) }
        app.repo.clearTime()
        service = Robolectric.setupService(AppLockService::class.java)
        // onServiceConnected sistem tarafından çağrılır; testte elle çağırıyoruz
        val m = android.accessibilityservice.AccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        m.isAccessible = true
        m.invoke(service)
    }

    private fun open(pkg: String) {
        @Suppress("DEPRECATION")
        val e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        e.packageName = pkg
        service.onAccessibilityEvent(e)
    }

    private fun idle(sec: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(sec))

    @Test fun lockedAppWithoutTimeShowsLockScreen() {
        assertTrue(AppLockService.connected)
        open(yt)
        assertTrue(service.isLockShowing)
        assertTrue(DebugLog.read().contains("KİLİT: $yt"))
    }

    @Test fun otherAppsStayOpen() {
        open("com.example.notes")
        assertFalse(service.isLockShowing)
    }

    @Test fun timeIsSpentThenAppLocks() {
        app.repo.adjustTime(5)
        open(yt)
        assertFalse(service.isLockShowing)
        idle(3)
        assertTrue(app.repo.time.value.bankSec in 1..3)
        idle(5)
        assertEquals(0, app.repo.time.value.bankSec)
        assertTrue(service.isLockShowing)
    }

    @Test fun parentBypassDisablesLock() {
        app.repo.updateSettings { it.copy(parentBypassUntil = System.currentTimeMillis() + 60_000) }
        open(yt)
        assertFalse(service.isLockShowing)
    }

    @Test fun nothingLocksBeforeOnboarding() {
        app.repo.updateSettings { it.copy(onboarded = false) }
        open(yt)
        assertFalse(service.isLockShowing)
    }

    @Test fun keyboardDoesNotStopTheSession() {
        app.repo.adjustTime(100)
        open(yt)
        open("com.android.systemui")
        idle(4)
        assertTrue(app.repo.time.value.bankSec < 100)
    }
}
