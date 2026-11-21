package com.example.ui

import com.example.ai.LyricCorrectionMode
import com.example.audio.AppLanguage
import com.example.audio.DiagnosticIssueType

data class StudioStrings(
    val appName: String,
    val langToggle: String,
    val uploadButton: String,
    val selectSong: String,
    val unloadedStats: String,
    val play: String,
    val pause: String,
    val abOrig: String,
    val abFixed: String,
    val exportWav: String,
    val rendering: String,
    val loadingAudio: String,
    val dropTitle: String,
    val dropSubtitle: String,
    val waveformHeader: String,
    val liveSpectrumHeader: String,
    val eqTitle: String,
    val dynamicsTitle: String,
    val hpfLabel: String,
    val lowLabel: String,
    val mudLabel: String,
    val presLabel: String,
    val deessLabel: String,
    val airLabel: String,
    val threshLabel: String,
    val ratioLabel: String,
    val widthLabel: String,
    val gainLabel: String,
    val limiterCeiling: String,
    val diagTitle: String,
    val diagEmpty: String,
    val diagBalanced: String,
    val exportSuccess: String,
    val decodeErrorPrefix: String,
    val exportErrorPrefix: String,
    // Auto Lyrics Corrector strings
    val lyricsTitle: String,
    val lyricsSubtitle: String,
    val lyricsPlaceholder: String,
    val lyricsModeSpelling: String,
    val lyricsModeSinglish: String,
    val lyricsModeRhythm: String,
    val lyricsAutoCorrectBtn: String,
    val lyricsFromAudioBtn: String,
    val lyricsProcessing: String,
    val lyricsEmptyInputError: String,
    val lyricsTimeoutError: String,
    val lyricsApplyBtn: String,
    val lyricsCopyBtn: String,
    val lyricsCopiedMsg: String,
    val lyricsCorrectedLabel: String,
    val lyricsFixesAppliedLabel: String,
    val lyricsNoChangesNeeded: String,
    val lyricsClearBtn: String,
    val lyricsApiConnectedBadge: String,
    val lyricsAutoApplyLabel: String,
    val lyricsRestoreOriginalBtn: String
)

fun studioStrings(lang: AppLanguage): StudioStrings = when (lang) {
    AppLanguage.SI -> StudioStrings(
        appName = "Swara Studio",
        langToggle = "සිංහල / EN",
        uploadButton = "ගීතයක් Upload කරන්න (MP3/WAV)",
        selectSong = "ගීතයක් තෝරන්න...",
        unloadedStats = "--:-- · -- kHz · Peak -- dBFS · RMS -- dBFS",
        play = "Play",
        pause = "Pause",
        abOrig = "A: මුල් ගීතය",
        abFixed = "B: Fixed Master",
        exportWav = "හැදූ WAV ගන්න",
        rendering = "Rendering...",
        loadingAudio = "Decoding audio...",
        dropTitle = "ඔබේ MP3 / WAV ගීතය මෙතනින් තෝරන්න",
        dropSubtitle = "Device එක ඇතුළේම අඩුපාඩු පරීක්ෂා කර EQ / Compressor යොදා WAV ලෙස save කළ හැක.",
        waveformHeader = "Waveform (Touch කර අවශ්‍ය තැනට යන්න)",
        liveSpectrumHeader = "Live Spectrum",
        eqTitle = "Parametric Repair EQ",
        dynamicsTitle = "Dynamics, Width & Limiter",
        hpfLabel = "Sub-Tight High-Pass",
        lowLabel = "808 / Bass Warmth (90 Hz)",
        mudLabel = "Mud Cut (250 Hz)",
        presLabel = "Vocal Clarity (3.2 kHz)",
        deessLabel = "De-Esser / Hi-Hat (6.8 kHz)",
        airLabel = "Studio Air (11 kHz)",
        threshLabel = "Compressor Threshold",
        ratioLabel = "Compression Ratio",
        widthLabel = "Stereo Width (Mid-Side)",
        gainLabel = "Output Makeup Gain",
        limiterCeiling = "Safety Limiter Ceiling",
        diagTitle = "හඳුනාගත් Audio අඩුපාඩු (DSP Audit)",
        diagEmpty = "ගීතයක් upload කළ පසු සංඛ්‍යාත සහ ශබ්ද අඩුපාඩු මෙහි දිස්වේ.",
        diagBalanced = "ගීතයේ සංඛ්‍යාත සමබරතාවය හොඳ මට්ටමක ඇත.",
        exportSuccess = "WAV file එක සාර්ථකව save විය.",
        decodeErrorPrefix = "Audio decode දෝෂයකි: ",
        exportErrorPrefix = "WAV export දෝෂයකි: ",
        lyricsTitle = "Auto Lyrics Corrector",
        lyricsSubtitle = "සිංහල, Singlish හෝ English ගීත පදවැල් ස්වයංක්‍රීයව නිවැරදි කරන්න (හෝ Upload කළ ගීතයෙන් Lyrics ලබාගන්න).",
        lyricsPlaceholder = "ඔබේ ගීතයේ Lyrics මෙහි type හෝ paste කරන්න...\n(උදා: Mage hitha laga inna oya...)",
        lyricsModeSpelling = "අක්ෂර හා ව්‍යාකරණ",
        lyricsModeSinglish = "Singlish → සිංහල",
        lyricsModeRhythm = "රිද්මය හා එළිසමය",
        lyricsAutoCorrectBtn = "Auto Correct Lyrics",
        lyricsFromAudioBtn = "ගීතයෙන් Lyrics ගෙන හදන්න",
        lyricsProcessing = "Lyrics නිවැරදි කරමින් පවතී...",
        lyricsEmptyInputError = "කරුණාකර Lyrics ඇතුළත් කරන්න, නැතහොත් ගීතයක් upload කර audio වලින් ලබාගන්න.",
        lyricsTimeoutError = "Lyrics නිවැරදි කිරීම කාලය ඉක්මවා ගියේය (60s). නැවත උත්සාහ කරන්න.",
        lyricsApplyBtn = "Editor වෙත යොදන්න",
        lyricsCopyBtn = "Copy",
        lyricsCopiedMsg = "නිවැරදි කළ Lyrics copy විය.",
        lyricsCorrectedLabel = "නිවැරදි කළ Lyrics",
        lyricsFixesAppliedLabel = "සිදු කළ නිවැරදි කිරීම්",
        lyricsNoChangesNeeded = "Lyrics දැනටමත් නිවැරදි මට්ටමක පවතී.",
        lyricsClearBtn = "Clear",
        lyricsApiConnectedBadge = "Gemini AI API සම්බන්ධයි",
        lyricsAutoApplyLabel = "නිවැරදි කළ වහාම Editor වෙත යොදන්න",
        lyricsRestoreOriginalBtn = "මුල් පදවැල ගන්න"
    )

    AppLanguage.EN -> StudioStrings(
        appName = "Swara Studio",
        langToggle = "සිංහල / EN",
        uploadButton = "Upload Song (MP3/WAV)",
        selectSong = "Select a song...",
        unloadedStats = "--:-- · -- kHz · Peak -- dBFS · RMS -- dBFS",
        play = "Play",
        pause = "Pause",
        abOrig = "A: Original",
        abFixed = "B: Fixed Master",
        exportWav = "Export Fixed WAV",
        rendering = "Rendering...",
        loadingAudio = "Decoding audio...",
        dropTitle = "Tap to Upload MP3 / WAV Song",
        dropSubtitle = "Analyze audio flaws on-device and apply EQ / Compressor to save as WAV.",
        waveformHeader = "Waveform (Tap or drag to seek)",
        liveSpectrumHeader = "Live Spectrum",
        eqTitle = "Parametric Repair EQ",
        dynamicsTitle = "Dynamics, Width & Limiter",
        hpfLabel = "Sub-Tight High-Pass",
        lowLabel = "808 / Bass Warmth (90 Hz)",
        mudLabel = "Mud Cut (250 Hz)",
        presLabel = "Vocal Clarity (3.2 kHz)",
        deessLabel = "De-Esser / Hi-Hat (6.8 kHz)",
        airLabel = "Studio Air (11 kHz)",
        threshLabel = "Compressor Threshold",
        ratioLabel = "Compression Ratio",
        widthLabel = "Stereo Width (Mid-Side)",
        gainLabel = "Output Makeup Gain",
        limiterCeiling = "Safety Limiter Ceiling",
        diagTitle = "Detected Audio Issues (DSP Audit)",
        diagEmpty = "Frequency and loudness flaws will appear here after uploading a song.",
        diagBalanced = "Frequency balance of the track is in a healthy range.",
        exportSuccess = "Mastered WAV saved successfully.",
        decodeErrorPrefix = "Audio decode failed: ",
        exportErrorPrefix = "WAV export failed: ",
        lyricsTitle = "Auto Lyrics Corrector",
        lyricsSubtitle = "Automatically fix Sinhala, Singlish, or English lyrics—or transcribe and correct directly from the uploaded song.",
        lyricsPlaceholder = "Type or paste your song lyrics here...\n(e.g., Mage hitha laga inna oya...)",
        lyricsModeSpelling = "Spelling & Grammar",
        lyricsModeSinglish = "Singlish → සිංහල",
        lyricsModeRhythm = "Rhythm & Rhyme",
        lyricsAutoCorrectBtn = "Auto Correct Lyrics",
        lyricsFromAudioBtn = "Extract & Fix from Song",
        lyricsProcessing = "Auto-correcting lyrics...",
        lyricsEmptyInputError = "Please enter draft lyrics, or upload a song to extract & correct from audio.",
        lyricsTimeoutError = "Lyrics correction timed out after 60 seconds. Please try again.",
        lyricsApplyBtn = "Apply to Editor",
        lyricsCopyBtn = "Copy",
        lyricsCopiedMsg = "Corrected lyrics copied to clipboard.",
        lyricsCorrectedLabel = "Corrected Lyrics",
        lyricsFixesAppliedLabel = "Corrections Applied",
        lyricsNoChangesNeeded = "Lyrics already look clean and well-formatted.",
        lyricsClearBtn = "Clear",
        lyricsApiConnectedBadge = "Gemini AI API Connected",
        lyricsAutoApplyLabel = "Auto-apply to editor",
        lyricsRestoreOriginalBtn = "Restore Original"
    )
}

fun lyricModeLabel(mode: LyricCorrectionMode, strings: StudioStrings): String = when (mode) {
    LyricCorrectionMode.SPELLING_GRAMMAR -> strings.lyricsModeSpelling
    LyricCorrectionMode.SINGLISH_TO_SINHALA -> strings.lyricsModeSinglish
    LyricCorrectionMode.RHYTHM_RHYME -> strings.lyricsModeRhythm
}

fun diagnosticIssueText(type: DiagnosticIssueType, lang: AppLanguage): Pair<String, String> =
    when (lang) {
        AppLanguage.SI -> when (type) {
            DiagnosticIssueType.MUD_EXCESS ->
                "250Hz Low-Mid Mud ගතිය වැඩියි" to
                    "808/Bass කලාපය Vocal හඬට බාධා කරයි. Mud Cut (-2.6 dB) නිර්දේශිතයි."
            DiagnosticIssueType.LOW_VOCAL_PRESENCE ->
                "Vocal Presence (3.2kHz) අඩුයි" to
                    "ගායන හඬ සංගීතයට යට වී ඇත. 3.2kHz (+2.8 dB) ඉහළ දමන්න."
            DiagnosticIssueType.HARSH_SIBILANCE ->
                "6.8kHz Hi-Hat / Sibilance තියුණුයි" to
                    "වැඩි ශබ්දයකදී කනට තියුණු ගතියක් දැනේ. De-Esser (-2.4 dB) යොදන්න."
            DiagnosticIssueType.LOW_HEADROOM ->
                "Streaming Headroom අඩුයි (Clipping අවදානම)" to
                    "Spotify/YouTube සඳහා -0.6 dBFS Safety Limiter සක්‍රීය කර ඇත."
        }

        AppLanguage.EN -> when (type) {
            DiagnosticIssueType.MUD_EXCESS ->
                "Excess 250Hz Low-Mid Mud" to
                    "808/Bass buildup is masking vocals. Mud Cut (-2.6 dB) recommended."
            DiagnosticIssueType.LOW_VOCAL_PRESENCE ->
                "Low Vocal Presence (3.2kHz)" to
                    "Lead vocal sits behind the instrumental. Boost 3.2kHz (+2.8 dB)."
            DiagnosticIssueType.HARSH_SIBILANCE ->
                "Harsh 6.8kHz Hi-Hat / Sibilance" to
                    "High frequencies sound sharp at loud volume. Apply De-Esser (-2.4 dB)."
            DiagnosticIssueType.LOW_HEADROOM ->
                "Low Streaming Headroom (Clipping Risk)" to
                    "-0.6 dBFS Safety Limiter is engaged for Spotify/YouTube loudness."
        }
    }

fun formatTimeSec(sec: Double): String {
    val safe = sec.coerceAtLeast(0.0).toInt()
    val m = safe / 60
    val s = safe % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
