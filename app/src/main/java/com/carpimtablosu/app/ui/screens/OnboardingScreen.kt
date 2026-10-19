package com.carpimtablosu.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carpimtablosu.app.ui.AppViewModel
import com.carpimtablosu.app.ui.components.BigButton
import com.carpimtablosu.app.ui.components.ButtonStyle
import com.carpimtablosu.app.ui.components.Mascot
import com.carpimtablosu.app.ui.components.Mood
import com.carpimtablosu.app.ui.components.ProgressBar
import com.carpimtablosu.app.ui.theme.Palette

/**
 * İlk açılış: isim, ebeveyn şifresi, kurtarma kodu, izinler ve kilitlenecek uygulamalar.
 * Şifre kurulmadan ana ekrana geçilemez.
 */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    // Şifre zaten kurulmuşsa (ör. işlem yarıda kaldıysa) şifre adımları atlanır
    var step by rememberSaveable { mutableIntStateOf(if (vm.repo.hasPassword()) 3 else 0) }
    var name by rememberSaveable { mutableStateOf(vm.progress.value.childName) }
    var code by rememberSaveable { mutableStateOf<String?>(null) }
    val total = 5

    val back: (() -> Unit)? = when (step) {
        1 -> { { step = 0 } }
        4 -> { { step = 3 } }
        else -> null
    }

    ScreenScaffold(title = "Kurulum", onBack = back, scroll = step != 4) {
        ProgressBar((step + 1f) / total, Palette.Green, height = 10.dp)
        Spacer(Modifier.height(16.dp))
        when (step) {
            0 -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Mascot(Mood.HAPPY, 200.dp) }
                Text(
                    "Çarpım tablosunu oyun gibi öğren",
                    style = MaterialTheme.typography.headlineMedium, color = Palette.Text,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Her doğru cevap telefonda oyun süresi kazandırır. Hazırsan adını yaz.",
                    color = Palette.TextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("Adın (isteğe bağlı)") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                BigButton("Devam", {
                    vm.repo.setChildName(name)
                    step = 1
                }, style = ButtonStyle.SUCCESS)
            }
            1 -> {
                Text("Ebeveyn şifresi", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                Text(
                    "Bu adımı ebeveyn yapmalı. Şifre; kilitleri, süreyi ve ayarları değiştirmek için gerekir.",
                    color = Palette.TextDim,
                )
                Spacer(Modifier.height(12.dp))
                NewPasswordForm(vm) {
                    code = it
                    step = 2
                }
            }
            2 -> {
                val c = code
                if (c != null) {
                    RecoveryCodeView(c) { step = 3 }
                } else {
                    LaunchedEffect(Unit) { step = 3 }
                }
            }
            3 -> {
                Text("İzinler", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                Text(
                    "Uygulama kilidi için erişilebilirlik servisi gerekir. Diğerleri isteğe bağlıdır. " +
                        "İzinleri daha sonra Ebeveyn paneli > Güvenlik ve izinler bölümünden de verebilirsiniz.",
                    color = Palette.TextDim,
                )
                Spacer(Modifier.height(12.dp))
                PermissionList(vm)
                Spacer(Modifier.height(16.dp))
                BigButton("Devam", { step = 4 }, style = ButtonStyle.SUCCESS)
            }
            else -> {
                Text("Kilitlenecek uygulamalar", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                Spacer(Modifier.height(8.dp))
                AppPicker(vm, Modifier.weight(1f))
                Spacer(Modifier.height(12.dp))
                BigButton("Kurulumu bitir", { vm.finishOnboarding() }, style = ButtonStyle.SUCCESS)
            }
        }
    }
}
