package com.carpimtablosu.app.data

import com.carpimtablosu.app.DebugLog
import kotlinx.serialization.KSerializer
import java.io.File
import java.io.RandomAccessFile

/**
 * Süreçler arası güvenli JSON dosya deposu.
 *
 * Kilit servisi ayrı bir süreçte çalışır; SharedPreferences iki süreç arasında
 * güvenli değildir (her süreç kendi önbelleğini tutar ve yazılanlar birbirini ezer).
 * Burada her güncelleme, dosya kilidi altında diskteki güncel değeri okuyup yazar.
 * Yazma geçici dosya + yeniden adlandırma ile yapılır; okuyan taraf asla yarım dosya görmez.
 */
class FileStore(val dir: File) {
    // Aynı süreçteki iki iş parçacığı aynı dosyayı kilitlemeye çalışırsa FileChannel hata verir
    private val processLock = Any()

    init {
        dir.mkdirs()
    }

    fun exists(name: String): Boolean = File(dir, name).exists()

    /** Dosyayı okur; yoksa null. Bozuksa yedeğini alır ve null döner. */
    fun <T> read(name: String, serializer: KSerializer<T>): T? {
        val f = File(dir, name)
        if (!f.exists()) return null
        return try {
            Repository.json.decodeFromString(serializer, f.readText())
        } catch (e: Exception) {
            DebugLog.error("Kayıt okunamadı: $name", e)
            runCatching { f.copyTo(File(dir, "$name.bozuk"), overwrite = true) }
            null
        }
    }

    /** Kilit altında oku → değiştir → yaz. Yeni değeri döndürür. */
    fun <T> update(name: String, serializer: KSerializer<T>, default: () -> T, f: (T) -> T): T =
        withLock(name) {
            val exists = File(dir, name).exists()
            val cur = read(name, serializer) ?: default()
            val next = f(cur)
            if (next != cur || !exists) write(name, serializer, next)
            next
        }

    fun <T> write(name: String, serializer: KSerializer<T>, value: T) {
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(Repository.json.encodeToString(serializer, value))
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun <R> withLock(name: String, block: () -> R): R = synchronized(processLock) {
        RandomAccessFile(File(dir, "$name.lock"), "rw").use { raf ->
            raf.channel.lock().use { block() }
        }
    }
}
