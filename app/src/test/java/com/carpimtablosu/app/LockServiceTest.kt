package com.carpimtablosu.app

import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import com.carpimtablosu.app.lock.AppLockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.time.Duration

/** Kilit servisinin uçtan uca davranışı: olay → karar → kilit ekranı. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockServiceTest {
    private val yt = "com.google.android.youtube"
    private lateinit var app: App
    private lateinit var service: AppLockService
    private lateinit var controller: ServiceController<AppLockService>

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.repo.updateSettings { it.copy(onboarded = true, lockedApps = setOf(yt), locksEnabled = true) }
        app.repo.clearTime()
        controller = Robolectric.buildService(AppLockService::class.java).create()
        service = controller.get()
        // onServiceConnected sistem tarafından çağrılır; testte elle çağırıyoruz
        val m = android.accessibilityservice.AccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        m.isAccessible = true
        m.invoke(service)
    }

    /**
     * Servis kapatılmazsa her saniyelik zamanlayıcısı ortak test döngüsünde kalır ve
     * sonraki testleri (ör. Compose testleri) bekletir.
     */
    @After fun tearDown() {
        controller.destroy()
    }

    private fun open(pkg: String) {
        @Suppress("DEPRECATION")
        val e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        e.packageName = pkg
        service.onAccessibilityEvent(e)
    }

    private fun idle(sec: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(sec))

    @Test fun lockedAppWithoutTimeShowsLockScreen() {
        assertTrue(app.repo.serviceStatus().connected)
        open(yt)
        assertTrue(service.isLockShowing)
        assertTrue(DebugLog.read().contains("KİLİT: $yt"))
    }

    @Test fun otherAppsStayOpen() {
        open("com.example.notes")
        assertFalse(service.isLockShowing)
    }

    @Test fun timeIsSpentThenAppLocks() {
        app.repo.adjustTime(8)
        open(yt)
        assertFalse(service.isLockShowing)
        idle(6)
        assertTrue(app.repo.time.value.bankSec in 1..3) // birkaç saniyede bir toplu düşülür
        idle(6)
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
        idle(7)
        assertTrue(app.repo.time.value.bankSec < 100)
    }

    @Test fun systemAppsAreNeverLockedEvenIfSelected() {
        // Ayarlarda yanlışlıkla seçilmiş olsalar bile ana ekran ve ayarlar kilitlenmez
        val risky = listOf("com.android.settings", "com.android.tv.settings", "com.google.android.apps.tv.launcherx", app.packageName)
        app.repo.updateSettings { it.copy(lockedApps = it.lockedApps + risky) }
        for (p in risky) {
            open(p)
            assertFalse("$p kilitlenmemeli", service.isLockShowing)
        }
    }

    @Test fun lockScreenClosesItselfAfterTimeout() {
        open(yt)
        assertTrue(service.isLockShowing)
        idle(30)
        assertTrue(service.isLockShowing)
        idle(20)
        assertFalse(service.isLockShowing)
        assertTrue(DebugLog.read().contains("süre aşımıyla kapandı"))
    }

    @Test fun safetyBrakeStopsRunawayLocking() {
        // Bir hata yüzünden sürekli kilit tetiklenirse kilit kısa süre durmalı
        repeat(16) {
            open(yt)
            service.dismissLockForTest()
            open("com.example.notes")
            idle(1)
        }
        open(yt)
        assertFalse(service.isLockShowing)
        assertTrue(DebugLog.read().contains("GÜVENLİK FRENİ"))
        idle(61)
        open("com.example.notes")
        open(yt)
        assertTrue(service.isLockShowing)
    }
}
