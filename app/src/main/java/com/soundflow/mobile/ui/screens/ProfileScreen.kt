package com.soundflow.mobile.ui.screens

import android.app.ActivityManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.soundflow.core.player.EqualizerPreset
import com.soundflow.mobile.BuildConfig
import com.soundflow.mobile.R
import com.soundflow.mobile.data.LocalStore
import com.soundflow.mobile.player.PlayerViewModel
import kotlinx.coroutines.launch

private data class LanguageOption(val label: String, val tag: String)
private val languageOptions = listOf(
    LanguageOption("Français", "fr"),
    LanguageOption("English", "en"),
    LanguageOption("日本語", "ja"),
)

private data class EqualizerOption(val labelResId: Int, val preset: EqualizerPreset)
private val equalizerOptions = listOf(
    EqualizerOption(R.string.eq_flat, EqualizerPreset.FLAT),
    EqualizerOption(R.string.eq_bass, EqualizerPreset.BASS_BOOST),
    EqualizerOption(R.string.eq_treble, EqualizerPreset.TREBLE_BOOST),
    EqualizerOption(R.string.eq_vocal, EqualizerPreset.VOCAL),
)

@UnstableApi
@Composable
fun ProfileScreen(playerViewModel: PlayerViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val offLabel = stringResource(R.string.settings_off)
    val listeningStats by playerViewModel.listeningStats.collectAsStateWithLifecycle()

    val currentTag = remember {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) locales.get(0)?.language.orEmpty() else "fr"
    }
    val initialLanguage = languageOptions.firstOrNull { it.tag == currentTag } ?: languageOptions.first()

    var selectedSleepTimerMinutes by remember { mutableStateOf<Int?>(null) }
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var selectedEqualizerIndex by remember { mutableStateOf(0) }
    var crossfadeSeconds by remember { mutableStateOf(0f) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    val sleepTimerLabel = if (selectedSleepTimerMinutes == null) offLabel else "$selectedSleepTimerMinutes min"

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item { SectionTitle(stringResource(R.string.settings_section_playback)) }

        item {
            DropdownSetting(
                label = stringResource(R.string.settings_sleep_timer),
                selectedLabel = sleepTimerLabel,
                options = listOf(offLabel, "15 min", "30 min", "1 h"),
                onOptionSelected = { index ->
                    val minutes = listOf(null, 15, 30, 60)[index]
                    selectedSleepTimerMinutes = minutes
                    playerViewModel.setSleepTimer(minutes?.let { it * 60_000L })
                },
            )
        }

        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Crossfade : ${crossfadeSeconds.toInt()}s", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.settings_crossfade_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = crossfadeSeconds,
                    onValueChange = { crossfadeSeconds = it },
                    valueRange = 0f..12f,
                    steps = 11,
                )
            }
        }

        item {
            DropdownSetting(
                label = stringResource(R.string.settings_equalizer),
                selectedLabel = stringResource(equalizerOptions[selectedEqualizerIndex].labelResId),
                options = equalizerOptions.map { stringResource(it.labelResId) },
                onOptionSelected = { index ->
                    selectedEqualizerIndex = index
                    playerViewModel.applyEqualizerPreset(equalizerOptions[index].preset)
                },
            )
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
        item { SectionTitle(stringResource(R.string.settings_section_stats)) }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                StatRow(stringResource(R.string.stats_today), formatDuration(listeningStats.todaySeconds))
                StatRow(stringResource(R.string.stats_week), formatDuration(listeningStats.weekSeconds))
                StatRow(stringResource(R.string.stats_month), formatDuration(listeningStats.monthSeconds))
                StatRow(stringResource(R.string.stats_all_time), formatDuration(listeningStats.allTimeSeconds))
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
        item { SectionTitle(stringResource(R.string.settings_section_general)) }

        item {
            DropdownSetting(
                label = stringResource(R.string.settings_language),
                selectedLabel = selectedLanguage.label,
                options = languageOptions.map { it.label },
                onOptionSelected = { index ->
                    val option = languageOptions[index]
                    selectedLanguage = option
                    coroutineScope.launch {
                        LocalStore(context).saveLanguageTag(option.tag)
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(option.tag),
                        )
                    }
                },
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodyLarge)
                Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

        item {
            OutlinedButton(
                onClick = { showResetConfirmation = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.settings_reset))
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(stringResource(R.string.reset_dialog_title)) },
            text = { Text(stringResource(R.string.reset_dialog_text)) },
            confirmButton = {
                TextButton(onClick = { resetAppData(context) }) {
                    Text(stringResource(R.string.reset_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text(stringResource(R.string.reset_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "0 min"
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes} min"
    }
}

@Composable
private fun DropdownSetting(
    label: String,
    selectedLabel: String,
    options: List<String>,
    onOptionSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Column {
            TextButton(onClick = { expanded = true }) {
                Text(selectedLabel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun resetAppData(context: Context) {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    activityManager.clearApplicationUserData()
}
