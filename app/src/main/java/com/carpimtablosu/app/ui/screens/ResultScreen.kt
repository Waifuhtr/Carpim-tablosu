package com.carpimtablosu.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carpimtablosu.app.logic.BadgeIcon
import com.carpimtablosu.app.logic.Curriculum
import com.carpimtablosu.app.logic.LessonKind
import com.carpimtablosu.app.logic.ProgressOps
import com.carpimtablosu.app.ui.AppViewModel
import com.carpimtablosu.app.ui.Screen
import com.carpimtablosu.app.ui.components.BigButton
import com.carpimtablosu.app.ui.components.ButtonStyle
import com.carpimtablosu.app.ui.components.Glyph
import com.carpimtablosu.app.ui.components.Mascot
import com.carpimtablosu.app.ui.components.Mood
import com.carpimtablosu.app.ui.theme.Palette

@Composable
fun ResultScreen(vm: AppViewModel, result: Screen.Result) {
    val s = result.state
    val exam = Curriculum.isExam(s.kind)
    val sprint = s.kind == LessonKind.Sprint
    val (title, subtitle, mood) = when {
        sprint -> Triple("Süre doldu!", "60 saniyede ${s.correct} doğru yaptın.", Mood.HAPPY)
        exam && s.passed -> Triple("Sınavı geçtin!", examPassText(s.kind), Mood.HAPPY)
        exam -> Triple("Bu sefer olmadı", "En fazla ${Curriculum.BOSS_MAX_WRONG} yanlış hakkın vardı. Biraz pratikle tekrar dene!", Mood.ENCOURAGE)
        s.perfect -> Triple("Kusursuz ders!", "Hiç yanlış yapmadın.", Mood.HAPPY)
        else -> Triple("Ders tamamlandı!", "Yanlışlarını tekrar ederek öğrendin.", Mood.HAPPY)
    }
    val xp = if (sprint) s.correct else s.correct + ProgressOps.LESSON_XP + if (s.perfect) ProgressOps.PERFECT_XP else 0
    val net = s.timeEarnedSec - s.timeLostSec

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Mascot(mood, 200.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Palette.Amber, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultTile("XP", "+$xp", Palette.Amber, BadgeIcon.STAR, Modifier.weight(1f))
            ResultTile("Doğru", "${s.correct}", Palette.Green, BadgeIcon.CHECK, Modifier.weight(1f))
            ResultTile(
                "Süre", (if (net >= 0) "+" else "−") + formatDelta(kotlin.math.abs(net)),
                if (net >= 0) Palette.Blue else Palette.Red, BadgeIcon.TIMER, Modifier.weight(1f),
            )
        }
        if (s.wrong > 0) {
            Spacer(Modifier.height(8.dp))
            Text("${s.wrong} yanlış · kaybedilen süre ${formatDelta(s.timeLostSec)}", color = Palette.TextDim)
        }
        if (result.badges.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Yeni rozet!", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            Spacer(Modifier.height(8.dp))
            result.badges.forEach { b ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.Surface)
                        .border(2.dp, Palette.Amber, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(b.icon, Palette.Amber, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(b.title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(b.description, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        if (exam && !s.passed) {
            BigButton("Tekrar dene", { vm.goHome(); vm.startLesson(s.kind) }, style = ButtonStyle.PRIMARY)
            Spacer(Modifier.height(10.dp))
            BigButton("Ana sayfa", { vm.goHome() }, style = ButtonStyle.SECONDARY)
        } else {
            BigButton("Devam", { vm.goHome() }, style = ButtonStyle.SUCCESS)
            if (sprint) {
                Spacer(Modifier.height(10.dp))
                BigButton("Tekrar oyna", { vm.goHome(); vm.startLesson(LessonKind.Sprint) }, style = ButtonStyle.INFO)
            }
        }
    }
}

private fun examPassText(kind: LessonKind): String = when (kind) {
    is LessonKind.Skip -> "${Curriculum.tableName(kind.table)} tablosuna kadar her şey açıldı."
    is LessonKind.Node -> "${Curriculum.tableName(kind.table)} tablosunun ustası oldun!"
    else -> ""
}

@Composable
private fun ResultTile(label: String, value: String, color: Color, icon: BadgeIcon, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, color, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(icon, color, 26.dp)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
