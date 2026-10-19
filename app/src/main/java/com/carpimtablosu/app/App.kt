package com.carpimtablosu.app

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.carpimtablosu.app.data.Repository
import com.carpimtablosu.app.lock.Watchdog
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Uygulama iki süreçte çalışır: ekranlar (ana süreç) ve kilit servisi (":kilit").
 * Böylece uygulama "Son uygulamalar"dan kapatılsa veya ekranda bir hata olsa bile
 * kilit servisi çalışmaya devam eder.
 */
class App : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val processName = currentProcessName()
        val isMain = processName == packageName
        DebugLog.init(this, if (isMain) "ekran" else "kilit")
        DebugLog.installCrashHandler()
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        DebugLog.log(
            "Süreç başladı (pid ${Process.myPid()}): sürüm=$version cihaz=${Build.MANUFACTURER} ${Build.MODEL} " +
                "Android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
        repo = Repository(this)
        if (isMain) {
            createChannels()
            logExitReasons()
            Watchdog.schedule(this)
            // Servis ölmüşse ve onarım izni varsa yeniden başlat
            Handler(Looper.getMainLooper()).postDelayed({
                Thread { runCatching { Watchdog.check(this, "uygulama açılışı") } }.start()
            }, 5_000)
        }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TIME, getString(R.string.channel_time), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /**
     * Android 11+: önceki süreçlerin neden kapandığını (çökme, bellek, kullanıcı, üretici)
     * kayda yazar. Kilit servisinin neden durduğunu anlamanın en güvenilir yolu budur.
     */
    private fun logExitReasons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val seenFile = File(filesDir, "cikis_son.txt")
            val seen = seenFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L
            val am = getSystemService(ActivityManager::class.java)
            val infos = am.getHistoricalProcessExitReasons(packageName, 0, 20).filter { it.timestamp > seen }
            val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
            for (i in infos.sortedBy { it.timestamp }) {
                val time = Instant.ofEpochMilli(i.timestamp).atZone(ZoneId.systemDefault()).format(fmt)
                DebugLog.log(
                    "Önceki süreç kapandı: ${i.processName} $time neden=${reasonName(i.reason)} " +
                        "önem=${i.importance} açıklama=${i.description ?: "-"}"
                )
            }
            infos.maxOfOrNull { it.timestamp }?.let { seenFile.writeText(it.toString()) }
        }.onFailure { DebugLog.error("Kapanma nedenleri okunamadı", it) }
    }

    private fun reasonName(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_CRASH -> "ÇÖKME"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "ÇÖKME_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "YANIT_VERMİYOR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "DÜŞÜK_BELLEK"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "KULLANICI_KAPATTI"
        ApplicationExitInfo.REASON_USER_STOPPED -> "KULLANICI_DURDURDU"
        ApplicationExitInfo.REASON_SIGNALED -> "SİNYAL(sistem/üretici öldürdü)"
        ApplicationExitInfo.REASON_EXIT_SELF -> "KENDİSİ_ÇIKTI"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "AŞIRI_KAYNAK"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "İZİN_DEĞİŞTİ"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "BAĞIMLILIK_ÖLDÜ"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "BAŞLATMA_HATASI"
        ApplicationExitInfo.REASON_OTHER -> "DİĞER(üretici)"
        else -> "KOD_$r"
    }

    private fun currentProcessName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n') }.getOrDefault(packageName)
        }

    companion object {
        const val CHANNEL_TIME = "time"
        const val CHANNEL_ALERTS = "alerts"

        fun repo(context: Context): Repository = (context.applicationContext as App).repo
    }
}
