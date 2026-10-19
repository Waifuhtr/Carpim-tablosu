package com.carpimtablosu.app.logic

import com.carpimtablosu.app.data.DayStat
import com.carpimtablosu.app.data.Progress
import java.time.LocalDate

/** İlerlemeyi güncelleyen saf fonksiyonlar. */
object ProgressOps {
    const val LESSON_XP = 5
    const val PERFECT_XP = 5
    /** Rapor için tutulan gün sayısı; eski günler silinir. */
    private const val KEEP_DAYS = 60L

    fun day(progress: Progress, date: LocalDate): DayStat = progress.days[date.toString()] ?: DayStat()

    private fun withDay(progress: Progress, date: LocalDate, f: (DayStat) -> DayStat): Progress {
        val key = date.toString()
        val cutoff = date.minusDays(KEEP_DAYS).toString()
        val days = progress.days.filterKeys { it > cutoff } + (key to f(day(progress, date)))
        return progress.copy(days = days)
    }

    /** Bir cevabı kaydeder: aralıklı tekrar, günlük sayılar, XP ve seri. */
    fun recordAnswer(
        progress: Progress, factKey: String, correct: Boolean, nowMs: Long, today: LocalDate, dailyGoal: Int,
    ): Progress {
        val facts = progress.facts + (factKey to Srs.onAnswer(progress.facts[factKey], correct, nowMs))
        var p = progress.copy(
            facts = facts,
            totalCorrect = progress.totalCorrect + if (correct) 1 else 0,
            totalWrong = progress.totalWrong + if (correct) 0 else 1,
            xp = progress.xp + if (correct) 1 else 0,
        )
        p = withDay(p, today) { d -> if (correct) d.copy(correct = d.correct + 1) else d.copy(wrong = d.wrong + 1) }
        return if (correct) updateStreak(p, today, dailyGoal) else p
    }

    /** Günlük hedefe ulaşıldığı gün seri bir artar; bir gün atlanırsa 1'den başlar. */
    fun updateStreak(progress: Progress, today: LocalDate, dailyGoal: Int): Progress {
        val todayKey = today.toString()
        if (progress.lastGoalDate == todayKey) return progress
        if (day(progress, today).correct < dailyGoal.coerceAtLeast(1)) return progress
        val continues = progress.lastGoalDate == today.minusDays(1).toString()
        val streak = if (continues) progress.streak + 1 else 1
        return progress.copy(streak = streak, bestStreak = maxOf(progress.bestStreak, streak), lastGoalDate = todayKey)
    }

    /** Ekranda gösterilecek seri: dün veya bugün hedef tutturulmadıysa seri bozulmuştur. */
    fun displayStreak(progress: Progress, today: LocalDate): Int {
        val last = progress.lastGoalDate ?: return 0
        return if (last == today.toString() || last == today.minusDays(1).toString()) progress.streak else 0
    }

    fun recordTime(progress: Progress, today: LocalDate, earnedSec: Long = 0L, spentSec: Long = 0L): Progress {
        if (earnedSec == 0L && spentSec == 0L) return progress
        return withDay(progress, today) { d ->
            d.copy(earnedSec = (d.earnedSec + earnedSec).coerceAtLeast(0L), spentSec = d.spentSec + spentSec)
        }
    }

    /** Ders bitince: düğüm ilerlemesi, XP, istatistik ve rozetler. */
    fun completeLesson(progress: Progress, state: LessonState): Progress {
        var p = Curriculum.complete(progress, state.kind, state.passed)
        if (state.kind == LessonKind.Sprint) {
            p = p.copy(sprintBest = maxOf(p.sprintBest, state.correct))
        } else if (state.correct + state.wrong > 0) {
            p = p.copy(
                lessonsDone = p.lessonsDone + 1,
                perfectLessons = p.perfectLessons + if (state.perfect) 1 else 0,
                xp = p.xp + LESSON_XP + if (state.perfect) PERFECT_XP else 0,
            )
        }
        return p
    }
}
