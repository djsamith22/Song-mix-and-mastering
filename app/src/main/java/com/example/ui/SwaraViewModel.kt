package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.LyricCorrectionMode
import com.example.ai.LyricsAiState
import com.example.ai.LyricsCorrectionRepository
import com.example.audio.AppLanguage
import com.example.audio.AudioAuditEngine
import com.example.audio.AudioFileCodec
import com.example.audio.AudioPlaybackEngine
import com.example.audio.DecodedAudio
import com.example.audio.DiagnosticMetrics
import com.example.audio.MASTERING_PRESETS
import com.example.audio.MasteringParams
import com.example.audio.MasteringPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SwaraViewModel"

data class SwaraUiState(
    val language: AppLanguage = AppLanguage.SI,
    val trackName: String? = null,
    val isAudioLoaded: Boolean = false,
    val isLoadingAudio: Boolean = false,
    val isExporting: Boolean = false,
    val isPlaying: Boolean = false,
    val isBypassed: Boolean = false,
    val currentSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val waveformPeaks: List<Float> = emptyList(),
    val spectrumBins: List<Float> = List(40) { 0f },
    val gainReductionDb: Float = 0f,
    val params: MasteringParams = MASTERING_PRESETS.first().params,
    val activePresetId: String? = MASTERING_PRESETS.first().id,
    val auditMetrics: DiagnosticMetrics? = null,
    val bannerMessage: String? = null,
    val isBannerError: Boolean = false,
    // Auto Lyrics Corrector state
    val lyricsDraft: String = "",
    val lyricMode: LyricCorrectionMode = LyricCorrectionMode.SPELLING_GRAMMAR,
    val lyricsAiState: LyricsAiState = LyricsAiState.Idle
)

class SwaraViewModel(application: Application) : AndroidViewModel(application) {
    private val playbackEngine = AudioPlaybackEngine()
    private val lyricsRepository = LyricsCorrectionRepository()
    private var loadedAudio: DecodedAudio? = null

    private val _uiState = MutableStateFlow(SwaraUiState())
    val uiState: StateFlow<SwaraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playbackEngine.snapshot.collect { snap ->
                _uiState.update { state ->
                    state.copy(
                        isPlaying = snap.isPlaying,
                        currentSec = snap.currentSec,
                        spectrumBins = snap.spectrumBins,
                        gainReductionDb = snap.gainReductionDb
                    )
                }
            }
        }
    }

    fun toggleLanguage() {
        _uiState.update { state ->
            val next = if (state.language == AppLanguage.SI) AppLanguage.EN else AppLanguage.SI
            state.copy(language = next)
        }
    }

    fun loadAudioFromUri(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingAudio = true,
                    bannerMessage = null,
                    isBannerError = false
                )
            }
            try {
                val decoded = AudioFileCodec.decodeAudioUri(context, uri)
                val (peaks, audit) = withContext(Dispatchers.Default) {
                    val p = AudioAuditEngine.extractWaveformPeaks(decoded, 140)
                    val a = AudioAuditEngine.runAudit(decoded)
                    p to a
                }
                loadedAudio = decoded
                playbackEngine.load(decoded)
                playbackEngine.updateParams(_uiState.value.params)
                playbackEngine.setBypassed(_uiState.value.isBypassed)

                _uiState.update {
                    it.copy(
                        trackName = decoded.displayName,
                        isAudioLoaded = true,
                        isLoadingAudio = false,
                        currentSec = 0.0,
                        durationSec = decoded.durationSec,
                        waveformPeaks = peaks,
                        auditMetrics = audit,
                        bannerMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Audio decode failed", t)
                val strings = studioStrings(_uiState.value.language)
                _uiState.update {
                    it.copy(
                        isLoadingAudio = false,
                        bannerMessage = strings.decodeErrorPrefix + (t.localizedMessage ?: "Unsupported format"),
                        isBannerError = true
                    )
                }
            }
        }
    }

    fun togglePlayPause() {
        if (!_uiState.value.isAudioLoaded) return
        playbackEngine.togglePlayPause()
    }

    fun setBypass(bypassed: Boolean) {
        playbackEngine.setBypassed(bypassed)
        _uiState.update { it.copy(isBypassed = bypassed) }
    }

    fun seekFraction(fraction: Float) {
        val audio = loadedAudio ?: return
        val sec = fraction.coerceIn(0f, 1f).toDouble() * audio.durationSec
        playbackEngine.seekToSec(sec)
    }

    fun selectPreset(preset: MasteringPreset) {
        playbackEngine.updateParams(preset.params)
        _uiState.update {
            it.copy(
                params = preset.params,
                activePresetId = preset.id
            )
        }
    }

    fun updateParams(transform: (MasteringParams) -> MasteringParams) {
        _uiState.update { state ->
            val updated = transform(state.params)
            playbackEngine.updateParams(updated)
            state.copy(
                params = updated,
                activePresetId = MASTERING_PRESETS.firstOrNull { it.params == updated }?.id
            )
        }
    }

    fun updateLyricsDraft(text: String) {
        _uiState.update { it.copy(lyricsDraft = text) }
    }

    fun clearLyrics() {
        _uiState.update {
            it.copy(
                lyricsDraft = "",
                lyricsAiState = LyricsAiState.Idle
            )
        }
    }

    fun selectLyricMode(mode: LyricCorrectionMode) {
        _uiState.update { it.copy(lyricMode = mode) }
    }

    fun autoCorrectLyrics() {
        val snapshot = _uiState.value
        val strings = studioStrings(snapshot.language)
        if (snapshot.lyricsDraft.isBlank()) {
            _uiState.update {
                it.copy(lyricsAiState = LyricsAiState.Error(strings.lyricsEmptyInputError))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(lyricsAiState = LyricsAiState.Loading) }
            try {
                val result = lyricsRepository.autoCorrectLyrics(
                    rawLyrics = snapshot.lyricsDraft,
                    mode = snapshot.lyricMode,
                    uiLanguage = snapshot.language,
                    trackName = snapshot.trackName
                )
                _uiState.update { it.copy(lyricsAiState = LyricsAiState.Success(result)) }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Auto-correct lyrics timed out", e)
                _uiState.update {
                    it.copy(lyricsAiState = LyricsAiState.Error(strings.lyricsTimeoutError))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Auto-correct lyrics failed", e)
                _uiState.update {
                    it.copy(
                        lyricsAiState = LyricsAiState.Error(
                            e.localizedMessage ?: "Auto-correction failed."
                        )
                    )
                }
            }
        }
    }

    fun transcribeAndCorrectFromAudio() {
        val audio = loadedAudio ?: return
        val snapshot = _uiState.value
        val strings = studioStrings(snapshot.language)

        viewModelScope.launch {
            _uiState.update { it.copy(lyricsAiState = LyricsAiState.Loading) }
            try {
                val compactWav = AudioFileCodec.encodeCompactMonoWavForAi(audio)
                val result = lyricsRepository.transcribeAndCorrectFromAudio(
                    audio = audio,
                    wavBytes = compactWav,
                    draftLyrics = snapshot.lyricsDraft,
                    mode = snapshot.lyricMode,
                    uiLanguage = snapshot.language
                )
                _uiState.update { it.copy(lyricsAiState = LyricsAiState.Success(result)) }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Audio lyrics correction timed out", e)
                _uiState.update {
                    it.copy(lyricsAiState = LyricsAiState.Error(strings.lyricsTimeoutError))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio lyrics correction failed", e)
                _uiState.update {
                    it.copy(
                        lyricsAiState = LyricsAiState.Error(
                            e.localizedMessage ?: "Audio lyrics extraction failed."
                        )
                    )
                }
            }
        }
    }

    fun applyCorrectedLyrics() {
        val successState = _uiState.value.lyricsAiState as? LyricsAiState.Success ?: return
        _uiState.update {
            it.copy(lyricsDraft = successState.result.correctedLyrics)
        }
    }

    fun showLyricsCopiedBanner() {
        val strings = studioStrings(_uiState.value.language)
        _uiState.update {
            it.copy(bannerMessage = strings.lyricsCopiedMsg, isBannerError = false)
        }
    }

    fun suggestedExportFileName(): String {
        val base = _uiState.value.trackName
            ?.substringBeforeLast('.')
            ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            ?.takeIf { it.isNotBlank() }
            ?: "swara_studio"
        return "${base}_fixed.wav"
    }

    fun exportWavToUri(uri: Uri) {
        val audio = loadedAudio ?: return
        val stateSnapshot = _uiState.value
        val context = getApplication<Application>()

        viewModelScope.launch {
            _uiState.update {
                it.copy(isExporting = true, bannerMessage = null, isBannerError = false)
            }
            try {
                AudioFileCodec.exportWavToUri(
                    context = context,
                    uri = uri,
                    audio = audio,
                    params = stateSnapshot.params,
                    isBypassed = stateSnapshot.isBypassed
                )
                val strings = studioStrings(_uiState.value.language)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        bannerMessage = strings.exportSuccess,
                        isBannerError = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "WAV export failed", t)
                val strings = studioStrings(_uiState.value.language)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        bannerMessage = strings.exportErrorPrefix + (t.localizedMessage ?: "Write error"),
                        isBannerError = true
                    )
                }
            }
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        playbackEngine.release()
    }
}
