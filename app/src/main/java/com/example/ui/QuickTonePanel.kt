package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.MasteringParams
import com.example.ui.theme.StudioColors
import com.example.ui.theme.StudioSansFontFamily

/**
 * Song Tone Sculptor providing direct Bass, Vocal, and Treble macro controls plus
 * song-adaptive Auto-Fit and character quick boosts synced live with the Parametric EQ.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickTonePanel(
    strings: StudioStrings,
    params: MasteringParams,
    isAudioLoaded: Boolean,
    isAutoMatchActive: Boolean,
    onAutoFitClick: () -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = StudioColors.Amber,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = strings.toneShaperTitle,
                    fontFamily = StudioSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = StudioColors.TextPrimary
                )
            }
        }

        Text(
            text = strings.toneShaperSubtitle,
            fontFamily = StudioSansFontFamily,
            fontSize = 12.sp,
            color = StudioColors.TextSecondary
        )

        HorizontalDivider(color = StudioColors.Border)

        // Direct Bass / Vocal / Treble sliders synced directly to MasteringParams
        StudioSliderRow(
            label = strings.bassSliderLabel,
            valueText = formatToneDb(params.low),
            value = params.low,
            valueRange = -6f..6f,
            step = 0.2f,
            testTag = "quick_slider_bass",
            onValueChange = { v -> onUpdateParams { it.copy(low = v) } }
        )

        StudioSliderRow(
            label = strings.vocalSliderLabel,
            valueText = formatToneDb(params.pres),
            value = params.pres,
            valueRange = -4f..6f,
            step = 0.2f,
            testTag = "quick_slider_vocal",
            onValueChange = { v -> onUpdateParams { it.copy(pres = v) } }
        )

        StudioSliderRow(
            label = strings.trebleSliderLabel,
            valueText = formatToneDb(params.air),
            value = params.air,
            valueRange = -4f..6f,
            step = 0.2f,
            testTag = "quick_slider_treble",
            onValueChange = { v -> onUpdateParams { it.copy(air = v) } }
        )

        // Quick Tone Character & Auto-Fit Song actions
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isAudioLoaded) {
                Button(
                    onClick = onAutoFitClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAutoMatchActive) StudioColors.Emerald else StudioColors.Amber,
                        contentColor = if (isAutoMatchActive) Color.White else Color(0xFF020617)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tone_auto_fit_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = strings.autoFitSongBtn,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = strings.autoFitSongBtn,
                        fontFamily = StudioSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    onUpdateParams { p ->
                        p.copy(
                            hpf = 32f,
                            low = (p.low + 1.4f).coerceIn(-6f, 6f),
                            mud = (p.mud - 0.4f).coerceIn(-6f, 4f)
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, StudioColors.BorderSubtle),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = StudioColors.SlateButton,
                    contentColor = StudioColors.TextPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("tone_deep_bass_button")
            ) {
                Text(
                    text = strings.deepBassBtn,
                    fontFamily = StudioSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = {
                    onUpdateParams { p ->
                        p.copy(
                            mud = (p.mud - 0.6f).coerceIn(-6f, 4f),
                            pres = (p.pres + 1.4f).coerceIn(-4f, 6f)
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, StudioColors.BorderSubtle),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = StudioColors.SlateButton,
                    contentColor = StudioColors.TextPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("tone_vocal_boost_button")
            ) {
                Text(
                    text = strings.vocalBoostBtn,
                    fontFamily = StudioSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = {
                    onUpdateParams { p ->
                        p.copy(
                            air = (p.air + 1.4f).coerceIn(-4f, 6f),
                            deess = p.deess.coerceAtMost(-1.6f)
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, StudioColors.BorderSubtle),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = StudioColors.SlateButton,
                    contentColor = StudioColors.TextPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("tone_bright_treble_button")
            ) {
                Text(
                    text = strings.brightTrebleBtn,
                    fontFamily = StudioSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun formatToneDb(value: Float): String {
    val clean = if (value > -0.05f && value < 0.05f) 0.0f else value
    val sign = if (clean >= 0f) "+" else ""
    return String.format("%s%.1f dB", sign, clean)
}
