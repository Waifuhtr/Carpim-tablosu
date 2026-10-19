package com.carpimtablosu.app

import com.carpimtablosu.app.data.FactStat
import com.carpimtablosu.app.data.Progress
import com.carpimtablosu.app.logic.Curriculum
import com.carpimtablosu.app.logic.Hints
import com.carpimtablosu.app.logic.LessonKind
import com.carpimtablosu.app.logic.LessonState
import com.carpimtablosu.app.logic.QType
import com.carpimtablosu.app.logic.QuestionFactory
import com.carpimtablosu.app.logic.Srs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LessonTest {

    @Test fun choiceOptionsAreValidForEveryFact() {
        repeat(5) { seed ->
            val f = QuestionFactory(Random(seed))
            for (a in Curriculum.TABLES) for (b in Curriculum.FACTORS) {
                val q = f.make(a, b, QType.CHOICE)
                assertEquals("$a x $b", 4, q.options.size)
                assertEquals(4, q.options.toSet().size)
                assertEquals(1, q.options.count { it == a * b })
                assertTrue(q.options.all { it > 0 })
            }
        }
    }

    @Test fun answersMatchQuestionType() {
        val f = QuestionFactory(Random(1))
        assertEquals(56, f.make(7, 8, QType.TYPE).answer)
        assertEquals(8, f.make(7, 8, QType.MISSING).answer)
        assertEquals(8, f.make(7, 8, QType.DIVISION).answer)
        assertEquals("56 ÷ 7 = ?", f.make(7, 8, QType.DIVISION).prompt)
        assertEquals("7 × ? = 56", f.make(7, 8, QType.MISSING).prompt)
        repeat(50) {
            val q = f.make(6, 7, QType.TRUE_FALSE)
            assertEquals(if (q.shown == 42) 1 else 0, q.answer)
            assertTrue(q.shown > 0)
        }
    }

    @Test fun distractorsNeverEqualAnswer() {
        val f = QuestionFactory(Random(7))
        for (a in Curriculum.TABLES) for (b in Curriculum.FACTORS) {
            val d = f.distractors(a, b)
            assertTrue(d.size >= 3)
            assertFalse(d.contains(a * b))
        }
    }

    @Test fun unlockOrderIsSequential() {
        val p = Progress()
        assertTrue(Curriculum.isUnlocked(p, 2, 0))
        assertFalse(Curriculum.isUnlocked(p, 2, 1))
        assertFalse(Curriculum.isUnlocked(p, 3, 0))
        assertEquals(2 to 0, Curriculum.currentNode(p))

        val p2 = Curriculum.complete(p, LessonKind.Node(2, 0), passed = true)
        assertTrue(Curriculum.isUnlocked(p2, 2, 1))
        assertEquals(2 to 1, Curriculum.currentNode(p2))

        // Usta sınavı geçilmezse tablo bitmez
        val p3 = p2.copy(completedNodes = mapOf(2 to 3))
        assertEquals(p3, Curriculum.complete(p3, LessonKind.Node(2, 3), passed = false))
        val p4 = Curriculum.complete(p3, LessonKind.Node(2, 3), passed = true)
        assertTrue(Curriculum.isTableDone(p4, 2))
        assertTrue(Curriculum.isUnlocked(p4, 3, 0))
    }

    @Test fun replayingOldNodeDoesNotGoBackwards() {
        val p = Progress(completedNodes = mapOf(2 to 3))
        assertEquals(3, Curriculum.completed(Curriculum.complete(p, LessonKind.Node(2, 0), true), 2))
    }

    @Test fun skipTestCompletesEarlierTables() {
        val p = Curriculum.complete(Progress(), LessonKind.Skip(5), passed = true)
        for (t in 2..5) assertTrue(Curriculum.isTableDone(p, t))
        assertEquals(6 to 0, Curriculum.currentNode(p))
        assertEquals(Progress(), Curriculum.complete(Progress(), LessonKind.Skip(5), passed = false))
    }

    @Test fun allDoneHasNoCurrentNode() {
        val p = Progress(completedNodes = Curriculum.TABLES.associateWith { Curriculum.NODES_PER_TABLE })
        assertNull(Curriculum.currentNode(p))
        assertEquals(Curriculum.TABLES.toList(), Curriculum.unlockedTables(p))
    }

    @Test fun lessonsHaveExpectedShape() {
        val f = QuestionFactory(Random(3))
        val p = Progress()
        val intro = Curriculum.buildLesson(LessonKind.Node(4, 0), p, 0, f, Random(3))
        assertEquals(Curriculum.LESSON_SIZE, intro.size)
        assertTrue(intro.all { it.a == 4 && it.b in 1..5 })
        val second = Curriculum.buildLesson(LessonKind.Node(4, 1), p, 0, f, Random(3))
        assertTrue(second.all { it.a == 4 && it.b in 6..10 })
        val boss = Curriculum.buildLesson(LessonKind.Node(4, 3), p, 0, f, Random(3))
        assertEquals(Curriculum.BOSS_SIZE, boss.size)
        assertTrue(boss.none { it.type == QType.TRUE_FALSE })
        val skip = Curriculum.buildLesson(LessonKind.Skip(6), p, 0, f, Random(3))
        assertTrue(skip.all { it.a in 2..6 })
        val review = Curriculum.buildLesson(LessonKind.Review, p, 0, f, Random(3))
        assertEquals(Curriculum.LESSON_SIZE, review.size)
        assertTrue(Curriculum.buildLesson(LessonKind.Sprint, p, 0, f).isEmpty())
    }

    @Test fun reviewPrefersDueFacts() {
        val now = 10_000_000L
        val facts = mapOf(
            Srs.key(7, 8) to FactStat(box = 0, dueAt = now - 1, seen = true),
            Srs.key(6, 7) to FactStat(box = 3, dueAt = now + 99999, seen = true),
        )
        val qs = Curriculum.buildLesson(LessonKind.Review, Progress(facts = facts), now, QuestionFactory(Random(1)), Random(1))
        assertTrue(qs.any { it.a == 7 && it.b == 8 })
    }

    @Test fun noSameFactTwiceInARow() {
        val f = QuestionFactory(Random(9))
        val list = listOf(f.make(2, 2, QType.TYPE), f.make(2, 2, QType.CHOICE), f.make(2, 3, QType.TYPE))
        val out = Curriculum.noBackToBack(list)
        for (i in 1 until out.size) assertTrue(out[i].factKey != out[i - 1].factKey)
    }

    @Test fun wrongAnswerIsRequeuedOnce() {
        val f = QuestionFactory(Random(1))
        val q1 = f.make(3, 4, QType.TYPE)
        val q2 = f.make(3, 5, QType.TYPE)
        var s = LessonState.start(LessonKind.Node(3, 0), listOf(q1, q2), now = 0)
        s = s.submit(99, f)
        assertFalse(s.answer!!.correct)
        assertEquals(2, s.queue.size) // q2 + tekrar
        s = s.next(0)
        assertEquals(q2.id, s.current!!.id)
        s = s.submit(15, f).next(0)
        assertEquals(q1.factKey, s.current!!.factKey)
        // Tekrar sorusunda yine yanlış: ikinci kez kuyruğa girmez
        s = s.submit(1, f)
        assertTrue(s.queue.isEmpty())
        s = s.next(0)
        assertTrue(s.isFinished)
        assertEquals(1, s.correct)
        assertEquals(2, s.wrong)
        assertTrue(s.passed)
    }

    @Test fun doubleSubmitIsIgnored() {
        val f = QuestionFactory(Random(1))
        var s = LessonState.start(LessonKind.Review, listOf(f.make(2, 2, QType.TYPE)), 0)
        s = s.submit(4, f)
        val again = s.submit(5, f)
        assertEquals(s, again)
        assertEquals(1, s.correct)
    }

    @Test fun nextBeforeAnsweringDoesNothing() {
        val f = QuestionFactory(Random(1))
        val s = LessonState.start(LessonKind.Review, listOf(f.make(2, 2, QType.TYPE), f.make(2, 3, QType.TYPE)), 0)
        assertEquals(s, s.next(0))
    }

    @Test fun progressBarMovesForward() {
        val f = QuestionFactory(Random(1))
        val qs = List(4) { f.make(2, it + 1, QType.TYPE) }
        var s = LessonState.start(LessonKind.Node(2, 0), qs, 0)
        assertEquals(0f, s.progress, 0.001f)
        s = s.submit(2, f)
        assertEquals(0.25f, s.progress, 0.001f)
        s = s.next(0).submit(0, f) // yanlış: tekrar eklenir, ilerleme artmaz
        assertEquals(0.25f, s.progress, 0.001f)
        s = s.next(0).submit(6, f)
        assertEquals(0.5f, s.progress, 0.001f)
    }

    @Test fun examFailsWithTooManyMistakes() {
        val f = QuestionFactory(Random(1))
        val qs = List(Curriculum.BOSS_SIZE) { f.make(5, (it % 10) + 1, QType.TYPE) }
        var s = LessonState.start(LessonKind.Node(5, 3), qs, 0)
        assertFalse(s.requeueWrong)
        var i = 0
        while (!s.isFinished) {
            val q = s.current!!
            s = s.submit(if (i < 3) -1 else q.answer, f).next(0)
            i++
        }
        assertEquals(3, s.wrong)
        assertFalse(s.passed)
    }

    @Test fun sprintEndsAtDeadline() {
        val f = QuestionFactory(Random(1))
        var s = LessonState.start(LessonKind.Sprint, emptyList(), now = 1000, first = f.make(2, 2, QType.TYPE))
        assertEquals(1000 + Curriculum.SPRINT_MS, s.deadline)
        s = s.submit(4, f).next(2000) { f.make(2, 3, QType.TYPE) }
        assertNotNull(s.current)
        s = s.submit(6, f).next(1000 + Curriculum.SPRINT_MS) { f.make(2, 4, QType.TYPE) }
        assertTrue(s.isFinished)
        assertEquals(2, s.correct)
    }

    @Test fun tableNamesFollowVowelHarmony() {
        assertEquals("2'ler", Curriculum.tableName(2))
        assertEquals("6'lar", Curriculum.tableName(6))
        assertEquals("9'lar", Curriculum.tableName(9))
        assertEquals("10'lar", Curriculum.tableName(10))
        assertEquals("11'ler", Curriculum.tableName(11))
        assertEquals("12'ler", Curriculum.tableName(12))
    }

    @Test fun hintsContainTheAnswer() {
        for (a in Curriculum.TABLES) for (b in Curriculum.FACTORS) {
            assertTrue("$a x $b", Hints.forFact(a, b).endsWith("${a * b}"))
        }
    }
}
