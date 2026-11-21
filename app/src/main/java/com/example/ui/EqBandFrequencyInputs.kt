package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.MasteringParams
import com.example.ui.theme.StudioColors
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSansFontFamily
import kotlin.math.roundToInt

private data class BandFreqFieldSpec(
    val index: Int,
    val label: String,
    val freqHz: Float,
    val minHz: Int,
    val maxHz: Int,
    val applyFreq: (MasteringParams, Float) -> MasteringParams
)

/**
 * Six manual numeric Hz input boxes positioned directly below the interactive EQ overlay
 * for exact frequency control across all 6 Parametric Repair EQ bands.
 */
@Composable
fun EqBandFrequencyInputs(
    params: MasteringParams,
    selectedBand: Int,
    onSelectBand: (Int) -> Unit,
    onUpdateParams: ((MasteringParams) -> MasteringParams) -> Unit,
    modifier: Modifier = Modifier
) {
    val specs = listOf(
        BandFreqFieldSpec(0, "HPF", params.hpf, 20, 250) { p, hz -> p.copy(hpf = hz) },
        BandFreqFieldSpec(1, "Low", params.lowFreq, 40, 300) { p, hz -> p.copy(lowFreq = hz) },
        BandFreqFieldSpec(2, "Mud", params.mudFreq, 120, 1000) { p, hz -> p.copy(mudFreq = hz) },
        BandFreqFieldSpec(3, "Vocal", params.presFreq, 800, 8000) { p, hz -> p.copy(presFreq = hz) },
        BandFreqFieldSpec(4, "De-Es", params.deessFreq, 2500, 14000) { p, hz -> p.copy(deessFreq = hz) },
        BandFreqFieldSpec(5, "Air", params.airFreq, 6000, 18000) { p, hz -> p.copy(airFreq = hz) }
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Band Frequencies (Hz)",
            fontFamily = StudioSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = StudioColors.TextSecondary
        )

        specs.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { spec ->
                    BandFrequencyInputCell(
                        spec = spec,
                        isSelected = spec.index == selectedBand,
                        onSelect = { onSelectBand(spec.index) },
                        onCommitFreq = { newHz ->
                            onUpdateParams { current -> spec.applyFreq(current, newHz) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BandFrequencyInputCell(
    spec: BandFreqFieldSpec,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onCommitFreq: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(spec.freqHz.roundToInt().toString()) }

    LaunchedEffect(spec.freqHz, isFocused) {
        if (!isFocused) {
            val formatted = spec.freqHz.roundToInt().toString()
            if (textValue != formatted) {
                textValue = formatted
            }
        }
    }

    fun commitClamped() {
        val parsed = textValue.toIntOrNull()
        val clamped = (parsed ?: spec.freqHz.roundToInt()).coerceIn(spec.minHz, spec.maxHz)
        textValue = clamped.toString()
        onCommitFreq(clamped.toFloat())
    }

    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) StudioColors.Amber.copy(alpha = 0.09f) else StudioColors.Surface)
            .border(
                1.dp,
                if (isFocused || isSelected) StudioColors.Amber else StudioColors.Border,
                RoundedCornerShape(8.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = spec.label,
                fontFamily = StudioSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = if (isSelected) StudioColors.AmberBright else StudioColors.TextSecondary
            )
            Text(
                text = "${spec.minHz}-${spec.maxHz}",
                fontFamily = StudioMonoFontFamily,
                fontSize = 9.sp,
                color = StudioColors.TextSecondary.copy(alpha = 0.75f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { raw ->
                        val digitsOnly = raw.filter { it.isDigit() }.take(5)
                        textValue = digitsOnly
                        val typed = digitsOnly.toIntOrNull()
                        if (typed != null && typed in spec.minHz..spec.maxHz) {
                            onCommitFreq(typed.toFloat())
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = StudioMonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = StudioColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(StudioColors.Amber),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            commitClamped()
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            val nowFocused = focusState.isFocused
                            if (nowFocused && !isFocused) {
                                onSelect()
                            } else if (!nowFocused && isFocused) {
                                commitClamped()
                            }
                            isFocused = nowFocused
                        }
                        .semantics {
                            contentDescription = "${spec.label} frequency in Hz"
                        }
                        .testTag("eq_freq_input_${spec.index}")
                )
            }

            Text(
                text = "Hz",
                fontFamily = StudioMonoFontFamily,
                fontSize = 10.sp,
                color = StudioColors.TextSecondary
            )
        }
    }
}
