package com.carpimtablosu.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carpimtablosu.app.data.Progress
import com.carpimtablosu.app.data.Settings
import com.carpimtablosu.app.logic.Achievements
import com.carpimtablosu.app.logic.BadgeIcon
import com.carpimtablosu.app.logic.Curriculum
import com.carpimtablosu.app.logic.LessonKind
import com.carpimtablosu.app.logic.ProgressOps
import com.carpimtablosu.app.logic.Srs
import com.carpimtablosu.app.ui.AppViewModel
import com.carpimtablosu.app.ui.HomeTab
import com.carpimtablosu.app.ui.Screen
import com.carpimtablosu.app.ui.components.BigButton
import com.carpimtablosu.app.ui.components.ButtonStyle
import com.carpimtablosu.app.ui.components.Card
import com.carpimtablosu.app.ui.components.ChoiceChips
import com.carpimtablosu.app.ui.components.Glyph
import com.carpimtablosu.app.ui.components.Mascot
import com.carpimtablosu.app.ui.components.Mood
import com.carpimtablosu.app.ui.components.ProgressBar
import com.carpimtablosu.app.ui.components.SectionTitle
import com.carpimtablosu.app.ui.components.SettingSwitch
import com.carpimtablosu.app.ui.components.StatChip
import com.carpimtablosu.app.ui.theme.Palette
import java.time.format.TextStyle
import java.util.Locale

private val TR = Locale.forLanguageTag("tr")

@Composable
fun HomeScreen(vm: AppViewModel) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val time by vm.time.collectAsStateWithLifecycle()
    val today = vm.repo.today()

    Column(Modifier.fillMaxSize()) {
        TopBar(
            streak = ProgressOps.displayStreak(progress, today),
            xp = progress.xp,
            minutes = time.bankSec / 60,
            onParent = { vm.push(Screen.ParentGate) },
        )
        Box(Modifier.weight(1f)) {
            when (vm.tab) {
                HomeTab.PATH -> PathTab(vm, progress, settings)
                HomeTab.PRACTICE -> PracticeTab(vm, progress)
                HomeTab.BADGES -> BadgesTab(progress)
                HomeTab.PROFILE -> ProfileTab(vm, progress, settings)
            }
        }
        BottomNav(vm.tab) { vm.tab = it }
    }
}

@Composable
private fun TopBar(streak: Int, xp: Int, minutes: Long, onParent: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatChip(BadgeIcon.FLAME, "$streak", if (streak > 0) Palette.Flame else Palette.TextDim)
        Spacer(Modifier.width(18.dp))
        StatChip(BadgeIcon.STAR, "$xp", Palette.Amber)
        Spacer(Modifier.width(18.dp))
        StatChip(BadgeIcon.TIMER, "$minutes dk", if (minutes > 0) Palette.Green else Palette.TextDim)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Surface)
                .clickable(onClick = onParent),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.carpimtablosu.app.R.drawable.ic_lock),
                contentDescription = "Ebeveyn paneli",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Palette.TextDim),
                modifier = Modifier.size(20.dp),
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Palette.Surface)
    )
}

@Composable
private fun BottomNav(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Palette.Surface)
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        val items = listOf(
            Triple(HomeTab.PATH, BadgeIcon.BOOK, "Yol"),
            Triple(HomeTab.PRACTICE, BadgeIcon.BULB, "Pratik"),
            Triple(HomeTab.BADGES, BadgeIcon.TROPHY, "Rozetler"),
            Triple(HomeTab.PROFILE, BadgeIcon.STAR, "Profil"),
        )
        items.forEach { (tab, icon, label) ->
            val on = tab == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(2.dp, if (on) Palette.Blue else Color.Transparent, RoundedCornerShape(14.dp))
                    .background(if (on) Palette.Blue.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Glyph(icon, if (on) Palette.Blue else Palette.TextDim, 26.dp)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (on) Palette.Blue else Palette.TextDim)
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// Yol haritası
// --------------------------------------------------------------------------------------

private enum class NodeState { DONE, CURRENT, OPEN, LOCKED }

@Composable
private fun PathTab(vm: AppViewModel, progress: Progress, settings: Settings) {
    val listState = rememberLazyListState()
    val current = Curriculum.currentNode(progress)
    val tables = Curriculum.TABLES.toList()
    var skipTable by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val t = current?.first ?: return@LaunchedEffect
        // Başlıkta günlük hedef kartı var (index 0); her ünite 2 öğe
        listState.scrollToItem(1 + tables.indexOf(t) * 2)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
    ) {
        item(key = "goal") { DailyGoalCard(progress, settings, vm) }
        tables.forEachIndexed { i, t ->
            item(key = "unit$t") { UnitHeader(i, t, progress) }
            item(key = "nodes$t") {
                UnitNodes(i, t, progress, current) { node, state ->
                    if (state == NodeState.LOCKED) skipTable = t
                    else vm.startLesson(LessonKind.Node(t, node))
                }
            }
        }
        item(key = "end") {
            if (current == null) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Mascot(Mood.HAPPY, 140.dp)
                    Text(
                        "Tüm tabloları bitirdin! Pratik bölümünde ustalığını koru.",
                        color = Palette.Text, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    skipTable?.let { t ->
        AlertDialog(
            onDismissRequest = { skipTable = null },
            title = { Text("${Curriculum.tableName(t)} tablosuna atla?") },
            text = {
                Text(
                    "Bu tabloyu zaten biliyorsan atlama sınavına gir. ${Curriculum.BOSS_SIZE} sorudan en fazla " +
                        "${Curriculum.BOSS_MAX_WRONG} yanlış yaparsan ${Curriculum.tableName(t)} tablosu ve öncekiler tamamlanır."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    skipTable = null
                    vm.startLesson(LessonKind.Skip(t))
                }) { Text("SINAVA GİR") }
            },
            dismissButton = { TextButton(onClick = { skipTable = null }) { Text("VAZGEÇ") } },
            containerColor = Palette.Surface,
        )
    }
}

@Composable
private fun DailyGoalCard(progress: Progress, settings: Settings, vm: AppViewModel) {
    val todayCorrect = ProgressOps.day(progress, vm.repo.today()).correct
    val goal = settings.dailyGoal
    val name = progress.childName.ifBlank { null }
    Card(Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (name != null) "Merhaba $name!" else "Günlük hedef",
                    style = MaterialTheme.typography.titleMedium, color = Palette.Text,
                )
                Text(
                    if (todayCorrect >= goal) "Bugünkü hedef tamam, serin devam ediyor!"
                    else "Bugün $todayCorrect / $goal doğru",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                ProgressBar(todayCorrect.toFloat() / goal, Palette.Amber, height = 12.dp)
            }
            Spacer(Modifier.width(12.dp))
            Glyph(BadgeIcon.FLAME, if (todayCorrect >= goal) Palette.Flame else Palette.Locked, 40.dp)
        }
    }
}

@Composable
private fun UnitHeader(index: Int, table: Int, progress: Progress) {
    val (color, dark) = Palette.Units[index % Palette.Units.size]
    val mastery = Srs.mastery(progress, table)
    val done = Curriculum.isTableDone(progress, table)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(dark)
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .padding(16.dp)
    ) {
        Column {
            Text("ÜNİTE ${index + 1}", style = MaterialTheme.typography.labelLarge, color = Color.Black.copy(alpha = 0.55f))
            Text(
                "${Curriculum.tableName(table)} tablosu",
                style = MaterialTheme.typography.headlineSmall, color = Color(0xFF0D1426),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (done) "Tamamlandı · ustalık %${(mastery * 100).toInt()}"
                else "$table × 1'den $table × 10'a",
                style = MaterialTheme.typography.bodyMedium, color = Color.Black.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun UnitNodes(
    index: Int,
    table: Int,
    progress: Progress,
    current: Pair<Int, Int>?,
    onClick: (Int, NodeState) -> Unit,
) {
    val colors = Palette.Units[index % Palette.Units.size]
    val offsets = listOf(0.dp, 56.dp, 0.dp, (-56).dp)
    val mascotLeft = index % 2 == 0
    val mood = when {
        Curriculum.isTableDone(progress, table) -> Mood.HAPPY
        current?.first == table -> Mood.ENCOURAGE
        else -> Mood.THINKING
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Mascot(
            mood, 120.dp,
            Modifier
                .align(if (mascotLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .offset(x = if (mascotLeft) (-4).dp else 4.dp),
        )
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            for (n in 0 until Curriculum.NODES_PER_TABLE) {
                val state = when {
                    Curriculum.isDone(progress, table, n) -> NodeState.DONE
                    current == table to n -> NodeState.CURRENT
                    Curriculum.isUnlocked(progress, table, n) -> NodeState.OPEN
                    else -> NodeState.LOCKED
                }
                // Maskota çarpmasın diye düğümler maskotun karşı tarafına kayar
                val x = if (mascotLeft) offsets[n] + 20.dp else -offsets[n] - 20.dp
                Column(
                    Modifier.offset(x = x).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PathNode(state, n == Curriculum.BOSS_NODE, colors) { onClick(n, state) }
                    Text(
                        Curriculum.nodeTitle(n),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state == NodeState.LOCKED) Palette.TextDim else Palette.Text,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PathNode(state: NodeState, boss: Boolean, colors: Pair<Color, Color>, onClick: () -> Unit) {
    val (top, shadow) = when (state) {
        NodeState.DONE -> Palette.Amber to Palette.AmberDark
        NodeState.CURRENT, NodeState.OPEN -> colors
        NodeState.LOCKED -> Palette.Locked to Palette.LockedDark
    }
    val icon = when {
        state == NodeState.DONE -> BadgeIcon.CHECK
        boss -> BadgeIcon.TROPHY
        else -> BadgeIcon.STAR
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bounce = if (state == NodeState.CURRENT) {
        val t = rememberInfiniteTransition(label = "bounce")
        t.animateFloat(0f, -6f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "y").value
    } else 0f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (state == NodeState.CURRENT) {
            Box(
                Modifier
                    .offset(y = bounce.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Text)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("BAŞLA", style = MaterialTheme.typography.labelLarge, color = shadow)
            }
            Spacer(Modifier.height(6.dp))
        }
        Box(Modifier.size(width = 78.dp, height = 84.dp)) {
            Box(
                Modifier
                    .offset(y = 6.dp)
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(shadow)
            )
            Box(
                Modifier
                    .offset(y = if (pressed) 6.dp else 0.dp)
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(top)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(
                    if (state == NodeState.LOCKED) BadgeIcon.LOCK else icon,
                    if (state == NodeState.LOCKED) Palette.TextDim else Color.White,
                    36.dp,
                )
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// Pratik
// --------------------------------------------------------------------------------------

@Composable
private fun PracticeTab(vm: AppViewModel, progress: Progress) {
    val due = Srs.dueFacts(progress, System.currentTimeMillis()).size
    val weak = Srs.weakFacts(progress, 6)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Akıllı tekrar", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                    Text(
                        if (due > 0) "$due işlemin tekrar zamanı geldi" else "Zorlandığın işlemleri pekiştir",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                    )
                    Spacer(Modifier.height(12.dp))
                    BigButton("Başla", { vm.startLesson(LessonKind.Review) }, style = ButtonStyle.SUCCESS)
                }
                Mascot(Mood.THINKING, 100.dp)
            }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hızlı tur", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                    Text(
                        "60 saniyede kaç doğru yapabilirsin? En iyi: ${progress.sprintBest}",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                    )
                    Spacer(Modifier.height(12.dp))
                    BigButton("Başla", { vm.startLesson(LessonKind.Sprint) }, style = ButtonStyle.INFO)
                }
                Spacer(Modifier.width(12.dp))
                Glyph(BadgeIcon.TIMER, Palette.Blue, 64.dp)
            }
        }
        SectionTitle("Tablo seç")
        Curriculum.TABLES.toList().chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    val m = Srs.mastery(progress, t)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Palette.Surface)
                            .border(2.dp, if (m >= 0.8f) Palette.Amber else Palette.Outline, RoundedCornerShape(16.dp))
                            .clickable { vm.startLesson(LessonKind.TablePractice(t)) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("×$t", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                            Text("%${(m * 100).toInt()}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (weak.isNotEmpty()) {
            SectionTitle("Zorlandıkların")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    weak.forEach { (f, s) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${f.first} × ${f.second} = ${f.first * f.second}",
                                style = MaterialTheme.typography.titleMedium, color = Palette.Text,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${s.wrong} yanlış", color = Palette.Red, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// Rozetler
// --------------------------------------------------------------------------------------

@Composable
private fun BadgesTab(progress: Progress) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val earned = Achievements.ALL.count { it.id in progress.achievements }
        Text("Rozetler", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
        Text("${Achievements.ALL.size} rozetten $earned tanesini kazandın", color = Palette.TextDim)
        Achievements.ALL.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    val got = a.id in progress.achievements
                    val p = a.progress(progress)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Palette.Surface)
                            .border(2.dp, if (got) Palette.Amber else Palette.Outline, RoundedCornerShape(16.dp))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (got) Palette.Amber else Palette.Locked),
                            contentAlignment = Alignment.Center,
                        ) { Glyph(a.icon, if (got) Color(0xFF231900) else Palette.TextDim, 30.dp) }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            a.title, style = MaterialTheme.typography.titleMedium,
                            color = if (got) Palette.Text else Palette.TextDim, textAlign = TextAlign.Center, maxLines = 1,
                        )
                        Text(
                            a.description, style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextDim, textAlign = TextAlign.Center, minLines = 2, maxLines = 2,
                        )
                        if (!got) {
                            Spacer(Modifier.height(6.dp))
                            ProgressBar(p, Palette.Blue, height = 8.dp)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// Profil
// --------------------------------------------------------------------------------------

@Composable
private fun ProfileTab(vm: AppViewModel, progress: Progress, settings: Settings) {
    val today = vm.repo.today()
    val total = progress.totalCorrect + progress.totalWrong
    val accuracy = if (total == 0) 0 else progress.totalCorrect * 100 / total
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(Mood.ENCOURAGE, 96.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(progress.childName.ifBlank { "Profil" }, style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                Text("${progress.xp} XP · ${progress.lessonsDone} ders", color = Palette.TextDim)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("En uzun seri", "${progress.bestStreak} gün", BadgeIcon.FLAME, Palette.Flame, Modifier.weight(1f))
            StatTile("Doğruluk", "%$accuracy", BadgeIcon.CHECK, Palette.Green, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Toplam doğru", "${progress.totalCorrect}", BadgeIcon.STAR, Palette.Amber, Modifier.weight(1f))
            StatTile("Hızlı tur rekoru", "${progress.sprintBest}", BadgeIcon.TIMER, Palette.Blue, Modifier.weight(1f))
        }

        SectionTitle("Son 7 gün")
        Card {
            val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
            val values = days.map { ProgressOps.day(progress, it).correct }
            val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().height(140.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEachIndexed { i, d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("${values[i]}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                        Box(
                            Modifier
                                .padding(horizontal = 6.dp)
                                .fillMaxWidth()
                                .height((90 * values[i] / max).coerceAtLeast(4).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (d == today) Palette.Amber else Palette.Blue)
                        )
                        Text(
                            d.dayOfWeek.getDisplayName(TextStyle.SHORT, TR),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (d == today) Palette.Amber else Palette.TextDim,
                        )
                    }
                }
            }
        }

        SectionTitle("Tablo ustalığı")
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Curriculum.TABLES.forEach { t ->
                    val m = Srs.mastery(progress, t)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("×$t", Modifier.width(44.dp), color = Palette.Text, style = MaterialTheme.typography.titleMedium)
                        ProgressBar(m, if (m >= 0.8f) Palette.Amber else Palette.Green, Modifier.weight(1f), height = 12.dp)
                        Text("%${(m * 100).toInt()}", Modifier.width(52.dp), color = Palette.TextDim, textAlign = TextAlign.End)
                    }
                }
            }
        }

        SectionTitle("Ayarlarım")
        Card {
            Column {
                Text("Günlük hedef (doğru cevap)", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(listOf(10 to "10", 20 to "20", 30 to "30", 50 to "50"), settings.dailyGoal) { g ->
                    vm.repo.updateSettings { it.copy(dailyGoal = g) }
                }
                Spacer(Modifier.height(8.dp))
                SettingSwitch("Soruları sesli oku", "Soru ekrana gelince okunur", settings.speakQuestions) { v ->
                    vm.repo.updateSettings { it.copy(speakQuestions = v) }
                }
                SettingSwitch("Sesli cevap", "Mikrofona cevabı söyleyebilirsin", settings.voiceAnswers) { v ->
                    vm.repo.updateSettings { it.copy(voiceAnswers = v) }
                }
                SettingSwitch("Ses efektleri", null, settings.sounds) { v ->
                    vm.repo.updateSettings { it.copy(sounds = v) }
                }
                SettingSwitch("Titreşim", null, settings.haptics) { v ->
                    vm.repo.updateSettings { it.copy(haptics = v) }
                }
            }
        }
        BigButton("Ebeveyn paneli", { vm.push(Screen.ParentGate) }, style = ButtonStyle.SECONDARY)
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: BadgeIcon, tint: Color, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface)
            .border(2.dp, Palette.Outline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, tint, 28.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, maxLines = 1)
        }
    }
}
