package com.carpimtablosu.app.lock

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.carpimtablosu.app.App
import com.carpimtablosu.app.DebugLog
import com.carpimtablosu.app.Platform
import com.carpimtablosu.app.ui.LocalIsTv
import androidx.compose.runtime.CompositionLocalProvider
import com.carpimtablosu.app.data.PasswordCheck
import com.carpimtablosu.app.logic.LockReason
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

enum class LockMode {
    NO_TIME, QUIET, PROTECT;

    companion object {
        fun of(reason: LockReason): LockMode = if (reason == LockReason.QUIET_HOURS) QUIET else NO_TIME
    }
}

/**
 * Kilit ekranı, erişilebilirlik servisinin kendi penceresi olarak çizilir
 * (TYPE_ACCESSIBILITY_OVERLAY). Bu pencere ayrı bir izin istemez ve bazı
 * üreticilerin (ör. Xiaomi) "arka plandan ekran açma" engeline takılmaz;
 * önceki sürümdeki Activity tabanlı kilit ekranı bu yüzden bazı telefonlarda hiç görünmüyordu.
 */
class LockOverlay(
    private val context: Context,
    private val onHome: () -> Unit,
    private val onEarn: () -> Unit,
    private val onEmergency: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var owner: OverlayOwner? = null
    private val state = mutableStateOf(LockMode.NO_TIME to "")
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isTv = Platform.isTv(context)

    /**
     * Güvenlik: kilit ekranı hiçbir zaman kalıcı olarak takılı kalmaz. Bir süre dokunulmazsa
     * kendiliğinden kapanır (kilitli uygulama zaten kapatılmış, arkada ana ekran vardır).
     * Kumanda odak alamazsa veya bir hata olursa televizyon böylece kullanılamaz hale gelmez.
     */
    private val autoHide = Runnable {
        if (root != null) {
            DebugLog.log("Kilit ekranı süre aşımıyla kapandı")
            hide()
        }
    }

    private fun bumpTimeout() {
        mainHandler.removeCallbacks(autoHide)
        val ms = if (state.value.first == LockMode.PROTECT) PROTECT_TIMEOUT_MS else TIMEOUT_MS
        mainHandler.postDelayed(autoHide, ms)
    }

    val isShowing: Boolean get() = root != null

    fun show(mode: LockMode, pkg: String) {
        state.value = mode to (if (pkg.isEmpty()) "" else SystemAccess.appLabel(context, pkg))
        bumpTimeout()
        if (root != null) return
        val o = OverlayOwner().also { it.start() }
        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                val (m, label) = state.value
                CarpimTheme {
                    CompositionLocalProvider(LocalIsTv provides isTv) {
                    LockContent(
                        mode = m,
                        appLabel = label,
                        onEarn = { hideThen(onEarn) },
                        onHome = { hideThen(onHome) },
                        onEmergency = { hideThen(onEmergency) },
                        onUnlocked = { hideThen {} },
                    )
                    }
                }
            }
        }
        // Geri tuşu ana ekrana götürür; kilitli uygulamaya dönülmez
        val frame = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                bumpTimeout()
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) hideThen(onHome)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                bumpTimeout()
                return super.dispatchTouchEvent(event)
            }
        }
        frame.setViewTreeLifecycleOwner(o)
        frame.setViewTreeSavedStateRegistryOwner(o)
        frame.addView(compose)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        try {
            wm.addView(frame, params)
            root = frame
            owner = o
            DebugLog.log("Kilit ekranı gösterildi: ${mode.name} $pkg")
        } catch (e: RuntimeException) {
            o.stop()
            DebugLog.error("Kilit ekranı gösterilemedi", e)
        }
    }

    /**
     * Pencereyi, içindeki tıklama işlenirken kaldırmak Compose'u çökertebilir;
     * bu yüzden kaldırma bir sonraki döngüye ertelenir.
     */
    private fun hideThen(action: () -> Unit) {
        mainHandler.post {
            hide()
            action()
        }
    }

    fun hide() {
        mainHandler.removeCallbacks(autoHide)
        val r = root ?: return
        root = null
        runCatching { wm.removeViewImmediate(r) }.onFailure { DebugLog.error("Kilit ekranı kapatılamadı", it) }
        owner?.stop()
        owner = null
        DebugLog.log("Kilit ekranı kapandı")
    }
}

private const val TIMEOUT_MS = 45_000L
private const val PROTECT_TIMEOUT_MS = 120_000L

/** Compose'un servis penceresinde çalışması için gereken yaşam döngüsü sahibi. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun start() {
        saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

@Composable
internal fun LockContent(
    mode: LockMode,
    appLabel: String,
    onEarn: () -> Unit,
    onHome: () -> Unit,
    onEmergency: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { App.repo(context) }
    val settings by repo.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var askPassword by remember(mode) { mutableStateOf(mode == LockMode.PROTECT) }
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val (title, body) = when (mode) {
        LockMode.NO_TIME -> "Süren bitti!" to
            (if (appLabel.isNotEmpty()) "$appLabel için " else "") + "süren kalmadı. Birkaç soru çöz, her doğru cevap " +
            "${formatDelta(settings.rewardSec)} kazandırır."
        LockMode.QUIET -> "Gece modu" to "Şu an dinlenme saati. Seçili uygulamalar sabaha kadar kapalı."
        LockMode.PROTECT -> "Bu ayar korunuyor" to "Bu ekranı açmak için ebeveyn şifresi gerekir."
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg),
        contentAlignment = Alignment.Center,
    ) {
    Column(
        Modifier
            // TV'de düğmeler ekran boyunca uzamasın
            .widthIn(max = 560.dp)
            .fillMaxSize()
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

        val isTv = LocalIsTv.current
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
                            if (mode == LockMode.PROTECT) {
                                repo.updateSettings { it.copy(settingsBypassUntil = now + 10 * 60_000L) }
                            } else {
                                repo.updateSettings { it.copy(parentBypassUntil = now + 30 * 60_000L) }
                            }
                            DebugLog.log("Kilit ekranında ebeveyn şifresi doğru (${mode.name})")
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
            if (mode == LockMode.NO_TIME) {
                BigButton("Soru çöz, süre kazan", onEarn, style = ButtonStyle.SUCCESS, autoFocus = isTv)
                Spacer(Modifier.height(10.dp))
            }
            BigButton(
                "Ana ekrana dön", onHome, style = ButtonStyle.SECONDARY,
                autoFocus = isTv && mode != LockMode.NO_TIME,
            )
            // TV'de kilit katmanında ekran klavyesi açılmayabilir; ebeveyn kilidi
            // uygulamanın ebeveyn panelinden kaldırır
            if (!isTv) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { askPassword = true }) {
                    Text("Ebeveyn: 30 dakika kilidi aç", color = Palette.Blue)
                }
            }
        }
        if (!isTv) {
            Spacer(Modifier.height(16.dp))
            SmallButton("Acil arama (112)", Modifier.fillMaxWidth(), onEmergency)
        }
    }
}
}
