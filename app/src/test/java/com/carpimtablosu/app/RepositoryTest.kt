package com.carpimtablosu.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.carpimtablosu.app.data.PasswordCheck
import com.carpimtablosu.app.data.Repository
import com.carpimtablosu.app.logic.LessonKind
import com.carpimtablosu.app.ui.AppViewModel
import com.carpimtablosu.app.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Gerçek SharedPreferences ile kayıt, süre ve şifre akışları. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("carpim", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun fresh() = Repository(context)

    @Test fun correctAndWrongAnswersMoveTheTimeBank() {
        val repo = fresh()
        assertEquals(60, repo.recordAnswer("7x8", true).deltaSec)
        assertEquals(60, repo.time.value.bankSec)
        assertEquals(-60, repo.recordAnswer("7x8", false).deltaSec)
        assertEquals(0, repo.time.value.bankSec)
        // Süre yokken yanlış: süre eksiye düşmez
        assertEquals(0, repo.recordAnswer("7x8", false).deltaSec)
        assertEquals(0, repo.time.value.bankSec)
        assertEquals(1, repo.progress.value.totalCorrect)
        assertEquals(2, repo.progress.value.totalWrong)
    }

    @Test fun dataSurvivesRestart() {
        val repo = fresh()
        repo.recordAnswer("2x3", true)
        repo.updateSettings { it.copy(lockedApps = setOf("x.y"), onboarded = true) }
        repo.setChildName("  Deniz  ")
        Thread.sleep(50)
        val again = fresh()
        assertEquals(60, again.time.value.bankSec)
        assertEquals(setOf("x.y"), again.settings.value.lockedApps)
        assertEquals("Deniz", again.progress.value.childName)
        assertEquals(1, again.progress.value.facts["2x3"]?.correct)
    }

    @Test fun corruptDataFallsBackToDefaults() {
        context.getSharedPreferences("carpim", Context.MODE_PRIVATE).edit()
            .putString("progress", "{bozuk").putString("settings", "[]").commit()
        val repo = fresh()
        assertEquals(0, repo.progress.value.xp)
        assertFalse(repo.settings.value.onboarded)
    }

    @Test fun legacyDataIsMigrated() {
        java.io.File(context.filesDir, "veri").deleteRecursively()
        context.getSharedPreferences("carpim", Context.MODE_PRIVATE).edit()
            .putString("progress", """{"childName":"Ece","xp":42}""")
            .putString("time", """{"bankSec":300}""")
            .putString("pw_salt", com.carpimtablosu.app.logic.Password.encode(byteArrayOf(1, 2, 3)))
            .putString("pw_hash", com.carpimtablosu.app.logic.Password.encode(
                com.carpimtablosu.app.logic.Password.hash("Eski1234", byteArrayOf(1, 2, 3))
            ))
            .commit()
        val repo = fresh()
        assertEquals("Ece", repo.progress.value.childName)
        assertEquals(42, repo.progress.value.xp)
        assertEquals(300, repo.time.value.bankSec)
        assertTrue(repo.hasPassword())
        assertEquals(PasswordCheck.Ok, repo.checkPassword("Eski1234"))
    }

    @Test fun twoInstancesSeeEachOthersWrites() {
        // Ekran ve kilit servisi ayrı süreçlerde ayrı Repository kullanır
        val ui = fresh()
        val lock = fresh()
        ui.adjustTime(120)
        assertEquals(60, lock.spend(60))
        ui.recordAnswer("2x2", true)
        lock.refresh()
        assertEquals(120, lock.time.value.bankSec)
        ui.updateSettings { it.copy(lockedApps = setOf("a.b")) }
        lock.refresh()
        assertEquals(setOf("a.b"), lock.settings.value.lockedApps)
    }

    @Test fun spendingNeverGoesNegative() {
        val repo = fresh()
        repo.adjustTime(90)
        assertEquals(60, repo.spend(60))
        assertEquals(30, repo.spend(60))
        assertEquals(0, repo.spend(60))
        assertEquals(0, repo.time.value.bankSec)
    }

    @Test fun passwordFlowWithLockout() {
        val repo = fresh()
        assertFalse(repo.hasPassword())
        val code = repo.setPassword("Baba1234")
        assertTrue(repo.hasPassword())
        assertEquals(PasswordCheck.Ok, repo.checkPassword("Baba1234"))
        repeat(4) { assertTrue(repo.checkPassword("yanlis1") is PasswordCheck.Wrong) }
        assertTrue(repo.checkPassword("yanlis1") is PasswordCheck.LockedOut)
        // Kilitliyken doğru şifre bile beklemeli
        assertTrue(repo.checkPassword("Baba1234") is PasswordCheck.LockedOut)
        // Kurtarma kodu da kilide tabi; kilit kalkınca çalışır
        val secFile = java.io.File(context.filesDir, "veri/guvenlik.json")
        val sec = Repository.json.decodeFromString(com.carpimtablosu.app.data.Security.serializer(), secFile.readText())
        secFile.writeText(Repository.json.encodeToString(com.carpimtablosu.app.data.Security.serializer(), sec.copy(lockoutUntil = 0L)))
        assertEquals(PasswordCheck.Ok, repo.checkRecovery(code.lowercase()))
        repo.setPassword("Yeni5678")
        assertTrue(repo.checkPassword("Baba1234") is PasswordCheck.Wrong)
        assertEquals(PasswordCheck.Ok, repo.checkPassword("Yeni5678"))
    }

    @Test fun backupRoundTripAndValidation() {
        val repo = fresh()
        repo.recordAnswer("9x9", true)
        val text = repo.exportBackup()
        repo.resetProgress()
        assertNull(repo.progress.value.facts["9x9"])
        assertTrue(repo.importBackup(text).isSuccess)
        assertEquals(1, repo.progress.value.facts["9x9"]?.correct)
        val before = repo.progress.value
        assertTrue(repo.importBackup("merhaba").isFailure)
        assertTrue(repo.importBackup("""{"format":1,"progress":{"completedNodes":{"2":99}}}""").isFailure)
        assertEquals(before, repo.progress.value)
    }

    @Test fun viewModelLessonFlow() {
        context.getSharedPreferences("carpim", Context.MODE_PRIVATE).edit()
            .putString("settings", """{"onboarded":true}""").commit()
        val app = ApplicationProvider.getApplicationContext<App>()
        // App.repo süreç başında oluşturuldu; testte taze bir depo ile değiştirmek yerine
        // aynı depoyu kullanıyoruz ve başlangıç durumunu ondan okuyoruz
        val vm = AppViewModel(app)
        val startXp = vm.progress.value.xp
        vm.startLesson(LessonKind.Node(2, 0))
        assertEquals(Screen.Lesson, vm.screen)
        var guard = 0
        while (vm.lesson != null && guard++ < 100) {
            val q = vm.lesson!!.current!!
            vm.submit(q.answer)
            vm.submit(q.answer + 1) // ikinci cevap yok sayılmalı
            vm.next()
        }
        val result = vm.screen as Screen.Result
        assertEquals(10, result.state.correct)
        assertEquals(0, result.state.wrong)
        assertTrue(vm.progress.value.xp > startXp)
        assertTrue((vm.progress.value.completedNodes[2] ?: 0) >= 1)
        vm.goHome()
        assertEquals(Screen.Home, vm.screen)
        assertFalse(vm.back())
    }
}
