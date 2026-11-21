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
}
