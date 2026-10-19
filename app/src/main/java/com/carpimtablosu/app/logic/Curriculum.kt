package com.carpimtablosu.app.logic

import com.carpimtablosu.app.data.Progress
import kotlin.random.Random

/** Ders türleri. */
sealed interface LessonKind {
    /** Yol haritasındaki bir düğüm. */
    data class Node(val table: Int, val node: Int) : LessonKind
    /** Kilitli bir tabloya atlama sınavı. */
    data class Skip(val table: Int) : LessonKind
    /** Aralıklı tekrar: zamanı gelen ve zayıf gerçekler. */
    data object Review : LessonKind
    /** Seçilen tek bir tablo üzerinde serbest alıştırma. */
    data class TablePractice(val table: Int) : LessonKind
    /** 60 saniyelik hızlı tur. */
    data object Sprint : LessonKind
}

object Curriculum {
    val TABLES = 2..12
    val FACTORS = 1..10
    const val NODES_PER_TABLE = 4
    const val BOSS_NODE = 3
    const val LESSON_SIZE = 10
    const val BOSS_SIZE = 12
    /** Usta sınavında izin verilen en fazla yanlış. */
    const val BOSS_MAX_WRONG = 2
    const val SPRINT_MS = 60_000L

    /** "7'ler", "6'lar": sayının okunuşuna göre ünlü uyumlu çoğul eki. */
    fun tableName(table: Int): String = "$table'" + if (table == 6 || table == 9 || table == 10) "lar" else "ler"

    fun nodeTitle(node: Int): String = when (node) {
        0 -> "1 – 5"
        1 -> "6 – 10"
        2 -> "Karışık"
        else -> "Usta sınavı"
    }

    fun completed(progress: Progress, table: Int): Int =
        (progress.completedNodes[table] ?: 0).coerceIn(0, NODES_PER_TABLE)

    fun isTableDone(progress: Progress, table: Int) = completed(progress, table) >= NODES_PER_TABLE

    /** Bir düğüm, kendinden önceki tüm düğümler bitince açılır. */
    fun isUnlocked(progress: Progress, table: Int, node: Int): Boolean {
        if (TABLES.filter { it < table }.any { !isTableDone(progress, it) }) return false
        return completed(progress, table) >= node
    }

    fun isDone(progress: Progress, table: Int, node: Int) = completed(progress, table) > node

    /** Çocuğun sıradaki düğümü; hepsi bittiyse null. */
    fun currentNode(progress: Progress): Pair<Int, Int>? {
        for (t in TABLES) {
            val c = completed(progress, t)
            if (c < NODES_PER_TABLE) return t to c
        }
        return null
    }

    /** Açılmış en büyük tablo (alıştırma ve hızlı tur için). */
    fun unlockedTables(progress: Progress): List<Int> {
        val cur = currentNode(progress)?.first ?: TABLES.last
        return TABLES.filter { it <= cur }
    }

    /** Ders sonucu uygulanınca yeni ilerleme. */
    fun complete(progress: Progress, kind: LessonKind, passed: Boolean): Progress = when (kind) {
        is LessonKind.Node -> {
            if (!passed) progress
            else {
                val c = completed(progress, kind.table)
                val n = maxOf(c, kind.node + 1)
                progress.copy(completedNodes = progress.completedNodes + (kind.table to n))
            }
        }
        is LessonKind.Skip -> {
            if (!passed) progress
            else {
                val map = progress.completedNodes.toMutableMap()
                for (t in TABLES) if (t <= kind.table) map[t] = NODES_PER_TABLE
                progress.copy(completedNodes = map)
            }
        }
        else -> progress
    }

    fun isExam(kind: LessonKind) =
        kind is LessonKind.Skip || (kind is LessonKind.Node && kind.node == BOSS_NODE)

    /** Bir dersin sorularını üretir. */
    fun buildLesson(
        kind: LessonKind,
        progress: Progress,
        now: Long,
        factory: QuestionFactory,
        rnd: Random = Random.Default,
    ): List<Question> = when (kind) {
        is LessonKind.Node -> when (kind.node) {
            0 -> introLesson(kind.table, 1..5, progress, factory, rnd)
            1 -> introLesson(kind.table, 6..10, progress, factory, rnd)
            2 -> mixed(List(LESSON_SIZE) { kind.table to FACTORS.random(rnd) }, ALL_TYPES, factory, rnd)
            else -> mixed(List(BOSS_SIZE) { kind.table to FACTORS.random(rnd) }, EXAM_TYPES, factory, rnd)
        }
        is LessonKind.Skip -> {
            // Atlanacak tablolardan karışık, son tabloya ağırlık verilerek
            val tables = TABLES.filter { it <= kind.table }
            val facts = List(BOSS_SIZE) { i ->
                val a = if (i % 2 == 0) kind.table else tables.random(rnd)
                a to FACTORS.random(rnd)
            }
            mixed(facts, EXAM_TYPES, factory, rnd)
        }
        is LessonKind.TablePractice ->
            mixed(FACTORS.shuffled(rnd).map { kind.table to it }, ALL_TYPES, factory, rnd)
        LessonKind.Review -> mixed(reviewFacts(progress, now, rnd), ALL_TYPES, factory, rnd)
        LessonKind.Sprint -> emptyList() // hızlı turda sorular tek tek üretilir
    }

    /** Hızlı tur için tek soru. */
    fun sprintQuestion(progress: Progress, factory: QuestionFactory, rnd: Random = Random.Default): Question {
        val a = unlockedTables(progress).random(rnd)
        return factory.make(a, FACTORS.random(rnd), SPRINT_TYPES.random(rnd))
    }

    private val ALL_TYPES = QType.entries.toList()
    private val EXAM_TYPES = listOf(QType.TYPE, QType.TYPE, QType.MISSING, QType.DIVISION, QType.CHOICE)
    private val SPRINT_TYPES = listOf(QType.CHOICE, QType.CHOICE, QType.TYPE, QType.TRUE_FALSE)

    /**
     * Yeni gerçekler önce seçenekli soruyla tanıtılır, sonra aynı gerçekler
     * farklı türlerde tekrar sorulur.
     */
    private fun introLesson(
        table: Int, range: IntRange, progress: Progress, factory: QuestionFactory, rnd: Random,
    ): List<Question> {
        val first = range.map { b ->
            val seen = progress.facts[Srs.key(table, b)]?.seen == true
            factory.make(table, b, if (seen) listOf(QType.CHOICE, QType.TRUE_FALSE).random(rnd) else QType.CHOICE)
        }.shuffled(rnd)
        val second = range.map { b ->
            factory.make(table, b, listOf(QType.TYPE, QType.CHOICE, QType.TRUE_FALSE, QType.TYPE).random(rnd))
        }.shuffled(rnd)
        return noBackToBack(first + second)
    }

    private fun mixed(
        facts: List<Pair<Int, Int>>, types: List<QType>, factory: QuestionFactory, rnd: Random,
    ): List<Question> = noBackToBack(facts.map { (a, b) -> factory.make(a, b, types.random(rnd)) }.shuffled(rnd))

    private fun reviewFacts(progress: Progress, now: Long, rnd: Random): List<Pair<Int, Int>> {
        val out = Srs.dueFacts(progress, now).take(LESSON_SIZE).toMutableList()
        if (out.size < LESSON_SIZE) {
            // Zamanı gelen yoksa en düşük kutudaki görülmüş gerçeklerle tamamla
            val seen = Srs.allFacts()
                .filter { progress.facts[Srs.key(it.first, it.second)]?.seen == true && it !in out }
                .sortedBy { progress.facts[Srs.key(it.first, it.second)]!!.box }
            out += seen.take(LESSON_SIZE - out.size)
        }
        if (out.size < LESSON_SIZE) {
            // Hiç çalışılmamışsa açık tablolardan rastgele
            val tables = unlockedTables(progress)
            while (out.size < LESSON_SIZE) out += tables.random(rnd) to FACTORS.random(rnd)
        }
        return out
    }

    /** Aynı gerçeğin arka arkaya gelmesini engeller (mümkün olduğunca). */
    fun noBackToBack(list: List<Question>): List<Question> {
        val out = list.toMutableList()
        for (i in 1 until out.size) {
            if (out[i].factKey == out[i - 1].factKey) {
                val j = (i + 1 until out.size).firstOrNull { out[it].factKey != out[i - 1].factKey }
                if (j != null) {
                    val tmp = out[i]; out[i] = out[j]; out[j] = tmp
                }
            }
        }
        return out
    }
}
