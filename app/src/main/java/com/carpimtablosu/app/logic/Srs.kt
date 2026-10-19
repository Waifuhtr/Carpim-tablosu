package com.carpimtablosu.app.logic

import com.carpimtablosu.app.data.FactStat
import com.carpimtablosu.app.data.Progress

/** Leitner kutularıyla aralıklı tekrar. */
object Srs {
    const val MAX_BOX = 5
    private const val DAY = 24L * 60 * 60 * 1000

    /** Kutu -> bir sonraki tekrara kadar geçecek süre. */
    private val INTERVALS = longArrayOf(0L, DAY, 2 * DAY, 4 * DAY, 7 * DAY, 14 * DAY)

    fun key(a: Int, b: Int) = "${a}x$b"

    fun onAnswer(stat: FactStat?, correct: Boolean, now: Long): FactStat {
        val s = stat ?: FactStat()
        return if (correct) {
            val box = (s.box + 1).coerceAtMost(MAX_BOX)
            s.copy(box = box, dueAt = now + INTERVALS[box], correct = s.correct + 1, seen = true)
        } else {
            val box = (s.box - 2).coerceAtLeast(0)
            s.copy(box = box, dueAt = now, wrong = s.wrong + 1, seen = true)
        }
    }

    /** 0..1 arası tablo ustalığı (1..10 çarpanlarının kutu ortalaması). */
    fun mastery(progress: Progress, table: Int): Float {
        var sum = 0
        for (b in Curriculum.FACTORS) sum += progress.facts[key(table, b)]?.box ?: 0
        return sum.toFloat() / (Curriculum.FACTORS.count() * MAX_BOX)
    }

    /** Tekrar zamanı gelmiş, daha önce görülmüş gerçekler (en acil önce). */
    fun dueFacts(progress: Progress, now: Long): List<Pair<Int, Int>> =
        allFacts().filter { (a, b) ->
            val s = progress.facts[key(a, b)]
            s != null && s.seen && s.dueAt <= now
        }.sortedWith(compareBy({ progress.facts[key(it.first, it.second)]!!.box }, { progress.facts[key(it.first, it.second)]!!.dueAt }))

    /** En çok zorlanılan gerçekler: yanlış oranı yüksek olanlar. */
    fun weakFacts(progress: Progress, limit: Int): List<Pair<Pair<Int, Int>, FactStat>> =
        allFacts().mapNotNull { f -> progress.facts[key(f.first, f.second)]?.let { f to it } }
            .filter { it.second.wrong > 0 }
            .sortedWith(
                compareByDescending<Pair<Pair<Int, Int>, FactStat>> {
                    it.second.wrong.toFloat() / (it.second.wrong + it.second.correct)
                }.thenByDescending { it.second.wrong }
            )
            .take(limit)

    fun allFacts(): List<Pair<Int, Int>> =
        Curriculum.TABLES.flatMap { a -> Curriculum.FACTORS.map { b -> a to b } }
}
