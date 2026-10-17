package com.routewise.sa.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceGuidanceManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    var isEnabled = true

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ENGLISH
            isInitialized = true
        }
    }

    fun speak(instruction: String) {
        if (!isEnabled || !isInitialized) return
        tts?.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, "instruction_id")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
