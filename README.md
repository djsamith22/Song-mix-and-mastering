# Swara Studio — Song Doctor & Mastering (Android)

Native Android (Kotlin + Jetpack Compose) audio mastering, 6-band parametric repair EQ, dynamics compressor, mid-side stereo width processor, and on-device DSP flaw analyzer.

## Features

- **Audio Import & Playback**: Zero-permission Storage Access Framework (`OpenDocument`) audio import for MP3, WAV, M4A, AAC, OGG, and FLAC decoded via `MediaExtractor` / `MediaCodec` and streamed in real time through `AudioTrack`.
- **Interactive Waveform & Live FFT Spectrum**: 140-bar tap/drag-to-seek waveform display and 40-bin logarithmic 2048-point FFT spectrum analyzer with live compressor Gain Reduction (`GR`) telemetry.
- **6-Band Parametric Repair EQ**: Sub-Tight High-Pass (`20–90 Hz`), 808/Bass Warmth (`90 Hz` low shelf), Mud Cut (`250 Hz`), Vocal Clarity (`3.2 kHz`), De-Esser / Hi-Hat (`6.8 kHz`), and Studio Air (`11 kHz` high shelf).
- **Dynamics, Width & Safety Limiter**: Soft-knee compressor (Threshold & Ratio), Mid-Side Stereo Width (`0.60x–1.45x`), Output Makeup Gain (`-6..+6 dB`), and a `-0.6 dBFS` safety limiter.
- **Presets & Instant A/B Bypass**: `Recommended Fix`, `Trap & Rap Punch`, `Vocal Clarity`, and `Flat (Reset)` presets with smooth `A: Original` vs `B: Fixed Master` comparison.
- **On-Device DSP Audit**: Analyzes Peak/RMS levels and frequency balance across 250 Hz, 3.2 kHz, and 6.8 kHz bands to report actionable mixing/mastering issues.
- **Mastered WAV Export**: Offline rendering through the full mastering DSP chain to 16-bit PCM RIFF WAV via `CreateDocument`.
- **Bilingual UI**: Instant Sinhala (`සිංහල`) and English (`EN`) toggle.