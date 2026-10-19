package com.carpimtablosu.app.logic

/** Ses tanımadan gelen metni sayıya çevirir: "42", "kırk iki", "kırkiki", "yüz yirmi". */
object TurkishNumbers {
    private val WORDS = linkedMapOf(
        "sifir" to 0, "bir" to 1, "iki" to 2, "uc" to 3, "dort" to 4, "bes" to 5,
        "alti" to 6, "yedi" to 7, "sekiz" to 8, "dokuz" to 9, "on" to 10, "yirmi" to 20,
        "otuz" to 30, "kirk" to 40, "elli" to 50, "altmis" to 60, "yetmis" to 70,
        "seksen" to 80, "doksan" to 90, "yuz" to 100,
    )
    // Uzun kelimeler önce denenir ("altmis" "alti"dan önce)
    private val BY_LENGTH = WORDS.keys.sortedByDescending { it.length }

    fun parse(text: String): Int? {
        val norm = ascii(text.lowercase(java.util.Locale.forLanguageTag("tr")))
        Regex("\\d+").find(norm)?.let { return it.value.take(6).toIntOrNull() }

        val values = mutableListOf<Int>()
        for (word in norm.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }) {
            split(word)?.let { values += it }
        }
        if (values.isEmpty()) return null

        var total = 0
        var current = 0
        for (v in values) {
            if (v == 100) {
                current = if (current == 0) 100 else current * 100
                total += current
                current = 0
            } else {
                current += v
            }
        }
        return total + current
    }

    /** Bir kelimeyi tamamen sayı parçalarına bölebiliyorsa parçaları döndürür. */
    private fun split(word: String): List<Int>? {
        if (word.isEmpty()) return emptyList()
        for (w in BY_LENGTH) {
            if (word.startsWith(w)) {
                val rest = split(word.substring(w.length))
                if (rest != null) return listOf(WORDS.getValue(w)) + rest
            }
        }
        return null
    }

    private fun ascii(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                'ı', 'i', 'î' -> 'i'
                'ü', 'û' -> 'u'
                'ş' -> 's'
                'ç' -> 'c'
                'ö' -> 'o'
                'ğ' -> 'g'
                'â' -> 'a'
                else -> c
            }
        )
    }
}
