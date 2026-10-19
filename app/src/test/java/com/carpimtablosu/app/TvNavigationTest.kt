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
import com.carpimtablosu.app.logic.QType
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV'de dokunmatik ekran yoktur: tüm akış yalnızca kumanda tuşlarıyla
 * (yön tuşları, OK, rakamlar) tamamlanabilmeli.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun press(keys: List<Key>) {
        rule.onRoot().performKeyInput { keys.forEach { pressKey(it) } }
        rule.waitForIdle()
    }

    private fun digitKey(d: Char): Key = when (d) {
        '0' -> Key.Zero; '1' -> Key.One; '2' -> Key.Two; '3' -> Key.Three; '4' -> Key.Four
        '5' -> Key.Five; '6' -> Key.Six; '7' -> Key.Seven; '8' -> Key.Eight; else -> Key.Nine
    }

    @Test fun wholeLessonWithRemoteOnly() {
        val app = ApplicationProvider.getApplicationContext<App>()
        app.repo.updateSettings { it.copy(onboarded = true) }
        val vm = AppViewModel(app)
        val media = MediaTools(Speaker(app), Listener(app), Sounds())
        rule.setContent {
            CarpimTheme {
                CompositionLocalProvider(LocalMedia provides media, LocalIsTv provides true) {
                    AppRoot(vm) {}
                }
            }
        }
        rule.waitForIdle()

        // Test ortamı dokunmatik modda başlar (bazı TV kutuları gibi); herhangi bir tuş kumanda
        // moduna geçirir ve odak "BAŞLA" düğümüne verilir
        press(listOf(Key.ShiftLeft))

        // 2'ler tablosunun dört dersi (son ders usta sınavı) art arda, yalnızca kumandayla
        for (node in 0 until 4) {
            assertTrue("Ana ekranda odaklı öğe yok (ders $node)", rule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty())
            press(listOf(Key.DirectionCenter))
            assertEquals("Ders $node başlamadı", Screen.Lesson, vm.screen)
            var answered = 0
            while (vm.lesson != null && answered < 40) {
                val q = vm.lesson!!.current!!
                when (q.type) {
                    QType.CHOICE -> {
                        // Odak sol üstteki seçenekte; 2x2 ızgarada doğru cevaba git
                        when (q.options.indexOf(q.answer)) {
                            1 -> press(listOf(Key.DirectionRight))
                            2 -> press(listOf(Key.DirectionDown))
                            3 -> press(listOf(Key.DirectionDown, Key.DirectionRight))
                        }
                        press(listOf(Key.DirectionCenter))
                    }
                    QType.TRUE_FALSE -> {
                        if (q.answer == 0) press(listOf(Key.DirectionRight))
                        press(listOf(Key.DirectionCenter))
                    }
                    else -> {
                        // Kumandanın rakam tuşlarıyla yaz, aşağı inip "Kontrol et"e bas
                        q.answer.toString().forEach { press(listOf(digitKey(it))) }
                        repeat(6) { press(listOf(Key.DirectionDown)) }
                        press(listOf(Key.DirectionCenter))
                    }
                }
                val a = vm.lesson?.answer
                assertTrue("Soru cevaplanamadı: ${q.prompt} (${q.type})", a != null)
                assertTrue("Yanlış cevap verildi: ${q.prompt} (${q.type}) → ${a!!.given}", a.correct)
                answered++
                // Odak "Devam" düğmesine geçmiş olmalı
                press(listOf(Key.DirectionCenter))
            }
            val result = vm.screen as Screen.Result
            assertEquals(0, result.state.wrong)
            assertTrue(result.state.passed)
            // Sonuç ekranındaki "Devam" ile ana ekrana dönülür
            press(listOf(Key.DirectionCenter))
            assertEquals(Screen.Home, vm.screen)
        }
        assertTrue(com.carpimtablosu.app.logic.Curriculum.isTableDone(app.repo.progress.value, 2))
    }
}
