package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * 2048-point Radix-2 Cooley-Tukey FFT analyzer producing 40 log-spaced spectrum bins (0f..1f)
 * matching Web Audio's AnalyserNode visual response in index.html.
 */
class FftAnalyzer(
    private val fftSize: Int = 2048,
    private val binCount: Int = 40
) {
    private val real = DoubleArray(fftSize)
    private val imag = DoubleArray(fftSize)
    private val window = DoubleArray(fftSize) { i ->
        0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
    }
    private val magnitudes = DoubleArray(fftSize / 2)
    private val smoothedBins = FloatArray(binCount)

    fun computeSpectrumBins(interleaved: FloatArray, framesAvailable: Int, channels: Int): List<Float> {
        real.fill(0.0)
        imag.fill(0.0)

        val copyFrames = minOf(fftSize, framesAvailable)
        if (channels >= 2) {
            for (i in 0 until copyFrames) {
                val mono = 0.5 * (interleaved[i * 2] + interleaved[i * 2 + 1])
                real[i] = mono * window[i]
            }
        } else {
            for (i in 0 until copyFrames) {
                real[i] = interleaved[i] * window[i]
            }
        }

        fftInPlace(real, imag)

        val half = fftSize / 2
        // Convert to normalized dB scale (-90 dB .. -10 dB) similar to Web Audio AnalyserNode byte data
        val minDb = -85.0
        val maxDb = -10.0
        val dbRange = maxDb - minDb
        for (i in 0 until half) {
            val mag = hypot(real[i], imag[i]) / (fftSize / 2.0)
            val db = 20.0 * log10(max(mag, 1e-7))
            magnitudes[i] = ((db - minDb) / dbRange).coerceIn(0.0, 1.0)
        }

        val out = MutableList(binCount) { 0f }
        for (i in 0 until binCount) {
            val idx = (i.toDouble() / binCount).pow(1.8) * half * 0.7
            val binIndex = idx.toInt().coerceIn(0, half - 1)
            val target = magnitudes[binIndex].toFloat()
            // Light temporal smoothing for crisp 60fps meter
            val prev = smoothedBins[i]
            val smoothed = if (target > prev) target else prev * 0.72f + target * 0.28f
            smoothedBins[i] = smoothed
            out[i] = smoothed
        }
        return out
    }

    fun reset() {
        smoothedBins.fill(0f)
    }

    private fun fftInPlace(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val ang = -2.0 * PI / len
            val wLenRe = cos(ang)
            val wLenIm = sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until halfLen) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val tRe = re[i + k + halfLen] * wRe - im[i + k + halfLen] * wIm
                    val tIm = re[i + k + halfLen] * wIm + im[i + k + halfLen] * wRe
                    re[i + k] = uRe + tRe
                    im[i + k] = uIm + tIm
                    re[i + k + halfLen] = uRe - tRe
                    im[i + k + halfLen] = uIm - tIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
