package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.SwaraStudioScreen
import com.example.ui.SwaraViewModel
import com.example.ui.theme.SwaraStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before UI, ViewModels, or AI calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            SwaraStudioTheme {
                val vm: SwaraViewModel = viewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()

                val openAudioLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        vm.loadAudioFromUri(uri)
                    }
                }

                val createWavLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("audio/wav")
                ) { uri ->
                    if (uri != null) {
                        vm.exportWavToUri(uri)
                    }
                }

                SwaraStudioScreen(
                    state = uiState,
                    onToggleLanguage = vm::toggleLanguage,
                    onPickAudioClick = { openAudioLauncher.launch(arrayOf("audio/*")) },
                    onTogglePlayPause = vm::togglePlayPause,
                    onSelectBypass = vm::setBypass,
                    onSeekFraction = vm::seekFraction,
                    onSelectPreset = vm::selectPreset,
                    onUpdateParams = vm::updateParams,
                    onExportWavClick = {
                        createWavLauncher.launch(vm.suggestedExportFileName())
                    },
                    onDismissBanner = vm::dismissBanner,
                    onLyricsChange = vm::updateLyricsDraft,
                    onClearLyrics = vm::clearLyrics,
                    onSelectLyricMode = vm::selectLyricMode,
                    onToggleAutoApplyLyrics = vm::toggleAutoApplyLyrics,
                    onAutoCorrectLyrics = vm::autoCorrectLyrics,
                    onExtractLyricsFromAudio = vm::transcribeAndCorrectFromAudio,
                    onApplyCorrectedLyrics = vm::applyCorrectedLyrics,
                    onRestoreOriginalLyrics = vm::restoreOriginalLyrics,
                    onLyricsCopied = vm::showLyricsCopiedBanner
                )
            }
        }
    }
}
