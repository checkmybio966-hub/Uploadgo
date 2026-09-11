package com.uploadgo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uploadgo.app.AppGraph
import com.uploadgo.app.R
import com.uploadgo.app.data.prefs.ShareDefaultBehavior
import com.uploadgo.app.data.prefs.ThemeMode
import com.uploadgo.app.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings by AppGraph.settingsRepository.settings.collectAsStateWithLifecycle(
        com.uploadgo.app.data.prefs.Settings()
    )
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tempBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        tempBytes = withContext(Dispatchers.IO) { AppGraph.tempFileManager.totalSizeBytes() }
    }

    fun clearTemp() {
        val msg = stringResource(R.string.temp_cleared)
        scope.launch {
            val remaining = withContext(Dispatchers.IO) {
                AppGraph.tempFileManager.clearAll()
                AppGraph.tempFileManager.totalSizeBytes()
            }
            tempBytes = remaining
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // ---- Sharing ----
            item { SectionHeader(stringResource(R.string.settings_sharing)) }
            item {
                Text(
                    text = stringResource(R.string.default_share_behavior),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            item {
                RadioRow(
                    label = stringResource(R.string.behavior_share_selected),
                    selected = settings.defaultBehavior == ShareDefaultBehavior.SHARE_SELECTED,
                    onClick = { scope.launch { AppGraph.settingsRepository.setDefaultBehavior(ShareDefaultBehavior.SHARE_SELECTED) } },
                )
            }
            item {
                RadioRow(
                    label = stringResource(R.string.behavior_share_zip_contents),
                    selected = settings.defaultBehavior == ShareDefaultBehavior.SHARE_ZIP_CONTENTS,
                    onClick = { scope.launch { AppGraph.settingsRepository.setDefaultBehavior(ShareDefaultBehavior.SHARE_ZIP_CONTENTS) } },
                )
            }

            // ---- Batch sharing ----
            item { SectionHeader(stringResource(R.string.files_per_batch)) }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.batch_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                RadioRow(
                    label = stringResource(R.string.batch_10),
                    selected = settings.shareBatchSize == 10,
                    onClick = { scope.launch { AppGraph.settingsRepository.setShareBatchSize(10) } },
                )
            }
            item {
                RadioRow(
                    label = stringResource(R.string.batch_20),
                    selected = settings.shareBatchSize == 20,
                    onClick = { scope.launch { AppGraph.settingsRepository.setShareBatchSize(20) } },
                )
            }
            item {
                RadioRow(
                    label = stringResource(R.string.batch_50),
                    selected = settings.shareBatchSize == 50,
                    onClick = { scope.launch { AppGraph.settingsRepository.setShareBatchSize(50) } },
                )
            }
            item {
                RadioRow(
                    label = stringResource(R.string.batch_all),
                    selected = settings.shareBatchSize <= 0,
                    onClick = { scope.launch { AppGraph.settingsRepository.setShareBatchSize(0) } },
                )
            }

            // ---- ZIP ----
            item { SectionHeader(stringResource(R.string.settings_zip)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.auto_extract_zip),
                    subtitle = stringResource(R.string.auto_extract_zip_desc),
                    checked = settings.autoExtractZip,
                    onCheckedChange = { scope.launch { AppGraph.settingsRepository.setAutoExtractZip(it) } },
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.show_unsupported_files),
                    subtitle = stringResource(R.string.show_unsupported_files_desc),
                    checked = settings.showUnsupported,
                    onCheckedChange = { scope.launch { AppGraph.settingsRepository.setShowUnsupported(it) } },
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.delete_temp_after_share),
                    subtitle = stringResource(R.string.delete_temp_after_share_desc),
                    checked = settings.deleteTempAfterShare,
                    onCheckedChange = { scope.launch { AppGraph.settingsRepository.setDeleteTempAfterShare(it) } },
                )
            }

            // ---- Appearance ----
            item { SectionHeader(stringResource(R.string.settings_appearance)) }
            item {
                Text(
                    text = stringResource(R.string.theme),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            item {
                ThemeRow(
                    label = stringResource(R.string.theme_system),
                    icon = { Icon(Icons.Rounded.BrightnessAuto, contentDescription = null) },
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { scope.launch { AppGraph.settingsRepository.setThemeMode(ThemeMode.SYSTEM) } },
                )
            }
            item {
                ThemeRow(
                    label = stringResource(R.string.theme_light),
                    icon = { Icon(Icons.Rounded.LightMode, contentDescription = null) },
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { scope.launch { AppGraph.settingsRepository.setThemeMode(ThemeMode.LIGHT) } },
                )
            }
            item {
                ThemeRow(
                    label = stringResource(R.string.theme_dark),
                    icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { scope.launch { AppGraph.settingsRepository.setThemeMode(ThemeMode.DARK) } },
                )
            }

            // ---- Storage ----
            item { SectionHeader(stringResource(R.string.settings_storage)) }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.temporary_storage), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (tempBytes > 0) {
                                    stringResource(R.string.temp_storage_usage, Format.bytes(tempBytes))
                                } else {
                                    stringResource(R.string.temp_storage_empty)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { clearTemp() },
                        enabled = tempBytes > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.clear_temp_files))
                    }
                }
            }

            // ---- About ----
            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                AboutRow(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.tagline),
                )
            }
            item {
                AboutRow(
                    title = stringResource(R.string.version),
                    subtitle = stringResource(R.string.version_number),
                )
            }
            item {
                ExpandableRow(
                    icon = { Icon(Icons.Rounded.PrivacyTip, contentDescription = null) },
                    title = stringResource(R.string.privacy),
                    expandedText = stringResource(R.string.privacy_summary),
                )
            }
            item {
                ExpandableRow(
                    icon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                    title = stringResource(R.string.open_source_licenses),
                    expandedText = stringResource(R.string.licenses_summary),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.sharesheet_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeRow(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        icon()
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AboutRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpandableRow(
    icon: @Composable () -> Unit,
    title: String,
    expandedText: String,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
        if (expanded) {
            Text(
                text = expandedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}
