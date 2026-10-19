package com.carpimtablosu.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
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
import java.time.LocalDate
import java.time.LocalTime

sealed interface PasswordCheck {
    data object Ok : PasswordCheck
    data class Wrong(val attemptsLeftBeforeLock: Int) : PasswordCheck
    data class LockedOut(val remainingMs: Long) : PasswordCheck
}

/**
 * Uygulamanın tek veri kaynağı. Her şey telefonda (SharedPreferences) saklanır;
 * sunucu yoktur. Tüm yazmalar tek kilit altında yapılır, böylece kilit servisi ile
 * ekran aynı anda süreyi değiştirse bile değer bozulmaz.
 */
class Repository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("carpim", Context.MODE_PRIVATE)
    private val lock = Any()

    private val _progress = MutableStateFlow(load(KEY_PROGRESS, Progress.serializer()) ?: Progress())
    private val _settings = MutableStateFlow(load(KEY_SETTINGS, Settings.serializer()) ?: Settings())
    private val _time = MutableStateFlow(load(KEY_TIME, TimeState.serializer()) ?: TimeState())

    val progress: StateFlow<Progress> = _progress.asStateFlow()
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val time: StateFlow<TimeState> = _time.asStateFlow()

    fun today(): LocalDate = LocalDate.now()
    fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    // ---------- genel güncelleme ----------

    fun updateProgress(f: (Progress) -> Progress) = synchronized(lock) {
        val n = f(_progress.value)
        if (n != _progress.value) {
            _progress.value = n
            save(KEY_PROGRESS, Progress.serializer(), n)
        }
    }

    fun updateSettings(f: (Settings) -> Settings) = synchronized(lock) {
        val n = f(_settings.value)
        if (n != _settings.value) {
            _settings.value = n
            save(KEY_SETTINGS, Settings.serializer(), n)
        }
    }

    private fun updateTime(f: (TimeState) -> TimeState) = synchronized(lock) {
        val n = f(_time.value)
        if (n != _time.value) {
            _time.value = n
            save(KEY_TIME, TimeState.serializer(), n)
        }
    }

    // ---------- öğrenme ----------

    data class AnswerOutcome(val deltaSec: Long, val capReached: Boolean)

    /** Bir cevabı kaydeder ve süre bankasını günceller. */
    fun recordAnswer(factKey: String, correct: Boolean): AnswerOutcome = synchronized(lock) {
        val s = _settings.value
        val today = today()
        updateProgress { ProgressOps.recordAnswer(it, factKey, correct, System.currentTimeMillis(), today, s.dailyGoal) }
        var delta = 0L
        var capReached = false
        updateTime { t ->
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
        AnswerOutcome(delta, capReached)
    }

    /** Ders bitti: ilerlemeyi işler, yeni rozetleri döndürür. */
    fun finishLesson(state: LessonState): List<Achievement> = synchronized(lock) {
        var fresh: List<Achievement> = emptyList()
        updateProgress { p ->
            val (n, f) = Achievements.unlockNew(ProgressOps.completeLesson(p, state))
            fresh = f
            n
        }
        fresh
    }

    fun setChildName(name: String) = updateProgress { it.copy(childName = name.trim().take(20)) }

    fun resetProgress() = synchronized(lock) {
        val name = _progress.value.childName
        updateProgress { Progress(childName = name) }
    }

    // ---------- süre ----------

    /** Kilitli uygulamada geçen süreyi düşer; gerçekten düşülen saniyeyi döndürür. */
    fun spend(sec: Long): Long = synchronized(lock) {
        var used = 0L
        updateTime { t ->
            val (n, u) = TimeBank.spend(TimeBank.rollDay(t, today().toString()), sec)
            used = u
            n
        }
        used
    }

    fun recordSpent(sec: Long) {
        if (sec > 0L) updateProgress { ProgressOps.recordTime(it, today(), spentSec = sec) }
    }

    fun adjustTime(deltaSec: Long) = updateTime { TimeBank.adjust(it, deltaSec) }

    fun clearTime() = updateTime { it.copy(bankSec = 0L) }

    // ---------- ebeveyn şifresi ----------

    fun hasPassword(): Boolean = prefs.contains(KEY_PW_HASH)

    /** Yeni şifreyi kaydeder ve yeni kurtarma kodunu döndürür. */
    @SuppressLint("ApplySharedPref") // şifre diske yazılmadan kurtarma kodu gösterilmemeli
    fun setPassword(pw: String): String = synchronized(lock) {
        val salt = Password.newSalt()
        val code = Password.newRecoveryCode()
        val rSalt = Password.newSalt()
        prefs.edit()
            .putString(KEY_PW_SALT, Password.encode(salt))
            .putString(KEY_PW_HASH, Password.encode(Password.hash(pw, salt)))
            .putString(KEY_RC_SALT, Password.encode(rSalt))
            .putString(KEY_RC_HASH, Password.encode(Password.hash(Password.normalizeRecovery(code), rSalt)))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCKOUT, 0L)
            .commit()
        code
    }

    fun checkPassword(pw: String): PasswordCheck =
        checkSecret(pw, KEY_PW_SALT, KEY_PW_HASH)

    fun checkRecovery(code: String): PasswordCheck =
        checkSecret(Password.normalizeRecovery(code), KEY_RC_SALT, KEY_RC_HASH)

    private fun checkSecret(secret: String, saltKey: String, hashKey: String): PasswordCheck = synchronized(lock) {
        val now = System.currentTimeMillis()
        val until = prefs.getLong(KEY_LOCKOUT, 0L)
        if (now < until) return PasswordCheck.LockedOut(until - now)
        val salt = prefs.getString(saltKey, null)
        val hash = prefs.getString(hashKey, null)
        // Şifre hiç kurulmamışsa (olmaması gerekir) panele girişe izin verilir ki ebeveyn kilitli kalmasın
        if (salt == null || hash == null) return PasswordCheck.Ok
        val ok = try {
            Password.verify(secret, Password.decode(salt), Password.decode(hash))
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Bozuk şifre kaydı", e)
            false
        }
        if (ok) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKOUT, 0L).apply()
            return PasswordCheck.Ok
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val lockMs = Password.lockoutMs(fails)
        prefs.edit().putInt(KEY_FAILS, fails).putLong(KEY_LOCKOUT, if (lockMs > 0) now + lockMs else 0L).apply()
        if (lockMs > 0) PasswordCheck.LockedOut(lockMs) else PasswordCheck.Wrong(FREE_ATTEMPTS_HINT - fails)
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

    // ---------- kalıcılık ----------

    private fun <T> load(key: String, serializer: KSerializer<T>): T? {
        val s = prefs.getString(key, null) ?: return null
        return try {
            json.decodeFromString(serializer, s)
        } catch (e: Exception) {
            // Bozuk kayıt uygulamayı çökertmesin; yedeğini saklayıp varsayılana dön
            Log.e(TAG, "Kayıt okunamadı: $key", e)
            prefs.edit().putString("$key.corrupt", s).apply()
            null
        }
    }

    private fun <T> save(key: String, serializer: KSerializer<T>, value: T) {
        prefs.edit().putString(key, json.encodeToString(serializer, value)).apply()
    }

    companion object {
        private const val TAG = "Repository"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_TIME = "time"
        private const val KEY_PW_SALT = "pw_salt"
        private const val KEY_PW_HASH = "pw_hash"
        private const val KEY_RC_SALT = "rc_salt"
        private const val KEY_RC_HASH = "rc_hash"
        private const val KEY_FAILS = "pw_fails"
        private const val KEY_LOCKOUT = "pw_lockout"
        private const val FREE_ATTEMPTS_HINT = 5

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }
    }
}
