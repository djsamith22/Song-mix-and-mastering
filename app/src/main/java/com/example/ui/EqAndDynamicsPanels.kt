package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AppLanguage
import com.example.audio.DiagnosticMetrics
import com.example.audio.MasteringParams
import com.example.ui.theme.StudioColors
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSansFontFamily
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = StudioSansFontFamily,
                fontSize = 12.sp,
                color = StudioColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                fontFamily = StudioMonoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = StudioColors.AmberBright,
                modifier = Modifier.testTag("${testTag}_value")
            )
        }

        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { raw ->
                val snapped = if (step > 0f) {
                    val stepsFromStart = ((raw - valueRange.start) / step).roundToInt()
                    (valueRange.start + stepsFromStart * step)
                        .coerceIn(valueRange.start, valueRange.endInclusive)
                } else {
                    raw
                }
                onValueChange(snapped)
            },
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag(testTag)
                .semantics { contentDescription = "$label $valueText" },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(StudioColors.Amber)
                        .border(1.dp, StudioColors.AmberCream, RoundedCornerShape(3.dp))
                )
            },
            track = { sliderState ->
                val fraction = if (valueRange.endInclusive > valueRange.start) {
                    ((sliderState.value - valueRange.start) /
                        (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(StudioColors.Border)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(StudioColors.Amber.copy(alpha = 0.65f))
                    )
                }
            }
        )
    }
}

@Composable
fun ParametricEqPanel(
    strings: StudioStrings,
    params: MasteringParams,
    eqCurveDb: List<Float>,
    bandTelemetry: List<com.example.audio.BandTelemetry>,
    isBypassed: Boolean,
    isPlaying: Boolean,
    onUpdateParams: ((MasteringParams) -> MasteringParams) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = strings.eqTitle,
            fontFamily = StudioSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = StudioColors.TextPrimary
        )
        HorizontalDivider(color = StudioColors.Border)

        EqFrequencyOverlay(
            params = params,
            eqCurveDb = eqCurveDb,
            bandTelemetry = bandTelemetry,
            isBypassed = isBypassed,
            isPlaying = isPlaying,
            onUpdateParams = onUpdateParams
        )

        StudioSliderRow(
            label = strings.hpfLabel,
            valueText = formatBandValueWithGr("${params.hpf.roundToInt()} Hz", bandTelemetry.getOrNull(0), isBypassed),
            value = params.hpf,
            valueRange = 20f..250f,
            step = 2f,
            testTag = "slider_hpf",
            onValueChange = { v -> onUpdateParams { it.copy(hpf = v) } }
        )

        StudioSliderRow(
            label = formatBandTitleWithFreq(strings.lowLabel, params.lowFreq),
            valueText = formatBandValueWithGr(formatSignedDb(params.low), bandTelemetry.getOrNull(1), isBypassed),
            value = params.low,
            valueRange = -6f..6f,
            step = 0.2f,
            testTag = "slider_low",
            onValueChange = { v -> onUpdateParams { it.copy(low = v) } }
        )

        StudioSliderRow(
            label = formatBandTitleWithFreq(strings.mudLabel, params.mudFreq),
            valueText = formatBandValueWithGr(formatSignedDb(params.mud), bandTelemetry.getOrNull(2), isBypassed),
            value = params.mud,
            valueRange = -6f..4f,
            step = 0.2f,
            testTag = "slider_mud",
            onValueChange = { v -> onUpdateParams { it.copy(mud = v) } }
        )

        StudioSliderRow(
            label = formatBandTitleWithFreq(strings.presLabel, params.presFreq),
            valueText = formatBandValueWithGr(formatSignedDb(params.pres), bandTelemetry.getOrNull(3), isBypassed),
            value = params.pres,
            valueRange = -4f..6f,
            step = 0.2f,
            testTag = "slider_pres",
            onValueChange = { v -> onUpdateParams { it.copy(pres = v) } }
        )

        StudioSliderRow(
            label = formatBandTitleWithFreq(strings.deessLabel, params.deessFreq),
            valueText = formatBandValueWithGr(formatSignedDb(params.deess), bandTelemetry.getOrNull(4), isBypassed),
            value = params.deess,
            valueRange = -6f..3f,
            step = 0.2f,
            testTag = "slider_deess",
            onValueChange = { v -> onUpdateParams { it.copy(deess = v) } }
        )

        StudioSliderRow(
            label = formatBandTitleWithFreq(strings.airLabel, params.airFreq),
            valueText = formatBandValueWithGr(formatSignedDb(params.air), bandTelemetry.getOrNull(5), isBypassed),
            value = params.air,
            valueRange = -4f..6f,
            step = 0.2f,
            testTag = "slider_air",
            onValueChange = { v -> onUpdateParams { it.copy(air = v) } }
        )
    }
}

@Composable
fun DynamicsPanel(
    strings: StudioStrings,
    params: MasteringParams,
    onUpdateParams: ((MasteringParams) -> MasteringParams) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = strings.dynamicsTitle,
            fontFamily = StudioSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = StudioColors.TextPrimary
        )
        HorizontalDivider(color = StudioColors.Border)

        StudioSliderRow(
            label = strings.threshLabel,
            valueText = "${params.thresh.roundToInt()} dB",
            value = params.thresh,
            valueRange = -30f..0f,
            step = 1f,
            testTag = "slider_thresh",
            onValueChange = { v -> onUpdateParams { it.copy(thresh = v) } }
        )

        StudioSliderRow(
            label = strings.ratioLabel,
            valueText = String.format("%.1f : 1", params.ratio),
            value = params.ratio,
            valueRange = 1f..8f,
            step = 0.1f,
            testTag = "slider_ratio",
            onValueChange = { v -> onUpdateParams { it.copy(ratio = v) } }
        )

        StudioSliderRow(
            label = strings.widthLabel,
            valueText = String.format("%.2f x", params.width),
            value = params.width,
            valueRange = 0.6f..1.45f,
            step = 0.02f,
            testTag = "slider_width",
            onValueChange = { v -> onUpdateParams { it.copy(width = v) } }
        )

        StudioSliderRow(
            label = strings.gainLabel,
            valueText = formatSignedDb(params.gain),
            value = params.gain,
            valueRange = -6f..6f,
            step = 0.2f,
            testTag = "slider_gain",
            onValueChange = { v -> onUpdateParams { it.copy(gain = v) } }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(StudioColors.Background)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.limiterCeiling,
                fontFamily = StudioSansFontFamily,
                fontSize = 12.sp,
                color = StudioColors.TextSecondary
            )
            Text(
                text = "-0.6 dBFS",
                fontFamily = StudioMonoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = StudioColors.EmeraldBright
            )
        }
    }
}

@Composable
fun DiagnosticsPanel(
    strings: StudioStrings,
    lang: AppLanguage,
    metrics: DiagnosticMetrics?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = strings.diagTitle,
            fontFamily = StudioSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = StudioColors.TextPrimary
        )
        HorizontalDivider(color = StudioColors.Border)

        if (metrics == null) {
            Text(
                text = strings.diagEmpty,
                fontFamily = StudioSansFontFamily,
                fontSize = 14.sp,
                color = StudioColors.TextBody
            )
        } else {
            val summary = if (lang == AppLanguage.SI) {
                String.format(
                    "DSP පරීක්ෂාව සම්පූර්ණයි — Peak %.1f dBFS | RMS %.1f dBFS",
                    metrics.peakDb,
                    metrics.rmsDb
                )
            } else {
                String.format(
                    "DSP Audit Complete — Peak %.1f dBFS | RMS %.1f dBFS",
                    metrics.peakDb,
                    metrics.rmsDb
                )
            }
            Text(
                text = summary,
                fontFamily = StudioSansFontFamily,
                fontSize = 14.sp,
                color = StudioColors.TextBody,
                modifier = Modifier.testTag("diag_summary")
            )

            if (metrics.issues.isEmpty()) {
                Text(
                    text = strings.diagBalanced,
                    fontFamily = StudioSansFontFamily,
                    fontSize = 14.sp,
                    color = StudioColors.EmeraldBright,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    metrics.issues.forEachIndexed { idx, issueType ->
                        val (title, detail) = diagnosticIssueText(issueType, lang)
                        if (idx > 0) {
                            HorizontalDivider(color = StudioColors.Border)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = title,
                                fontFamily = StudioSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = StudioColors.AmberSoft
                            )
                            Text(
                                text = detail,
                                fontFamily = StudioSansFontFamily,
                                fontSize = 12.sp,
                                color = StudioColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSignedDb(value: Float): String {
    val clean = if (value > -0.05f && value < 0.05f) 0.0f else value
    val sign = if (clean >= 0f) "+" else ""
    return String.format("%s%.1f dB", sign, clean)
}

private fun formatBandTitleWithFreq(templateLabel: String, freqHz: Float): String {
    val base = templateLabel.substringBefore(" (")
    val hz = freqHz.roundToInt()
    val freqLabel = if (hz < 1000) {
        "$hz Hz"
    } else if (hz % 1000 == 0) {
        "${hz / 1000} kHz"
    } else {
        String.format("%.1f kHz", hz / 1000.0)
    }
    return "$base ($freqLabel)"
}

private fun formatBandValueWithGr(
    baseText: String,
    telemetry: com.example.audio.BandTelemetry?,
    isBypassed: Boolean
): String {
    if (telemetry == null || isBypassed || telemetry.totalGrDb > -0.15f) {
        return baseText
    }
    return String.format("%s  (GR %.1f dB)", baseText, telemetry.totalGrDb)
}
