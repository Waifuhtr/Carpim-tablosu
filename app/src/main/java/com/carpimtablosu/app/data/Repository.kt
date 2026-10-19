package com.carpimtablosu.app.data

import android.content.Context
import android.os.FileObserver
import com.carpimtablosu.app.DebugLog
import com.carpimtablosu.app.logic.Achievement
import com.carpimtablosu.app.logic.Achievements
import com.carpimtablosu.app.logic.LessonState
import com.carpimtablosu.app.logic.Password
import com.carpimtablosu.app.logic.ProgressOps
import com.carpimtablosu.app.logic.TimeBank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

sealed interface PasswordCheck {
    data object Ok : PasswordCheck
    data class Wrong(val attemptsLeftBeforeLock: Int) : PasswordCheck
    data class LockedOut(val remainingMs: Long) : PasswordCheck
}

/**
 * Uygulamanın tek veri kaynağı. Her şey telefonda saklanır; sunucu yoktur.
 *
 * Ekran ve kilit servisi ayrı süreçlerde çalışır. Veriler [FileStore] ile süreçler
 * arası dosya kilidi altında yazılır; diğer sürecin yaptığı değişiklikler FileObserver
 * ile algılanıp akışlara (StateFlow) yansıtılır.
 */
class Repository(context: Context) {
    private val store = FileStore(File(context.applicationContext.filesDir, "veri"))
    private val legacy = context.applicationContext.getSharedPreferences("carpim", Context.MODE_PRIVATE)

    private val _progress = MutableStateFlow(loadOrMigrate(PROGRESS, Progress.serializer(), "progress") { Progress() })
    private val _settings = MutableStateFlow(loadOrMigrate(SETTINGS, Settings.serializer(), "settings") { Settings() })
    private val _time = MutableStateFlow(loadOrMigrate(TIME, TimeState.serializer(), "time") { TimeState() })

    val progress: StateFlow<Progress> = _progress.asStateFlow()
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val time: StateFlow<TimeState> = _time.asStateFlow()

    // Diğer süreç yazınca yeniden oku. Referans tutulmalı, yoksa çöp toplayıcı izlemeyi durdurur.
    @Suppress("DEPRECATION")
    private val observer = object : FileObserver(store.dir.path, MOVED_TO or CLOSE_WRITE) {
        override fun onEvent(event: Int, path: String?) {
            when (path) {
                PROGRESS -> reloadProgress()
                SETTINGS -> reloadSettings()
                TIME -> reloadTime()
            }
        }
    }.also { it.startWatching() }

    init {
        migrateSecurity()
    }

    fun today(): LocalDate = LocalDate.now()
    fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    /** Diskteki son hali okur (kilit servisi karar vermeden önce çağırır). */
    fun refresh() {
        reloadSettings()
        reloadTime()
    }

    private fun reloadProgress() { store.read(PROGRESS, Progress.serializer())?.let { _progress.value = it } }
    private fun reloadSettings() { store.read(SETTINGS, Settings.serializer())?.let { _settings.value = it } }
    private fun reloadTime() { store.read(TIME, TimeState.serializer())?.let { _time.value = it } }

    // ---------- genel güncelleme ----------

    fun updateProgress(f: (Progress) -> Progress) {
        _progress.value = store.update(PROGRESS, Progress.serializer(), ::Progress, f)
    }

    fun updateSettings(f: (Settings) -> Settings) {
        var old: Settings? = null
        val n = store.update(SETTINGS, Settings.serializer(), ::Settings) { cur -> old = cur; f(cur) }
        old?.let { logSettingsChange(it, n) }
        _settings.value = n
    }

    private fun updateTime(f: (TimeState) -> TimeState): TimeState {
        val n = store.update(TIME, TimeState.serializer(), ::TimeState, f)
        _time.value = n
        return n
    }

    private fun logSettingsChange(old: Settings, n: Settings) {
        val parts = mutableListOf<String>()
        if (old.onboarded != n.onboarded) parts += "kurulum=${n.onboarded}"
        if (old.locksEnabled != n.locksEnabled) parts += "kilitler=${n.locksEnabled}"
        if (old.lockedApps != n.lockedApps) parts += "kilitliUygulamalar=${n.lockedApps.sorted()}"
        if (old.parentBypassUntil != n.parentBypassUntil) parts += "ebeveynMuafiyetiBitiş=${n.parentBypassUntil}"
        if (old.quietEnabled != n.quietEnabled) parts += "geceModu=${n.quietEnabled}"
        if (old.protectSettings != n.protectSettings) parts += "ayarKoruması=${n.protectSettings}"
        if (parts.isNotEmpty()) DebugLog.log("Ayar değişti: " + parts.joinToString(", "))
    }

    // ---------- öğrenme ----------

    data class AnswerOutcome(val deltaSec: Long, val capReached: Boolean)

    /** Bir cevabı kaydeder ve süre bankasını günceller. */
    fun recordAnswer(factKey: String, correct: Boolean): AnswerOutcome {
        val s = store.read(SETTINGS, Settings.serializer()) ?: _settings.value
        val today = today()
        updateProgress { ProgressOps.recordAnswer(it, factKey, correct, System.currentTimeMillis(), today, s.dailyGoal) }
        var delta = 0L
        var capReached = false
        val t = updateTime { t ->
            if (correct) {
                val (n, gain) = TimeBank.reward(t, s.rewardSec, s.dailyEarnCapSec, today.toString())
                delta = gain
                capReached = gain < s.rewardSec
                n
            } else {
                val (n, loss) = TimeBank.penalize(t, s.penaltySec, today.toString())
                delta = -loss
                n
            }
        }
        if (delta != 0L) updateProgress { ProgressOps.recordTime(it, today, earnedSec = delta) }
        DebugLog.log("Cevap ${if (correct) "doğru" else "yanlış"} ($factKey): süre ${if (delta >= 0) "+" else ""}${delta}sn → ${t.bankSec}sn")
        return AnswerOutcome(delta, capReached)
    }

    /** Ders bitti: ilerlemeyi işler, yeni rozetleri döndürür. */
    fun finishLesson(state: LessonState): List<Achievement> {
        var fresh: List<Achievement> = emptyList()
        updateProgress { p ->
            val (n, f) = Achievements.unlockNew(ProgressOps.completeLesson(p, state))
            fresh = f
            n
        }
        return fresh
    }

    fun setChildName(name: String) = updateProgress { it.copy(childName = name.trim().take(20)) }

    fun resetProgress() = updateProgress { Progress(childName = it.childName) }

    // ---------- süre ----------

    /** Kilitli uygulamada geçen süreyi düşer; gerçekten düşülen saniyeyi döndürür. */
    fun spend(sec: Long): Long {
        var used = 0L
        updateTime { t ->
            val (n, u) = TimeBank.spend(TimeBank.rollDay(t, today().toString()), sec)
            used = u
            n
        }
        return used
    }

    fun recordSpent(sec: Long) {
        if (sec > 0L) updateProgress { ProgressOps.recordTime(it, today(), spentSec = sec) }
    }

    fun adjustTime(deltaSec: Long) {
        val t = updateTime { TimeBank.adjust(it, deltaSec) }
        DebugLog.log("Ebeveyn süreyi değiştirdi: ${deltaSec}sn → ${t.bankSec}sn")
    }

    fun clearTime() {
        updateTime { it.copy(bankSec = 0L) }
        DebugLog.log("Ebeveyn süreyi sıfırladı")
    }

    // ---------- kilit servisi durumu ----------

    fun serviceStatus(): ServiceStatus = store.read(STATUS, ServiceStatus.serializer()) ?: ServiceStatus()

    fun writeServiceStatus(status: ServiceStatus) {
        runCatching { store.withLock(STATUS) { store.write(STATUS, ServiceStatus.serializer(), status) } }
            .onFailure { DebugLog.error("Servis durumu yazılamadı", it) }
    }

    // ---------- ebeveyn şifresi ----------

    private fun security(): Security = store.read(SECURITY, Security.serializer()) ?: Security()

    fun hasPassword(): Boolean = security().pwHash != null

    /** Yeni şifreyi kaydeder ve yeni kurtarma kodunu döndürür. */
    fun setPassword(pw: String): String {
        val salt = Password.newSalt()
        val code = Password.newRecoveryCode()
        val rSalt = Password.newSalt()
        val sec = Security(
            pwSalt = Password.encode(salt),
            pwHash = Password.encode(Password.hash(pw, salt)),
            rcSalt = Password.encode(rSalt),
            rcHash = Password.encode(Password.hash(Password.normalizeRecovery(code), rSalt)),
        )
        store.withLock(SECURITY) { store.write(SECURITY, Security.serializer(), sec) }
        DebugLog.log("Ebeveyn şifresi kaydedildi")
        return code
    }

    fun checkPassword(pw: String): PasswordCheck = checkSecret(pw, recovery = false)

    fun checkRecovery(code: String): PasswordCheck = checkSecret(Password.normalizeRecovery(code), recovery = true)

    private fun checkSecret(secret: String, recovery: Boolean): PasswordCheck {
        val now = System.currentTimeMillis()
        var result: PasswordCheck = PasswordCheck.Ok
        store.update(SECURITY, Security.serializer(), ::Security) { s ->
            if (now < s.lockoutUntil) {
                result = PasswordCheck.LockedOut(s.lockoutUntil - now)
                return@update s
            }
            val salt = if (recovery) s.rcSalt else s.pwSalt
            val hash = if (recovery) s.rcHash else s.pwHash
            // Şifre hiç kurulmamışsa (olmaması gerekir) panele girişe izin verilir ki ebeveyn kilitli kalmasın
            if (salt == null || hash == null) {
                result = PasswordCheck.Ok
                return@update s
            }
            val ok = try {
                Password.verify(secret, Password.decode(salt), Password.decode(hash))
            } catch (e: IllegalArgumentException) {
                DebugLog.error("Bozuk şifre kaydı", e)
                false
            }
            if (ok) {
                result = PasswordCheck.Ok
                s.copy(fails = 0, lockoutUntil = 0L)
            } else {
                val fails = s.fails + 1
                val lockMs = Password.lockoutMs(fails)
                result = if (lockMs > 0) PasswordCheck.LockedOut(lockMs) else PasswordCheck.Wrong(FREE_ATTEMPTS_HINT - fails)
                s.copy(fails = fails, lockoutUntil = if (lockMs > 0) now + lockMs else 0L)
            }
        }
        return result
    }

    // ---------- yedek ----------

    fun exportBackup(): String = json.encodeToString(Backup.serializer(), Backup(progress = _progress.value))

    /** Yedeği doğrular ve yükler. Hatalı dosyada hiçbir şey değişmez. */
    fun importBackup(text: String): Result<Unit> = runCatching {
        val backup = json.decodeFromString(Backup.serializer(), text)
        require(backup.format == 1) { "Desteklenmeyen yedek sürümü" }
        val p = backup.progress
        require(p.facts.values.all { it.box in 0..5 && it.correct >= 0 && it.wrong >= 0 }) { "Bozuk yedek" }
        require(p.completedNodes.values.all { it in 0..4 }) { "Bozuk yedek" }
        updateProgress { p }
    }

    // ---------- eski sürümden taşıma ----------

    /** Dosya yoksa 1.0.2 ve öncesinin SharedPreferences kaydından taşır. */
    private fun <T> loadOrMigrate(name: String, serializer: KSerializer<T>, legacyKey: String, default: () -> T): T =
        store.update(name, serializer, {
            val old = legacy.getString(legacyKey, null)
            val migrated = old?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            if (migrated != null) DebugLog.log("Eski kayıt taşındı: $legacyKey")
            migrated ?: default()
        }) { it }

    private fun migrateSecurity() {
        if (store.exists(SECURITY)) return
        val hash = legacy.getString("pw_hash", null) ?: return
        store.update(SECURITY, Security.serializer(), ::Security) { cur ->
            if (cur.pwHash != null) cur
            else Security(
                pwSalt = legacy.getString("pw_salt", null),
                pwHash = hash,
                rcSalt = legacy.getString("rc_salt", null),
                rcHash = legacy.getString("rc_hash", null),
            )
        }
        DebugLog.log("Eski şifre kaydı taşındı")
    }

    companion object {
        private const val PROGRESS = "ilerleme.json"
        private const val SETTINGS = "ayarlar.json"
        private const val TIME = "sure.json"
        private const val SECURITY = "guvenlik.json"
        private const val STATUS = "servis.json"
        private const val FREE_ATTEMPTS_HINT = 5

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }
    }
}
