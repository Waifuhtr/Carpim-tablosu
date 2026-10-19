package com.carpimtablosu.app

import com.carpimtablosu.app.data.Settings
import com.carpimtablosu.app.data.TimeState
import com.carpimtablosu.app.logic.LockPolicy
import com.carpimtablosu.app.logic.LockReason
import com.carpimtablosu.app.logic.TimeBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeAndLockTest {
    private val day = "2026-10-19"

    @Test fun rewardAddsTime() {
        val (s, gain) = TimeBank.reward(TimeState(day = day), 60, 3600, day)
        assertEquals(60, gain)
        assertEquals(60, s.bankSec)
        assertEquals(60, s.earnedTodaySec)
    }

    @Test fun rewardRespectsDailyCap() {
        val start = TimeState(bankSec = 100, earnedTodaySec = 3570, day = day)
        val (s, gain) = TimeBank.reward(start, 60, 3600, day)
        assertEquals(30, gain)
        assertEquals(130, s.bankSec)
        val (s2, gain2) = TimeBank.reward(s, 60, 3600, day)
        assertEquals(0, gain2)
        assertEquals(130, s2.bankSec)
    }

    @Test fun zeroCapMeansUnlimited() {
        val (s, gain) = TimeBank.reward(TimeState(earnedTodaySec = 999_999, day = day), 60, 0, day)
        assertEquals(60, gain)
        assertEquals(60, s.bankSec)
    }

    @Test fun newDayResetsEarnedCounter() {
        val start = TimeState(bankSec = 10, earnedTodaySec = 3600, day = "2026-10-18")
        val (s, gain) = TimeBank.reward(start, 60, 3600, day)
        assertEquals(60, gain)
        assertEquals(day, s.day)
        assertEquals(60, s.earnedTodaySec)
        assertEquals(70, s.bankSec)
    }

    @Test fun penaltyNeverGoesBelowZero() {
        val (s, loss) = TimeBank.penalize(TimeState(bankSec = 20, earnedTodaySec = 20, day = day), 60, day)
        assertEquals(20, loss)
        assertEquals(0, s.bankSec)
        assertEquals(0, s.earnedTodaySec)
        val (s2, loss2) = TimeBank.penalize(s, 60, day)
        assertEquals(0, loss2)
        assertEquals(0, s2.bankSec)
    }

    @Test fun penaltyGivesBackDailyRoom() {
        // Cezalı süre, günlük sınırdan da düşülür; tekrar kazanılabilir
        val full = TimeState(bankSec = 3600, earnedTodaySec = 3600, day = day)
        val (afterWrong, _) = TimeBank.penalize(full, 60, day)
        val (afterRight, gain) = TimeBank.reward(afterWrong, 60, 3600, day)
        assertEquals(60, gain)
        assertEquals(3600, afterRight.bankSec)
    }

    @Test fun spendClampsToBank() {
        val (s, used) = TimeBank.spend(TimeState(bankSec = 5), 10)
        assertEquals(5, used)
        assertEquals(0, s.bankSec)
        assertEquals(0, TimeBank.spend(s, -3).second)
    }

    @Test fun adjustNeverNegative() {
        assertEquals(0, TimeBank.adjust(TimeState(bankSec = 100), -500).bankSec)
        assertEquals(400, TimeBank.adjust(TimeState(bankSec = 100), 300).bankSec)
    }

    @Test fun quietHoursAcrossMidnight() {
        val start = 22 * 60
        val end = 7 * 60
        assertTrue(TimeBank.inQuietHours(true, start, end, 23 * 60))
        assertTrue(TimeBank.inQuietHours(true, start, end, 3 * 60))
        assertTrue(TimeBank.inQuietHours(true, start, end, 22 * 60))
        assertFalse(TimeBank.inQuietHours(true, start, end, 7 * 60))
        assertFalse(TimeBank.inQuietHours(true, start, end, 12 * 60))
        assertFalse(TimeBank.inQuietHours(false, start, end, 23 * 60))
    }

    @Test fun quietHoursSameDayAndDisabled() {
        assertTrue(TimeBank.inQuietHours(true, 13 * 60, 15 * 60, 14 * 60))
        assertFalse(TimeBank.inQuietHours(true, 13 * 60, 15 * 60, 15 * 60))
        assertFalse(TimeBank.inQuietHours(true, 600, 600, 600))
    }

    @Test fun formatTime() {
        assertEquals("0:00", TimeBank.format(0))
        assertEquals("1:05", TimeBank.format(65))
        assertEquals("1:00:00", TimeBank.format(3600))
        assertEquals("0:00", TimeBank.format(-5))
    }

    // ---------------- kilit kararı ----------------

    private val yt = "com.google.android.youtube"
    private val base = Settings(onboarded = true, lockedApps = setOf(yt))
    private val now = 1_000_000L

    @Test fun lockedAppWithoutTimeIsBlocked() {
        assertEquals(LockReason.NO_TIME, LockPolicy.decide(base, TimeState(bankSec = 0), yt, now, 600))
    }

    @Test fun lockedAppWithTimeIsAllowed() {
        assertEquals(LockReason.NONE, LockPolicy.decide(base, TimeState(bankSec = 30), yt, now, 600))
    }

    @Test fun otherAppsAreNeverBlocked() {
        assertEquals(LockReason.NONE, LockPolicy.decide(base, TimeState(), "com.example.notes", now, 600))
    }

    @Test fun noLocksBeforeOnboarding() {
        assertEquals(LockReason.NONE, LockPolicy.decide(base.copy(onboarded = false), TimeState(), yt, now, 600))
    }

    @Test fun parentCanDisableOrBypass() {
        assertEquals(LockReason.NONE, LockPolicy.decide(base.copy(locksEnabled = false), TimeState(), yt, now, 600))
        assertEquals(LockReason.NONE, LockPolicy.decide(base.copy(parentBypassUntil = now + 1), TimeState(), yt, now, 600))
        assertEquals(LockReason.NO_TIME, LockPolicy.decide(base.copy(parentBypassUntil = now), TimeState(), yt, now, 600))
    }

    @Test fun quietHoursBlockEvenWithTime() {
        val s = base.copy(quietEnabled = true, quietStartMin = 22 * 60, quietEndMin = 7 * 60)
        assertEquals(LockReason.QUIET_HOURS, LockPolicy.decide(s, TimeState(bankSec = 600), yt, now, 23 * 60))
        assertEquals(LockReason.NONE, LockPolicy.decide(s, TimeState(bankSec = 600), yt, now, 12 * 60))
    }

    @Test fun settingsProtection() {
        assertTrue(LockPolicy.protectSettings(base, now))
        assertFalse(LockPolicy.protectSettings(base.copy(protectSettings = false), now))
        assertFalse(LockPolicy.protectSettings(base.copy(settingsBypassUntil = now + 1), now))
        assertFalse(LockPolicy.protectSettings(base.copy(locksEnabled = false), now))
        assertFalse(LockPolicy.protectSettings(base.copy(onboarded = false), now))
    }
}
