package com.example.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader

enum class VpnConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class VpnUiState(
    val connectionState: VpnConnectionState = VpnConnectionState.DISCONNECTED,
    val tunnelName: String = "RehanVPN",
    val endpoint: String = "",
    val interfaceAddress: String = "",
    val dnsServers: String = "",
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val hasValidConfig: Boolean = false,
    val rawConfig: String = "",
    val errorMessage: String? = null
)

class RehanTunnel(
    private val tunnelName: String,
    private val onStateChangedCallback: (Tunnel.State) -> Unit
) : Tunnel {
    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) {
        onStateChangedCallback(newState)
    }
}

class WireGuardManager(private val context: Context) {

    companion object {
        private const val TAG = "WireGuardManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backend: GoBackend by lazy { GoBackend(context.applicationContext) }

    private val _uiState = MutableStateFlow(VpnUiState())
    val uiState: StateFlow<VpnUiState> = _uiState.asStateFlow()

    private var activeTunnel: RehanTunnel? = null
    private var parsedConfig: Config? = null
    private var statsJob: Job? = null

    init {
        RehanVpnService.onDisconnectRequested = {
            scope.launch { disconnect() }
        }
    }

    fun prepareVpnPermission(): Intent? = VpnService.prepare(context)

    fun loadConfig(configText: String, name: String = "RehanVPN"): Result<Config> {
        return try {
            val trimmed = configText.trim()
            if (trimmed.isEmpty()) {
                _uiState.update {
                    it.copy(
                        hasValidConfig = false,
                        rawConfig = "",
                        endpoint = "",
                        interfaceAddress = "",
                        dnsServers = "",
                        errorMessage = null
                    )
                }
                return Result.failure(IllegalArgumentException("Empty WireGuard configuration"))
            }
            val config = Config.parse(BufferedReader(StringReader(trimmed)))
            parsedConfig = config
            val endpoint = config.peers.firstOrNull()?.endpoint?.map { it.toString() }?.orElse("None") ?: "None"
            val ifaceAddr = config.`interface`.addresses.joinToString(", ") { it.toString() }
            val dns = config.`interface`.dnsServers.joinToString(", ") { it.hostAddress ?: it.toString() }
            val cleanName = name.ifBlank { "RehanVPN" }.replace(Regex("[^a-zA-Z0-9_=+.-]"), "_")
            _uiState.update {
                it.copy(
                    tunnelName = cleanName,
                    endpoint = endpoint,
                    interfaceAddress = ifaceAddr,
                    dnsServers = dns,
                    hasValidConfig = true,
                    rawConfig = trimmed,
                    errorMessage = null
                )
            }
            Result.success(config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WireGuard config", e)
            parsedConfig = null
            _uiState.update {
                it.copy(
                    hasValidConfig = false,
                    rawConfig = configText,
                    errorMessage = "Invalid WireGuard config: ${e.localizedMessage ?: "parse error"}"
                )
            }
            Result.failure(e)
        }
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        val config = parsedConfig
        if (config == null) {
            _uiState.update {
                it.copy(
                    connectionState = VpnConnectionState.ERROR,
                    errorMessage = "Import a valid WireGuard .conf file or scan a QR code first."
                )
            }
            return@withContext false
        }

        if (VpnService.prepare(context) != null) {
            _uiState.update {
                it.copy(
                    connectionState = VpnConnectionState.ERROR,
                    errorMessage = "VPN permission is required before connecting."
                )
            }
            return@withContext false
        }

        _uiState.update {
            it.copy(connectionState = VpnConnectionState.CONNECTING, errorMessage = null)
        }

        return@withContext try {
            val tunnelName = _uiState.value.tunnelName
            val tunnel = RehanTunnel(tunnelName) { newState ->
                val mapped = when (newState) {
                    Tunnel.State.UP -> VpnConnectionState.CONNECTED
                    Tunnel.State.DOWN -> VpnConnectionState.DISCONNECTED
                    Tunnel.State.TOGGLE -> VpnConnectionState.CONNECTING
                }
                _uiState.update { it.copy(connectionState = mapped) }
            }
            activeTunnel = tunnel
            val state = backend.setState(tunnel, Tunnel.State.UP, config)
            if (state == Tunnel.State.UP) {
                _uiState.update {
                    it.copy(connectionState = VpnConnectionState.CONNECTED, errorMessage = null)
                }
                RehanVpnService.startOrUpdateNotification(
                    context,
                    tunnelName,
                    "Endpoint: ${_uiState.value.endpoint}"
                )
                startStatsPolling(tunnel)
                true
            } else {
                _uiState.update {
                    it.copy(
                        connectionState = VpnConnectionState.DISCONNECTED,
                        errorMessage = "Tunnel did not transition to UP."
                    )
                }
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "WireGuard connect failed", e)
            RehanVpnService.stopNotification(context)
            _uiState.update {
                it.copy(
                    connectionState = VpnConnectionState.ERROR,
                    errorMessage = e.localizedMessage ?: "Failed to establish WireGuard tunnel."
                )
            }
            false
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        statsJob?.cancel()
        statsJob = null
        try {
            val tunnel = activeTunnel
            if (tunnel != null) {
                backend.setState(tunnel, Tunnel.State.DOWN, parsedConfig)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WireGuard tunnel", e)
        } finally {
            activeTunnel = null
            RehanVpnService.stopNotification(context)
            _uiState.update {
                it.copy(
                    connectionState = VpnConnectionState.DISCONNECTED,
                    rxBytes = 0L,
                    txBytes = 0L
                )
            }
        }
    }

    private fun startStatsPolling(tunnel: Tunnel) {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                try {
                    val stats: Statistics = backend.getStatistics(tunnel)
                    val rx = stats.totalRx()
                    val tx = stats.totalTx()
                    _uiState.update { it.copy(rxBytes = rx, txBytes = tx) }
                    RehanVpnService.startOrUpdateNotification(
                        context,
                        _uiState.value.tunnelName,
                        "↓ ${formatBytes(rx)}  •  ↑ ${formatBytes(tx)}"
                    )
                } catch (_: Exception) {
                }
                delay(2000L)
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }
}
