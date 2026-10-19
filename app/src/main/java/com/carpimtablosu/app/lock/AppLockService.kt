package com.carpimtablosu.app.lock

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.carpimtablosu.app.App
import com.carpimtablosu.app.DebugLog
import com.carpimtablosu.app.Platform
import com.carpimtablosu.app.R
import com.carpimtablosu.app.data.Repository
import com.carpimtablosu.app.data.ServiceStatus
import com.carpimtablosu.app.logic.LockPolicy
import com.carpimtablosu.app.logic.LockReason
import com.carpimtablosu.app.logic.TimeBank
import com.carpimtablosu.app.ui.MainActivity

/**
 * Ön plandaki uygulamayı izler. Seçili bir uygulama açıkken süre bankasından düşer,
 * süre bitince (veya gece modunda) kilit ekranını gösterir.
 *
 * Ön plandaki uygulama iki yoldan öğrenilir: pencere değişim olayları ve her saniye
 * yapılan yoklama (bazı telefonlar olayları geciktirir veya hiç göndermez).
 * Süre ölçümü SystemClock.elapsedRealtime ile yapılır; saati değiştirmek süreyi etkilemez.
 *
 * Servis ayrı bir süreçte (":kilit") çalışır; uygulama ekranı kapansa veya hata verse
 * bile etkilenmez. Durumunu diske yazar, ekran tarafı tanılama için oradan okur.
 */
class AppLockService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repo: Repository
    private lateinit var power: PowerManager
    private lateinit var notifications: NotificationManager
    private lateinit var overlay: LockOverlay

    /** Kullanıcının şu an gördüğü uygulama (klavye ve bildirim paneli hariç). */
    private var foregroundPkg: String? = null
    /** Süresi işleyen kilitli uygulama. */
    private var sessionPkg: String? = null
    private var lastTick = 0L
    private var pendingMs = 0L
    private var unflushedSpent = 0L
    private var lastFlush = 0L
    private var warned = false
    private var lastBlock = 0L
    private var lastProtectCheck = 0L
    private var lastLoggedDecision = ""
    private var lastDecision: String? = null
    private var lastStatusWrite = 0L
    private var ignored: Set<String> = emptySet()
    /** Ana ekran, ayarlar, sistem: ayarlarda seçili olsa bile asla kilitlenmez. */
    private var neverLock: Set<String> = emptySet()
    private var isTv = false
    /** Son kilitlerin zamanları; kısa sürede çok fazla kilit olursa fren devreye girer. */
    private val recentBlocks = ArrayDeque<Long>()
    private var brakeUntil = 0L

    private val testReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            DebugLog.log("Deneme kilidi istendi")
            overlay.show(LockMode.NO_TIME, "")
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                // Ekran kapanırken birikmiş süreyi işle; kapalıyken süre saymaz
                accountTime(force = true)
                flushSpent()
            } else {
                lastTick = SystemClock.elapsedRealtime()
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                onTick()
            } catch (e: RuntimeException) {
                DebugLog.error("Zamanlayıcı hatası", e)
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repo = App.repo(this)
        power = getSystemService(PowerManager::class.java)
        notifications = getSystemService(NotificationManager::class.java)
        overlay = LockOverlay(this, onHome = { goHome() }, onEarn = { openTraining() }, onEmergency = { openDialer() })
        ignored = buildIgnoredSet()
        neverLock = SystemAccess.neverLockPackages(this)
        isTv = Platform.isTv(this)
        DebugLog.log("Platform: ${if (isTv) "TV" else "telefon"}, asla kilitlenmeyen: ${neverLock.sorted()}")
        repo.refresh()
        val s = repo.settings.value
        DebugLog.log(
            "Servis bağlandı. kurulum=${s.onboarded} kilitler=${s.locksEnabled} " +
                "kilitliUygulama=${s.lockedApps.size} süre=${repo.time.value.bankSec}sn yoksay=$ignored"
        )
        // Not: burada koruma muafiyeti verilmez; aksi halde telefonu yeniden başlatmak
        // korumayı birkaç dakika kapatırdı. Ebeveyn servisi panelden açarken panel zaten muafiyet verir.
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this, testReceiver, IntentFilter(ACTION_TEST_LOCK), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        writeStatus(connected = true)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::repo.isInitialized) return
        val pkg = event?.packageName?.toString() ?: return
        val type = event.eventType
        try {
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                onWindowChanged(pkg, "olay")
            } else if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                pkg == foregroundPkg && pkg in SystemAccess.PROTECTED_PACKAGES
            ) {
                checkProtection(throttle = true)
            }
        } catch (e: RuntimeException) {
            DebugLog.error("Olay işlenemedi ($pkg)", e)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        DebugLog.log("Servis bağlantısı kesildi (ayarlardan kapatılmış olabilir)")
        if (::repo.isInitialized) writeStatus(connected = false)
        return super.onUnbind(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) DebugLog.log("Bellek azalıyor (seviye $level)")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::repo.isInitialized) {
            endSession()
            overlay.hide()
            runCatching { unregisterReceiver(screenReceiver) }
            runCatching { unregisterReceiver(testReceiver) }
            writeStatus(connected = false)
        }
        DebugLog.log("Servis durdu")
        super.onDestroy()
    }

    // ---------------------------------------------------------------------

    private fun buildIgnoredSet(): Set<String> {
        val imes = runCatching {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        }.getOrDefault(emptyList())
        return (imes + "com.android.systemui").toSet()
    }

    private fun onWindowChanged(pkg: String, source: String) {
        // Klavye ve bildirim paneli ön plandaki uygulamayı değiştirmez
        if (pkg in ignored) return
        if (pkg != foregroundPkg) DebugLog.log("Ön plan ($source): $pkg")
        foregroundPkg = pkg
        when {
            pkg == packageName -> endSession()
            pkg in SystemAccess.PROTECTED_PACKAGES -> {
                endSession()
                checkProtection(throttle = false)
            }
            pkg in neverLock -> {
                // Güvenlik: sistem uygulamaları ayarlarda seçili olsa bile kilitlenmez
                if (pkg in repo.settings.value.lockedApps) DebugLog.log("Güvenlik: $pkg sistem uygulaması, kilitlenmedi")
                endSession()
            }
            else -> evaluate(pkg)
        }
    }

    private fun evaluate(pkg: String) {
        // Ayarlar ve süre ekran sürecinde değişmiş olabilir: diskteki son hali kullan
        repo.refresh()
        val settings = repo.settings.value
        val now = System.currentTimeMillis()
        val bank = repo.time.value.bankSec
        val reason = LockPolicy.decide(settings, repo.time.value, pkg, now, repo.minuteOfDay())
        if (pkg in settings.lockedApps) {
            val d = "Karar $pkg: $reason (süre=${bank}sn, kurulum=${settings.onboarded}, " +
                "kilitler=${settings.locksEnabled}, ebeveynMuafiyeti=${settings.parentBypassUntil > now})"
            if (d != lastLoggedDecision) {
                DebugLog.log(d)
                lastLoggedDecision = d
            }
        }
        if (lastDecision != "$pkg → $reason") {
            lastDecision = "$pkg → $reason"
            writeStatus(connected = true)
        }
        if (reason != LockReason.NONE) {
            endSession()
            block(LockMode.of(reason), pkg)
            return
        }
        val tracked = pkg in settings.lockedApps && LockPolicy.locksActive(settings, now)
        if (tracked) startSession(pkg) else endSession()
    }

    private fun startSession(pkg: String) {
        if (sessionPkg == pkg) return
        endSession()
        sessionPkg = pkg
        lastTick = SystemClock.elapsedRealtime()
        lastFlush = lastTick
        pendingMs = 0L
        warned = repo.time.value.bankSec <= WARN_AT_SEC
        DebugLog.log("Süre işliyor: $pkg (kalan ${repo.time.value.bankSec}sn)")
        showRemaining()
    }

    private fun endSession() {
        val pkg = sessionPkg ?: return
        accountTime(force = true)
        flushSpent()
        sessionPkg = null
        notifications.cancel(NOTIF_REMAINING)
        DebugLog.log("Süre durdu: $pkg (kalan ${repo.time.value.bankSec}sn)")
    }

    /**
     * Son ölçümden bu yana geçen süreyi bankadan düşer. Diske her saniye yazmamak için
     * birkaç saniye biriktirilir; [force] ile hemen işlenir.
     */
    private fun accountTime(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTick).coerceAtLeast(0L)
        lastTick = now
        if (sessionPkg == null) return
        // Ekran kapalıyken süre saymaz; uzun bir boşluk (ör. cihaz uykudaydı) tek seferde düşülmez
        if (power.isInteractive) pendingMs += delta.coerceAtMost(MAX_GAP_MS)
        if (!force && pendingMs < SPEND_BATCH_MS) return
        val sec = pendingMs / 1000
        if (sec > 0) {
            pendingMs -= sec * 1000
            unflushedSpent += repo.spend(sec)
        }
    }

    private fun flushSpent() {
        if (unflushedSpent > 0) {
            repo.recordSpent(unflushedSpent)
            unflushedSpent = 0L
        }
        lastFlush = SystemClock.elapsedRealtime()
    }

    private fun onTick() {
        val nowRt = SystemClock.elapsedRealtime()
        // Canlılık sinyali: ekran açıkken sık, kapalıyken seyrek yazılır
        val statusEvery = if (power.isInteractive) STATUS_EVERY_MS else STATUS_EVERY_MS * 6
        if (nowRt - lastStatusWrite > statusEvery) writeStatus(connected = true)
        pollForeground()
        val pkg = sessionPkg ?: return
        accountTime()
        if (nowRt - lastFlush > FLUSH_MS) flushSpent()

        repo.refresh()
        val settings = repo.settings.value
        val now = System.currentTimeMillis()
        if (pkg !in settings.lockedApps || !LockPolicy.locksActive(settings, now)) {
            endSession()
            return
        }
        val reason = LockPolicy.decide(settings, repo.time.value, pkg, now, repo.minuteOfDay())
        if (reason != LockReason.NONE) {
            DebugLog.log("Süre bitti / kilit zamanı: $pkg → $reason")
            endSession()
            block(LockMode.of(reason), pkg)
            return
        }
        val left = repo.time.value.bankSec
        if (!warned && left <= WARN_AT_SEC) {
            warned = true
            warnOneMinute()
        }
        showRemaining()
    }

    /** Olaylar gelmese bile ön plandaki uygulamayı yakalamak için etkin pencereye bakar. */
    private fun pollForeground() {
        if (overlay.isShowing || !power.isInteractive) return
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        val pkg = root.packageName?.toString()
        @Suppress("DEPRECATION")
        runCatching { root.recycle() }
        if (pkg != null && pkg != foregroundPkg) onWindowChanged(pkg, "yoklama")
    }

    private fun block(mode: LockMode, pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (pkg.isNotEmpty() && pkg in neverLock) return
        if (now < brakeUntil) return
        if (now - lastBlock < BLOCK_DEBOUNCE_MS && overlay.isShowing) return
        // Fren: bir hata sonucu durmadan kilitleme olursa (ör. ana ekrana dönülemiyorsa)
        // cihazın kullanılamaz hale gelmemesi için kilit kısa süre durdurulur.
        while (recentBlocks.isNotEmpty() && now - recentBlocks.first() > BRAKE_WINDOW_MS) recentBlocks.removeFirst()
        recentBlocks.addLast(now)
        if (recentBlocks.size > BRAKE_MAX_BLOCKS) {
            brakeUntil = now + BRAKE_PAUSE_MS
            recentBlocks.clear()
            DebugLog.log("GÜVENLİK FRENİ: ${BRAKE_WINDOW_MS / 1000} sn içinde çok fazla kilit; ${BRAKE_PAUSE_MS / 1000} sn duraklatıldı")
            overlay.hide()
            return
        }
        lastBlock = now
        DebugLog.log("KİLİT: $pkg ($mode)")
        val home = performGlobalAction(GLOBAL_ACTION_HOME)
        if (!home) DebugLog.log("Ana ekrana dönülemedi")
        overlay.show(mode, pkg)
        if (!overlay.isShowing) {
            // Katman eklenemediyse en azından uyarı ver
            Toast.makeText(this, "Süren bitti! Soru çözerek süre kazan.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkProtection(throttle: Boolean) {
        // TV'de ayar koruması yoktur: kumandayla şifre girilemezse ebeveyn ayarlara ulaşamazdı
        if (isTv) return
        val now = SystemClock.elapsedRealtime()
        if (throttle && now - lastProtectCheck < PROTECT_THROTTLE_MS) return
        lastProtectCheck = now
        if (!LockPolicy.protectSettings(repo.settings.value, System.currentTimeMillis())) return
        val root = rootInActiveWindow ?: return
        val label = getString(R.string.app_name)
        val hit = runCatching { root.findAccessibilityNodeInfosByText(label).isNotEmpty() }.getOrDefault(false)
        if (hit) {
            DebugLog.log("Korunan ayar ekranı: ${root.packageName}")
            block(LockMode.PROTECT, root.packageName?.toString() ?: "")
        }
    }

    private fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun openTraining() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            DebugLog.log("Eğitim ekranı açıldı")
        } catch (e: RuntimeException) {
            DebugLog.error("Eğitim ekranı açılamadı", e)
            Toast.makeText(this, "Çarpım Tablosu uygulamasını açarak soru çözebilirsin.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openDialer() {
        runCatching {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { DebugLog.error("Arama ekranı açılamadı", it) }
    }

    internal val isLockShowing: Boolean get() = overlay.isShowing

    /** Testler için: kullanıcının kilit ekranını kapatmasını taklit eder. */
    internal fun dismissLockForTest() = overlay.hide()

    private fun writeStatus(connected: Boolean) {
        lastStatusWrite = SystemClock.elapsedRealtime()
        repo.writeServiceStatus(
            ServiceStatus(
                connected = connected,
                heartbeat = System.currentTimeMillis(),
                lastForeground = foregroundPkg,
                lastDecision = lastDecision,
                pid = android.os.Process.myPid(),
            )
        )
    }

    // ------------------------- bildirimler -------------------------

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun showRemaining() {
        if (!notifications.areNotificationsEnabled()) return
        val left = repo.time.value.bankSec
        val n = Notification.Builder(this, App.CHANNEL_TIME)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Kalan süre: ${TimeBank.format(left)}")
            .setContentText("Süre bitince uygulama kilitlenir. Soru çözerek süre kazan.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()
        notifications.notify(NOTIF_REMAINING, n)
    }

    private fun warnOneMinute() {
        DebugLog.log("1 dakika uyarısı")
        Toast.makeText(this, "1 dakika kaldı!", Toast.LENGTH_LONG).show()
        if (!notifications.areNotificationsEnabled()) return
        val n = Notification.Builder(this, App.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("1 dakika kaldı!")
            .setContentText("Süren bitmek üzere. Devam etmek için birkaç soru çözebilirsin.")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notifications.notify(NOTIF_WARN, n)
    }

    companion object {
        private const val TICK_MS = 1_000L
        private const val FLUSH_MS = 30_000L
        private const val MAX_GAP_MS = 5_000L
        private const val WARN_AT_SEC = 60L
        private const val BLOCK_DEBOUNCE_MS = 800L
        private const val PROTECT_THROTTLE_MS = 500L
        private const val SPEND_BATCH_MS = 5_000L
        private const val BRAKE_WINDOW_MS = 60_000L
        private const val BRAKE_MAX_BLOCKS = 15
        private const val BRAKE_PAUSE_MS = 60_000L
        private const val STATUS_EVERY_MS = 10_000L
        private const val NOTIF_REMAINING = 1
        private const val NOTIF_WARN = 2

        /** Ekran sürecinden "kilit ekranını dene" isteği. */
        const val ACTION_TEST_LOCK = "com.carpimtablosu.app.TEST_LOCK"

        fun requestTestLock(context: Context) {
            context.sendBroadcast(Intent(ACTION_TEST_LOCK).setPackage(context.packageName))
        }
    }
}
