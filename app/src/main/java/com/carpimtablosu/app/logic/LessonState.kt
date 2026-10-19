package com.carpimtablosu.app.logic

/** Verilen bir cevabın sonucu. */
data class AnswerResult(
    val given: Int,
    val correct: Boolean,
    /** Süre bankasına etkisi (saniye, + veya -). */
    val timeDeltaSec: Long = 0L,
    /** Günlük kazanım sınırına takıldı mı? */
    val capReached: Boolean = false,
)

/**
 * Bir dersin değişmez durumu. Her işlem yeni bir durum döndürür; böylece ekran
 * döndürme veya çift dokunma gibi durumlar tutarsızlık yaratmaz.
 */
data class LessonState(
    val kind: LessonKind,
    val current: Question?,
    val queue: List<Question>,
    val requeueWrong: Boolean,
    val answer: AnswerResult? = null,
    val cleared: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    /** Yeniden kuyruğa eklenmiş soruların gerçekleri (her biri bir kez). */
    val requeuedFacts: Set<String> = emptySet(),
    val startedAt: Long = 0L,
    val deadline: Long? = null,
    val timeEarnedSec: Long = 0L,
    val timeLostSec: Long = 0L,
) {
    val isAnswered get() = answer != null
    val isFinished get() = current == null

    /** Duolingo tarzı ilerleme çubuğu oranı. */
    val progress: Float
        get() {
            val pending = queue.size + if (current != null && answer == null) 1 else 0
            val total = cleared + pending
            return if (deadline != null || total == 0) 0f else cleared.toFloat() / total
        }

    /** Sınav dersi geçildi mi? Normal derslerde her zaman true. */
    val passed: Boolean
        get() = !Curriculum.isExam(kind) || wrong <= Curriculum.BOSS_MAX_WRONG

    val perfect: Boolean get() = wrong == 0 && correct > 0

    /** Cevabı değerlendirir. Zaten cevaplanmış soruya ikinci cevap yok sayılır. */
    fun submit(given: Int, factory: QuestionFactory): LessonState {
        val q = current ?: return this
        if (answer != null) return this
        val ok = given == q.answer
        val requeue = !ok && requeueWrong && q.factKey !in requeuedFacts
        return copy(
            answer = AnswerResult(given, ok),
            correct = correct + if (ok) 1 else 0,
            wrong = wrong + if (ok) 0 else 1,
            cleared = cleared + if (requeue) 0 else 1,
            queue = if (requeue) queue + factory.again(q) else queue,
            requeuedFacts = if (requeue) requeuedFacts + q.factKey else requeuedFacts,
        )
    }

    fun withTime(delta: Long, capReached: Boolean): LessonState {
        val a = answer ?: return this
        return copy(
            answer = a.copy(timeDeltaSec = delta, capReached = capReached),
            timeEarnedSec = timeEarnedSec + delta.coerceAtLeast(0L),
            timeLostSec = timeLostSec + (-delta).coerceAtLeast(0L),
        )
    }

    /** Sonraki soruya geçer. Kuyruk boşsa ve üretici varsa ondan alır. */
    fun next(now: Long, generate: (() -> Question)? = null): LessonState {
        if (answer == null && current != null) return this
        if (deadline != null && now >= deadline) return copy(current = null, answer = null)
        val nextQ = queue.firstOrNull() ?: generate?.invoke()
        return copy(current = nextQ, queue = queue.drop(1), answer = null)
    }

    /** Hızlı turda süre doldu. */
    fun timeUp(): LessonState = copy(current = null, queue = emptyList(), answer = null)

    companion object {
        fun start(kind: LessonKind, questions: List<Question>, now: Long, first: Question? = null): LessonState {
            val all = if (first != null) listOf(first) + questions else questions
            val sprint = kind == LessonKind.Sprint
            return LessonState(
                kind = kind,
                current = all.firstOrNull(),
                queue = all.drop(1),
                requeueWrong = !Curriculum.isExam(kind) && !sprint,
                startedAt = now,
                deadline = if (sprint) now + Curriculum.SPRINT_MS else null,
            )
        }
    }
}
