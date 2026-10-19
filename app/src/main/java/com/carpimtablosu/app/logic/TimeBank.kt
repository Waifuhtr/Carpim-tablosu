package com.carpimtablosu.app.logic

import com.carpimtablosu.app.data.TimeState

/**
 * Süre bankası kuralları. Saf fonksiyonlar; tüm durum değişiklikleri buradan geçer.
 * Süre hiçbir zaman 0'ın altına inmez.
 */
object TimeBank {

    /** Gün değiştiyse günlük kazanım sayacını sıfırlar. */
    fun rollDay(state: TimeState, today: String): TimeState =
        if (state.day == today) state else state.copy(day = today, earnedTodaySec = 0L)

    /**
     * Doğru cevap ödülü. Günlük sınır (capSec, 0 = sınırsız) aşılmaz.
     * @return yeni durum ve gerçekten eklenen saniye
     */
    fun reward(state: TimeState, rewardSec: Long, capSec: Long, today: String): Pair<TimeState, Long> {
        val s = rollDay(state, today)
        val room = if (capSec <= 0L) Long.MAX_VALUE else (capSec - s.earnedTodaySec).coerceAtLeast(0L)
        val gain = rewardSec.coerceAtLeast(0L).coerceAtMost(room)
        return s.copy(bankSec = s.bankSec + gain, earnedTodaySec = s.earnedTodaySec + gain) to gain
    }

    /**
     * Yanlış cevap cezası. Kalandan fazlası düşülmez.
     * Ceza, günlük kazanım sayacından da düşülür; aksi halde yanlış-doğru döngüsüyle
     * çocuk günlük sınırı kendi aleyhine tüketmiş olurdu.
     * @return yeni durum ve gerçekten düşülen saniye
     */
    fun penalize(state: TimeState, penaltySec: Long, today: String): Pair<TimeState, Long> {
        val s = rollDay(state, today)
        val loss = penaltySec.coerceAtLeast(0L).coerceAtMost(s.bankSec)
        return s.copy(
            bankSec = s.bankSec - loss,
            earnedTodaySec = (s.earnedTodaySec - loss).coerceAtLeast(0L),
        ) to loss
    }

    /** Kilitli uygulamada geçen süreyi düşer. */
    fun spend(state: TimeState, sec: Long): Pair<TimeState, Long> {
        val used = sec.coerceAtLeast(0L).coerceAtMost(state.bankSec)
        return state.copy(bankSec = state.bankSec - used) to used
    }

    /** Ebeveynin elle eklediği/çıkardığı süre. Günlük sınırı etkilemez. */
    fun adjust(state: TimeState, deltaSec: Long): TimeState =
        state.copy(bankSec = (state.bankSec + deltaSec).coerceAtLeast(0L))

    /** Gece modu: start..end aralığında mı? (gün içi dakika, gece yarısını aşabilir) */
    fun inQuietHours(enabled: Boolean, startMin: Int, endMin: Int, nowMin: Int): Boolean {
        if (!enabled || startMin == endMin) return false
        return if (startMin < endMin) nowMin in startMin until endMin
        else nowMin >= startMin || nowMin < endMin
    }

    fun format(sec: Long): String {
        val s = sec.coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
    }
}
