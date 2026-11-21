package com.example.ai

import com.example.audio.AppLanguage
import com.example.audio.DecodedAudio
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

enum class LyricCorrectionMode {
    SPELLING_GRAMMAR,
    SINGLISH_TO_SINHALA,
    RHYTHM_RHYME
}

data class LyricFixItem(
    val original: String,
    val fixed: String,
    val reason: String
)

data class LyricCorrectionResult(
    val correctedLyrics: String,
    val detectedLanguage: String,
    val summaryNote: String,
    val corrections: List<LyricFixItem>
)

sealed interface LyricsAiState {
    data object Idle : LyricsAiState
    data object Loading : LyricsAiState
    data class Success(val result: LyricCorrectionResult) : LyricsAiState
    data class Error(val message: String) : LyricsAiState
}

class LyricsCorrectionRepository {

    private val correctionSchema = Schema.obj(
        properties = mapOf(
            "correctedLyrics" to Schema.string(
                description = "Full corrected and cleanly formatted song lyrics preserving verse/chorus line breaks."
            ),
            "detectedLanguage" to Schema.string(
                description = "Detected language of the lyrics, e.g. Sinhala, English, Singlish, or Sinhala + English."
            ),
            "summaryNote" to Schema.string(
                description = "Short 1-sentence summary of the corrections made."
            ),
            "corrections" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "original" to Schema.string(description = "Original phrase or word before correction."),
                        "fixed" to Schema.string(description = "Corrected phrase or word."),
                        "reason" to Schema.string(description = "Brief reason for the correction.")
                    )
                ),
                description = "List of notable spelling, grammar, Sinhala Unicode, or rhythm corrections applied."
            )
        )
    )

    private val model by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = correctionSchema
        }
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = config,
                systemInstruction = content {
                    text(
                        "You are Swara Studio's expert Sinhala, Singlish, and English song lyricist " +
                            "and audio vocal transcriber. Always return accurate Sinhala Unicode " +
                            "orthography, clean stanza breaks, and valid JSON matching the schema."
                    )
                }
            )
    }

    suspend fun autoCorrectLyrics(
        rawLyrics: String,
        mode: LyricCorrectionMode,
        uiLanguage: AppLanguage,
        trackName: String?
    ): LyricCorrectionResult = withContext(Dispatchers.IO) {
        val prompt = buildTextPrompt(rawLyrics, mode, uiLanguage, trackName)
        val response = withTimeout(60_000L) {
            model.generateContent(prompt)
        }
        val jsonText = response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Model returned an empty response.")
        parseCorrectionJson(jsonText)
    }

    suspend fun transcribeAndCorrectFromAudio(
        audio: DecodedAudio,
        wavBytes: ByteArray,
        draftLyrics: String,
        mode: LyricCorrectionMode,
        uiLanguage: AppLanguage
    ): LyricCorrectionResult = withContext(Dispatchers.IO) {
        val promptText = buildAudioPrompt(draftLyrics, mode, uiLanguage, audio.displayName)
        val inputContent = content {
            inlineData(wavBytes, "audio/wav")
            text(promptText)
        }
        val response = withTimeout(60_000L) {
            model.generateContent(inputContent)
        }
        val jsonText = response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Model returned an empty response.")
        parseCorrectionJson(jsonText)
    }

    private fun parseCorrectionJson(jsonText: String): LyricCorrectionResult {
        val root = JSONObject(jsonText)
        val correctedLyrics = root.optString("correctedLyrics", "").ifBlank { jsonText }
        val detectedLanguage = root.optString("detectedLanguage", "Sinhala / English")
        val summaryNote = root.optString("summaryNote", "")
        val arr = root.optJSONArray("corrections")
        val items = buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val orig = obj.optString("original").trim()
                    val fixed = obj.optString("fixed").trim()
                    val reason = obj.optString("reason").trim()
                    if (orig.isNotEmpty() || fixed.isNotEmpty()) {
                        add(LyricFixItem(original = orig, fixed = fixed, reason = reason))
                    }
                }
            }
        }
        return LyricCorrectionResult(
            correctedLyrics = correctedLyrics,
            detectedLanguage = detectedLanguage,
            summaryNote = summaryNote,
            corrections = items
        )
    }

    private fun modeInstruction(mode: LyricCorrectionMode): String = when (mode) {
        LyricCorrectionMode.SPELLING_GRAMMAR ->
            "Fix spelling, Sinhala Unicode orthography (hal kirima, kombuwa, ispilla, binduwa), English spelling, punctuation, and grammar while keeping the artist's original meaning and vocal phrasing intact."
        LyricCorrectionMode.SINGLISH_TO_SINHALA ->
            "Convert any Romanized Sinhala (Singlish) lines into accurate, natural Sinhala Unicode script (සිංහල) while preserving genuine English loanwords or English rap/chorus lines cleanly."
        LyricCorrectionMode.RHYTHM_RHYME ->
            "Fix spelling/grammar AND gently polish awkward syllables, meter, and end-rhymes so the lyrics flow smoothly when sung over a beat, without altering the song's core emotion."
    }

    private fun feedbackLanguageNote(uiLanguage: AppLanguage): String =
        if (uiLanguage == AppLanguage.SI) {
            "Write `summaryNote` and each correction `reason` concisely in Sinhala (සිංහල)."
        } else {
            "Write `summaryNote` and each correction `reason` concisely in English."
        }

    private fun buildTextPrompt(
        rawLyrics: String,
        mode: LyricCorrectionMode,
        uiLanguage: AppLanguage,
        trackName: String?
    ): String = buildString {
        appendLine("You are an expert Sinhala & English song lyricist and music producer assistant inside Swara Studio.")
        appendLine("Task: ${modeInstruction(mode)}")
        appendLine(feedbackLanguageNote(uiLanguage))
        if (!trackName.isNullOrBlank()) {
            appendLine("Song track filename for context: $trackName")
        }
        appendLine("Preserve clean stanza/verse/chorus line breaks.")
        appendLine()
        appendLine("Draft lyrics:")
        appendLine(rawLyrics)
    }

    private fun buildAudioPrompt(
        draftLyrics: String,
        mode: LyricCorrectionMode,
        uiLanguage: AppLanguage,
        trackName: String
    ): String = buildString {
        appendLine("You are an expert Sinhala & English song lyricist and audio transcriber inside Swara Studio.")
        appendLine("Listen carefully to the vocals in the attached song audio ($trackName).")
        if (draftLyrics.isNotBlank()) {
            appendLine("The artist provided draft lyrics below. Cross-check what is actually sung in the audio, fix misheard or misspelled words, and apply: ${modeInstruction(mode)}")
            appendLine()
            appendLine("Draft lyrics:")
            appendLine(draftLyrics)
        } else {
            appendLine("Transcribe the sung lyrics accurately (Sinhala Unicode and/or English) with clean verse/chorus line breaks, then apply: ${modeInstruction(mode)}")
        }
        appendLine(feedbackLanguageNote(uiLanguage))
    }
}
