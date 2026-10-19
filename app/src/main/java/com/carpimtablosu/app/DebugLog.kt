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

    fun init(context: Context) {
        file = File(context.filesDir, "kayit.txt")
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
                f.appendText("${LocalDateTime.now().format(fmt)}  $msg\n")
            }
        }
    }

    fun error(msg: String, e: Throwable) {
        Log.e(TAG, msg, e)
        log("HATA: $msg: ${e.javaClass.simpleName}: ${e.message}")
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
