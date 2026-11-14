package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.localization.LocalBrowserStrings
import com.example.vpn.VpnConnectionState
import com.example.vpn.VpnUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnSheet(
    vpnState: VpnUiState,
    formatBytes: (Long) -> String,
    onToggleConnect: () -> Unit,
    onImportConfFile: () -> Unit,
    onScanQrCode: () -> Unit,
    onSavePastedConfig: (configText: String, name: String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val strings = LocalBrowserStrings.current
    var showPasteDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.vpnTitle, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Connect / Disconnect Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (vpnState.connectionState) {
                                VpnConnectionState.CONNECTED -> strings.vpnConnected
                                VpnConnectionState.CONNECTING -> "Connecting..."
                                VpnConnectionState.ERROR -> "Attention Needed"
                                VpnConnectionState.DISCONNECTED -> strings.vpnDisconnected
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = when (vpnState.connectionState) {
                                VpnConnectionState.CONNECTED -> MaterialTheme.colorScheme.tertiary
                                VpnConnectionState.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (vpnState.hasValidConfig) {
                                "Tunnel: ${vpnState.tunnelName} • ${vpnState.endpoint}"
                            } else {
                                "No WireGuard profile loaded"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vpnState.connectionState == VpnConnectionState.CONNECTED ||
                            vpnState.connectionState == VpnConnectionState.CONNECTING,
                        onCheckedChange = { onToggleConnect() },
                        modifier = Modifier.testTag("vpn_connect_switch")
                    )
                }
            }

            if (vpnState.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = vpnState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Live Stats when configured
            if (vpnState.hasValidConfig) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Interface: ${vpnState.interfaceAddress}", style = MaterialTheme.typography.labelSmall)
                        if (vpnState.dnsServers.isNotBlank()) {
                            Text("DNS: ${vpnState.dnsServers}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (vpnState.connectionState == VpnConnectionState.CONNECTED) {
                            Text(
                                text = "Traffic: ↓ ${formatBytes(vpnState.rxBytes)}   ↑ ${formatBytes(vpnState.txBytes)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Import Actions: File / QR / Paste
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onImportConfFile,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vpn_import_file_button")
                ) {
                    Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import .conf")
                }
                OutlinedButton(
                    onClick = onScanQrCode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vpn_scan_qr_button")
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan QR")
                }
            }

            OutlinedButton(
                onClick = { showPasteDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vpn_paste_config_button")
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Paste / Edit WireGuard Config")
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }

    if (showPasteDialog) {
        var tunnelName by remember { mutableStateOf(vpnState.tunnelName) }
        var configText by remember { mutableStateOf(vpnState.rawConfig) }
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("WireGuard Configuration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tunnelName,
                        onValueChange = { tunnelName = it },
                        label = { Text("Tunnel Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = configText,
                        onValueChange = { configText = it },
                        label = { Text("[Interface] & [Peer] config") },
                        minLines = 6,
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSavePastedConfig(configText, tunnelName)
                        showPasteDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
