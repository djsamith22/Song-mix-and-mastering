package com.example.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Full mastering DSP chain mirroring Swara Studio's Web Audio graph:
 * HPF -> Low Shelf (90 Hz) -> Mud Cut (250 Hz) -> Vocal Presence (3.2 kHz)
 * -> De-Esser (6.8 kHz) -> Air Shelf (11 kHz) -> Soft-Knee Compressor
 * -> Mid-Side Stereo Width -> Makeup Gain -> -0.6 dBFS Safety Limiter.
 */
class MasteringDspChain(
    private val sampleRate: Int,
    private val channels: Int
) {
    private val sr = sampleRate.toDouble().coerceAtLeast(8000.0)

    private val hpf = BiquadFilter(BiquadType.HIGHPASS, sr)
    private val low = BiquadFilter(BiquadType.LOWSHELF, sr)
    private val mud = BiquadFilter(BiquadType.PEAKING, sr)
    private val pres = BiquadFilter(BiquadType.PEAKING, sr)
    private val deess = BiquadFilter(BiquadType.PEAKING, sr)
    private val air = BiquadFilter(BiquadType.HIGHSHELF, sr)

    // Compressor state (knee = 6 dB, attack = 8ms, release = 160ms)
    private val compAttackCoeff = exp(-1.0 / (0.008 * sr))
    private val compReleaseCoeff = exp(-1.0 / (0.160 * sr))
    private var compEnv = 0.0

    // Limiter state (threshold = -0.6 dBFS, ratio = 20:1, attack = 1ms, release = 60ms)
    private val limAttackCoeff = exp(-1.0 / (0.001 * sr))
    private val limReleaseCoeff = exp(-1.0 / (0.060 * sr))
    private var limEnv = 0.0

    // Smoothed scalar parameters to avoid zipper noise when adjusting sliders
    private var currentParams = MasteringParams()
    private var smoothedWidth = currentParams.width.toDouble()
    private var smoothedMakeup = 10.0.pow(currentParams.gain / 20.0)
    private var bypassGain = 0.0 // 0.0 = wet (Fixed Master), 1.0 = dry (A: Original)

    @Volatile
    var lastGainReductionDb: Float = 0f
        private set

    init {
        applyFilterCoeffs(currentParams)
    }

    fun reset() {
        hpf.reset()
        low.reset()
        mud.reset()
        pres.reset()
        deess.reset()
        air.reset()
        compEnv = 0.0
        limEnv = 0.0
        lastGainReductionDb = 0f
    }

    fun updateParams(params: MasteringParams) {
        if (params != currentParams) {
            currentParams = params
            applyFilterCoeffs(params)
        }
    }

    private fun applyFilterCoeffs(p: MasteringParams) {
        hpf.configure(freqHz = p.hpf.toDouble(), q = 0.70710678)
        low.configure(freqHz = 90.0, q = 0.70710678, gainDb = p.low.toDouble())
        mud.configure(freqHz = 250.0, q = 1.4, gainDb = p.mud.toDouble())
        pres.configure(freqHz = 3200.0, q = 1.2, gainDb = p.pres.toDouble())
        deess.configure(freqHz = 6800.0, q = 2.2, gainDb = p.deess.toDouble())
        air.configure(freqHz = 11000.0, q = 0.70710678, gainDb = p.air.toDouble())
    }

    private fun computeCompressorGainDb(inputLevelDb: Double, threshDb: Double, ratio: Double, kneeDb: Double): Double {
        if (ratio <= 1.001) return 0.0
        val halfKnee = kneeDb / 2.0
        val overshoot = inputLevelDb - threshDb
        return when {
            overshoot <= -halfKnee -> 0.0
            overshoot >= halfKnee -> {
                val compressed = threshDb + overshoot / ratio
                compressed - inputLevelDb
            }
            else -> {
                val x = overshoot + halfKnee
                val gainChange = (1.0 / ratio - 1.0) * (x * x) / (2.0 * kneeDb)
                gainChange
            }
        }
    }

    /**
     * Processes [frames] frames from [dryInterleaved] starting at [dryOffsetFloats]
     * and writes final mixed output to [outInterleaved].
     * Always processes the wet chain so live slider adjustments & GR stay smooth even when bypassed.
     */
    fun processBlock(
        dryInterleaved: FloatArray,
        dryOffsetFloats: Int,
        outInterleaved: FloatArray,
        frames: Int,
        isBypassed: Boolean
    ) {
        val p = currentParams
        val targetWidth = p.width.toDouble()
        val targetMakeup = 10.0.pow(p.gain.toDouble() / 20.0)
        val targetBypass = if (isBypassed) 1.0 else 0.0
        val thresh = p.thresh.toDouble()
        val ratio = p.ratio.toDouble()

        var minGrDb = 0.0

        if (channels >= 2) {
            for (f in 0 until frames) {
                val idx = f * 2
                val dryIdx = dryOffsetFloats + idx
                val dryL = dryInterleaved[dryIdx].toDouble()
                val dryR = dryInterleaved[dryIdx + 1].toDouble()

                // 6-band EQ
                var wL = air.processLeft(deess.processLeft(pres.processLeft(mud.processLeft(low.processLeft(hpf.processLeft(dryL))))))
                var wR = air.processRight(deess.processRight(pres.processRight(mud.processRight(low.processRight(hpf.processRight(dryR))))))

                // Compressor envelope follower (linked stereo peak)
                val peakIn = max(abs(wL), abs(wR))
                compEnv = if (peakIn > compEnv) {
                    compAttackCoeff * compEnv + (1.0 - compAttackCoeff) * peakIn
                } else {
                    compReleaseCoeff * compEnv + (1.0 - compReleaseCoeff) * peakIn
                }
                val envDb = 20.0 * log10(max(compEnv, 1e-7))
                val grDb = computeCompressorGainDb(envDb, thresh, ratio, 6.0)
                if (grDb < minGrDb) minGrDb = grDb
                val compGain = 10.0.pow(grDb / 20.0)
                wL *= compGain
                wR *= compGain

                // Smooth width & makeup
                smoothedWidth += (targetWidth - smoothedWidth) * 0.002
                smoothedMakeup += (targetMakeup - smoothedMakeup) * 0.002
                bypassGain += (targetBypass - bypassGain) * 0.004

                // Mid-Side Stereo Width
                val mid = 0.5 * (wL + wR)
                val side = 0.5 * (wL - wR) * smoothedWidth
                wL = (mid + side) * smoothedMakeup
                wR = (mid - side) * smoothedMakeup

                // Safety Limiter (-0.6 dBFS, 20:1)
                val limPeak = max(abs(wL), abs(wR))
                limEnv = if (limPeak > limEnv) {
                    limAttackCoeff * limEnv + (1.0 - limAttackCoeff) * limPeak
                } else {
                    limReleaseCoeff * limEnv + (1.0 - limReleaseCoeff) * limPeak
                }
                val limEnvDb = 20.0 * log10(max(limEnv, 1e-7))
                val limGrDb = computeCompressorGainDb(limEnvDb, -0.6, 20.0, 1.0)
                val limGain = 10.0.pow(limGrDb / 20.0)
                wL = (wL * limGain).coerceIn(-0.999, 0.999)
                wR = (wR * limGain).coerceIn(-0.999, 0.999)

                val wetMix = 1.0 - bypassGain
                outInterleaved[idx] = (dryL * bypassGain + wL * wetMix).toFloat()
                outInterleaved[idx + 1] = (dryR * bypassGain + wR * wetMix).toFloat()
            }
        } else {
            for (f in 0 until frames) {
                val dryIdx = dryOffsetFloats + f
                val dry = dryInterleaved[dryIdx].toDouble()

                var w = air.processLeft(deess.processLeft(pres.processLeft(mud.processLeft(low.processLeft(hpf.processLeft(dry))))))

                val peakIn = abs(w)
                compEnv = if (peakIn > compEnv) {
                    compAttackCoeff * compEnv + (1.0 - compAttackCoeff) * peakIn
                } else {
                    compReleaseCoeff * compEnv + (1.0 - compReleaseCoeff) * peakIn
                }
                val envDb = 20.0 * log10(max(compEnv, 1e-7))
                val grDb = computeCompressorGainDb(envDb, thresh, ratio, 6.0)
                if (grDb < minGrDb) minGrDb = grDb
                w *= 10.0.pow(grDb / 20.0)

                smoothedMakeup += (targetMakeup - smoothedMakeup) * 0.002
                bypassGain += (targetBypass - bypassGain) * 0.004
                w *= smoothedMakeup

                val limPeak = abs(w)
                limEnv = if (limPeak > limEnv) {
                    limAttackCoeff * limEnv + (1.0 - limAttackCoeff) * limPeak
                } else {
                    limReleaseCoeff * limEnv + (1.0 - limReleaseCoeff) * limPeak
                }
                val limEnvDb = 20.0 * log10(max(limEnv, 1e-7))
                val limGrDb = computeCompressorGainDb(limEnvDb, -0.6, 20.0, 1.0)
                w = (w * 10.0.pow(limGrDb / 20.0)).coerceIn(-0.999, 0.999)

                val wetMix = 1.0 - bypassGain
                outInterleaved[f] = (dry * bypassGain + w * wetMix).toFloat()
            }
        }

        lastGainReductionDb = if (isBypassed) 0f else minGrDb.toFloat()
    }
}
