package com.example.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Full mastering DSP chain mirroring Swara Studio's Web Audio graph:
 * HPF -> Low Shelf (90 Hz) -> Mud Cut (250 Hz) -> Vocal Presence (3.2 kHz)
 * -> De-Esser (6.8 kHz) -> Air Shelf (11 kHz) -> Soft-Knee Compressor
 * -> Mid-Side Stereo Width -> Makeup Gain -> -0.6 dBFS Safety Limiter,
 * plus real-time per-band energy & gain reduction metering.
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

    // 6-band envelope detectors for per-band real-time gain reduction overlay
    private val bandProbes = Array(6) { BiquadFilter(BiquadType.BANDPASS, sr) }
    private val bandEnvelopes = DoubleArray(6)
    private val probeDecay = exp(-1.0 / (0.080 * sr))
    private val probeAttack = exp(-1.0 / (0.010 * sr))

    // Compressor state (knee = 6 dB, attack = 8ms, release = 160ms)
    private val compAttackCoeff = exp(-1.0 / (0.008 * sr))
    private val compReleaseCoeff = exp(-1.0 / (0.160 * sr))
    private var compEnv = 0.0

    // Limiter state (threshold = -0.6 dBFS, ratio = 20:1, attack = 1ms, release = 60ms)
    private val limAttackCoeff = exp(-1.0 / (0.001 * sr))
    private val limReleaseCoeff = exp(-1.0 / (0.060 * sr))
    private var limEnv = 0.0

    // Smoothed scalar parameters to avoid zipper noise when adjusting sliders
    @Volatile
    private var currentParams = MasteringParams()
    private var smoothedWidth = currentParams.width.toDouble()
    private var smoothedMakeup = 10.0.pow(currentParams.gain / 20.0)
    private var bypassGain = 0.0 // 0.0 = wet (Fixed Master), 1.0 = dry (A: Original)

    @Volatile
    var lastGainReductionDb: Float = 0f
        private set

    @Volatile
    var lastLimiterGrDb: Float = 0f
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
        bandProbes.forEach { it.reset() }
        bandEnvelopes.fill(0.0)
        compEnv = 0.0
        limEnv = 0.0
        lastGainReductionDb = 0f
        lastLimiterGrDb = 0f
    }

    fun updateParams(params: MasteringParams) {
        if (params != currentParams) {
            currentParams = params
            applyFilterCoeffs(params)
        }
    }

    private fun applyFilterCoeffs(p: MasteringParams) {
        val hpfHz = p.hpf.toDouble().coerceIn(20.0, 250.0)
        val lowHz = p.lowFreq.toDouble().coerceIn(40.0, 300.0)
        val mudHz = p.mudFreq.toDouble().coerceIn(120.0, 1000.0)
        val presHz = p.presFreq.toDouble().coerceIn(800.0, 8000.0)
        val deessHz = p.deessFreq.toDouble().coerceIn(2500.0, 14000.0)
        val airHz = p.airFreq.toDouble().coerceIn(6000.0, 18000.0)

        hpf.configure(freqHz = hpfHz, q = 0.70710678)
        low.configure(freqHz = lowHz, q = 0.70710678, gainDb = p.low.toDouble())
        mud.configure(freqHz = mudHz, q = 1.4, gainDb = p.mud.toDouble())
        pres.configure(freqHz = presHz, q = 1.2, gainDb = p.pres.toDouble())
        deess.configure(freqHz = deessHz, q = 2.2, gainDb = p.deess.toDouble())
        air.configure(freqHz = airHz, q = 0.70710678, gainDb = p.air.toDouble())

        bandProbes[0].configure(freqHz = hpfHz.coerceAtLeast(24.0), q = 1.0)
        bandProbes[1].configure(freqHz = lowHz, q = 1.2)
        bandProbes[2].configure(freqHz = mudHz, q = 1.4)
        bandProbes[3].configure(freqHz = presHz, q = 1.2)
        bandProbes[4].configure(freqHz = deessHz, q = 2.0)
        bandProbes[5].configure(freqHz = airHz, q = 1.2)
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
                (1.0 / ratio - 1.0) * (x * x) / (2.0 * kneeDb)
            }
        }
    }

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

        var minCompGrDb = 0.0
        var minLimGrDb = 0.0

        if (channels >= 2) {
            for (f in 0 until frames) {
                val idx = f * 2
                val dryIdx = dryOffsetFloats + idx
                val dryL = dryInterleaved[dryIdx].toDouble()
                val dryR = dryInterleaved[dryIdx + 1].toDouble()

                // Measure per-band input energy on mono sum (every 2nd sample saves CPU with identical envelope)
                if ((f and 1) == 0) {
                    val mono = 0.5 * (dryL + dryR)
                    for (b in 0 until 6) {
                        val bp = abs(bandProbes[b].processLeft(mono))
                        val env = bandEnvelopes[b]
                        bandEnvelopes[b] = if (bp > env) {
                            probeAttack * env + (1.0 - probeAttack) * bp
                        } else {
                            probeDecay * env + (1.0 - probeDecay) * bp
                        }
                    }
                }

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
                if (grDb < minCompGrDb) minCompGrDb = grDb
                val compGain = 10.0.pow(grDb / 20.0)
                wL *= compGain
                wR *= compGain

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
                if (limGrDb < minLimGrDb) minLimGrDb = limGrDb
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

                if ((f and 1) == 0) {
                    for (b in 0 until 6) {
                        val bp = abs(bandProbes[b].processLeft(dry))
                        val env = bandEnvelopes[b]
                        bandEnvelopes[b] = if (bp > env) {
                            probeAttack * env + (1.0 - probeAttack) * bp
                        } else {
                            probeDecay * env + (1.0 - probeDecay) * bp
                        }
                    }
                }

                var w = air.processLeft(deess.processLeft(pres.processLeft(mud.processLeft(low.processLeft(hpf.processLeft(dry))))))

                val peakIn = abs(w)
                compEnv = if (peakIn > compEnv) {
                    compAttackCoeff * compEnv + (1.0 - compAttackCoeff) * peakIn
                } else {
                    compReleaseCoeff * compEnv + (1.0 - compReleaseCoeff) * peakIn
                }
                val envDb = 20.0 * log10(max(compEnv, 1e-7))
                val grDb = computeCompressorGainDb(envDb, thresh, ratio, 6.0)
                if (grDb < minCompGrDb) minCompGrDb = grDb
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
                if (limGrDb < minLimGrDb) minLimGrDb = limGrDb
                w = (w * 10.0.pow(limGrDb / 20.0)).coerceIn(-0.999, 0.999)

                val wetMix = 1.0 - bypassGain
                outInterleaved[f] = (dry * bypassGain + w * wetMix).toFloat()
            }
        }

        lastGainReductionDb = if (isBypassed) 0f else minCompGrDb.toFloat()
        lastLimiterGrDb = if (isBypassed) 0f else minLimGrDb.toFloat()
    }

    /**
     * Returns real-time per-band EQ gain + live dynamic gain reduction at each of the 6 EQ frequencies.
     */
    fun currentBandTelemetry(isBypassed: Boolean, isPlaying: Boolean): List<BandTelemetry> {
        val p = currentParams
        val bandSpecs = listOf(
            Triple(0, "HPF", p.hpf),
            Triple(1, formatShortFreq(p.lowFreq), p.lowFreq),
            Triple(2, formatShortFreq(p.mudFreq), p.mudFreq),
            Triple(3, formatShortFreq(p.presFreq), p.presFreq),
            Triple(4, formatShortFreq(p.deessFreq), p.deessFreq),
            Triple(5, formatShortFreq(p.airFreq), p.airFreq)
        )
        val totalDynGr = if (isBypassed || !isPlaying) 0f else (lastGainReductionDb + lastLimiterGrDb)

        return bandSpecs.map { (idx, label, freq) ->
            val evalFreq = if (idx == 0) freq.toDouble() else freq.toDouble()
            val eqGainDb = totalEqDbAt(evalFreq).toFloat()
            val env = if (isPlaying) bandEnvelopes[idx] else 0.0
            val envDb = 20.0 * log10(max(env, 1e-6))
            // Map band energy -54 dBFS .. -6 dBFS -> 0f .. 1f
            val levelNorm = ((envDb + 54.0) / 48.0).toFloat().coerceIn(0f, 1f)
            // Dynamic gain reduction weighted by active energy in that band
            val bandDynGr = if (isBypassed || !isPlaying) {
                0f
            } else {
                totalDynGr * (0.25f + 0.75f * levelNorm)
            }
            val staticCut = min(0f, eqGainDb)
            val totalGr = (staticCut + bandDynGr).coerceAtLeast(-18f)

            BandTelemetry(
                bandIndex = idx,
                shortLabel = label,
                freqHz = freq,
                eqGainDb = eqGainDb,
                dynamicGrDb = bandDynGr,
                totalGrDb = totalGr,
                levelNorm = levelNorm
            )
        }
    }

    fun totalEqDbAt(freqHz: Double): Double {
        return hpf.magnitudeDbAt(freqHz) +
            low.magnitudeDbAt(freqHz) +
            mud.magnitudeDbAt(freqHz) +
            pres.magnitudeDbAt(freqHz) +
            deess.magnitudeDbAt(freqHz) +
            air.magnitudeDbAt(freqHz)
    }

    companion object {
        /**
         * Static curve helper usable even before an audio file is loaded.
         */
        fun computeStaticCurveAndTelemetry(
            params: MasteringParams,
            sampleRate: Int = 44100,
            isBypassed: Boolean = false
        ): Pair<List<Float>, List<BandTelemetry>> {
            val chain = MasteringDspChain(sampleRate, 2).apply {
                updateParams(params)
            }
            val points = 96
            val minLog = log10(20.0)
            val maxLog = log10(20000.0)
            val curve = List(points) { i ->
                val f = 10.0.pow(minLog + (i.toDouble() / (points - 1)) * (maxLog - minLog))
                chain.totalEqDbAt(f).toFloat()
            }
            val telemetry = chain.currentBandTelemetry(isBypassed = isBypassed, isPlaying = false)
            return curve to telemetry
        }

        private fun formatShortFreq(freqHz: Float): String {
            val hz = freqHz.toInt()
            return if (hz < 1000) {
                "${hz}Hz"
            } else {
                val khz = hz / 1000.0
                if (hz % 1000 == 0) "${hz / 1000}k" else String.format("%.1fk", khz)
            }
        }
    }
}
