package com.carpimtablosu.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.carpimtablosu.app.lock.LockContent
import com.carpimtablosu.app.lock.LockMode
import com.carpimtablosu.app.logic.LessonKind
import com.carpimtablosu.app.logic.QType
import com.carpimtablosu.app.ui.AppRoot
import com.carpimtablosu.app.ui.AppViewModel
import com.carpimtablosu.app.ui.HomeTab
import com.carpimtablosu.app.ui.Listener
import com.carpimtablosu.app.ui.LocalIsTv
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

/** TV (1920x1080, yatay) ekran görüntüleri. Sadece `-Pshots` ile çalışır. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvScreenshotTest {
    private lateinit var app: App
    private lateinit var vm: AppViewModel
    private val dir by lazy { File(System.getProperty("shots.dir") ?: "screenshots", "tv").apply { mkdirs() } }

    @Before fun setUp() {
        assumeTrue(System.getProperty("shots") == "true")
        app = ApplicationProvider.getApplicationContext()
        app.repo.updateSettings { it.copy(onboarded = true) }
        app.repo.updateProgress { it.copy(childName = "Deniz", completedNodes = mapOf(2 to 4, 3 to 1), xp = 120) }
        repeat(8) { app.repo.recordAnswer("3x${it + 1}", true) }
        vm = AppViewModel(app)
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        val media = MediaTools(Speaker(app), Listener(app), Sounds())
        captureRoboImage(File(dir, "$name.png").absolutePath) {
            CarpimTheme {
                CompositionLocalProvider(LocalMedia provides media, LocalIsTv provides true) {
                    Box(Modifier.fillMaxSize().background(Palette.Bg)) { content() }
                }
            }
        }
    }

    private fun root(name: String) = shot(name) { AppRoot(vm) {} }

    @Test fun screens() {
        root("01_yol")
        vm.tab = HomeTab.PRACTICE
        root("02_pratik")
        vm.tab = HomeTab.PATH
        vm.startLesson(LessonKind.Node(3, 1))
        val seen = mutableSetOf<QType>()
        var i = 0
        while (vm.lesson != null && i < 30) {
            val q = vm.lesson!!.current!!
            if (seen.add(q.type)) root("10_ders_${q.type.name.lowercase()}")
            vm.submit(if (i == 2) q.answer + 1 else q.answer)
            if (i == 2) root("11_ders_yanlis")
            vm.next()
            i++
        }
        root("12_sonuc")
        vm.goHome()
        vm.push(Screen.ParentGate)
        root("20_ebeveyn_giris")
        vm.enterParent()
        root("21_ebeveyn_panel")
        vm.push(Screen.Parent(ParentSection.SECURITY))
        root("22_guvenlik")
    }

    @Test fun lockScreen() {
        shot("30_kilit") { LockContent(LockMode.NO_TIME, "YouTube", {}, {}, {}, {}) }
    }
}
