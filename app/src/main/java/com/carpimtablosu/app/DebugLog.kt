package com.carpimtablosu.app

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Telefonda tutulan tanılama kaydı. Ebeveyn paneli > Tanılama ekranından okunup
 * kopyalanabilir. Dosya belirli bir boyutu geçince eski satırlar silinir.
 */
object DebugLog {
    private const val TAG = "Carpim"
    private const val MAX_BYTES = 200_000L
    private val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    @Volatile private var file: File? = null
    /** Hangi süreçten yazıldığı: "ekran" veya "kilit". */
    @Volatile private var tag = "?"

    fun init(context: Context, processTag: String) {
        file = File(context.filesDir, "kayit.txt")
        tag = processTag
    }

    fun log(msg: String) {
        Log.i(TAG, msg)
        val f = file ?: return
        synchronized(this) {
            runCatching {
                if (f.length() > MAX_BYTES) {
                    val keep = f.readText().takeLast((MAX_BYTES / 2).toInt())
                    f.writeText(keep.substringAfter('\n'))
                }
                f.appendText("${LocalDateTime.now().format(fmt)} [$tag] $msg\n")
            }
        }
    }

    fun error(msg: String, e: Throwable) {
        Log.e(TAG, msg, e)
        log("HATA: $msg: ${e.javaClass.simpleName}: ${e.message}")
    }

    /** Çökmeleri yığın iziyle kaydeder, sonra sistemin varsayılan davranışına bırakır. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                log("ÇÖKME (${thread.name}): ${e.javaClass.name}: ${e.message}\n" + Log.getStackTraceString(e).take(4000))
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Son [maxLines] satır. */
    fun read(maxLines: Int = 400): String = synchronized(this) {
        val f = file ?: return ""
        runCatching { f.readLines().takeLast(maxLines).joinToString("\n") }.getOrDefault("")
    }

    fun clear() = synchronized(this) {
        runCatching { file?.writeText("") }
        Unit
    }
}
