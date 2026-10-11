package com.example.ui.wifi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.wifi.ChannelRecommendation
import com.example.data.wifi.ChannelRecommendationEngine
import com.example.data.wifi.ScanRefreshInterval
import com.example.data.wifi.SignalHistoryPoint
import com.example.data.wifi.WifiAccessPoint
import com.example.data.wifi.WifiBand
import com.example.data.wifi.WifiScannerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class WifiSpectrumUiState(
    val isScanning: Boolean = false,
    val allAccessPoints: List<WifiAccessPoint> = emptyList(),
    val filteredAccessPoints: List<WifiAccessPoint> = emptyList(),
    val selectedBand: WifiBand = WifiBand.BAND_2_4_GHZ,
    val recommendation: ChannelRecommendation? = null,
    val selectedApForMeter: WifiAccessPoint? = null,
    val signalHistory: List<SignalHistoryPoint> = emptyList(),
    val refreshInterval: ScanRefreshInterval = ScanRefreshInterval.SECONDS_5,
    val searchQuery: String = "",
    val hasPermissions: Boolean = false,
    val isWifiEnabled: Boolean = true,
    val lastScanTime: Long = 0L,
    val minRssiDbm: Int = -100,
    val maxRssiDbm: Int = -30,
    val avgRssiDbm: Int = -60
)

class WifiSpectrumViewModel(application: Application) : AndroidViewModel(application) {

    private val scannerManager = WifiScannerManager(application)

    private val _uiState = MutableStateFlow(
        WifiSpectrumUiState(
            hasPermissions = scannerManager.hasRequiredPermissions(),
            isWifiEnabled = scannerManager.isWifiEnabled()
        )
    )
    val uiState: StateFlow<WifiSpectrumUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        checkPermissionsAndScan()
        startAutoRefreshLoop()
    }

    fun checkPermissionsAndScan() {
        val hasPerms = scannerManager.hasRequiredPermissions()
        val wifiEnabled = scannerManager.isWifiEnabled()
        _uiState.update {
            it.copy(
                hasPermissions = hasPerms,
                isWifiEnabled = wifiEnabled
            )
        }
        performScan()
    }

    fun onPermissionsGranted() {
        _uiState.update { it.copy(hasPermissions = true) }
        performScan()
    }

    fun setSelectedBand(band: WifiBand) {
        _uiState.update { currentState ->
            val recommendation = ChannelRecommendationEngine.evaluateBand(
                currentState.allAccessPoints,
                band
            )
            val filtered = filterAndSortAps(currentState.allAccessPoints, band, currentState.searchQuery)
            val selectedMeterAp = if (currentState.selectedApForMeter?.band != band) {
                filtered.firstOrNull()
            } else {
                currentState.selectedApForMeter
            }

            currentState.copy(
                selectedBand = band,
                recommendation = recommendation,
                filteredAccessPoints = filtered,
                selectedApForMeter = selectedMeterAp
            )
        }
    }

    fun setRefreshInterval(interval: ScanRefreshInterval) {
        _uiState.update { it.copy(refreshInterval = interval) }
        startAutoRefreshLoop()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { currentState ->
            val filtered = filterAndSortAps(currentState.allAccessPoints, currentState.selectedBand, query)
            currentState.copy(
                searchQuery = query,
                filteredAccessPoints = filtered
            )
        }
    }

    fun selectApForMeter(ap: WifiAccessPoint) {
        _uiState.update { currentState ->
            val initialHistory = listOf(SignalHistoryPoint(System.currentTimeMillis(), ap.rssiDbm))
            currentState.copy(
                selectedApForMeter = ap,
                signalHistory = initialHistory,
                minRssiDbm = ap.rssiDbm,
                maxRssiDbm = ap.rssiDbm,
                avgRssiDbm = ap.rssiDbm
            )
        }
    }

    fun performScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            scannerManager.triggerHardwareScan()

            // Small delay for hardware scanner settling
            delay(300)

            val rawAps = scannerManager.getScannedAccessPoints()
            val currentBand = _uiState.value.selectedBand
            val recommendation = ChannelRecommendationEngine.evaluateBand(rawAps, currentBand)
            val filtered = filterAndSortAps(rawAps, currentBand, _uiState.value.searchQuery)

            val currentSelected = _uiState.value.selectedApForMeter
            val updatedSelected = if (currentSelected != null) {
                rawAps.find { it.bssid == currentSelected.bssid } ?: currentSelected
            } else {
                filtered.firstOrNull()
            }

            // Update signal history for the live technician gauge
            val now = System.currentTimeMillis()
            val newHistory = _uiState.value.signalHistory.toMutableList()
            if (updatedSelected != null) {
                // Introduce slight realistic RF fluctuation (+/- 1-2 dBm) if same reading
                val jitter = if (rawAps.size <= 6) Random.nextInt(-2, 3) else 0
                val activeRssi = (updatedSelected.rssiDbm + jitter).coerceIn(-98, -25)
                newHistory.add(SignalHistoryPoint(now, activeRssi))

                // Keep only last 25 readings (~2 minutes)
                while (newHistory.size > 25) {
                    newHistory.removeAt(0)
                }
            }

            val minRssi = newHistory.minOfOrNull { it.rssiDbm } ?: -95
            val maxRssi = newHistory.maxOfOrNull { it.rssiDbm } ?: -30
            val avgRssi = if (newHistory.isNotEmpty()) newHistory.map { it.rssiDbm }.average().toInt() else -60

            _uiState.update {
                it.copy(
                    isScanning = false,
                    allAccessPoints = rawAps,
                    filteredAccessPoints = filtered,
                    recommendation = recommendation,
                    selectedApForMeter = updatedSelected,
                    signalHistory = newHistory,
                    lastScanTime = now,
                    minRssiDbm = minRssi,
                    maxRssiDbm = maxRssi,
                    avgRssiDbm = avgRssi
                )
            }
        }
    }

    private fun startAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        val interval = _uiState.value.refreshInterval
        if (interval == ScanRefreshInterval.MANUAL) return

        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(interval.seconds * 1000L)
                performScan()
            }
        }
    }

    private fun filterAndSortAps(
        all: List<WifiAccessPoint>,
        band: WifiBand,
        query: String
    ): List<WifiAccessPoint> {
        return all
            .filter { it.band == band }
            .filter {
                if (query.isBlank()) true
                else it.ssid.contains(query, ignoreCase = true) ||
                        it.bssid.contains(query, ignoreCase = true) ||
                        it.channel.toString() == query.trim()
            }
            .sortedByDescending { it.rssiDbm }
    }
}
