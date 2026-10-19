package com.carpimtablosu.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.carpimtablosu.app.ui.AppRoot
import com.carpimtablosu.app.ui.AppViewModel
import com.carpimtablosu.app.ui.Listener
import com.carpimtablosu.app.ui.LocalIsTv
import com.carpimtablosu.app.ui.LocalMedia
import com.carpimtablosu.app.ui.MediaTools
import com.carpimtablosu.app.ui.Screen
import com.carpimtablosu.app.ui.Sounds
import com.carpimtablosu.app.ui.Speaker
import com.carpimtablosu.app.ui.theme.CarpimTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/** Ana ekranda farklı ilerleme durumlarında kumandayla ilk odak ve ders başlatma. */
@OptIn(ExperimentalTestApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvHomeFocusTest(private val done: Map<Int, Int>) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "ilerleme={0}")
        fun params(): List<Array<Any>> = listOf(
            mapOf<Int, Int>(),
            mapOf(2 to 1),
            mapOf(2 to 2),
            mapOf(2 to 3),
            mapOf(2 to 4),
            mapOf(2 to 4, 3 to 2),
            (2..6).associateWith { 4 },
        ).map { arrayOf<Any>(it) }
    }

    @Test fun firstKeyFocusesCurrentNodeAndOkStartsLesson() {
        val app = ApplicationProvider.getApplicationContext<App>()
        app.repo.updateSettings { it.copy(onboarded = true) }
        app.repo.updateProgress { it.copy(completedNodes = done) }
        val vm = AppViewModel(app)
        val media = MediaTools(Speaker(app), Listener(app), Sounds())
        rule.setContent {
            CarpimTheme {
                CompositionLocalProvider(LocalMedia provides media, LocalIsTv provides true) { AppRoot(vm) {} }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty())
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(Screen.Lesson, vm.screen)
    }
}
