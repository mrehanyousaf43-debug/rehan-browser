package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.BrowserPreferencesState
import com.example.data.local.SearchEngine
import com.example.privacy.ClearDataOptions
import com.example.privacy.DohLookupResult
import com.example.privacy.DohProvider
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.LocalBrowserStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacySettingsSheet(
    prefs: BrowserPreferencesState,
    dohTestResult: DohLookupResult?,
    isTestingDoh: Boolean,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetSearchEngine: (SearchEngine) -> Unit,
    onSetBlockThirdPartyCookies: (Boolean) -> Unit,
    onSetBlockAllCookies: (Boolean) -> Unit,
    onSetSendDnt: (Boolean) -> Unit,
    onSetHttpsOnly: (Boolean) -> Unit,
    onSetJavaScript: (Boolean) -> Unit,
    onSetDohProvider: (DohProvider) -> Unit,
    onTestDoh: (String) -> Unit,
    onClearBrowsingData: (ClearDataOptions) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val strings = LocalBrowserStrings.current
    var showClearDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("privacy_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(strings.privacySettings, style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Appearance & Language (RTL)
                SettingsSection("Appearance & Language (RTL)") {
                    ToggleRow(strings.darkTheme, "Deep obsidian dark mode (default)", prefs.isDarkTheme, onSetDarkTheme)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(strings.languageLabel, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = prefs.languageCode == lang.code,
                                onClick = { onSetLanguage(lang.code) },
                                label = { Text("${lang.nativeName} (${lang.displayName})") },
                                modifier = Modifier.testTag("lang_${lang.code}")
                            )
                        }
                    }
                }

                // Search Engine
                SettingsSection("Default Search Engine") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchEngine.entries.forEach { engine ->
                            FilterChip(
                                selected = prefs.searchEngineId == engine.id,
                                onClick = { onSetSearchEngine(engine) },
                                label = { Text(engine.displayName) }
                            )
                        }
                    }
                }

                // Tracker & Cookie Controls
                SettingsSection("Tracker & Cookie Controls") {
                    ToggleRow("Block Third-Party Cookies", "Prevent cross-site tracking cookies", prefs.blockThirdPartyCookies, onSetBlockThirdPartyCookies)
                    ToggleRow("Block All Cookies", "Strict zero-cookie isolation mode", prefs.blockAllCookies, onSetBlockAllCookies)
                    ToggleRow("Do Not Track & Sec-GPC", "Send DNT: 1 and Global Privacy Control headers", prefs.sendDntHeader, onSetSendDnt)
                    ToggleRow("HTTPS-Only Mode", "Upgrade http:// links to https:// automatically", prefs.httpsOnlyMode, onSetHttpsOnly)
                    ToggleRow("Enable JavaScript", "Disable for ultra-strict hardening", prefs.javaScriptEnabled, onSetJavaScript)
                }

                // DNS-over-HTTPS
                SettingsSection("DNS-over-HTTPS (DoH)") {
                    DohProvider.entries.forEach { provider ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = prefs.dohProviderId == provider.id,
                                onClick = { onSetDohProvider(provider) },
                                modifier = Modifier.testTag("doh_${provider.id}")
                            )
                            Column {
                                Text(provider.displayName, style = MaterialTheme.typography.bodyLarge)
                                if (provider.jsonEndpoint != null) {
                                    Text(provider.jsonEndpoint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { onTestDoh("example.com") },
                        enabled = !isTestingDoh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_doh_button")
                    ) {
                        if (isTestingDoh) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Test DoH Resolution (example.com)")
                        }
                    }
                    dohTestResult?.let { res ->
                        Text(
                            text = if (res.error != null) {
                                "DoH Error (${res.providerName}): ${res.error}"
                            } else {
                                "${res.providerName} resolved ${res.host} -> ${res.addresses.joinToString()} (${res.latencyMs}ms)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (res.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Clear Browsing Data
                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_clear_data_button")
                ) {
                    Icon(Icons.Default.DeleteForever, null)
                    Text("  ${strings.clearBrowsingData}")
                }
            }
        }
    }

    if (showClearDialog) {
        var clearHistory by remember { mutableStateOf(true) }
        var clearCache by remember { mutableStateOf(true) }
        var clearCookies by remember { mutableStateOf(true) }
        var clearForm by remember { mutableStateOf(true) }
        var clearDownloads by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(strings.clearBrowsingData) },
            text = {
                Column {
                    CheckRow("Browsing history", clearHistory) { clearHistory = it }
                    CheckRow("Cached images & files", clearCache) { clearCache = it }
                    CheckRow("Cookies & site storage", clearCookies) { clearCookies = it }
                    CheckRow("Saved form data", clearForm) { clearForm = it }
                    CheckRow("Downloads list", clearDownloads) { clearDownloads = it }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearBrowsingData(
                            ClearDataOptions(clearHistory, clearCache, clearCookies, clearForm, clearDownloads)
                        )
                        showClearDialog = false
                    }
                ) { Text("Clear Now") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
