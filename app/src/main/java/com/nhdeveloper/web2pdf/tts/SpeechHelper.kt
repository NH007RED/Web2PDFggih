package com.nhdeveloper.web2pdf.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Small wrapper around Android TextToSpeech that speaks Hindi (or English for Latin text). */
class SpeechHelper(context: Context, private val onState: (Boolean) -> Unit) {

    sealed interface Status {
        data object Starting : Status
        data object Ready : Status
        data object Failed : Status
    }

    @Volatile var status: Status = Status.Starting
        private set

    private var tts: TextToSpeech? = null
    private var counter = 0L
    @Volatile private var currentId: String = ""
    @Volatile private var hindiOk = true

    /** True when no Hindi voice data is installed on the phone. */
    val hindiMissing: Boolean get() = !hindiOk

    init {
        tts = TextToSpeech(context.applicationContext) { code ->
            if (code == TextToSpeech.SUCCESS) {
                val t = tts
                if (t != null) {
                    val r = t.setLanguage(Locale("hi", "IN"))
                    hindiOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                    t.setSpeechRate(0.9f)
                    t.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (utteranceId == currentId) onState(true)
                        }
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == currentId) onState(false)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == currentId) onState(false)
                        }
                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            if (utteranceId == currentId) onState(false)
                        }
                    })
                    status = Status.Ready
                } else {
                    status = Status.Failed
                }
            } else {
                status = Status.Failed
            }
        }
    }

    private fun hasDevanagari(s: String): Boolean = s.any { it in '\u0900'..'\u097F' }

    /** Speaks the text, replacing anything currently being spoken. Returns false if it could not start. */
    fun speak(text: String): Boolean {
        val t = tts ?: return false
        if (status != Status.Ready) return false
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val locale = if (hasDevanagari(clean)) Locale("hi", "IN") else Locale.ENGLISH
        val r = t.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (locale.language == "hi") hindiOk = false
            return false
        }
        if (locale.language == "hi") hindiOk = true
        counter++
        val id = "w2p-$counter"
        currentId = id
        val res = t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
        return res == TextToSpeech.SUCCESS
    }

    fun stop() {
        currentId = ""
        try {
            tts?.stop()
        } catch (ignored: Exception) {
        }
        onState(false)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (ignored: Exception) {
        }
        tts = null
    }
}
