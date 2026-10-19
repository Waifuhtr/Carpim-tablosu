package com.carpimtablosu.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.carpimtablosu.app.ui.screens.HomeScreen
import com.carpimtablosu.app.ui.screens.LessonScreen
import com.carpimtablosu.app.ui.screens.OnboardingScreen
import com.carpimtablosu.app.ui.screens.ParentGateScreen
import com.carpimtablosu.app.ui.screens.ParentScreen
import com.carpimtablosu.app.ui.screens.ResultScreen
import com.carpimtablosu.app.ui.theme.CarpimTheme
import com.carpimtablosu.app.ui.theme.Palette

/** Ses, okuma ve dinleme araçları; etkinlik ömrü boyunca bir kez oluşturulur. */
class MediaTools(val speaker: Speaker, val listener: Listener, val sounds: Sounds)

val LocalMedia = staticCompositionLocalOf<MediaTools> { error("MediaTools yok") }

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private lateinit var media: MediaTools

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        media = MediaTools(Speaker(this), Listener(this), Sounds())
        setContent {
            CarpimTheme {
                CompositionLocalProvider(LocalMedia provides media) {
                    AppRoot(vm, onExit = { finish() })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onPause() {
        super.onPause()
        media.listener.stop()
        media.speaker.stop()
    }

    override fun onDestroy() {
        media.listener.stop()
        media.speaker.shutdown()
        media.sounds.release()
        super.onDestroy()
    }
}

@Composable
fun AppRoot(vm: AppViewModel, onExit: () -> Unit) {
    val screen = vm.screen
    // Ders ekranı kendi geri tuşunu yönetir (çıkış onayı)
    BackHandler(enabled = screen !is Screen.Lesson) {
        if (screen is Screen.Result) vm.goHome() else if (!vm.back()) onExit()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .systemBarsPadding()
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Onboarding -> OnboardingScreen(vm)
                Screen.Home -> HomeScreen(vm)
                Screen.Lesson -> LessonScreen(vm)
                is Screen.Result -> ResultScreen(vm, s)
                Screen.ParentGate -> ParentGateScreen(vm)
                is Screen.Parent -> ParentScreen(vm, s.section)
            }
        }
    }
}
