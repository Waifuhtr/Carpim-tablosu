package com.carpimtablosu.app.logic

import com.carpimtablosu.app.data.Settings
import com.carpimtablosu.app.data.TimeState

enum class LockReason { NONE, NO_TIME, QUIET_HOURS }

object LockPolicy {
    /** Kurulum bitmeden, ebeveyn kapattıysa veya geçici olarak kaldırdıysa kilit yoktur. */
    fun locksActive(settings: Settings, nowMs: Long): Boolean =
        settings.onboarded && settings.locksEnabled && nowMs >= settings.parentBypassUntil

    /** Seçili bir uygulama şu an açılabilir mi? */
    fun decide(settings: Settings, time: TimeState, pkg: String, nowMs: Long, minuteOfDay: Int): LockReason {
        if (pkg !in settings.lockedApps || !locksActive(settings, nowMs)) return LockReason.NONE
        if (TimeBank.inQuietHours(settings.quietEnabled, settings.quietStartMin, settings.quietEndMin, minuteOfDay)) {
            return LockReason.QUIET_HOURS
        }
        return if (time.bankSec <= 0L) LockReason.NO_TIME else LockReason.NONE
    }

    /** Ayarlar / kaldırma ekranı korunmalı mı? */
    fun protectSettings(settings: Settings, nowMs: Long): Boolean =
        settings.protectSettings && locksActive(settings, nowMs) && nowMs >= settings.settingsBypassUntil
}
