package com.carpimtablosu.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import com.carpimtablosu.app.ui.theme.Palette

/** Ekran TV'de mi çalışıyor? (kumanda ile kullanım, yatay düzen) */
val LocalIsTv = staticCompositionLocalOf { false }

/**
 * Kumanda veya klavye ile odaklanan öğeyi belirgin çerçeve ve hafif büyütmeyle gösterir.
 * Dokunmatik kullanımda odak oluşmadığı için telefonda görünmez.
 * Tıklanabilir modifier'dan ÖNCE eklenmelidir.
 */
fun Modifier.focusRing(shape: Shape, color: Color = Palette.Text): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "focusScale")
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        // Kenarlık her zaman zincirde kalır, yalnızca rengi değişir. Odakla birlikte modifier
        // eklenip çıkarılırsa odak düğümü yeniden oluşturulabilir ve odak öğeler arasında
        // gidip gelerek ekranı kilitleyebilir.
        .border(3.dp, if (focused) color else Color.Transparent, shape)
}

/**
 * Ekran açıldığında odağı bu öğeye verir (yalnızca [enabled] ise; telefonda kapalı tutulur).
 * [key] değişince yeniden odaklanır (ör. yeni soru). Tıklanabilir modifier'dan ÖNCE eklenmelidir.
 *
 * Tıklanabilir öğeler yalnızca kumanda/klavye modunda odak alabilir. Çoğu TV bu modda açılır;
 * dokunmatik modda açılan TV kutularında ilk tuş basışı modu değiştirir ve odak o anda verilir.
 */
fun Modifier.initialFocus(enabled: Boolean, key: Any? = Unit): Modifier = composed {
    val requester = remember { FocusRequester() }
    val mode = LocalInputModeManager.current.inputMode
    LaunchedEffect(enabled, key, mode) {
        if (enabled && mode == InputMode.Keyboard) {
            // Öğe ekrana yerleşsin diye bir kare beklenir; odak verilemezse sessizce geçilir
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    this.focusRequester(requester)
}
