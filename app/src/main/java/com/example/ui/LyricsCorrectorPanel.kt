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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.LyricCorrectionMode
import com.example.ai.LyricsAiState
import com.example.ui.theme.StudioColors
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSansFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsCorrectorPanel(
    strings: StudioStrings,
    lyricsDraft: String,
    selectedMode: LyricCorrectionMode,
    aiState: LyricsAiState,
    isAudioLoaded: Boolean,
    onLyricsChange: (String) -> Unit,
    onClearLyrics: () -> Unit,
    onSelectMode: (LyricCorrectionMode) -> Unit,
    onAutoCorrectClick: () -> Unit,
    onExtractFromAudioClick: () -> Unit,
    onApplyCorrectedClick: () -> Unit,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val isLoading = aiState is LyricsAiState.Loading

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    imageVector = Icons.Filled.AutoFixHigh,
                    contentDescription = null,
                    tint = StudioColors.Amber,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = strings.lyricsTitle,
                    fontFamily = StudioSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = StudioColors.TextPrimary
                )
            }

            if (lyricsDraft.isNotEmpty() || aiState !is LyricsAiState.Idle) {
                TextButton(
                    onClick = onClearLyrics,
                    enabled = !isLoading,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lyrics_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = strings.lyricsClearBtn,
                        tint = StudioColors.TextSecondary,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = strings.lyricsClearBtn,
                        fontFamily = StudioSansFontFamily,
                        fontSize = 12.sp,
                        color = StudioColors.TextSecondary
                    )
                }
            }
        }

        HorizontalDivider(color = StudioColors.Border)

        Text(
            text = strings.lyricsSubtitle,
            fontFamily = StudioSansFontFamily,
            fontSize = 12.sp,
            color = StudioColors.TextSecondary
        )

        // Correction Mode Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LyricCorrectionMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                OutlinedButton(
                    onClick = { onSelectMode(mode) },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) StudioColors.Amber else StudioColors.BorderSubtle
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) {
                            StudioColors.Amber.copy(alpha = 0.14f)
                        } else {
                            StudioColors.SlateButton
                        },
                        contentColor = if (isSelected) StudioColors.AmberBright else StudioColors.TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lyric_mode_${mode.name.lowercase()}")
                ) {
                    Text(
                        text = lyricModeLabel(mode, strings),
                        fontFamily = StudioSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Draft Lyrics Editor
        OutlinedTextField(
            value = lyricsDraft,
            onValueChange = onLyricsChange,
            enabled = !isLoading,
            placeholder = {
                Text(
                    text = strings.lyricsPlaceholder,
                    fontFamily = StudioSansFontFamily,
                    fontSize = 13.sp,
                    color = StudioColors.TextSecondary
                )
            },
            textStyle = TextStyle(
                fontFamily = StudioSansFontFamily,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = StudioColors.TextPrimary
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = StudioColors.Background,
                unfocusedContainerColor = StudioColors.Background,
                disabledContainerColor = StudioColors.Background.copy(alpha = 0.7f),
                focusedBorderColor = StudioColors.Amber,
                unfocusedBorderColor = StudioColors.Border,
                cursorColor = StudioColors.Amber
            ),
            shape = RoundedCornerShape(10.dp),
            minLines = 4,
            maxLines = 10,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .testTag("lyrics_input")
        )

        // Action Buttons
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onAutoCorrectClick,
                enabled = !isLoading,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioColors.Amber,
                    contentColor = Color(0xFF020617)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lyrics_autocorrect_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        color = Color(0xFF020617),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AutoFixHigh,
                        contentDescription = strings.lyricsAutoCorrectBtn,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                    )
                }
                Text(
                    text = if (isLoading) strings.lyricsProcessing else strings.lyricsAutoCorrectBtn,
                    fontFamily = StudioSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }

            if (isAudioLoaded) {
                OutlinedButton(
                    onClick = onExtractFromAudioClick,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, StudioColors.EmeraldBright.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StudioColors.Emerald.copy(alpha = 0.16f),
                        contentColor = StudioColors.EmeraldBright
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lyrics_from_audio_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = strings.lyricsFromAudioBtn,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                    )
                    Text(
                        text = strings.lyricsFromAudioBtn,
                        fontFamily = StudioSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Error State
        if (aiState is LyricsAiState.Error) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioColors.Background)
                    .border(1.dp, StudioColors.ErrorRed, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .testTag("lyrics_error_card")
            ) {
                Text(
                    text = aiState.message,
                    fontFamily = StudioSansFontFamily,
                    fontSize = 13.sp,
                    color = StudioColors.TextPrimary
                )
            }
        }

        // Success Output Card
        if (aiState is LyricsAiState.Success) {
            val res = aiState.result
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioColors.Background)
                    .border(1.dp, StudioColors.Amber.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
                    .testTag("lyrics_result_card"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.lyricsCorrectedLabel,
                        fontFamily = StudioSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = StudioColors.AmberBright
                    )
                    Text(
                        text = res.detectedLanguage,
                        fontFamily = StudioMonoFontFamily,
                        fontSize = 11.sp,
                        color = StudioColors.EmeraldBright,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StudioColors.Emerald.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (res.summaryNote.isNotBlank()) {
                    Text(
                        text = res.summaryNote,
                        fontFamily = StudioSansFontFamily,
                        fontSize = 12.sp,
                        color = StudioColors.TextSecondary
                    )
                }

                Text(
                    text = res.correctedLyrics,
                    fontFamily = StudioSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = StudioColors.TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioColors.Surface)
                        .border(1.dp, StudioColors.Border, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .testTag("lyrics_corrected_text")
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onApplyCorrectedClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioColors.Emerald,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lyrics_apply_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = strings.lyricsApplyBtn,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(15.dp)
                        )
                        Text(
                            text = strings.lyricsApplyBtn,
                            fontFamily = StudioSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(res.correctedLyrics))
                            onCopied()
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
                            .testTag("lyrics_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = strings.lyricsCopyBtn,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(15.dp)
                        )
                        Text(
                            text = strings.lyricsCopyBtn,
                            fontFamily = StudioSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(color = StudioColors.Border)

                Text(
                    text = strings.lyricsFixesAppliedLabel,
                    fontFamily = StudioSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = StudioColors.TextBody
                )

                if (res.corrections.isEmpty()) {
                    Text(
                        text = strings.lyricsNoChangesNeeded,
                        fontFamily = StudioSansFontFamily,
                        fontSize = 12.sp,
                        color = StudioColors.EmeraldBright
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        res.corrections.forEach { fix ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StudioColors.Surface)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = if (fix.original.isNotBlank()) {
                                        "${fix.original}  →  ${fix.fixed}"
                                    } else {
                                        fix.fixed
                                    },
                                    fontFamily = StudioSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = StudioColors.AmberSoft
                                )
                                if (fix.reason.isNotBlank()) {
                                    Text(
                                        text = fix.reason,
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
    }
}
