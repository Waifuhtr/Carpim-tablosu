package com.carpimtablosu.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carpimtablosu.app.App
import com.carpimtablosu.app.data.PasswordCheck
import com.carpimtablosu.app.logic.Achievement
import com.carpimtablosu.app.logic.Curriculum
import com.carpimtablosu.app.logic.LessonKind
import com.carpimtablosu.app.logic.LessonState
import com.carpimtablosu.app.logic.QuestionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Lesson : Screen
    data class Result(val state: LessonState, val badges: List<Achievement>) : Screen
    data object ParentGate : Screen
    data class Parent(val section: ParentSection) : Screen
}

enum class ParentSection { MAIN, APPS, RULES, REPORT, SECURITY }

enum class HomeTab { PATH, PRACTICE, BADGES, PROFILE }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = App.repo(app)
    val progress = repo.progress
    val settings = repo.settings
    val time = repo.time

    private val stack = mutableStateListOf<Screen>()
    val screen: Screen get() = stack.lastOrNull() ?: Screen.Home
    val canGoBack: Boolean get() = stack.size > 1

    var tab by mutableStateOf(HomeTab.PATH)
    var lesson by mutableStateOf<LessonState?>(null)
        private set

    /** Ayarlardan dönünce izin durumlarını yeniden okumak için artar. */
    var resumeTick by mutableIntStateOf(0)
        private set

    private val factory = QuestionFactory()

    init {
        stack.add(if (settings.value.onboarded) Screen.Home else Screen.Onboarding)
    }

    fun onResume() {
        resumeTick++
    }

    // ---------------- gezinme ----------------

    fun push(s: Screen) {
        stack.add(s)
    }

    /** @return geri gidildiyse true; false ise etkinlik kapanabilir. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        val leaving = stack.removeAt(stack.lastIndex)
        if (leaving is Screen.Parent && stack.none { it is Screen.Parent }) onParentLeft()
        return true
    }

    fun goHome() {
        val wasParent = stack.any { it is Screen.Parent }
        stack.clear()
        stack.add(Screen.Home)
        if (wasParent) onParentLeft()
    }

    // ---------------- ders ----------------

    fun startLesson(kind: LessonKind) {
        val now = SystemClock.elapsedRealtime()
        val p = progress.value
        val qs = Curriculum.buildLesson(kind, p, System.currentTimeMillis(), factory)
        val first = if (kind == LessonKind.Sprint) Curriculum.sprintQuestion(p, factory) else null
        lesson = LessonState.start(kind, qs, now, first)
        push(Screen.Lesson)
    }

    fun submit(answer: Int) {
        val l = lesson ?: return
        val q = l.current ?: return
        if (l.isAnswered) return
        val s = l.submit(answer, factory)
        val ok = s.answer?.correct == true
        val outcome = repo.recordAnswer(q.factKey, ok)
        lesson = s.withTime(outcome.deltaSec, outcome.capReached)
    }

    fun next() {
        val l = lesson ?: return
        val now = SystemClock.elapsedRealtime()
        val n = if (l.kind == LessonKind.Sprint) {
            l.next(now) { Curriculum.sprintQuestion(progress.value, factory) }
        } else {
            l.next(now)
        }
        lesson = n
        if (n.isFinished) finish(n)
    }

    fun timeUp() {
        val l = lesson ?: return
        if (l.isFinished) return
        val n = l.timeUp()
        lesson = n
        finish(n)
    }

    private fun finish(state: LessonState) {
        val badges = repo.finishLesson(state)
        lesson = null
        // Ders ekranını sonuç ekranıyla değiştir
        if (stack.lastOrNull() == Screen.Lesson) stack.removeAt(stack.lastIndex)
        push(Screen.Result(state, badges))
    }

    fun quitLesson() {
        lesson = null
        if (stack.lastOrNull() == Screen.Lesson) stack.removeAt(stack.lastIndex)
    }

    // ---------------- ebeveyn ----------------

    suspend fun checkPassword(pw: String): PasswordCheck = withContext(Dispatchers.Default) { repo.checkPassword(pw) }

    suspend fun checkRecovery(code: String): PasswordCheck = withContext(Dispatchers.Default) { repo.checkRecovery(code) }

    suspend fun setPassword(pw: String): String = withContext(Dispatchers.Default) { repo.setPassword(pw) }

    /** Şifre doğrulandı: ebeveyn paneline gir. Panel açıkken ayar koruması devre dışıdır. */
    fun enterParent() {
        if (stack.lastOrNull() == Screen.ParentGate) stack.removeAt(stack.lastIndex)
        push(Screen.Parent(ParentSection.MAIN))
        repo.updateSettings { it.copy(settingsBypassUntil = System.currentTimeMillis() + PARENT_SETTINGS_WINDOW_MS) }
    }

    private fun onParentLeft() {
        repo.updateSettings { it.copy(settingsBypassUntil = 0L) }
    }

    fun finishOnboarding() {
        repo.updateSettings { it.copy(onboarded = true, settingsBypassUntil = 0L) }
        stack.clear()
        stack.add(Screen.Home)
    }

    fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        private const val PARENT_SETTINGS_WINDOW_MS = 10 * 60_000L
    }
}
