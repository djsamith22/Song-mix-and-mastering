package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.MASTERING_PRESETS
import com.example.audio.MasteringParams
import com.example.audio.MasteringPreset
import com.example.ui.theme.StudioColors
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSansFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwaraStudioScreen(
    state: SwaraUiState,
    onToggleLanguage: () -> Unit,
    onPickAudioClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSelectBypass: (Boolean) -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSelectPreset: (MasteringPreset) -> Unit,
    onAutoFitSong: () -> Unit,
    onUpdateParams: ((MasteringParams) -> MasteringParams) -> Unit,
    onExportWavClick: () -> Unit,
    onDismissBanner: () -> Unit,
    onLyricsChange: (String) -> Unit,
    onClearLyrics: () -> Unit,
    onSelectLyricMode: (com.example.ai.LyricCorrectionMode) -> Unit,
    onToggleAutoApplyLyrics: () -> Unit,
    onAutoCorrectLyrics: () -> Unit,
    onExtractLyricsFromAudio: () -> Unit,
    onApplyCorrectedLyrics: () -> Unit,
    onRestoreOriginalLyrics: () -> Unit,
    onLyricsCopied: () -> Unit
) {
    val strings = studioStrings(state.language)

    Scaffold(
        containerColor = StudioColors.Background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            StudioTopBar(
                strings = strings,
                isLoadingAudio = state.isLoadingAudio,
                onToggleLanguage = onToggleLanguage,
                onPickAudioClick = onPickAudioClick
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(StudioColors.Background),
            contentAlignment = Alignment.TopCenter
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1320.dp)
            ) {
                val isWide = maxWidth >= 720.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status / Error Banner
                    AnimatedVisibility(visible = state.bannerMessage != null || state.isLoadingAudio) {
                        val msg = if (state.isLoadingAudio) strings.loadingAudio else state.bannerMessage.orEmpty()
                        val borderColor = when {
                            state.isLoadingAudio -> StudioColors.Amber
                            state.isBannerError -> StudioColors.ErrorRed
                            else -> StudioColors.EmeraldBright
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(StudioColors.Surface)
                                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (state.isLoadingAudio) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = StudioColors.Amber,
                                    strokeWidth = 2.dp
                                )
                            }
                            Text(
                                text = msg,
                                fontFamily = StudioSansFontFamily,
                                fontSize = 13.sp,
                                color = StudioColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (!state.isLoadingAudio) {
                                IconButton(
                                    onClick = onDismissBanner,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Dismiss message",
                                        tint = StudioColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Transport & A/B Bar
                    TransportAndBypassCard(
                        strings = strings,
                        state = state,
                        onTogglePlayPause = onTogglePlayPause,
                        onSelectBypass = onSelectBypass,
                        onExportWavClick = onExportWavClick
                    )

                    // Upload Prompt Banner (shown until audio is loaded)
                    AnimatedVisibility(visible = !state.isAudioLoaded) {
                        UploadDropZoneCard(
                            strings = strings,
                            onClick = onPickAudioClick
                        )
                    }

                    // Waveform & Live Spectrum
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            WaveformCard(
                                headerText = strings.waveformHeader,
                                peaks = state.waveformPeaks,
                                currentSec = state.currentSec,
                                durationSec = state.durationSec,
                                isAudioLoaded = state.isAudioLoaded,
                                isBypassed = state.isBypassed,
                                onSeekFraction = onSeekFraction,
                                modifier = Modifier.weight(8f)
                            )
                            LiveSpectrumCard(
                                headerText = strings.liveSpectrumHeader,
                                spectrumBins = state.spectrumBins,
                                gainReductionDb = state.gainReductionDb,
                                isPlaying = state.isPlaying,
                                isBypassed = state.isBypassed,
                                modifier = Modifier.weight(4f)
                            )
                        }
                    } else {
                        WaveformCard(
                            headerText = strings.waveformHeader,
                            peaks = state.waveformPeaks,
                            currentSec = state.currentSec,
                            durationSec = state.durationSec,
                            isAudioLoaded = state.isAudioLoaded,
                            isBypassed = state.isBypassed,
                            onSeekFraction = onSeekFraction
                        )
                        LiveSpectrumCard(
                            headerText = strings.liveSpectrumHeader,
                            spectrumBins = state.spectrumBins,
                            gainReductionDb = state.gainReductionDb,
                            isPlaying = state.isPlaying,
                            isBypassed = state.isBypassed
                        )
                    }

                    // Presets Bar
                    PresetBar(
                        activePresetId = state.activePresetId,
                        onSelectPreset = onSelectPreset
                    )

                    // Song Tone Sculptor (Bass / Vocal / Treble) + Song-Adaptive Auto-Fit
                    QuickTonePanel(
                        strings = strings,
                        params = state.params,
                        isAudioLoaded = state.isAudioLoaded,
                        isAutoMatchActive = state.activePresetId == "auto",
                        onAutoFitClick = onAutoFitSong,
                        onUpdateParams = onUpdateParams
                    )

                    // EQ & Dynamics Controls
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            ParametricEqPanel(
                                strings = strings,
                                params = state.params,
                                eqCurveDb = state.eqCurveDb,
                                bandTelemetry = state.bandTelemetry,
                                isBypassed = state.isBypassed,
                                isPlaying = state.isPlaying,
                                onUpdateParams = onUpdateParams,
                                modifier = Modifier.weight(7f)
                            )
                            DynamicsPanel(
                                strings = strings,
                                params = state.params,
                                onUpdateParams = onUpdateParams,
                                modifier = Modifier.weight(5f)
                            )
                        }
                    } else {
                        ParametricEqPanel(
                            strings = strings,
                            params = state.params,
                            eqCurveDb = state.eqCurveDb,
                            bandTelemetry = state.bandTelemetry,
                            isBypassed = state.isBypassed,
                            isPlaying = state.isPlaying,
                            onUpdateParams = onUpdateParams
                        )
                        DynamicsPanel(
                            strings = strings,
                            params = state.params,
                            onUpdateParams = onUpdateParams
                        )
                    }

                    // DSP Diagnostics
                    DiagnosticsPanel(
                        strings = strings,
                        lang = state.language,
                        metrics = state.auditMetrics
                    )

                    // Auto Lyrics Corrector (Firebase AI Logic)
                    LyricsCorrectorPanel(
                        strings = strings,
                        lyricsDraft = state.lyricsDraft,
                        selectedMode = state.lyricMode,
                        aiState = state.lyricsAiState,
                        isAudioLoaded = state.isAudioLoaded,
                        autoApplyEnabled = state.autoApplyCorrectedLyrics,
                        canRestoreOriginal = state.previousLyricsBeforeAutoApply != null,
                        onLyricsChange = onLyricsChange,
                        onClearLyrics = onClearLyrics,
                        onSelectMode = onSelectLyricMode,
                        onToggleAutoApply = onToggleAutoApplyLyrics,
                        onAutoCorrectClick = onAutoCorrectLyrics,
                        onExtractFromAudioClick = onExtractLyricsFromAudio,
                        onApplyCorrectedClick = onApplyCorrectedLyrics,
                        onRestoreOriginalClick = onRestoreOriginalLyrics,
                        onCopied = onLyricsCopied
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun StudioTopBar(
    strings: StudioStrings,
    isLoadingAudio: Boolean,
    onToggleLanguage: () -> Unit,
    onPickAudioClick: () -> Unit
) {
    Surface(
        color = StudioColors.Background.copy(alpha = 0.96f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = StudioColors.Amber,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = strings.appName,
                        fontFamily = StudioSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = StudioColors.TextPrimary,
                        maxLines = 1
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onToggleLanguage,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, StudioColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = StudioColors.SlateButton,
                            contentColor = StudioColors.TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lang_button")
                    ) {
                        Text(
                            text = strings.langToggle,
                            fontFamily = StudioSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = onPickAudioClick,
                        enabled = !isLoadingAudio,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioColors.Amber,
                            contentColor = Color(0xFF020617)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("upload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.UploadFile,
                            contentDescription = stringResource(R.string.cd_upload_audio),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(16.dp)
                        )
                        Text(
                            text = strings.uploadButton,
                            fontFamily = StudioSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(StudioColors.Border)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransportAndBypassCard(
    strings: StudioStrings,
    state: SwaraUiState,
    onTogglePlayPause: () -> Unit,
    onSelectBypass: (Boolean) -> Unit,
    onExportWavClick: () -> Unit
) {
    val statsText = state.auditMetrics?.let { m ->
        String.format(
            "%s · %.1f kHz · Peak %.1f dBFS · RMS %.1f dBFS",
            formatTimeSec(m.durationSec),
            m.sampleRate / 1000.0,
            m.peakDb,
            m.rmsDb
        )
    } ?: strings.unloadedStats

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.trackName ?: strings.selectSong,
                fontFamily = StudioSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = StudioColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("track_name")
            )
            Text(
                text = statsText,
                fontFamily = StudioMonoFontFamily,
                fontSize = 12.sp,
                color = StudioColors.TextSecondary,
                modifier = Modifier.testTag("track_stats")
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onTogglePlayPause,
                enabled = state.isAudioLoaded && !state.isLoadingAudio,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioColors.TextPrimary,
                    contentColor = Color(0xFF020617),
                    disabledContainerColor = StudioColors.TextPrimary.copy(alpha = 0.35f),
                    disabledContentColor = Color(0xFF020617).copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("play_button")
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_pause),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(16.dp)
                )
                Text(
                    text = if (state.isPlaying) strings.pause else strings.play,
                    fontFamily = StudioSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }

            // A/B Toggle Segmented Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioColors.Background)
                    .border(1.dp, StudioColors.Border, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val origSelected = state.isBypassed
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (origSelected) StudioColors.SlateActive else Color.Transparent)
                        .clickable { onSelectBypass(true) }
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("ab_orig_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.abOrig,
                        fontFamily = StudioSansFontFamily,
                        fontWeight = if (origSelected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (origSelected) Color.White else StudioColors.TextSecondary
                    )
                }

                val fixedSelected = !state.isBypassed
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (fixedSelected) StudioColors.Amber else Color.Transparent)
                        .clickable { onSelectBypass(false) }
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("ab_fixed_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.abFixed,
                        fontFamily = StudioSansFontFamily,
                        fontWeight = if (fixedSelected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (fixedSelected) Color(0xFF020617) else StudioColors.TextSecondary
                    )
                }
            }

            Button(
                onClick = onExportWavClick,
                enabled = state.isAudioLoaded && !state.isExporting && !state.isLoadingAudio,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioColors.Emerald,
                    contentColor = Color.White,
                    disabledContainerColor = StudioColors.Emerald.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("export_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = stringResource(R.string.cd_export_wav),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(16.dp)
                )
                Text(
                    text = if (state.isExporting) strings.rendering else strings.exportWav,
                    fontFamily = StudioSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun UploadDropZoneCard(
    strings: StudioStrings,
    onClick: () -> Unit
) {
    val dashColor = StudioColors.BorderSubtle
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.Surface)
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                )
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = stroke
                )
            }
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(24.dp)
            .testTag("drop_zone_card"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AudioFile,
            contentDescription = stringResource(R.string.cd_upload_audio),
            tint = StudioColors.Amber,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = strings.dropTitle,
            fontFamily = StudioSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = StudioColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = strings.dropSubtitle,
            fontFamily = StudioSansFontFamily,
            fontSize = 12.sp,
            color = StudioColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetBar(
    activePresetId: String?,
    onSelectPreset: (MasteringPreset) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MASTERING_PRESETS.forEach { preset ->
            val isActive = preset.id == activePresetId
            OutlinedButton(
                onClick = { onSelectPreset(preset) },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (isActive) StudioColors.Amber else StudioColors.BorderSubtle
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isActive) {
                        StudioColors.Amber.copy(alpha = 0.14f)
                    } else {
                        StudioColors.SlateButton
                    },
                    contentColor = if (isActive) StudioColors.AmberBright else StudioColors.TextPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("preset_${preset.id}")
            ) {
                Text(
                    text = preset.name,
                    fontFamily = StudioSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
    }
}
