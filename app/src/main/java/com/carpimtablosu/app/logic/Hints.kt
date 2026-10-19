package com.carpimtablosu.app.logic

/** Yanlış cevaptan sonra gösterilen kısa ipucu: cevabı ezberletmek yerine yolu gösterir. */
object Hints {
    fun forFact(a: Int, b: Int): String {
        val p = a * b
        return when {
            b == 1 -> "Bir sayının 1 katı kendisidir: $a × 1 = $a"
            b == 10 -> "10 ile çarparken sonuna 0 ekle: $a × 10 = $p"
            b == 2 -> "İki katı: $a + $a = $p"
            b == 5 -> "$a × 10 = ${a * 10}, bunun yarısı $p"
            b == 9 -> "$a × 10 − $a = ${a * 10} − $a = $p"
            a >= 11 -> "$a × $b = 10 × $b + ${a - 10} × $b = ${10 * b} + ${(a - 10) * b} = $p"
            b % 2 == 0 -> "$a × ${b / 2} = ${p / 2}, iki katı $p"
            else -> "$a × ${b - 1} = ${a * (b - 1)}, bir $a daha: $p"
        }
    }
}
