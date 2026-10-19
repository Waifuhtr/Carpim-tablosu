package com.carpimtablosu.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carpimtablosu.app.data.PasswordCheck
import com.carpimtablosu.app.data.Progress
import com.carpimtablosu.app.DebugLog
import com.carpimtablosu.app.App
import com.carpimtablosu.app.lock.AppLockService
import com.carpimtablosu.app.lock.Watchdog
import com.carpimtablosu.app.logic.BadgeIcon
import com.carpimtablosu.app.logic.LockPolicy
import com.carpimtablosu.app.logic.Curriculum
import com.carpimtablosu.app.logic.Password
import com.carpimtablosu.app.logic.ProgressOps
import com.carpimtablosu.app.logic.Srs
import com.carpimtablosu.app.logic.TimeBank
import com.carpimtablosu.app.lock.SystemAccess
import com.carpimtablosu.app.ui.AppViewModel
import com.carpimtablosu.app.ui.ParentSection
import com.carpimtablosu.app.ui.Screen
import com.carpimtablosu.app.ui.components.BigButton
import com.carpimtablosu.app.ui.components.ButtonStyle
import com.carpimtablosu.app.ui.components.Card
import com.carpimtablosu.app.ui.components.ChoiceChips
import com.carpimtablosu.app.ui.components.Mascot
import com.carpimtablosu.app.ui.components.Mood
import com.carpimtablosu.app.ui.components.NavRow
import com.carpimtablosu.app.ui.components.SectionTitle
import com.carpimtablosu.app.ui.components.SettingSwitch
import com.carpimtablosu.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TR = Locale.forLanguageTag("tr")

// ======================================================================================
// Şifre ekranı
// ======================================================================================

@Composable
fun ParentGateScreen(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("gate") }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var newCode by remember { mutableStateOf<String?>(null) }

    fun describe(r: PasswordCheck): String? = when (r) {
        PasswordCheck.Ok -> null
        is PasswordCheck.Wrong -> "Hatalı. ${r.attemptsLeftBeforeLock} deneme hakkı sonra bekleme süresi başlar."
        is PasswordCheck.LockedOut -> "Çok fazla hatalı deneme. ${(r.remainingMs + 999) / 1000} saniye bekleyin."
    }

    ScreenScaffold(title = "Ebeveyn paneli", onBack = { vm.back() }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Mascot(Mood.LOCKED, 150.dp) }
        Spacer(Modifier.height(12.dp))
        when (mode) {
            "gate" -> {
                Text("Devam etmek için ebeveyn şifresini girin.", color = Palette.TextDim)
                Spacer(Modifier.height(12.dp))
                PasswordField("Şifre", input) { input = it; error = null }
                ErrorText(error)
                Spacer(Modifier.height(12.dp))
                BigButton("Giriş", {
                    if (busy) return@BigButton
                    busy = true
                    scope.launch {
                        val r = vm.checkPassword(input)
                        busy = false
                        if (r == PasswordCheck.Ok) vm.enterParent() else error = describe(r)
                        input = ""
                    }
                }, enabled = input.isNotEmpty() && !busy)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { mode = "recover"; input = ""; error = null }) {
                    Text("Şifremi unuttum", color = Palette.Blue)
                }
            }
            "recover" -> {
                Text(
                    "Kurulumda size gösterilen kurtarma kodunu girin (ör. ABCDE-23456).",
                    color = Palette.TextDim,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(16); error = null },
                    label = { Text("Kurtarma kodu") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorText(error)
                Spacer(Modifier.height(12.dp))
                BigButton("Doğrula", {
                    if (busy) return@BigButton
                    busy = true
                    scope.launch {
                        val r = vm.checkRecovery(input)
                        busy = false
                        if (r == PasswordCheck.Ok) {
                            mode = "new"; error = null
                        } else {
                            error = describe(r)
                        }
                        input = ""
                    }
                }, enabled = input.isNotEmpty() && !busy)
                TextButton(onClick = { mode = "gate"; input = ""; error = null }) { Text("Geri", color = Palette.Blue) }
            }
            "new" -> {
                val code = newCode
                if (code == null) {
                    Text("Yeni bir şifre belirleyin.", color = Palette.TextDim)
                    Spacer(Modifier.height(12.dp))
                    NewPasswordForm(vm) { newCode = it }
                } else {
                    RecoveryCodeView(code) { vm.enterParent() }
                }
            }
        }
        if (busy) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

// ======================================================================================
// Ebeveyn paneli
// ======================================================================================

@Composable
fun ParentScreen(vm: AppViewModel, section: ParentSection) {
    when (section) {
        ParentSection.MAIN -> ParentMain(vm)
        ParentSection.APPS -> ScreenScaffold("Kilitli uygulamalar", onBack = { vm.back() }, scroll = false) {
            AppPicker(vm, Modifier.weight(1f))
        }
        ParentSection.RULES -> RulesScreen(vm)
        ParentSection.REPORT -> ReportScreen(vm)
        ParentSection.SECURITY -> SecurityScreen(vm)
        ParentSection.DIAGNOSTICS -> DiagnosticsScreen(vm)
    }
}

@Composable
private fun ParentMain(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val time by vm.time.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val serviceOn = remember(vm.resumeTick) { SystemAccess.isAccessibilityEnabled(context) }
    val serviceDead = remember(vm.resumeTick) { Watchdog.isDead(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(vm.repo.exportBackup().toByteArray()) }
                        ?: error("Dosya açılamadı")
                }.fold({ "Yedek kaydedildi" }, { "Yedek kaydedilemedi: ${it.message}" })
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = withContext(Dispatchers.IO) {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { s ->
                        val bytes = s.readBytes()
                        require(bytes.size < 2_000_000) { "Dosya çok büyük" }
                        String(bytes)
                    } ?: error("Dosya açılamadı")
                }
                text.fold(
                    { t -> vm.repo.importBackup(t).fold({ "Yedek yüklendi" }, { "Geçersiz yedek dosyası" }) },
                    { "Dosya okunamadı: ${it.message}" },
                )
            }
        }
    }

    ScreenScaffold("Ebeveyn paneli", onBack = { vm.back() }) {
        if (serviceDead) {
            Card {
                Column {
                    Text("Kilit servisi çalışmıyor", style = MaterialTheme.typography.titleMedium, color = Palette.Amber)
                    Text(
                        "Servis ayarlarda açık görünüyor ama durmuş (ayarlarda \"hatalı çalışıyor\" yazar). " +
                            "Erişilebilirlik ayarlarından kapatıp yeniden açın. Tekrar etmemesi için: Tanılama ve kayıtlar.",
                        color = Palette.TextDim,
                    )
                    Spacer(Modifier.height(10.dp))
                    BigButton("Erişilebilirlik ayarları", { safeStart(context, SystemAccess.accessibilitySettings()) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!serviceOn) {
            Card {
                Column {
                    Text("Kilit servisi kapalı", style = MaterialTheme.typography.titleMedium, color = Palette.Red)
                    Text(
                        "Uygulama kilitleme için erişilebilirlik servisini açın. Ayrıntılar: Güvenlik ve izinler.",
                        color = Palette.TextDim,
                    )
                    Spacer(Modifier.height(10.dp))
                    BigButton("Servisi aç", { safeStart(context, SystemAccess.accessibilitySettings()) }, style = ButtonStyle.DANGER)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Card {
            Column {
                Text("Kalan süre", color = Palette.TextDim)
                Text(
                    TimeBank.format(time.bankSec),
                    fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Green,
                )
                Text(
                    "Bugün kazanılan: ${(time.earnedTodaySec.takeIf { time.day == vm.repo.today().toString() } ?: 0) / 60} dk" +
                        if (settings.dailyEarnCapSec > 0) " / ${settings.dailyEarnCapSec / 60} dk" else "",
                    color = Palette.TextDim,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("+5 dk", Modifier.weight(1f)) { vm.repo.adjustTime(5 * 60) }
                    SmallButton("+15 dk", Modifier.weight(1f)) { vm.repo.adjustTime(15 * 60) }
                    SmallButton("−5 dk", Modifier.weight(1f)) { vm.repo.adjustTime(-5 * 60) }
                    SmallButton("Sıfırla", Modifier.weight(1f)) { vm.repo.clearTime() }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card {
            Column {
                SettingSwitch(
                    "Kilitler açık",
                    if (settings.locksEnabled) "Seçili uygulamalar süre bitince kilitlenir" else "Hiçbir uygulama kilitlenmiyor",
                    settings.locksEnabled,
                ) { v -> vm.repo.updateSettings { it.copy(locksEnabled = v) } }
                if (settings.locksEnabled) {
                    if (settings.parentBypassUntil > now) {
                        val until = Instant.ofEpochMilli(settings.parentBypassUntil).atZone(ZoneId.systemDefault())
                        Text(
                            "Kilitler ${until.format(DateTimeFormatter.ofPattern("HH:mm"))}'e kadar kaldırıldı",
                            color = Palette.Amber,
                        )
                        Spacer(Modifier.height(8.dp))
                        BigButton("Kilitleri şimdi geri aç", {
                            vm.repo.updateSettings { it.copy(parentBypassUntil = 0L) }
                        }, style = ButtonStyle.SECONDARY)
                    } else {
                        Text("Geçici olarak kaldır:", color = Palette.TextDim)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30 to "30 dk", 60 to "1 saat", 180 to "3 saat").forEach { (m, label) ->
                                SmallButton(label, Modifier.weight(1f)) {
                                    val t = System.currentTimeMillis() + m * 60_000L
                                    now = System.currentTimeMillis()
                                    vm.repo.updateSettings { it.copy(parentBypassUntil = t) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(padding = 8.dp) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                NavRow(BadgeIcon.LOCK, Palette.Red, "Kilitli uygulamalar", "${settings.lockedApps.size} uygulama seçili") {
                    vm.push(Screen.Parent(ParentSection.APPS))
                }
                NavRow(BadgeIcon.TIMER, Palette.Blue, "Süre kuralları", rulesSummary(settings)) {
                    vm.push(Screen.Parent(ParentSection.RULES))
                }
                NavRow(BadgeIcon.BOOK, Palette.Green, "Rapor", "Son 7 gün, zayıf işlemler") {
                    vm.push(Screen.Parent(ParentSection.REPORT))
                }
                NavRow(BadgeIcon.CHECK, Palette.Purple, "Güvenlik ve izinler", "Şifre, koruma, izin durumu") {
                    vm.push(Screen.Parent(ParentSection.SECURITY))
                }
                NavRow(BadgeIcon.BULB, Palette.Amber, "Tanılama ve kayıtlar", "Kilit çalışmıyorsa buraya bakın") {
                    vm.push(Screen.Parent(ParentSection.DIAGNOSTICS))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("Yedekleme")
        Text(
            "İlerleme bir dosyaya kaydedilir (şifre ve süre hariç). Telefon değişince bu dosyayı yükleyin.",
            color = Palette.TextDim,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Dışa aktar", Modifier.weight(1f)) {
                runCatching { exporter.launch("carpim-yedek-${vm.repo.today()}.json") }
                    .onFailure { message = "Dosya seçici açılamadı" }
            }
            SmallButton("İçe aktar", Modifier.weight(1f)) {
                runCatching { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                    .onFailure { message = "Dosya seçici açılamadı" }
            }
        }
        Spacer(Modifier.height(24.dp))
        BigButton("İlerlemeyi sıfırla", { confirmReset = true }, style = ButtonStyle.DANGER)
    }

    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("TAMAM") } },
            containerColor = Palette.Surface,
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Emin misiniz?") },
            text = { Text("Tüm dersler, seri, rozetler ve istatistikler silinir. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.repo.resetProgress()
                    message = "İlerleme sıfırlandı"
                }) { Text("SIFIRLA", color = Palette.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("VAZGEÇ") } },
            containerColor = Palette.Surface,
        )
    }
}

private fun rulesSummary(s: com.carpimtablosu.app.data.Settings): String {
    val cap = if (s.dailyEarnCapSec > 0) "${s.dailyEarnCapSec / 60} dk/gün" else "sınırsız"
    return "Doğru +${formatDelta(s.rewardSec)}, yanlış −${formatDelta(s.penaltySec)}, $cap"
}

// ======================================================================================
// Süre kuralları
// ======================================================================================

@Composable
private fun RulesScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    ScreenScaffold("Süre kuralları", onBack = { vm.back() }) {
        Card {
            Column {
                Text("Her doğru cevap", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(listOf(30L to "30 sn", 60L to "1 dk", 120L to "2 dk"), s.rewardSec) { v ->
                    vm.repo.updateSettings { it.copy(rewardSec = v) }
                }
                Spacer(Modifier.height(16.dp))
                Text("Her yanlış cevap", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(listOf(0L to "Yok", 30L to "30 sn", 60L to "1 dk", 120L to "2 dk"), s.penaltySec) { v ->
                    vm.repo.updateSettings { it.copy(penaltySec = v) }
                }
                Spacer(Modifier.height(16.dp))
                Text("Günlük kazanma sınırı", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(
                    listOf(30 * 60L to "30 dk", 60 * 60L to "1 sa", 120 * 60L to "2 sa", 0L to "Yok"),
                    s.dailyEarnCapSec,
                ) { v -> vm.repo.updateSettings { it.copy(dailyEarnCapSec = v) } }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sınıra ulaşınca çocuk XP kazanmaya devam eder ama süre eklenmez.",
                    color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            Column {
                SettingSwitch(
                    "Gece modu",
                    "Bu saatlerde kilitli uygulamalar süre olsa bile açılmaz",
                    s.quietEnabled,
                ) { v -> vm.repo.updateSettings { it.copy(quietEnabled = v) } }
                if (s.quietEnabled) {
                    TimeStepper("Başlangıç", s.quietStartMin) { v -> vm.repo.updateSettings { it.copy(quietStartMin = v) } }
                    TimeStepper("Bitiş", s.quietEndMin) { v -> vm.repo.updateSettings { it.copy(quietEndMin = v) } }
                }
            }
        }
    }
}

@Composable
private fun TimeStepper(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Palette.Text, style = MaterialTheme.typography.titleMedium)
        SmallButton("−", Modifier.width(52.dp)) { onChange(Math.floorMod(minutes - 30, 24 * 60)) }
        Text(
            "%02d:%02d".format(minutes / 60, minutes % 60),
            Modifier.width(84.dp), textAlign = TextAlign.Center,
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Text,
        )
        SmallButton("+", Modifier.width(52.dp)) { onChange(Math.floorMod(minutes + 30, 24 * 60)) }
    }
}

// ======================================================================================
// Rapor
// ======================================================================================

@Composable
private fun ReportScreen(vm: AppViewModel) {
    val p by vm.progress.collectAsStateWithLifecycle()
    val today = vm.repo.today()
    ScreenScaffold("Rapor", onBack = { vm.back() }) {
        SectionTitle("Son 7 gün")
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReportRow("Gün", "Doğru", "Yanlış", "Kazanç", "Kullanım", header = true)
                (0..6).forEach { i ->
                    val d = today.minusDays(i.toLong())
                    val st = ProgressOps.day(p, d)
                    ReportRow(
                        if (i == 0) "Bugün" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, TR),
                        "${st.correct}", "${st.wrong}", "${st.earnedSec / 60} dk", "${st.spentSec / 60} dk",
                    )
                }
            }
        }
        SectionTitle("En çok zorlandığı işlemler")
        Card {
            val weak = Srs.weakFacts(p, 10)
            if (weak.isEmpty()) {
                Text("Henüz yeterli veri yok.", color = Palette.TextDim)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    weak.forEach { (f, s) ->
                        Row {
                            Text("${f.first} × ${f.second} = ${f.first * f.second}", Modifier.weight(1f), color = Palette.Text)
                            Text("${s.correct} doğru / ${s.wrong} yanlış", color = Palette.TextDim)
                        }
                    }
                }
            }
        }
        SectionTitle("Tablolar")
        Card { TableSummary(p) }
        Spacer(Modifier.height(12.dp))
        Text(
            "Toplam ${p.totalCorrect} doğru, ${p.totalWrong} yanlış · ${p.lessonsDone} ders · en uzun seri ${p.bestStreak} gün",
            color = Palette.TextDim,
        )
    }
}

@Composable
private fun TableSummary(p: Progress) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Curriculum.TABLES.forEach { t ->
            var c = 0
            var w = 0
            Curriculum.FACTORS.forEach { b -> p.facts[Srs.key(t, b)]?.let { c += it.correct; w += it.wrong } }
            val acc = if (c + w == 0) "—" else "%${c * 100 / (c + w)}"
            val status = when {
                Curriculum.isTableDone(p, t) -> "tamamlandı"
                Curriculum.completed(p, t) > 0 -> "${Curriculum.completed(p, t)}/${Curriculum.NODES_PER_TABLE} ders"
                else -> "başlanmadı"
            }
            Row {
                Text("×$t", Modifier.width(48.dp), color = Palette.Text, fontWeight = FontWeight.Bold)
                Text(status, Modifier.weight(1f), color = Palette.TextDim)
                Text("doğruluk $acc", color = Palette.Text)
            }
        }
    }
}

@Composable
private fun ReportRow(a: String, b: String, c: String, d: String, e: String, header: Boolean = false) {
    val color = if (header) Palette.TextDim else Palette.Text
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    Row {
        Text(a, Modifier.weight(1.2f), color = color, fontWeight = weight)
        Text(b, Modifier.weight(1f), color = color, fontWeight = weight, textAlign = TextAlign.End)
        Text(c, Modifier.weight(1f), color = color, fontWeight = weight, textAlign = TextAlign.End)
        Text(d, Modifier.weight(1.2f), color = color, fontWeight = weight, textAlign = TextAlign.End)
        Text(e, Modifier.weight(1.3f), color = color, fontWeight = weight, textAlign = TextAlign.End)
    }
}

// ======================================================================================
// Tanılama
// ======================================================================================

@Composable
private fun DiagnosticsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val time by vm.time.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var log by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = SystemClock.elapsedRealtime()
            delay(2_000)
        }
    }
    LaunchedEffect(refresh, vm.resumeTick) { log = withContext(Dispatchers.IO) { DebugLog.read() } }
    val summary = remember(settings, time, tick, vm.resumeTick) { diagnosticsSummary(context, settings, time) }

    ScreenScaffold("Tanılama ve kayıtlar", onBack = { vm.back() }) {
        Text(
            "Kilit beklendiği gibi çalışmıyorsa \"Kayıtları kopyala\" ile metni kopyalayıp geliştiriciye gönderin.",
            color = Palette.TextDim,
        )
        Spacer(Modifier.height(10.dp))
        Card {
            Text(summary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.Text, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Kilit ekranını dene", Modifier.weight(1f)) {
                if (Watchdog.isDead(context) || !SystemAccess.isAccessibilityEnabled(context)) {
                    info = "Servis çalışmıyor; önce erişilebilirlik servisini kapatıp açın."
                } else {
                    AppLockService.requestTestLock(context)
                }
            }
            SmallButton("Yenile", Modifier.weight(1f)) { refresh++ }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Kayıtları kopyala", Modifier.weight(1f)) {
                clipboard.setText(AnnotatedString(summary + "\n\n--- Kayıtlar ---\n" + DebugLog.read(1500)))
                info = "Kopyalandı. Mesaj olarak yapıştırabilirsiniz."
            }
            SmallButton("Kayıtları sil", Modifier.weight(1f)) {
                DebugLog.clear()
                refresh++
            }
        }
        info?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Palette.Green)
        }
        SectionTitle("Otomatik onarım (isteğe bağlı)")
        Card {
            Column {
                val canRepair = remember(vm.resumeTick, refresh) { Watchdog.canRepair(context) }
                Text(
                    if (canRepair) "AÇIK: servis durursa uygulama onu kendisi yeniden başlatır (15 dakikada bir ve uygulama açılınca kontrol edilir)."
                    else "KAPALI. Android, duran bir erişilebilirlik servisini kendiliğinden başlatmaz. Bilgisayarınız varsa " +
                        "telefonu USB hata ayıklama açık şekilde bağlayıp şu komutu bir kez çalıştırın:",
                    color = if (canRepair) Palette.Green else Palette.TextDim,
                )
                if (!canRepair) {
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(Watchdog.ADB_COMMAND, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.Amber)
                    }
                    Spacer(Modifier.height(8.dp))
                    SmallButton("Komutu kopyala", Modifier.fillMaxWidth()) {
                        clipboard.setText(AnnotatedString(Watchdog.ADB_COMMAND))
                        info = "Komut kopyalandı."
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    SmallButton("Servisi şimdi yeniden başlat", Modifier.fillMaxWidth()) {
                        Thread { Watchdog.repair(context.applicationContext) }.start()
                        info = "Servis yeniden başlatılıyor…"
                    }
                }
            }
        }
        SectionTitle("Kayıtlar")
        Card {
            SelectionContainer {
                Text(
                    log.ifEmpty { "Henüz kayıt yok." },
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Palette.TextDim, lineHeight = 15.sp,
                )
            }
        }
    }
}

private fun diagnosticsSummary(
    context: Context,
    s: com.carpimtablosu.app.data.Settings,
    t: com.carpimtablosu.app.data.TimeState,
): String {
    val now = System.currentTimeMillis()
    val st = App.repo(context).serviceStatus()
    val beat = if (st.heartbeat == 0L) "hiç" else "${(now - st.heartbeat) / 1000} sn önce"
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    val lines = listOf(
        "Erişilebilirlik (ayar): " + if (SystemAccess.isAccessibilityEnabled(context)) "AÇIK" else "KAPALI",
        "Servis çalışıyor: " + (if (!Watchdog.isDead(context) && st.connected) "EVET" else "HAYIR") +
            " (son sinyal: $beat, pid ${st.pid})",
        "Otomatik onarım izni: " + if (Watchdog.canRepair(context)) "var" else "yok",
        "Kurulum tamam: " + if (s.onboarded) "evet" else "HAYIR",
        "Kilitler: " + if (s.locksEnabled) "açık" else "KAPALI",
        "Ebeveyn muafiyeti: " + if (s.parentBypassUntil > now) {
            Instant.ofEpochMilli(s.parentBypassUntil).atZone(ZoneId.systemDefault()).format(fmt) + "'e kadar"
        } else "yok",
        "Kilitler şu an etkin: " + if (LockPolicy.locksActive(s, now)) "EVET" else "HAYIR",
        "Gece modu: " + if (s.quietEnabled) "${hm(s.quietStartMin)}–${hm(s.quietEndMin)}" else "kapalı",
        "Kalan süre: ${TimeBank.format(t.bankSec)} (${t.bankSec} sn)",
        "Kilitli uygulamalar (${s.lockedApps.size}): " + s.lockedApps.sorted().joinToString(", ").ifEmpty { "YOK" },
        "Son ön plan: " + (st.lastForeground ?: "-"),
        "Son karar: " + (st.lastDecision ?: "-"),
        "Bildirim izni: " + if (SystemAccess.notificationsAllowed(context)) "var" else "YOK",
        "Pil kısıtlaması yok: " + if (SystemAccess.isIgnoringBattery(context)) "evet" else "hayır",
        "Cihaz: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), uygulama $version",
    )
    return lines.joinToString("\n")
}

// ======================================================================================
// Güvenlik ve izinler
// ======================================================================================

@Composable
private fun SecurityScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    var changing by remember { mutableStateOf(false) }
    var newCode by remember { mutableStateOf<String?>(null) }
    var adminTick by remember { mutableIntStateOf(0) }
    val adminOn = remember(vm.resumeTick, adminTick) { SystemAccess.isAdminActive(context) }

    ScreenScaffold("Güvenlik ve izinler", onBack = { vm.back() }) {
        SectionTitle("Şifre")
        Card {
            Column {
                val code = newCode
                when {
                    code != null -> RecoveryCodeView(code) { newCode = null; changing = false }
                    changing -> NewPasswordForm(vm) { newCode = it }
                    else -> BigButton("Şifreyi değiştir", { changing = true }, style = ButtonStyle.SECONDARY)
                }
            }
        }
        SectionTitle("Koruma")
        Card {
            Column {
                SettingSwitch(
                    "Ayar koruması",
                    "Ayarlarda bu uygulamanın sayfası (erişilebilirlik, uygulama bilgisi, kaldırma) açılınca şifre istenir",
                    s.protectSettings,
                ) { v -> vm.repo.updateSettings { it.copy(protectSettings = v) } }
                Spacer(Modifier.height(8.dp))
                Text("Kaldırma koruması", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text(
                    if (adminOn) "Açık: uygulama doğrudan kaldırılamaz." else "Kapalı: uygulama kaldırılabilir.",
                    color = if (adminOn) Palette.Green else Palette.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                if (adminOn) {
                    BigButton("Kaldırma korumasını kapat", {
                        SystemAccess.removeAdmin(context)
                        adminTick++
                    }, style = ButtonStyle.SECONDARY)
                } else {
                    BigButton("Kaldırma korumasını aç", { safeStart(context, SystemAccess.addAdminIntent(context)) }, style = ButtonStyle.INFO)
                }
            }
        }
        SectionTitle("İzinler")
        PermissionList(vm)
    }
}

/** Kurulumda ve güvenlik ekranında kullanılan izin listesi. */
@Composable
fun PermissionList(vm: AppViewModel) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    // Ayarlardan dönünce (resumeTick) veya izin cevabından sonra (refresh) yeniden okunur
    val key = vm.resumeTick to refresh
    val a11y = remember(key) { SystemAccess.isAccessibilityEnabled(context) }
    val notif = remember(key) { SystemAccess.notificationsAllowed(context) }
    val battery = remember(key) { SystemAccess.isIgnoringBattery(context) }
    val mic = remember(key) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    Card {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PermissionRow(
                "Erişilebilirlik servisi (zorunlu)",
                "Kilit için gerekli. Ayarlar > Erişilebilirlik > Yüklü uygulamalar > Çarpım Tablosu > Aç.",
                a11y,
            ) { safeStart(context, SystemAccess.accessibilitySettings()) }
            if (SystemAccess.needsRestrictedSettingsHint() && !a11y) {
                Text(
                    "Android 13 ve üzeri: Servis gri görünüyorsa önce Uygulama bilgisi > sağ üstteki ⋮ > " +
                        "\"Kısıtlanmış ayarlara izin ver\" seçeneğine dokunun, sonra servisi açın.",
                    color = Palette.Amber, style = MaterialTheme.typography.bodyMedium,
                )
                BigButton("Uygulama bilgisini aç", { safeStart(context, SystemAccess.appDetails(context)) }, style = ButtonStyle.SECONDARY)
            }
            PermissionRow("Bildirimler", "Kalan süre ve 1 dakika uyarısı için.", notif) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    safeStart(context, SystemAccess.appDetails(context))
                }
            }
            PermissionRow(
                "Pil optimizasyonu",
                "Bazı telefonlar (Xiaomi, Huawei, Oppo) arka plandaki servisi kapatabilir. Listeden uygulamayı \"Kısıtlama yok\" yapın.",
                battery,
            ) { safeStart(context, SystemAccess.batterySettings()) }
            if (SystemAccess.isXiaomi()) {
                PermissionRow(
                    "Otomatik başlatma (Xiaomi)",
                    "Xiaomi/Redmi/POCO telefonlar servisi durdurabilir. Açılan listede Çarpım Tablosu'nu açın. " +
                        "Ayrıca Son uygulamalar ekranında uygulamaya basılı tutup kilit simgesine dokunun.",
                    false,
                ) { safeStart(context, SystemAccess.xiaomiAutostart()) }
            }
            PermissionRow("Mikrofon (isteğe bağlı)", "Sesli cevap için.", mic) {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, desc: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(if (ok) Palette.Green else Palette.Red)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        }
        if (!ok) {
            Spacer(Modifier.width(8.dp))
            SmallButton("Aç", Modifier.width(64.dp), onFix)
        }
    }
}

// ======================================================================================
// Uygulama seçici
// ======================================================================================

private class AppRow(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
fun AppPicker(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppRow>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            runCatching {
                SystemAccess.launchableApps(context).map { a ->
                    val bmp = runCatching { a.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
                    AppRow(a.pkg, a.label, bmp)
                }
            }.getOrDefault(emptyList())
        }
    }

    Column(modifier) {
        Text(
            "Seçtiğiniz uygulamalar sadece çocuğun kazandığı süre boyunca açılır. Telefon, mesaj ve ayarlar listede yoktur; her zaman açık kalır.",
            color = Palette.TextDim,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Ara") },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text("${settings.lockedApps.size} uygulama seçili", color = Palette.Amber, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val q = query.trim().lowercase(TR)
            val shown = if (q.isEmpty()) list else list.filter { it.label.lowercase(TR).contains(q) }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(shown, key = { it.pkg }) { app ->
                    val on = app.pkg in settings.lockedApps
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                vm.repo.updateSettings {
                                    it.copy(lockedApps = if (on) it.lockedApps - app.pkg else it.lockedApps + app.pkg)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (app.icon != null) {
                            Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        } else {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Locked))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, Modifier.weight(1f), color = Palette.Text, style = MaterialTheme.typography.bodyLarge)
                        Checkbox(
                            checked = on,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = Palette.Red, uncheckedColor = Palette.Outline),
                        )
                    }
                }
            }
        }
    }
}

// ======================================================================================
// Şifre bileşenleri
// ======================================================================================

@Composable
fun NewPasswordForm(vm: AppViewModel, onSaved: (recoveryCode: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column {
        Text(
            "Harf ve rakam kullanabilirsiniz (en az ${Password.MIN_LENGTH} karakter). Büyük/küçük harf fark eder.",
            color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        PasswordField("Yeni şifre", pw) { pw = it; error = null }
        Spacer(Modifier.height(8.dp))
        PasswordField("Şifre tekrar", pw2) { pw2 = it; error = null }
        ErrorText(error)
        Spacer(Modifier.height(12.dp))
        BigButton("Şifreyi kaydet", {
            val v = Password.validate(pw)
            when {
                v != null -> error = v
                pw != pw2 -> error = "Şifreler aynı değil"
                busy -> Unit
                else -> {
                    busy = true
                    scope.launch {
                        val code = vm.setPassword(pw)
                        busy = false
                        onSaved(code)
                    }
                }
            }
        }, enabled = pw.isNotEmpty() && pw2.isNotEmpty() && !busy)
    }
}

@Composable
fun RecoveryCodeView(code: String, onContinue: () -> Unit) {
    var noted by remember { mutableStateOf(false) }
    Column {
        Text("Kurtarma kodu", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        Text(
            "Şifrenizi unutursanız bu kodla yeni şifre belirleyebilirsiniz. Sunucu olmadığı için bu kod " +
                "başka hiçbir yerde saklanmaz. Lütfen bir kağıda yazın; çocuğun göremeyeceği bir yerde saklayın.",
            color = Palette.TextDim,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.SurfaceHigh)
                .border(2.dp, Palette.Amber, RoundedCornerShape(14.dp))
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(code, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Palette.Amber)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clickable { noted = !noted },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = noted, onCheckedChange = { noted = it })
            Text("Kodu bir yere yazdım", color = Palette.Text)
        }
        Spacer(Modifier.height(8.dp))
        BigButton("Devam", onContinue, enabled = noted, style = ButtonStyle.SUCCESS)
    }
}

@Composable
fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> !c.isWhitespace() }.take(Password.MAX_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) { Text(if (visible) "Gizle" else "Göster", color = Palette.Blue) }
        },
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Blue,
    unfocusedBorderColor = Palette.Outline,
    focusedLabelColor = Palette.Blue,
    unfocusedLabelColor = Palette.TextDim,
    cursorColor = Palette.Blue,
    focusedTextColor = Palette.Text,
    unfocusedTextColor = Palette.Text,
)

@Composable
fun ErrorText(error: String?) {
    if (error != null) {
        Spacer(Modifier.height(6.dp))
        Text(error, color = Palette.Red, style = MaterialTheme.typography.bodyMedium)
    }
}

// ======================================================================================
// Ortak iskelet
// ======================================================================================

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Text("‹", fontSize = 32.sp, color = Palette.Text) }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            content = content,
        )
    }
}

@Composable
fun SmallButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.SurfaceHigh)
            .border(2.dp, Palette.Outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Palette.Text, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Bazı üreticiler bu ayar ekranını kaldırmış olabilir; genel ayarlara düş
        runCatching {
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (e: SecurityException) {
        runCatching {
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
