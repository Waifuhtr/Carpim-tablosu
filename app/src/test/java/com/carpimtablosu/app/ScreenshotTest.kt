package com.carpimtablosu.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.carpimtablosu.app.data.FactStat
import com.carpimtablosu.app.lock.LockContent
import com.carpimtablosu.app.lock.LockMode
import com.carpimtablosu.app.logic.Curriculum
import com.carpimtablosu.app.logic.LessonKind
import com.carpimtablosu.app.logic.QType
import com.carpimtablosu.app.logic.Srs
import com.carpimtablosu.app.ui.AppRoot
import com.carpimtablosu.app.ui.AppViewModel
import com.carpimtablosu.app.ui.HomeTab
import com.carpimtablosu.app.ui.Listener
import com.carpimtablosu.app.ui.LocalMedia
import com.carpimtablosu.app.ui.MediaTools
import com.carpimtablosu.app.ui.ParentSection
import com.carpimtablosu.app.ui.Screen
import com.carpimtablosu.app.ui.Sounds
import com.carpimtablosu.app.ui.Speaker
import com.carpimtablosu.app.ui.theme.CarpimTheme
import com.carpimtablosu.app.ui.theme.Palette
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Ekranların görüntüsünü üretir (sadece `-Pshots` ile çalışır, CI'da atlanır).
 * Görsel hataları emülatör olmadan yakalamak için kullanılır.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h850dp-xxhdpi")
class ScreenshotTest {
    private lateinit var app: App
    private lateinit var vm: AppViewModel
    private val dir by lazy { File(System.getProperty("shots.dir") ?: "screenshots").apply { mkdirs() } }

    @Before fun setUp() {
        assumeTrue(System.getProperty("shots") == "true")
        app = ApplicationProvider.getApplicationContext()
        val now = System.currentTimeMillis()
        app.repo.updateSettings { it.copy(onboarded = true) }
        app.repo.updateProgress {
            it.copy(
                childName = "Deniz",
                completedNodes = mapOf(2 to 4, 3 to 4, 4 to 1),
                xp = 245, streak = 4, bestStreak = 6, lastGoalDate = app.repo.today().toString(),
                totalCorrect = 230, totalWrong = 31, lessonsDone = 12, perfectLessons = 3, sprintBest = 17,
                achievements = setOf("first_lesson", "perfect", "streak3", "correct100"),
                facts = Srs.allFacts().filter { (a, _) -> a <= 4 }.associate { (a, b) ->
                    Srs.key(a, b) to FactStat(box = (a + b) % 6, dueAt = now - 1, correct = 5, wrong = (b % 3), seen = true)
                },
            )
        }
        repeat(20) { app.repo.recordAnswer("4x${it % 10 + 1}", it % 6 != 0) }
        vm = AppViewModel(app)
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        val media = MediaTools(Speaker(app), Listener(app), Sounds())
        captureRoboImage(File(dir, "$name.png").absolutePath) {
            CarpimTheme {
                CompositionLocalProvider(LocalMedia provides media) {
                    Box(Modifier.fillMaxSize().background(Palette.Bg)) { content() }
                }
            }
        }
    }

    private fun root(name: String) = shot(name) { AppRoot(vm) {} }

    @Test fun home() {
        root("01_yol")
        vm.tab = HomeTab.PRACTICE
        root("02_pratik")
        vm.tab = HomeTab.BADGES
        root("03_rozetler")
        vm.tab = HomeTab.PROFILE
        root("04_profil")
    }

    @Test fun lesson() {
        vm.startLesson(LessonKind.Node(4, 1))
        // İlk soruyu türlerine göre göster
        var i = 0
        val seen = mutableSetOf<QType>()
        while (vm.lesson != null && i < 30) {
            val q = vm.lesson!!.current!!
            if (seen.add(q.type)) root("10_ders_${q.type.name.lowercase()}")
            vm.submit(if (i == 1) q.answer + 1 else q.answer)
            if (i == 0) root("12_ders_dogru")
            if (i == 1) root("11_ders_yanlis")
            vm.next()
            i++
        }
        root("13_sonuc")
    }

    @Test fun keypadAndSprint() {
        vm.startLesson(LessonKind.Node(4, 3))
        root("14_sinav")
        vm.quitLesson()
        vm.startLesson(LessonKind.Sprint)
        root("15_hizli_tur")
    }

    @Test fun parent() {
        vm.push(Screen.ParentGate)
        root("20_ebeveyn_giris")
        vm.enterParent()
        root("21_ebeveyn_panel")
        vm.push(Screen.Parent(ParentSection.RULES))
        root("22_kurallar")
        vm.push(Screen.Parent(ParentSection.REPORT))
        root("23_rapor")
        vm.push(Screen.Parent(ParentSection.SECURITY))
        root("24_guvenlik")
        vm.push(Screen.Parent(ParentSection.APPS))
        root("25_uygulamalar")
        vm.push(Screen.Parent(ParentSection.DIAGNOSTICS))
        root("26_tanilama")
    }

    @Test fun onboardingAndLock() {
        app.repo.updateSettings { it.copy(onboarded = false) }
        val fresh = AppViewModel(app)
        shot("30_kurulum") { AppRoot(fresh) {} }
        LockMode.entries.forEach { m ->
            shot("31_kilit_${m.name.lowercase()}") {
                LockContent(m, "YouTube", {}, {}, {}, {})
            }
        }
    }

    @Test fun allTablesDone() {
        app.repo.updateProgress { it.copy(completedNodes = Curriculum.TABLES.associateWith { Curriculum.NODES_PER_TABLE }) }
        root("05_hepsi_bitti")
    }
}
