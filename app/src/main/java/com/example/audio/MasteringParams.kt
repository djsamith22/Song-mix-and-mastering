package com.example.audio

enum class AppLanguage {
    SI,
    EN
}

data class MasteringParams(
    val hpf: Float = 34f,
    val low: Float = -1.0f,
    val lowFreq: Float = 90f,
    val mud: Float = -2.6f,
    val mudFreq: Float = 250f,
    val pres: Float = 2.8f,
    val presFreq: Float = 3200f,
    val deess: Float = -2.4f,
    val deessFreq: Float = 6800f,
    val air: Float = 1.8f,
    val airFreq: Float = 11000f,
    val thresh: Float = -15f,
    val ratio: Float = 2.8f,
    val width: Float = 1.08f,
    val gain: Float = 1.5f
)

data class MasteringPreset(
    val id: String,
    val name: String,
    val params: MasteringParams
)

val MASTERING_PRESETS: List<MasteringPreset> = listOf(
    MasteringPreset(
        id = "rec",
        name = "Recommended Fix",
        params = MasteringParams(
            hpf = 34f,
            low = -1.0f,
            mud = -2.6f,
            pres = 2.8f,
            deess = -2.4f,
            air = 1.8f,
            thresh = -15f,
            ratio = 2.8f,
            width = 1.08f,
            gain = 1.5f
        )
    ),
    MasteringPreset(
        id = "trap",
        name = "Trap & Rap Punch",
        params = MasteringParams(
            hpf = 32f,
            low = 1.2f,
            mud = -3.0f,
            pres = 2.2f,
            deess = -1.8f,
            air = 2.0f,
            thresh = -16f,
            ratio = 3.2f,
            width = 1.12f,
            gain = 1.8f
        )
    ),
    MasteringPreset(
        id = "vocal",
        name = "Vocal Clarity",
        params = MasteringParams(
            hpf = 42f,
            low = -1.5f,
            mud = -3.2f,
            pres = 3.6f,
            deess = -2.8f,
            air = 2.4f,
            thresh = -14f,
            ratio = 2.5f,
            width = 0.96f,
            gain = 1.6f
        )
    ),
    MasteringPreset(
        id = "flat",
        name = "Flat (Reset)",
        params = MasteringParams(
            hpf = 20f,
            low = 0f,
            mud = 0f,
            pres = 0f,
            deess = 0f,
            air = 0f,
            thresh = -6f,
            ratio = 1.5f,
            width = 1.0f,
            gain = 0f
        )
    )
)

enum class DiagnosticIssueType {
    MUD_EXCESS,
    LOW_VOCAL_PRESENCE,
    HARSH_SIBILANCE,
    LOW_HEADROOM
}

data class DiagnosticMetrics(
    val durationSec: Double,
    val sampleRate: Int,
    val channelCount: Int,
    val peakDb: Double,
    val rmsDb: Double,
    val issues: List<DiagnosticIssueType>
)

class DecodedAudio(
    val displayName: String,
    val sampleRate: Int,
    val channelCount: Int,
    val interleavedSamples: FloatArray,
    val totalFrames: Int,
    val durationSec: Double
)

data class BandTelemetry(
    val bandIndex: Int,
    val shortLabel: String,
    val freqHz: Float,
    val eqGainDb: Float,
    val dynamicGrDb: Float,
    val totalGrDb: Float,
    val levelNorm: Float
)

data class PlaybackSnapshot(
    val isPlaying: Boolean = false,
    val currentSec: Double = 0.0,
    val spectrumBins: List<Float> = List(40) { 0f },
    val gainReductionDb: Float = 0f,
    val bandTelemetry: List<BandTelemetry> = emptyList()
)
