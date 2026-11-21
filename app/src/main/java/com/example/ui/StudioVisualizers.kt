package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StudioColors
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSansFontFamily
import kotlin.math.max

@Composable
fun WaveformCard(
    headerText: String,
    peaks: List<Float>,
    currentSec: Double,
    durationSec: Double,
    isAudioLoaded: Boolean,
    isBypassed: Boolean,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerText,
                fontFamily = StudioSansFontFamily,
                fontSize = 12.sp,
                color = StudioColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${formatTimeSec(currentSec)} / ${formatTimeSec(durationSec)}",
                fontFamily = StudioMonoFontFamily,
                fontSize = 12.sp,
                color = StudioColors.TextPrimary,
                modifier = Modifier.testTag("time_display")
            )
        }

        val playedColor = if (isBypassed) StudioColors.TextSecondary else StudioColors.Amber
        val unplayedColor = StudioColors.BarInactive

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(StudioColors.Background)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(8.dp))
                .testTag("waveform_canvas")
                .semantics { contentDescription = headerText }
                .pointerInput(isAudioLoaded) {
                    if (!isAudioLoaded) return@pointerInput
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        onSeekFraction(frac)
                    }
                }
                .pointerInput(isAudioLoaded) {
                    if (!isAudioLoaded) return@pointerInput
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        onSeekFraction(frac)
                    }
                }
        ) {
            if (peaks.isEmpty() || durationSec <= 0.0) return@Canvas
            val pct = (currentSec / durationSec).toFloat().coerceIn(0f, 1f)
            val barWidth = size.width / peaks.size.toFloat()
            val maxBarH = (size.height - 14.dp.toPx()).coerceAtLeast(4f)
            val drawW = max(1.5f, barWidth - 2f)

            peaks.forEachIndexed { index, amp ->
                val h = max(4.dp.toPx(), amp * maxBarH)
                val barPct = index.toFloat() / peaks.size.toFloat()
                val color = if (barPct <= pct) playedColor else unplayedColor
                drawRect(
                    color = color,
                    topLeft = Offset(index * barWidth + 1f, (size.height - h) / 2f),
                    size = Size(drawW, h)
                )
            }
        }
    }
}

@Composable
fun LiveSpectrumCard(
    headerText: String,
    spectrumBins: List<Float>,
    gainReductionDb: Float,
    isPlaying: Boolean,
    isBypassed: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerText,
                fontFamily = StudioSansFontFamily,
                fontSize = 12.sp,
                color = StudioColors.TextSecondary
            )
            val grDisplay = if (isBypassed || !isPlaying) 0.0f else gainReductionDb
            Text(
                text = String.format("GR: %.1f dB", grDisplay),
                fontFamily = StudioMonoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = StudioColors.AmberBright,
                modifier = Modifier.testTag("gr_display")
            )
        }

        val barColor = if (isBypassed) {
            StudioColors.TextSecondary.copy(alpha = 0.6f)
        } else {
            StudioColors.Amber.copy(alpha = 0.85f)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(StudioColors.Background)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(8.dp))
                .testTag("spectrum_canvas")
                .semantics { contentDescription = headerText }
        ) {
            val count = spectrumBins.size.coerceAtLeast(1)
            val bw = size.width / count.toFloat()
            val drawW = max(2f, bw - 2f)
            val maxH = (size.height - 8.dp.toPx()).coerceAtLeast(2f)

            if (isPlaying) {
                spectrumBins.forEachIndexed { i, v ->
                    val h = max(2.dp.toPx(), v.coerceIn(0f, 1f) * maxH)
                    drawRect(
                        color = barColor,
                        topLeft = Offset(i * bw + 1f, size.height - h),
                        size = Size(drawW, h)
                    )
                }
            }
        }
    }
}
