package com.carpimtablosu.app.logic

import com.carpimtablosu.app.data.Progress

enum class BadgeIcon { STAR, FLAME, TROPHY, TIMER, BULB, BOOK, CHECK, LOCK }

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: BadgeIcon,
    /** 0..1 arası ilerleme. */
    val progress: (Progress) -> Float,
)

object Achievements {
    private fun ratio(v: Int, target: Int) = (v.toFloat() / target).coerceIn(0f, 1f)

    private fun tablesDone(p: Progress) = Curriculum.TABLES.count { Curriculum.isTableDone(p, it) }

    val ALL: List<Achievement> = listOf(
        Achievement("first_lesson", "İlk Adım", "İlk dersini bitir", BadgeIcon.BOOK) { ratio(it.lessonsDone, 1) },
        Achievement("perfect", "Kusursuz", "Hatasız bir ders bitir", BadgeIcon.CHECK) { ratio(it.perfectLessons, 1) },
        Achievement("perfect10", "Keskin Nişancı", "10 dersi hatasız bitir", BadgeIcon.CHECK) { ratio(it.perfectLessons, 10) },
        Achievement("streak3", "Isınma Turu", "3 günlük seri yap", BadgeIcon.FLAME) { ratio(it.bestStreak, 3) },
        Achievement("streak7", "Bir Hafta", "7 günlük seri yap", BadgeIcon.FLAME) { ratio(it.bestStreak, 7) },
        Achievement("streak30", "Durmak Yok", "30 günlük seri yap", BadgeIcon.FLAME) { ratio(it.bestStreak, 30) },
        Achievement("correct100", "Yüzlük", "Toplam 100 doğru cevap", BadgeIcon.STAR) { ratio(it.totalCorrect, 100) },
        Achievement("correct500", "Beş Yüz", "Toplam 500 doğru cevap", BadgeIcon.STAR) { ratio(it.totalCorrect, 500) },
        Achievement("correct1000", "Bin Doğru", "Toplam 1000 doğru cevap", BadgeIcon.STAR) { ratio(it.totalCorrect, 1000) },
        Achievement("sprint15", "Hızlı Eller", "Hızlı turda 15 doğru", BadgeIcon.TIMER) { ratio(it.sprintBest, 15) },
        Achievement("sprint30", "Şimşek", "Hızlı turda 30 doğru", BadgeIcon.TIMER) { ratio(it.sprintBest, 30) },
        Achievement("tables3", "Yolun Başı", "3 tabloyu bitir", BadgeIcon.TROPHY) { ratio(tablesDone(it), 3) },
        Achievement("tables6", "Yarı Yol", "6 tabloyu bitir", BadgeIcon.TROPHY) { ratio(tablesDone(it), 6) },
        Achievement("tablesAll", "Çarpım Ustası", "Tüm tabloları bitir", BadgeIcon.TROPHY) {
            ratio(tablesDone(it), Curriculum.TABLES.count())
        },
        Achievement("lessons50", "Azimli", "50 ders tamamla", BadgeIcon.BULB) { ratio(it.lessonsDone, 50) },
    )

    /** Yeni kazanılan rozetleri ilerlemeye ekler ve listesini döndürür. */
    fun unlockNew(progress: Progress): Pair<Progress, List<Achievement>> {
        val fresh = ALL.filter { it.id !in progress.achievements && it.progress(progress) >= 1f }
        if (fresh.isEmpty()) return progress to emptyList()
        return progress.copy(achievements = progress.achievements + fresh.map { it.id }) to fresh
    }
}
