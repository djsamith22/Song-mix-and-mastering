package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Computes waveform peaks (140 bars) and DSP flaw diagnostics matching index.html's runAudit().
 */
object AudioAuditEngine {

    fun extractWaveformPeaks(audio: DecodedAudio, barCount: Int = 140): List<Float> {
        val totalFrames = audio.totalFrames
        if (totalFrames <= 0) return emptyList()
        val ch = audio.channelCount
        val samples = audio.interleavedSamples
        val step = max(1, totalFrames / barCount)
        val peaks = ArrayList<Float>(barCount)

        for (i in 0 until barCount) {
            val startFrame = i * step
            val endFrame = min(totalFrames, (i + 1) * step)
            var peak = 0f
            var frame = startFrame
            while (frame < endFrame) {
                val sampleIdx = frame * ch
                if (sampleIdx < samples.size) {
                    val v = abs(samples[sampleIdx])
                    if (v > peak) peak = v
                }
                frame += 8
            }
            peaks.add(peak.coerceIn(0f, 1f))
        }
        return peaks
    }

    private fun measureBandDb(
        samples: FloatArray,
        channels: Int,
        totalFrames: Int,
        sr: Int,
        freq: Double,
        q: Double
    ): Double {
        val w0 = (2.0 * PI * freq) / sr.toDouble()
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosW
        val a2 = 1.0 - alpha
        val b0 = alpha
        val b1 = 0.0
        val b2 = -alpha

        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        var sq = 0.0
        val n = min(totalFrames, sr * 60)
        if (n <= 0) return -96.0

        val b0a = b0 / a0
        val b1a = b1 / a0
        val b2a = b2 / a0
        val a1a = a1 / a0
        val a2a = a2 / a0

        for (i in 0 until n) {
            val x0 = samples[i * channels].toDouble()
            val y0 = b0a * x0 + b1a * x1 + b2a * x2 - a1a * y1 - a2a * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            sq += y0 * y0
        }

        val rms = sqrt(sq / max(1, n))
        return if (rms > 1e-6) 20.0 * log10(rms) else -96.0
    }

    fun runAudit(audio: DecodedAudio): DiagnosticMetrics {
        val ch = audio.channelCount
        val samples = audio.interleavedSamples
        val frames = audio.totalFrames
        var peak = 0.0
        var sq = 0.0
        var count = 0

        var f = 0
        while (f < frames) {
            val s = samples[f * ch].toDouble()
            val a = abs(s)
            if (a > peak) peak = a
            sq += s * s
            count++
            f += 2
        }

        val rmsDb = 20.0 * log10(max(1e-6, sqrt(sq / max(1, count))))
        val peakDb = 20.0 * log10(max(1e-6, peak))

        val mudDb = measureBandDb(samples, ch, frames, audio.sampleRate, 250.0, 1.2)
        val vocDb = measureBandDb(samples, ch, frames, audio.sampleRate, 3200.0, 1.0)
        val harshDb = measureBandDb(samples, ch, frames, audio.sampleRate, 6800.0, 1.5)

        val issues = mutableListOf<DiagnosticIssueType>()
        if (mudDb - vocDb > 4.0) {
            issues.add(DiagnosticIssueType.MUD_EXCESS)
        }
        if (vocDb < rmsDb - 11.0) {
            issues.add(DiagnosticIssueType.LOW_VOCAL_PRESENCE)
        }
        if (harshDb > vocDb - 3.5) {
            issues.add(DiagnosticIssueType.HARSH_SIBILANCE)
        }
        if (peakDb > -1.0) {
            issues.add(DiagnosticIssueType.LOW_HEADROOM)
        }

        return DiagnosticMetrics(
            durationSec = audio.durationSec,
            sampleRate = audio.sampleRate,
            channelCount = audio.channelCount,
            peakDb = peakDb,
            rmsDb = rmsDb,
            issues = issues
        )
    }

    /**
     * Analyzes the song's spectral balance (sub, bass, mud, vocal, sibilance, treble) and crest
     * factor to synthesize tailored MasteringParams (Bass, Vocal, Treble, Dynamics) for that song.
     */
    fun analyzeSongAndRecommend(audio: DecodedAudio): Pair<DiagnosticMetrics, MasteringParams> {
        val metrics = runAudit(audio)
        val ch = audio.channelCount
        val samples = audio.interleavedSamples
        val frames = audio.totalFrames
        val sr = audio.sampleRate

        val subDb = measureBandDb(samples, ch, frames, sr, 42.0, 0.8)
        val bassDb = measureBandDb(samples, ch, frames, sr, 88.0, 0.9)
        val mudDb = measureBandDb(samples, ch, frames, sr, 250.0, 1.2)
        val vocDb = measureBandDb(samples, ch, frames, sr, 3200.0, 1.0)
        val harshDb = measureBandDb(samples, ch, frames, sr, 6800.0, 1.5)
        val airDb = measureBandDb(samples, ch, frames, sr, 11500.0, 0.8)

        val rmsDb = metrics.rmsDb
        val peakDb = metrics.peakDb
        val crestDb = (peakDb - rmsDb).coerceAtLeast(3.0)

        fun snapStep(value: Double, minV: Double, maxV: Double, step: Double): Float {
            val clamped = value.coerceIn(minV, maxV)
            val steps = kotlin.math.round(clamped / step)
            return (steps * step).coerceIn(minV, maxV).toFloat()
        }

        // Sub rumble -> tight HPF
        val recHpf = when {
            subDb > rmsDb - 6.5 -> 44f
            subDb > rmsDb - 10.0 -> 36f
            else -> 28f
        }

        // Bass warmth target ~ (rmsDb - 5.0)
        val recBass = snapStep((rmsDb - 5.0 - bassDb) * 0.55, -3.6, 4.2, 0.2)

        // Low-mid mud cut relative to vocal energy
        val mudExcess = mudDb - vocDb - 2.0
        val recMud = if (mudExcess > 0.0) {
            snapStep(-0.8 - mudExcess * 0.45, -4.8, 0.0, 0.2)
        } else {
            snapStep(-0.6, -4.8, 0.0, 0.2)
        }

        // Vocal presence target ~ (rmsDb - 8.5)
        val recVocal = snapStep((rmsDb - 8.5 - vocDb) * 0.55, -1.2, 4.4, 0.2)

        // Sibilance control relative to vocal
        val sibilanceExcess = harshDb - vocDb + 4.5
        val recDeess = if (sibilanceExcess > 0.0) {
            snapStep(-1.0 - sibilanceExcess * 0.45, -4.6, -0.4, 0.2)
        } else {
            -1.0f
        }

        // Treble / Studio Air target ~ (rmsDb - 14.5)
        val recTreble = snapStep((rmsDb - 14.5 - airDb) * 0.50, -1.8, 3.8, 0.2)

        // Dynamics tailored to crest factor & headroom
        val recThresh = when {
            crestDb > 13.0 -> -18f
            crestDb > 9.5 -> -15f
            else -> -11f
        }
        val recRatio = when {
            crestDb > 13.0 -> 3.0f
            crestDb > 9.5 -> 2.6f
            else -> 1.8f
        }
        val recGain = snapStep((-11.5 - rmsDb) * 0.35, -1.0, 3.2, 0.2)

        val recommended = MasteringParams(
            hpf = recHpf,
            low = recBass,
            lowFreq = 90f,
            mud = recMud,
            mudFreq = 250f,
            pres = recVocal,
            presFreq = 3200f,
            deess = recDeess,
            deessFreq = 6800f,
            air = recTreble,
            airFreq = 11000f,
            thresh = recThresh,
            ratio = recRatio,
            width = 1.08f,
            gain = recGain
        )

        return metrics to recommended
    }
}
