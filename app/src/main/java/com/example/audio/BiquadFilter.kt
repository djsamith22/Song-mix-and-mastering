package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class BiquadType {
    HIGHPASS,
    LOWSHELF,
    PEAKING,
    HIGHSHELF,
    BANDPASS
}

/**
 * RBJ Audio EQ Cookbook stereo/mono biquad filter with Double-precision state.
 */
class BiquadFilter(
    private val type: BiquadType,
    private val sampleRate: Double
) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // Filter delay states for up to 2 channels
    private var x1L = 0.0
    private var x2L = 0.0
    private var y1L = 0.0
    private var y2L = 0.0

    private var x1R = 0.0
    private var x2R = 0.0
    private var y1R = 0.0
    private var y2R = 0.0

    fun configure(freqHz: Double, q: Double = 0.70710678, gainDb: Double = 0.0) {
        val clampedFreq = freqHz.coerceIn(10.0, sampleRate * 0.45)
        val clampedQ = q.coerceAtLeast(0.1)
        val w0 = 2.0 * PI * clampedFreq / sampleRate
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / (2.0 * clampedQ)

        var rawB0 = 1.0
        var rawB1 = 0.0
        var rawB2 = 0.0
        var rawA0 = 1.0
        var rawA1 = 0.0
        var rawA2 = 0.0

        when (type) {
            BiquadType.HIGHPASS -> {
                rawB0 = (1.0 + cosW) / 2.0
                rawB1 = -(1.0 + cosW)
                rawB2 = (1.0 + cosW) / 2.0
                rawA0 = 1.0 + alpha
                rawA1 = -2.0 * cosW
                rawA2 = 1.0 - alpha
            }

            BiquadType.BANDPASS -> {
                rawB0 = alpha
                rawB1 = 0.0
                rawB2 = -alpha
                rawA0 = 1.0 + alpha
                rawA1 = -2.0 * cosW
                rawA2 = 1.0 - alpha
            }

            BiquadType.PEAKING -> {
                val a = 10.0.pow(gainDb / 40.0)
                rawB0 = 1.0 + alpha * a
                rawB1 = -2.0 * cosW
                rawB2 = 1.0 - alpha * a
                rawA0 = 1.0 + alpha / a
                rawA1 = -2.0 * cosW
                rawA2 = 1.0 - alpha / a
            }

            BiquadType.LOWSHELF -> {
                val a = 10.0.pow(gainDb / 40.0)
                val shelfAlpha = sinW / 2.0 * sqrt((a + 1.0 / a) * (1.0 / 1.0 - 1.0) + 2.0)
                val twoSqrtAAlpha = 2.0 * sqrt(a) * shelfAlpha
                rawB0 = a * ((a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha)
                rawB1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW)
                rawB2 = a * ((a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha)
                rawA0 = (a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha
                rawA1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW)
                rawA2 = (a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha
            }

            BiquadType.HIGHSHELF -> {
                val a = 10.0.pow(gainDb / 40.0)
                val shelfAlpha = sinW / 2.0 * sqrt((a + 1.0 / a) * (1.0 / 1.0 - 1.0) + 2.0)
                val twoSqrtAAlpha = 2.0 * sqrt(a) * shelfAlpha
                rawB0 = a * ((a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha)
                rawB1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW)
                rawB2 = a * ((a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha)
                rawA0 = (a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha
                rawA1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW)
                rawA2 = (a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha
            }
        }

        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    fun reset() {
        x1L = 0.0; x2L = 0.0; y1L = 0.0; y2L = 0.0
        x1R = 0.0; x2R = 0.0; y1R = 0.0; y2R = 0.0
    }

    fun processLeft(x0: Double): Double {
        val y0 = b0 * x0 + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L
        x1L = x0
        y2L = y1L
        y1L = y0
        return y0
    }

    fun processRight(x0: Double): Double {
        val y0 = b0 * x0 + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R
        x1R = x0
        y2R = y1R
        y1R = y0
        return y0
    }
}
