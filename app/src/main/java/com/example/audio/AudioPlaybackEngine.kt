package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Real-time AudioTrack playback engine running Swara Studio's MasteringDspChain
 * and 2048-point FFT analyzer on an audio-priority worker coroutine.
 */
class AudioPlaybackEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playbackJob: Job? = null

    private var currentAudio: DecodedAudio? = null
    private var dspChain: MasteringDspChain? = null
    private val fftAnalyzer = FftAnalyzer(fftSize = 2048, binCount = 40)

    @Volatile
    private var params = MasteringParams()

    @Volatile
    private var isBypassed = false

    @Volatile
    private var pauseFrame = 0

    @Volatile
    private var playbackGeneration = 0L

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    fun load(audio: DecodedAudio) {
        stopInternal(resetPosition = true)
        currentAudio = audio
        dspChain = MasteringDspChain(audio.sampleRate, audio.channelCount).apply {
            updateParams(params)
        }
        fftAnalyzer.reset()
        _snapshot.value = PlaybackSnapshot(
            isPlaying = false,
            currentSec = 0.0,
            spectrumBins = List(40) { 0f },
            gainReductionDb = 0f
        )
    }

    fun updateParams(newParams: MasteringParams) {
        params = newParams
        dspChain?.updateParams(newParams)
    }

    fun setBypassed(bypassed: Boolean) {
        isBypassed = bypassed
        if (!_snapshot.value.isPlaying) {
            _snapshot.value = _snapshot.value.copy(
                gainReductionDb = 0f
            )
        }
    }

    fun togglePlayPause() {
        if (_snapshot.value.isPlaying) {
            pause()
        } else {
            playFromFrame(pauseFrame)
        }
    }

    fun pause() {
        stopInternal(resetPosition = false)
        val audio = currentAudio
        val sec = if (audio != null) pauseFrame.toDouble() / audio.sampleRate else 0.0
        _snapshot.value = _snapshot.value.copy(
            isPlaying = false,
            currentSec = sec,
            spectrumBins = List(40) { 0f },
            gainReductionDb = 0f
        )
    }

    fun seekToSec(sec: Double) {
        val audio = currentAudio ?: return
        val clampedSec = sec.coerceIn(0.0, audio.durationSec)
        val targetFrame = (clampedSec * audio.sampleRate).toInt().coerceIn(0, audio.totalFrames)
        pauseFrame = targetFrame
        if (_snapshot.value.isPlaying) {
            playFromFrame(targetFrame)
        } else {
            _snapshot.value = _snapshot.value.copy(currentSec = clampedSec)
        }
    }

    private fun playFromFrame(startFrame: Int) {
        val audio = currentAudio ?: return
        val chain = dspChain ?: MasteringDspChain(audio.sampleRate, audio.channelCount).also {
            dspChain = it
        }
        stopInternal(resetPosition = false)

        val safeStart = if (startFrame >= audio.totalFrames - 1) 0 else startFrame.coerceAtLeast(0)
        pauseFrame = safeStart
        chain.reset()
        chain.updateParams(params)

        val gen = ++playbackGeneration
        playbackJob = scope.launch {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            } catch (_: Throwable) {
            }

            val ch = audio.channelCount
            val channelMask = if (ch >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
            val minBufBytes = AudioTrack.getMinBufferSize(
                audio.sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_FLOAT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(audio.sampleRate)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(minBufBytes * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val blockFrames = 2048
            val outBlock = FloatArray(blockFrames * ch)
            var frameCursor = safeStart

            try {
                track.play()
                _snapshot.value = _snapshot.value.copy(
                    isPlaying = true,
                    currentSec = frameCursor.toDouble() / audio.sampleRate
                )

                while (isActive && playbackGeneration == gen && frameCursor < audio.totalFrames) {
                    val framesNow = min(blockFrames, audio.totalFrames - frameCursor)
                    chain.updateParams(params)
                    chain.processBlock(
                        dryInterleaved = audio.interleavedSamples,
                        dryOffsetFloats = frameCursor * ch,
                        outInterleaved = outBlock,
                        frames = framesNow,
                        isBypassed = isBypassed
                    )

                    val writtenFloats = track.write(
                        outBlock,
                        0,
                        framesNow * ch,
                        AudioTrack.WRITE_BLOCKING
                    )
                    if (writtenFloats < 0) break

                    frameCursor += framesNow
                    pauseFrame = frameCursor

                    val bins = fftAnalyzer.computeSpectrumBins(outBlock, framesNow, ch)
                    val gr = if (isBypassed) 0f else chain.lastGainReductionDb
                    _snapshot.value = PlaybackSnapshot(
                        isPlaying = true,
                        currentSec = frameCursor.toDouble() / audio.sampleRate,
                        spectrumBins = bins,
                        gainReductionDb = gr
                    )
                }

                // Completed natural playback
                if (isActive && playbackGeneration == gen && frameCursor >= audio.totalFrames) {
                    pauseFrame = 0
                    fftAnalyzer.reset()
                    _snapshot.value = PlaybackSnapshot(
                        isPlaying = false,
                        currentSec = 0.0,
                        spectrumBins = List(40) { 0f },
                        gainReductionDb = 0f
                    )
                }
            } finally {
                try {
                    track.pause()
                    track.flush()
                    track.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun stopInternal(resetPosition: Boolean) {
        playbackGeneration++
        playbackJob?.cancel()
        playbackJob = null
        if (resetPosition) {
            pauseFrame = 0
        }
    }

    fun release() {
        stopInternal(resetPosition = true)
        scope.cancel()
    }
}
