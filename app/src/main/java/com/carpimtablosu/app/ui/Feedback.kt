package com.carpimtablosu.app.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Sesli okuma (TTS). Türkçe ses paketi yoksa sessizce devre dışı kalır.
 */
class Speaker(context: Context) {
    private var ready = false
    var available = false
        private set

    private lateinit var tts: TextToSpeech

    init {
        // Başlatma sonucu ana iş parçacığına sonradan gelir; tts o zamana kadar atanmış olur
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts.setLanguage(Locale.forLanguageTag("tr-TR"))
                available = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                ready = true
            }
        }
    }

    fun speak(text: String) {
        if (ready && available) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "q")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() {
        runCatching { tts.shutdown() }
    }
}

/**
 * Sesli cevap. Sonuç metni çağırana verilir; sayıya çevirme işini TurkishNumbers yapar.
 * Tanıyıcı yoksa veya hata olursa onError çağrılır, uygulama çökmez.
 */
class Listener(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(onResult: (List<String>) -> Unit, onError: (String) -> Unit, onEnd: () -> Unit) {
        stop()
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (r == null) {
            onError("Ses tanıma kullanılamıyor")
            onEnd()
            return
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                onResult(list)
                onEnd()
            }

            override fun onError(error: Int) {
                onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Anlayamadım, tekrar dene"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "İnternet bağlantısı gerekli"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni gerekli"
                        else -> "Ses tanıma hatası"
                    }
                )
                onEnd()
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        try {
            r.startListening(intent)
        } catch (e: RuntimeException) {
            Log.e("Listener", "startListening", e)
            onError("Ses tanıma başlatılamadı")
            onEnd()
        }
    }

    fun stop() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }
}

/** Kısa doğru/yanlış sesleri. ToneGenerator bazı cihazlarda oluşturulamayabilir. */
class Sounds {
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()

    fun correct() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
    }

    fun wrong() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_NACK, 220) }
    }

    fun release() {
        runCatching { tone?.release() }
    }
}
