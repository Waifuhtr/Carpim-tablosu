package com.carpimtablosu.app.data

import kotlinx.serialization.Serializable

/** Tek bir çarpım gerçeği (ör. 7 × 8) için Leitner kutusu ve istatistikleri. */
@Serializable
data class FactStat(
    val box: Int = 0,
    val dueAt: Long = 0L,
    val correct: Int = 0,
    val wrong: Int = 0,
    val seen: Boolean = false,
)

/** Bir güne ait sayılar. Tarih anahtarı ISO biçimindedir (2026-10-19). */
@Serializable
data class DayStat(
    val correct: Int = 0,
    val wrong: Int = 0,
    val earnedSec: Long = 0L,
    val spentSec: Long = 0L,
)

/** Çocuğun öğrenme ilerlemesi. Yedekleme dosyasına yazılan kısım budur. */
@Serializable
data class Progress(
    val childName: String = "",
    val facts: Map<String, FactStat> = emptyMap(),
    /** Tablo -> tamamlanan düğüm sayısı (0..Curriculum.NODES_PER_TABLE). */
    val completedNodes: Map<Int, Int> = emptyMap(),
    val xp: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastGoalDate: String? = null,
    val days: Map<String, DayStat> = emptyMap(),
    val totalCorrect: Int = 0,
    val totalWrong: Int = 0,
    val lessonsDone: Int = 0,
    val perfectLessons: Int = 0,
    val sprintBest: Int = 0,
    val achievements: Set<String> = emptySet(),
)

/** Ebeveynin ve çocuğun ayarları. */
@Serializable
data class Settings(
    // Kilit
    val locksEnabled: Boolean = true,
    val lockedApps: Set<String> = emptySet(),
    val rewardSec: Long = 60L,
    val penaltySec: Long = 60L,
    /** Günde kazanılabilecek en fazla süre (saniye). 0 = sınırsız. */
    val dailyEarnCapSec: Long = 60L * 60L,
    /** Gece modu başlangıç/bitiş (gün içi dakika). Eşitse kapalı. */
    val quietStartMin: Int = 22 * 60,
    val quietEndMin: Int = 7 * 60,
    val quietEnabled: Boolean = false,
    /** Ebeveyn bu zamana kadar (epoch ms) tüm kilitleri kaldırdı. */
    val parentBypassUntil: Long = 0L,
    /** Ayarlar ve kaldırma ekranlarını şifreyle koru. */
    val protectSettings: Boolean = true,
    val settingsBypassUntil: Long = 0L,
    // Çocuk
    val dailyGoal: Int = 20,
    val speakQuestions: Boolean = false,
    val voiceAnswers: Boolean = true,
    val sounds: Boolean = true,
    val haptics: Boolean = true,
    val onboarded: Boolean = false,
)

/** Kilitli uygulamalarda kullanılabilecek süre. */
@Serializable
data class TimeState(
    val bankSec: Long = 0L,
    val earnedTodaySec: Long = 0L,
    val day: String = "",
)

/** Dışa aktarılan yedek dosyası. Şifre ve süre bilerek dahil edilmez. */
@Serializable
data class Backup(
    val format: Int = 1,
    val progress: Progress,
)

/** Ebeveyn şifresi ve kurtarma kodu özetleri (düz metin saklanmaz). */
@Serializable
data class Security(
    val pwSalt: String? = null,
    val pwHash: String? = null,
    val rcSalt: String? = null,
    val rcHash: String? = null,
    val fails: Int = 0,
    val lockoutUntil: Long = 0L,
)

/** Kilit servisinin kendi sürecinden yazdığı durum; ekran tarafı tanılama için okur. */
@Serializable
data class ServiceStatus(
    val connected: Boolean = false,
    /** Son canlılık sinyali (epoch ms). */
    val heartbeat: Long = 0L,
    val lastForeground: String? = null,
    val lastDecision: String? = null,
    val pid: Int = 0,
)
