package com.carpimtablosu.app.lock

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import com.carpimtablosu.app.App
import com.carpimtablosu.app.DebugLog

/**
 * Kilit servisi bekçisi.
 *
 * Android, süreci ölen bir erişilebilirlik servisini kendiliğinden yeniden başlatmaz
 * (ayarlarda "Bu hizmet hatalı çalışıyor" yazar). Kullanıcı servisi kapatıp açınca düzelir.
 * Ebeveyn bilgisayardan bir kez
 *   adb shell pm grant com.carpimtablosu.app android.permission.WRITE_SECURE_SETTINGS
 * komutunu verirse bekçi aynı kapat-aç işlemini kendisi yapar.
 */
object Watchdog {
    private const val JOB_ID = 4201
    private const val PERIOD_MS = 15 * 60_000L
    /** Servis bu kadar süredir sinyal vermiyorsa ölü sayılır. */
    private const val STALE_MS = 90_000L

    const val ADB_COMMAND = "adb shell pm grant com.carpimtablosu.app android.permission.WRITE_SECURE_SETTINGS"

    fun schedule(context: Context) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        if (js.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, WatchdogJob::class.java))
            .setPeriodic(PERIOD_MS)
            .build()
        runCatching { js.schedule(job) }.onFailure { DebugLog.error("Bekçi zamanlanamadı", it) }
    }

    fun canRepair(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Servis ayarlarda açık ama çalışmıyor mu? */
    fun isDead(context: Context): Boolean {
        if (!SystemAccess.isAccessibilityEnabled(context)) return false
        val st = App.repo(context).serviceStatus()
        return !st.connected || System.currentTimeMillis() - st.heartbeat > STALE_MS
    }

    /** Gerekirse servisi yeniden başlatır. Arka planda (iş parçacığında) çağrılabilir. */
    fun check(context: Context, source: String) {
        // Ekran kapalıyken servis zamanlayıcısı ertelenebilir; yanlış alarm vermemek için atla
        if (!context.getSystemService(PowerManager::class.java).isInteractive) return
        if (!isDead(context)) return
        DebugLog.log("Bekçi ($source): kilit servisi çalışmıyor")
        if (!canRepair(context)) {
            DebugLog.log("Bekçi: otomatik onarım izni yok (adb komutu verilmemiş)")
            return
        }
        repair(context)
    }

    /** Servisi ayarlardan kapatıp yeniden açar; sistem servisi yeniden bağlar. */
    fun repair(context: Context) {
        val cr = context.contentResolver
        val me = ComponentName(context, AppLockService::class.java).flattenToString()
        try {
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val others = current.split(':').filter { it.isNotBlank() && !it.equals(me, ignoreCase = true) }
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.joinToString(":"))
            Thread.sleep(1_000)
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (others + me).joinToString(":"))
            Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            DebugLog.log("Bekçi: kilit servisi yeniden başlatıldı")
        } catch (e: SecurityException) {
            DebugLog.error("Bekçi: ayar yazılamadı", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

/** 15 dakikada bir bekçiyi çalıştırır. */
class WatchdogJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            runCatching { Watchdog.check(applicationContext, "zamanlanmış") }
                .onFailure { DebugLog.error("Bekçi hatası", it) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
