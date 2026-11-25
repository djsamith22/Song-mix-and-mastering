package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StudioColors
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSansFontFamily

@Composable
fun ExportRenameDialog(
    strings: StudioStrings,
    initialBaseName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameInput by remember(initialBaseName) { mutableStateOf(initialBaseName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Surface,
        titleContentColor = StudioColors.TextPrimary,
        textContentColor = StudioColors.TextBody,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.testTag("export_rename_dialog"),
        title = {
            Text(
                text = strings.exportDialogTitle,
                fontFamily = StudioSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = StudioColors.TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.exportFileNameLabel,
                    fontFamily = StudioSansFontFamily,
                    fontSize = 12.sp,
                    color = StudioColors.TextSecondary
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = strings.exportFileNameHint,
                            fontFamily = StudioSansFontFamily,
                            fontSize = 13.sp,
                            color = StudioColors.TextSecondary
                        )
                    },
                    suffix = {
                        Text(
                            text = ".wav",
                            fontFamily = StudioMonoFontFamily,
                            fontSize = 13.sp,
                            color = StudioColors.AmberBright
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = StudioSansFontFamily,
                        fontSize = 14.sp,
                        color = StudioColors.TextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = StudioColors.Background,
                        unfocusedContainerColor = StudioColors.Background,
                        focusedBorderColor = StudioColors.Amber,
                        unfocusedBorderColor = StudioColors.Border,
                        cursorColor = StudioColors.Amber
                    ),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onConfirm(nameInput) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_filename_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nameInput) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioColors.Emerald,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("export_confirm_button")
            ) {
                Text(
                    text = strings.exportConfirmBtn,
                    fontFamily = StudioSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("export_cancel_button")
            ) {
                Text(
                    text = strings.cancelBtn,
                    fontFamily = StudioSansFontFamily,
                    fontSize = 12.sp,
                    color = StudioColors.TextSecondary
                )
            }
        }
    )
}
