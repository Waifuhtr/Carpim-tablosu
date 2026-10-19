package com.carpimtablosu.app.logic

import kotlin.random.Random

enum class QType {
    /** a × b = ? (4 seçenek) */
    CHOICE,
    /** a × b = ? (tuş takımı) */
    TYPE,
    /** a × ? = c */
    MISSING,
    /** c ÷ a = ? */
    DIVISION,
    /** a × b = x doğru mu? */
    TRUE_FALSE,
}

data class Question(
    val id: Int,
    val a: Int,
    val b: Int,
    val type: QType,
    /** CHOICE için 4 seçenek; TRUE_FALSE için [1, 0]. */
    val options: List<Int> = emptyList(),
    /** TRUE_FALSE'ta ekranda gösterilen sonuç. */
    val shown: Int = 0,
) {
    val product: Int get() = a * b
    val factKey: String get() = Srs.key(a, b)

    /** Beklenen cevap. TRUE_FALSE için 1 = doğru, 0 = yanlış. */
    val answer: Int
        get() = when (type) {
            QType.CHOICE, QType.TYPE -> product
            QType.MISSING, QType.DIVISION -> b
            QType.TRUE_FALSE -> if (shown == product) 1 else 0
        }

    val usesKeypad: Boolean get() = type == QType.TYPE || type == QType.MISSING || type == QType.DIVISION

    val prompt: String
        get() = when (type) {
            QType.CHOICE, QType.TYPE -> "$a × $b = ?"
            QType.MISSING -> "$a × ? = $product"
            QType.DIVISION -> "$product ÷ $a = ?"
            QType.TRUE_FALSE -> "$a × $b = $shown"
        }

    val instruction: String
        get() = when (type) {
            QType.CHOICE -> "Doğru cevabı seç"
            QType.TYPE -> "Cevabı yaz"
            QType.MISSING -> "Eksik sayıyı bul"
            QType.DIVISION -> "Bölmeyi çöz"
            QType.TRUE_FALSE -> "Bu işlem doğru mu?"
        }

    /** Sesli okuma metni. */
    val spoken: String
        get() = when (type) {
            QType.CHOICE, QType.TYPE -> "$a çarpı $b kaç?"
            QType.MISSING -> "$a çarpı kaç, $product eder?"
            QType.DIVISION -> "$product bölü $a kaç?"
            QType.TRUE_FALSE -> "$a çarpı $b, $shown eder mi?"
        }

    /** Doğru cevabın gösterimi (geri bildirim için). */
    val solution: String
        get() = when (type) {
            QType.MISSING -> "$a × $b = $product"
            QType.DIVISION -> "$product ÷ $a = $b"
            else -> "$a × $b = $product"
        }
}

class QuestionFactory(private val rnd: Random = Random.Default) {
    private var nextId = 1

    fun make(a: Int, b: Int, type: QType): Question {
        val id = nextId++
        return when (type) {
            QType.CHOICE -> Question(id, a, b, type, options = choiceOptions(a, b))
            QType.TRUE_FALSE -> {
                val shown = if (rnd.nextBoolean()) a * b else distractors(a, b).first()
                Question(id, a, b, type, options = listOf(1, 0), shown = shown)
            }
            else -> Question(id, a, b, type)
        }
    }

    /** Aynı soruyu yeni bir kimlikle yeniden üretir (yanlış cevabı tekrar sormak için). */
    fun again(q: Question): Question = q.copy(id = nextId++)

    fun choiceOptions(a: Int, b: Int): List<Int> =
        (distractors(a, b).take(3) + (a * b)).shuffled(rnd)

    /**
     * Çocukların sık yaptığı hatalara benzeyen yanlış cevaplar:
     * komşu çarpımlar, ±10, rakamları ters çevrilmiş sayı.
     */
    fun distractors(a: Int, b: Int): List<Int> {
        val p = a * b
        val near = listOf(a * (b + 1), a * (b - 1), (a + 1) * b, (a - 1) * b).shuffled(rnd)
        val far = listOf(p + 10, p - 10, reverseDigits(p), p + a + 1, p + 2, p - 2, a + b).shuffled(rnd)
        val out = LinkedHashSet<Int>()
        for (c in near + far) {
            if (c > 0 && c != p) out.add(c)
        }
        var k = 1
        while (out.size < 3) {
            val c = p + k
            if (c > 0 && c != p) out.add(c)
            k = if (k > 0) -k else -k + 1
        }
        return out.toList()
    }

    private fun reverseDigits(n: Int): Int = n.toString().reversed().toInt()
}
