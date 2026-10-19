package com.carpimtablosu.app.lock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carpimtablosu.app.App
import com.carpimtablosu.app.data.PasswordCheck
import com.carpimtablosu.app.logic.LockReason
import com.carpimtablosu.app.ui.MainActivity
import com.carpimtablosu.app.ui.components.BigButton
import com.carpimtablosu.app.ui.components.ButtonStyle
import com.carpimtablosu.app.ui.components.Mascot
import com.carpimtablosu.app.ui.components.Mood
import com.carpimtablosu.app.ui.screens.ErrorText
import com.carpimtablosu.app.ui.screens.PasswordField
import com.carpimtablosu.app.ui.screens.SmallButton
import com.carpimtablosu.app.ui.screens.formatDelta
import com.carpimtablosu.app.ui.theme.CarpimTheme
import com.carpimtablosu.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kilitli uygulamanın yerine açılan tam ekran. Geri tuşu ana ekrana götürür;
 * kilitli uygulamaya geri dönmenin tek yolu süre kazanmak veya ebeveyn şifresidir.
 */
class LockActivity : ComponentActivity() {

    enum class Mode { NO_TIME, QUIET, PROTECT }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    override fun onStop() {
        super.onStop()
        // Arka planda eski bir kilit ekranı kalmasın
        if (!isChangingConfigurations) finish()
    }

    private fun render() {
        val mode = runCatching { Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "") }.getOrDefault(Mode.NO_TIME)
        val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        val label = if (pkg.isEmpty()) "" else SystemAccess.appLabel(this, pkg)
        setContent {
            CarpimTheme {
                LockContent(
                    mode = mode,
                    appLabel = label,
                    onEarn = { openTraining() },
                    onHome = { goHome() },
                    onEmergency = { openDialer() },
                    onUnlocked = { finish() },
                )
            }
        }
    }

    private fun openTraining() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openDialer() {
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_PKG = "pkg"

        fun modeFor(reason: LockReason): Mode = when (reason) {
            LockReason.QUIET_HOURS -> Mode.QUIET
            else -> Mode.NO_TIME
        }

        fun intent(context: Context, mode: Mode, pkg: String): Intent =
            Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_PKG, pkg)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
    }
}

@Composable
internal fun LockContent(
    mode: LockActivity.Mode,
    appLabel: String,
    onEarn: () -> Unit,
    onHome: () -> Unit,
    onEmergency: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { App.repo(context) }
    val settings by repo.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var askPassword by remember { mutableStateOf(mode == LockActivity.Mode.PROTECT) }
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val (title, body) = when (mode) {
        LockActivity.Mode.NO_TIME -> "Süren bitti!" to
            (if (appLabel.isNotEmpty()) "$appLabel için " else "") + "süren kalmadı. Birkaç soru çöz, her doğru cevap " +
            "${formatDelta(settings.rewardSec)} kazandırır."
        LockActivity.Mode.QUIET -> "Gece modu" to "Şu an dinlenme saati. Seçili uygulamalar sabaha kadar kapalı."
        LockActivity.Mode.PROTECT -> "Bu ayar korunuyor" to "Bu ekranı açmak için ebeveyn şifresi gerekir."
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Mascot(Mood.LOCKED, 200.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Palette.Amber, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        if (askPassword) {
            PasswordField("Ebeveyn şifresi", pw) { pw = it; error = null }
            ErrorText(error)
            Spacer(Modifier.height(12.dp))
            BigButton("Onayla", {
                if (busy) return@BigButton
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.Default) { repo.checkPassword(pw) }
                    busy = false
                    pw = ""
                    when (r) {
                        PasswordCheck.Ok -> {
                            val now = System.currentTimeMillis()
                            if (mode == LockActivity.Mode.PROTECT) {
                                repo.updateSettings { it.copy(settingsBypassUntil = now + 10 * 60_000L) }
                            } else {
                                repo.updateSettings { it.copy(parentBypassUntil = now + 30 * 60_000L) }
                            }
                            onUnlocked()
                        }
                        is PasswordCheck.Wrong -> error = "Hatalı şifre"
                        is PasswordCheck.LockedOut -> error = "Çok fazla deneme. ${(r.remainingMs + 999) / 1000} sn bekleyin."
                    }
                }
            }, enabled = pw.isNotEmpty() && !busy)
            Spacer(Modifier.height(10.dp))
            BigButton("Ana ekrana dön", onHome, style = ButtonStyle.SECONDARY)
        } else {
            if (mode == LockActivity.Mode.NO_TIME) {
                BigButton("Soru çöz, süre kazan", onEarn, style = ButtonStyle.SUCCESS)
                Spacer(Modifier.height(10.dp))
            }
            BigButton("Ana ekrana dön", onHome, style = ButtonStyle.SECONDARY)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { askPassword = true }) {
                Text("Ebeveyn: 30 dakika kilidi aç", color = Palette.Blue)
            }
        }
        Spacer(Modifier.height(16.dp))
        SmallButton("Acil arama (112)", Modifier.fillMaxWidth(), onEmergency)
    }
}
