package com.carpimtablosu.app.lock

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.carpimtablosu.app.App
import com.carpimtablosu.app.R
import com.carpimtablosu.app.data.Repository
import com.carpimtablosu.app.logic.LockPolicy
import com.carpimtablosu.app.logic.LockReason
import com.carpimtablosu.app.logic.TimeBank
import com.carpimtablosu.app.ui.MainActivity

/**
 * Ön plandaki uygulamayı izler. Seçili bir uygulama açıkken süre bankasından düşer,
 * süre bitince (veya gece modunda) kilit ekranını gösterir.
 *
 * Süre ölçümü SystemClock.elapsedRealtime ile yapılır; telefonun saatini değiştirmek
 * süreyi etkilemez. Ekran kapalıyken süre düşmez.
 */
class AppLockService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repo: Repository
    private lateinit var power: PowerManager
    private lateinit var notifications: NotificationManager

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
    private var ignored: Set<String> = emptySet()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                // Ekran kapanırken birikmiş süreyi işle; kapalıyken süre saymaz
                accountTime()
                flushSpent()
            } else {
                lastTick = SystemClock.elapsedRealtime()
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repo = App.repo(this)
        power = getSystemService(PowerManager::class.java)
        notifications = getSystemService(NotificationManager::class.java)
        ignored = buildIgnoredSet()
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
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::repo.isInitialized) return
        val pkg = event?.packageName?.toString() ?: return
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            onWindowChanged(pkg)
        } else if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            pkg == foregroundPkg && pkg in SystemAccess.PROTECTED_PACKAGES
        ) {
            checkProtection(throttle = true)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::repo.isInitialized) {
            endSession()
            runCatching { unregisterReceiver(screenReceiver) }
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------------

    private fun buildIgnoredSet(): Set<String> {
        val imes = runCatching {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        }.getOrDefault(emptyList())
        return (imes + "com.android.systemui").toSet()
    }

    private fun onWindowChanged(pkg: String) {
        // Klavye ve bildirim paneli ön plandaki uygulamayı değiştirmez
        if (pkg in ignored) return
        foregroundPkg = pkg
        when {
            pkg == packageName -> endSession()
            pkg in SystemAccess.PROTECTED_PACKAGES -> {
                endSession()
                checkProtection(throttle = false)
            }
            else -> evaluate(pkg)
        }
    }

    private fun evaluate(pkg: String) {
        val settings = repo.settings.value
        val now = System.currentTimeMillis()
        val reason = LockPolicy.decide(settings, repo.time.value, pkg, now, repo.minuteOfDay())
        if (reason != LockReason.NONE) {
            endSession()
            block(LockActivity.modeFor(reason), pkg)
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
        showRemaining()
    }

    private fun endSession() {
        if (sessionPkg == null) return
        accountTime()
        flushSpent()
        sessionPkg = null
        notifications.cancel(NOTIF_REMAINING)
    }

    /** Son ölçümden bu yana geçen süreyi bankadan düşer. */
    private fun accountTime() {
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTick).coerceAtLeast(0L)
        lastTick = now
        if (sessionPkg == null || !power.isInteractive) return
        // Uzun bir boşluk (ör. cihaz uykudaydı) tek seferde düşülmez
        pendingMs += delta.coerceAtMost(MAX_GAP_MS)
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
        val pkg = sessionPkg ?: return
        accountTime()
        if (SystemClock.elapsedRealtime() - lastFlush > FLUSH_MS) flushSpent()

        val settings = repo.settings.value
        val now = System.currentTimeMillis()
        if (pkg !in settings.lockedApps || !LockPolicy.locksActive(settings, now)) {
            endSession()
            return
        }
        val reason = LockPolicy.decide(settings, repo.time.value, pkg, now, repo.minuteOfDay())
        if (reason != LockReason.NONE) {
            endSession()
            block(LockActivity.modeFor(reason), pkg)
            return
        }
        val left = repo.time.value.bankSec
        if (!warned && left <= WARN_AT_SEC) {
            warned = true
            warnOneMinute()
        }
        showRemaining()
    }

    private fun block(mode: LockActivity.Mode, pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlock < BLOCK_DEBOUNCE_MS) return
        lastBlock = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(LockActivity.intent(this, mode, pkg))
    }

    private fun checkProtection(throttle: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (throttle && now - lastProtectCheck < PROTECT_THROTTLE_MS) return
        lastProtectCheck = now
        if (!LockPolicy.protectSettings(repo.settings.value, System.currentTimeMillis())) return
        val root = rootInActiveWindow ?: return
        val label = getString(R.string.app_name)
        val hit = runCatching { root.findAccessibilityNodeInfosByText(label).isNotEmpty() }.getOrDefault(false)
        if (hit) block(LockActivity.Mode.PROTECT, root.packageName?.toString() ?: "")
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
        private const val NOTIF_REMAINING = 1
        private const val NOTIF_WARN = 2
    }
}
