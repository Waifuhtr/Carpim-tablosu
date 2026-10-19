package com.carpimtablosu.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carpimtablosu.app.logic.BadgeIcon
import com.carpimtablosu.app.logic.Hints
import com.carpimtablosu.app.logic.LessonKind
import com.carpimtablosu.app.logic.LessonState
import com.carpimtablosu.app.logic.QType
import com.carpimtablosu.app.logic.Question
import com.carpimtablosu.app.logic.TurkishNumbers
import com.carpimtablosu.app.ui.AppViewModel
import com.carpimtablosu.app.ui.LocalIsTv
import com.carpimtablosu.app.ui.LocalMedia
import com.carpimtablosu.app.ui.focusRing
import com.carpimtablosu.app.ui.initialFocus
import com.carpimtablosu.app.logic.AnswerResult
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.KeyEvent as AndroidKeyEvent
import com.carpimtablosu.app.ui.components.BigButton
import com.carpimtablosu.app.ui.components.ButtonStyle
import com.carpimtablosu.app.ui.components.Glyph
import com.carpimtablosu.app.ui.components.Mascot
import com.carpimtablosu.app.ui.components.Mood
import com.carpimtablosu.app.ui.components.PressableSurface
import com.carpimtablosu.app.ui.components.ProgressBar
import com.carpimtablosu.app.ui.components.SpeechBubble
import com.carpimtablosu.app.ui.components.StatChip
import com.carpimtablosu.app.ui.theme.Palette
import kotlinx.coroutines.delay

private val PRAISE = listOf("Harika!", "Süper!", "Aynen böyle!", "Mükemmel!", "Çok iyi!", "Tam isabet!")

@Composable
fun LessonScreen(vm: AppViewModel) {
    // Ekran geçiş animasyonu sırasında ders bitmiş olabilir
    val lesson = vm.lesson ?: return
    val q = lesson.current ?: return
    val settings by vm.settings.collectAsStateWithLifecycle()
    val time by vm.time.collectAsStateWithLifecycle()
    val media = LocalMedia.current
    val haptics = LocalHapticFeedback.current
    val sprint = lesson.kind == LessonKind.Sprint
    var askQuit by remember { mutableStateOf(false) }

    BackHandler { askQuit = true }

    // Soru gelince sesli oku
    LaunchedEffect(q.id) {
        if (settings.speakQuestions && !sprint) media.speaker.speak(q.spoken)
    }
    // Cevap sonrası ses/titreşim; hızlı turda kendiliğinden ilerle
    val answer = lesson.answer
    LaunchedEffect(q.id, answer != null) {
        if (answer == null) return@LaunchedEffect
        if (settings.sounds) if (answer.correct) media.sounds.correct() else media.sounds.wrong()
        if (settings.haptics) {
            haptics.performHapticFeedback(
                if (answer.correct) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress
            )
        }
        if (sprint) {
            delay(if (answer.correct) 350 else 900)
            vm.next()
        }
    }

    Column(Modifier.fillMaxSize()) {
        LessonTopBar(lesson, time.bankSec, sprint, onClose = { askQuit = true }, onTimeUp = { vm.timeUp() })

        if (LocalIsTv.current) {
            // TV: yatay ekran; solda soru, sağda cevaplar
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) { QuestionPanel(q, answer, sprint) }
                Spacer(Modifier.width(32.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(8.dp))
                    AnswerArea(vm, q, lesson, settings.voiceAnswers, sprint)
                }
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                QuestionPanel(q, answer, sprint)
                Spacer(Modifier.height(20.dp))
                AnswerArea(vm, q, lesson, settings.voiceAnswers, sprint)
            }
        }

        FeedbackArea(lesson, q, onNext = { vm.next() }, sprint = sprint)
    }

    if (askQuit) {
        AlertDialog(
            onDismissRequest = { askQuit = false },
            title = { Text("Dersten çıkılsın mı?") },
            text = { Text("Bu dersin ilerlemesi kaydedilmez. Kazandığın ve kaybettiğin süre olduğu gibi kalır.") },
            confirmButton = {
                TextButton(onClick = {
                    askQuit = false
                    media.speaker.stop()
                    vm.quitLesson()
                }) { Text("ÇIK", color = Palette.Red) }
            },
            dismissButton = { TextButton(onClick = { askQuit = false }) { Text("DEVAM ET") } },
            containerColor = Palette.Surface,
        )
    }
}

@Composable
private fun QuestionPanel(q: Question, answer: AnswerResult?, sprint: Boolean) {
    val media = LocalMedia.current
    Spacer(Modifier.height(8.dp))
    Text(q.instruction, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mascot(
            when {
                answer == null -> Mood.THINKING
                answer.correct -> Mood.HAPPY
                else -> Mood.SAD
            },
            104.dp,
        )
        Spacer(Modifier.width(8.dp))
        SpeechBubble(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    q.prompt,
                    fontSize = if (q.prompt.length > 11) 30.sp else 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Palette.Text,
                    modifier = Modifier.weight(1f),
                )
                if (media.speaker.available && !sprint) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .focusRing(RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.Blue)
                            .clickable { media.speaker.speak(q.spoken) },
                        contentAlignment = Alignment.Center,
                    ) { Text("🔊", fontSize = 18.sp) }
                }
            }
        }
    }
}

@Composable
private fun LessonTopBar(lesson: LessonState, bankSec: Long, sprint: Boolean, onClose: () -> Unit, onTimeUp: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .focusRing(RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("✕", fontSize = 22.sp, color = Palette.TextDim) }
        Spacer(Modifier.width(8.dp))
        if (sprint && lesson.deadline != null) {
            SprintClock(lesson.deadline, lesson.correct, onTimeUp, Modifier.weight(1f))
        } else {
            ProgressBar(lesson.progress, Palette.Green, Modifier.weight(1f))
        }
        Spacer(Modifier.width(12.dp))
        StatChip(BadgeIcon.TIMER, bankLabel(bankSec), if (bankSec > 0) Palette.Green else Palette.TextDim)
    }
}

@Composable
private fun SprintClock(deadline: Long, correct: Int, onTimeUp: () -> Unit, modifier: Modifier) {
    var left by remember { mutableLongStateOf(deadline - SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadline) {
        while (true) {
            left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0) {
                onTimeUp()
                break
            }
            delay(200)
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ProgressBar(left.coerceAtLeast(0) / 60_000f, if (left < 10_000) Palette.Red else Palette.Blue, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text("${(left.coerceAtLeast(0) + 999) / 1000} sn", color = Palette.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        StatChip(BadgeIcon.CHECK, "$correct", Palette.Green)
    }
}

@Composable
private fun AnswerArea(vm: AppViewModel, q: Question, lesson: LessonState, voice: Boolean, sprint: Boolean) {
    val answered = lesson.answer
    when (q.type) {
        QType.CHOICE -> ChoiceGrid(q, answered?.given, answered != null) { vm.submit(it) }
        QType.TRUE_FALSE -> TrueFalse(q, answered?.given, answered != null) { vm.submit(it) }
        else -> KeypadAnswer(vm, q, answered != null, voice && !sprint)
    }
}

/** Seçenekli soru: dokununca hemen kontrol edilir (Duolingo'nun hızlı modu gibi). */
@Composable
private fun ChoiceGrid(q: Question, given: Int?, locked: Boolean, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        q.options.chunked(2).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { c, opt ->
                    OptionButton(
                        text = "$opt",
                        state = optionState(opt, q.answer, given, locked),
                        enabled = !locked,
                        modifier = Modifier.weight(1f),
                        focusModifier = Modifier.initialFocus(LocalIsTv.current && !locked && r == 0 && c == 0, q.id),
                    ) { onPick(opt) }
                }
            }
        }
    }
}

@Composable
private fun TrueFalse(q: Question, given: Int?, locked: Boolean, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(1 to "Doğru", 0 to "Yanlış").forEach { (v, label) ->
            OptionButton(
                text = label,
                state = optionState(v, q.answer, given, locked),
                enabled = !locked,
                modifier = Modifier.weight(1f),
                focusModifier = Modifier.initialFocus(LocalIsTv.current && !locked && v == 1, q.id),
            ) { onPick(v) }
        }
    }
}

private enum class OptState { NORMAL, RIGHT, WRONG, FADED }

private fun optionState(value: Int, answer: Int, given: Int?, locked: Boolean): OptState = when {
    !locked -> OptState.NORMAL
    value == answer -> OptState.RIGHT
    value == given -> OptState.WRONG
    else -> OptState.FADED
}

@Composable
private fun OptionButton(
    text: String,
    state: OptState,
    enabled: Boolean,
    modifier: Modifier,
    focusModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (bg, shadow, border, fg) = when (state) {
        OptState.NORMAL -> Quad(Palette.Surface, Palette.Outline, Palette.Outline, Palette.Text)
        // Opak olmalı: yarı saydam olursa alttaki gölge katmanı görünür
        OptState.RIGHT -> Quad(Color(0xFF1B4536), Palette.GreenDark, Palette.Green, Palette.Green)
        OptState.WRONG -> Quad(Color(0xFF4A2230), Palette.RedDark, Palette.Red, Palette.Red)
        OptState.FADED -> Quad(Palette.Surface, Palette.Outline, Palette.Outline, Palette.TextDim)
    }
    PressableSurface(
        onClick = onClick,
        color = bg,
        shadow = shadow,
        border = border,
        enabled = enabled,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 22.dp),
        focusModifier = focusModifier,
    ) {
        Text(text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}

private data class Quad(val a: Color, val b: Color, val c: Color, val d: Color)

/** Tuş takımıyla cevap. Sistem klavyesi kullanılmaz; sadece rakam girilebilir. */
@Composable
private fun KeypadAnswer(vm: AppViewModel, q: Question, locked: Boolean, voiceEnabled: Boolean) {
    val context = LocalContext.current
    val media = LocalMedia.current
    var typed by remember(q.id) { mutableStateOf("") }
    var listening by remember(q.id) { mutableStateOf(false) }
    var voiceMsg by remember(q.id) { mutableStateOf<String?>(null) }
    val recognizerAvailable = remember { media.listener.isAvailable }
    val canVoice = voiceEnabled && recognizerAvailable

    fun startListening() {
        listening = true
        voiceMsg = "Dinliyorum…"
        media.listener.start(
            onResult = { results ->
                val n = results.firstNotNullOfOrNull { TurkishNumbers.parse(it) }
                if (n != null && n in 0..999) {
                    typed = n.toString()
                    voiceMsg = "Duyduğum: $n"
                } else {
                    voiceMsg = "Anlayamadım, tekrar dene"
                }
            },
            onError = { voiceMsg = it },
            onEnd = { listening = false },
        )
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else voiceMsg = "Mikrofon izni verilmedi"
    }

    val answered = vm.lesson?.answer
    val boxColor = when {
        answered == null -> Palette.Outline
        answered.correct -> Palette.Green
        else -> Palette.Red
    }

    val isTv = LocalIsTv.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            // Kumanda/klavyedeki rakam tuşları doğrudan yazar
            .onPreviewKeyEvent { e ->
                if (locked || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = e.nativeKeyEvent.keyCode
                val digit = when (code) {
                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> code - AndroidKeyEvent.KEYCODE_0
                    in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> code - AndroidKeyEvent.KEYCODE_NUMPAD_0
                    else -> -1
                }
                when {
                    digit >= 0 -> {
                        if (typed.length < 3) typed = (typed + digit).trimStart('0').ifEmpty { "0" }
                        true
                    }
                    code == AndroidKeyEvent.KEYCODE_DEL -> {
                        typed = typed.dropLast(1)
                        true
                    }
                    else -> false
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (isTv) 60.dp else 72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Surface)
                .border(3.dp, boxColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                typed.ifEmpty { "?" },
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (typed.isEmpty()) Palette.TextDim else Palette.Text,
            )
        }
        Text(
            voiceMsg ?: " ",
            color = Palette.TextDim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(if (canVoice) "🎤" else "", "0", "⌫"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val isMic = key == "🎤"
                            PressableSurface(
                                onClick = {
                                    when (key) {
                                        "⌫" -> typed = typed.dropLast(1)
                                        "🎤" -> if (!listening) {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                        else -> if (typed.length < 3) typed = (typed + key).trimStart('0').ifEmpty { "0" }
                                    }
                                },
                                color = if (isMic && listening) Palette.Blue else Palette.Surface,
                                shadow = Palette.Outline,
                                border = Palette.Outline,
                                enabled = !locked,
                                depth = 3.dp,
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = if (isTv) 8.dp else 12.dp),
                                focusModifier = Modifier.initialFocus(isTv && !locked && key == "1", q.id),
                            ) {
                                Text(key, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!locked) {
            BigButton(
                "Kontrol et",
                onClick = {
                    media.listener.stop()
                    typed.toIntOrNull()?.let { vm.submit(it) }
                },
                style = ButtonStyle.SUCCESS,
                enabled = typed.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun FeedbackArea(lesson: LessonState, q: Question, onNext: () -> Unit, sprint: Boolean) {
    val answer = lesson.answer
    AnimatedVisibility(visible = answer != null, enter = slideInVertically { it }) {
        if (answer == null) return@AnimatedVisibility
        val ok = answer.correct
        val praise = remember(q.id) { PRAISE.random() }
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (ok) Color(0xFF173D2E) else Color(0xFF4A1F28))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ok) praise else "Doğru cevap: ${q.solution}",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (ok) Palette.Green else Palette.Red,
                    modifier = Modifier.weight(1f),
                )
                val delta = answer.timeDeltaSec
                if (delta != 0L) {
                    Text(
                        (if (delta > 0) "+" else "−") + formatDelta(kotlin.math.abs(delta)),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (delta > 0) Palette.Green else Palette.Red,
                    )
                }
            }
            if (!ok) {
                Spacer(Modifier.height(6.dp))
                Text("İpucu: " + Hints.forFact(q.a, q.b), color = Palette.Text, style = MaterialTheme.typography.bodyLarge)
            }
            if (ok && answer.capReached) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Bugünkü süre kazanma sınırına ulaştın. XP kazanmaya devam!",
                    color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!sprint) {
                Spacer(Modifier.height(14.dp))
                BigButton(
                    "Devam", onNext,
                    style = if (ok) ButtonStyle.SUCCESS else ButtonStyle.DANGER,
                    autoFocus = LocalIsTv.current,
                    focusKey = q.id,
                )
            }
        }
    }
}

/** Üst çubuktaki kalan süre: 1 dakikanın altında saniye gösterilir ki "0 dk" yanıltmasın. */
fun bankLabel(sec: Long): String = if (sec >= 60) "${sec / 60} dk" else "$sec sn"

fun formatDelta(sec: Long): String =
    if (sec % 60 == 0L) "${sec / 60} dk" else if (sec < 60) "$sec sn" else "${sec / 60} dk ${sec % 60} sn"
