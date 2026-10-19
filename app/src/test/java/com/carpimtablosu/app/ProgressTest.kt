package com.carpimtablosu.app

import com.carpimtablosu.app.data.Backup
import com.carpimtablosu.app.data.DayStat
import com.carpimtablosu.app.data.FactStat
import com.carpimtablosu.app.data.Progress
import com.carpimtablosu.app.data.Repository
import com.carpimtablosu.app.data.Settings
import com.carpimtablosu.app.logic.Achievements
import com.carpimtablosu.app.logic.LessonKind
import com.carpimtablosu.app.logic.LessonState
import com.carpimtablosu.app.logic.Password
import com.carpimtablosu.app.logic.ProgressOps
import com.carpimtablosu.app.logic.QType
import com.carpimtablosu.app.logic.QuestionFactory
import com.carpimtablosu.app.logic.Srs
import com.carpimtablosu.app.logic.TurkishNumbers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class ProgressTest {
    private val d0 = LocalDate.of(2026, 10, 19)

    private fun answerCorrect(p: Progress, date: LocalDate, n: Int, goal: Int = 3): Progress {
        var x = p
        repeat(n) { x = ProgressOps.recordAnswer(x, "2x2", true, 0, date, goal) }
        return x
    }

    @Test fun streakStartsWhenGoalReached() {
        var p = answerCorrect(Progress(), d0, 2)
        assertEquals(0, p.streak)
        p = answerCorrect(p, d0, 1)
        assertEquals(1, p.streak)
        p = answerCorrect(p, d0, 5)
        assertEquals(1, p.streak) // aynı gün bir kez sayılır
    }

    @Test fun streakContinuesAndBreaks() {
        var p = answerCorrect(Progress(), d0, 3)
        p = answerCorrect(p, d0.plusDays(1), 3)
        assertEquals(2, p.streak)
        assertEquals(2, ProgressOps.displayStreak(p, d0.plusDays(2)))
        assertEquals(0, ProgressOps.displayStreak(p, d0.plusDays(3)))
        p = answerCorrect(p, d0.plusDays(4), 3)
        assertEquals(1, p.streak)
        assertEquals(2, p.bestStreak)
    }

    @Test fun wrongAnswersDoNotAdvanceStreak() {
        var p = Progress()
        repeat(10) { p = ProgressOps.recordAnswer(p, "3x3", false, 0, d0, 3) }
        assertEquals(0, p.streak)
        assertEquals(10, p.totalWrong)
        assertEquals(10, ProgressOps.day(p, d0).wrong)
    }

    @Test fun oldDaysArePruned() {
        var p = answerCorrect(Progress(), d0, 1)
        p = answerCorrect(p, d0.plusDays(100), 1)
        assertNull(p.days[d0.toString()])
        assertEquals(1, p.days.size)
    }

    @Test fun recordTimeAddsToDay() {
        var p = ProgressOps.recordTime(Progress(), d0, earnedSec = 60)
        p = ProgressOps.recordTime(p, d0, earnedSec = -60)
        p = ProgressOps.recordTime(p, d0, spentSec = 30)
        assertEquals(DayStat(earnedSec = 0, spentSec = 30), ProgressOps.day(p, d0))
    }

    @Test fun srsBoxes() {
        var s = Srs.onAnswer(null, true, 0)
        assertEquals(1, s.box)
        assertTrue(s.dueAt > 0)
        repeat(10) { s = Srs.onAnswer(s, true, 0) }
        assertEquals(Srs.MAX_BOX, s.box)
        s = Srs.onAnswer(s, false, 5)
        assertEquals(Srs.MAX_BOX - 2, s.box)
        assertEquals(5, s.dueAt)
        s = Srs.onAnswer(FactStat(box = 1), false, 0)
        assertEquals(0, s.box)
    }

    @Test fun weakFactsSortedByErrorRate() {
        val p = Progress(
            facts = mapOf(
                Srs.key(7, 8) to FactStat(correct = 1, wrong = 3, seen = true),
                Srs.key(6, 9) to FactStat(correct = 5, wrong = 1, seen = true),
                Srs.key(2, 2) to FactStat(correct = 9, seen = true),
            )
        )
        val weak = Srs.weakFacts(p, 5)
        assertEquals(2, weak.size)
        assertEquals(7 to 8, weak[0].first)
    }

    @Test fun lessonCompletionGivesXpAndBadges() {
        val f = QuestionFactory(Random(1))
        var s = LessonState.start(LessonKind.Node(2, 0), listOf(f.make(2, 3, QType.TYPE)), 0)
        s = s.submit(6, f).next(0)
        val p = ProgressOps.completeLesson(Progress(), s)
        assertEquals(1, p.lessonsDone)
        assertEquals(1, p.perfectLessons)
        assertEquals(ProgressOps.LESSON_XP + ProgressOps.PERFECT_XP, p.xp)
        assertEquals(1, p.completedNodes[2])
        val (withBadges, fresh) = Achievements.unlockNew(p)
        assertTrue(fresh.any { it.id == "first_lesson" })
        assertTrue(fresh.any { it.id == "perfect" })
        // İkinci kez aynı rozet verilmez
        assertTrue(Achievements.unlockNew(withBadges).second.isEmpty())
    }

    @Test fun sprintRecordsBestScore() {
        val f = QuestionFactory(Random(1))
        var s = LessonState.start(LessonKind.Sprint, emptyList(), 0, first = f.make(2, 2, QType.TYPE))
        s = s.submit(4, f).timeUp()
        val p = ProgressOps.completeLesson(Progress(sprintBest = 0), s)
        assertEquals(1, p.sprintBest)
        assertEquals(0, p.lessonsDone)
        assertEquals(5, ProgressOps.completeLesson(Progress(sprintBest = 5), s).sprintBest)
    }

    // ---------------- şifre ----------------

    @Test fun passwordRules() {
        assertNotEquals(null, Password.validate("abc12"))
        assertNull(Password.validate("abc123"))
        assertNull(Password.validate("şifreğüİ"))
        assertNull(Password.validate("123456"))
        assertNull(Password.validate("abcdef"))
        assertNotEquals(null, Password.validate("abc 123"))
        assertNotEquals(null, Password.validate("abc-123"))
        assertNotEquals(null, Password.validate("a".repeat(33)))
    }

    @Test fun passwordHashing() {
        val salt = Password.newSalt()
        val h = Password.hash("Anne2026", salt)
        assertTrue(Password.verify("Anne2026", salt, h))
        assertFalse(Password.verify("anne2026", salt, h))
        assertFalse(Password.verify("Anne2026", Password.newSalt(), h))
        assertArrayEquals(h, Password.decode(Password.encode(h)))
    }

    @Test fun recoveryCodeFormat() {
        val c = Password.newRecoveryCode()
        assertTrue(Regex("[A-Z2-9]{5}-[A-Z2-9]{5}").matches(c))
        assertEquals(10, Password.normalizeRecovery(c).length)
        assertEquals(Password.normalizeRecovery(c), Password.normalizeRecovery(" ${c.lowercase()} "))
    }

    @Test fun lockoutGrows() {
        assertEquals(0, Password.lockoutMs(4))
        assertEquals(30_000, Password.lockoutMs(5))
        assertEquals(60_000, Password.lockoutMs(6))
        assertEquals(15 * 60_000L, Password.lockoutMs(50))
    }

    // ---------------- ses ----------------

    @Test fun turkishNumbers() {
        assertEquals(42, TurkishNumbers.parse("42"))
        assertEquals(56, TurkishNumbers.parse("cevap 56"))
        assertEquals(42, TurkishNumbers.parse("kırk iki"))
        assertEquals(42, TurkishNumbers.parse("kırkiki"))
        assertEquals(64, TurkishNumbers.parse("altmış dört"))
        assertEquals(96, TurkishNumbers.parse("doksan altı"))
        assertEquals(120, TurkishNumbers.parse("yüz yirmi"))
        assertEquals(144, TurkishNumbers.parse("YÜZ KIRK DÖRT"))
        assertEquals(100, TurkishNumbers.parse("yüz"))
        assertEquals(6, TurkishNumbers.parse("altı"))
        assertEquals(10, TurkishNumbers.parse("on"))
        assertEquals(3, TurkishNumbers.parse("üç"))
        assertNull(TurkishNumbers.parse("bilmiyorum"))
        assertNull(TurkishNumbers.parse(""))
    }

    // ---------------- kayıt ----------------

    @Test fun serializationRoundTrip() {
        val p = Progress(
            childName = "Ali",
            facts = mapOf("7x8" to FactStat(box = 3, dueAt = 99, correct = 4, wrong = 1, seen = true)),
            completedNodes = mapOf(2 to 4, 3 to 1),
            days = mapOf("2026-10-19" to DayStat(5, 1, 300, 120)),
            achievements = setOf("perfect"),
        )
        val text = Repository.json.encodeToString(Backup.serializer(), Backup(progress = p))
        assertEquals(p, Repository.json.decodeFromString(Backup.serializer(), text).progress)
    }

    @Test fun unknownAndMissingFieldsAreTolerated() {
        val s = Repository.json.decodeFromString(Settings.serializer(), """{"lockedApps":["a"],"future":1}""")
        assertEquals(setOf("a"), s.lockedApps)
        assertEquals(60L, s.rewardSec)
    }
}
