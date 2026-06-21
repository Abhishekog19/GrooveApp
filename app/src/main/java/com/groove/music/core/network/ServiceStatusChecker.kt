package com.groove.music.core.network

import android.util.Log
import com.groove.music.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ServiceStatusChecker"

/**
 * Checks whether the backend TIDAL proxy is functional.
 * Hits /api/tidal-download/search?q=test&limit=1 to prove end-to-end connectivity.
 *
 * States:
 *   CHECKING  — probe in flight
 *   OK        — proxy returned usable data
 *   DOWN      — proxy is unreachable or returning errors
 */
enum class ServiceStatus { CHECKING, OK, DOWN }

@Singleton
class ServiceStatusChecker @Inject constructor() {

    private val _status = MutableStateFlow(ServiceStatus.CHECKING)
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Probe the search endpoint — this proves the full proxy + TIDAL chain works
    private val probeUrl: String
        get() {
            val base = BuildConfig.SERVER_BASE_URL.let {
                if (it.endsWith("/")) it.dropLast(1) else it
            }
            return "$base/api/tidal-download/search?q=test&limit=1"
        }

    private val healthUrl: String
        get() {
            val base = BuildConfig.SERVER_BASE_URL.let {
                if (it.endsWith("/")) it.dropLast(1) else it
            }
            return "$base/api/health"
        }

    private var pollingJob: Job? = null

    /**
     * Start periodic checking. Safe to call multiple times — only one loop runs.
     */
    fun startChecking() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                check()
                delay(3 * 60 * 1000L) // Re-check every 3 minutes
            }
        }
    }

    /**
     * Force an immediate re-check (e.g. when user taps "Retry").
     */
    fun retry() {
        scope.launch { check() }
    }

    private suspend fun check() {
        _status.value = ServiceStatus.CHECKING
        try {
            // Step 1: Check if the server is alive at all
            val healthOk = probeEndpoint(healthUrl, 5000)
            if (!healthOk) {
                Log.w(TAG, "Health check failed — server is down")
                _status.value = ServiceStatus.DOWN
                return
            }

            // Step 2: Check if the TIDAL proxy actually works end-to-end
            val proxyOk = probeEndpoint(probeUrl, 10000)
            if (proxyOk) {
                Log.d(TAG, "Service status: OK")
                _status.value = ServiceStatus.OK
            } else {
                Log.w(TAG, "TIDAL proxy probe failed — service is down")
                _status.value = ServiceStatus.DOWN
            }
        } catch (e: Exception) {
            Log.e(TAG, "Service check failed: ${e.message}")
            _status.value = ServiceStatus.DOWN
        }
    }

    /**
     * Makes a GET request and returns true if the response is 2xx with valid JSON body.
     */
    private suspend fun probeEndpoint(url: String, timeoutMs: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.setRequestProperty("Accept", "application/json")
                conn.connect()

                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    // Verify it's actual JSON, not an HTML error page
                    body.trimStart().let { it.startsWith("{") || it.startsWith("[") }
                } else {
                    conn.disconnect()
                    false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Probe to $url failed: ${e.message}")
                false
            }
        }
}
