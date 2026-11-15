package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks whether the device has a network with internet capability. Only informs the UI; never blocks the website. */
class NetworkMonitor(context: Context) {

    private val cm =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(computeOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = hasInternet(cm.getNetworkCapabilities(network))
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _isOnline.value = hasInternet(networkCapabilities)
        }
    }

    init {
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            // keep the initial value
        }
    }

    fun isCurrentlyOnline(): Boolean = computeOnline()

    private fun hasInternet(caps: NetworkCapabilities?): Boolean =
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    private fun computeOnline(): Boolean = try {
        hasInternet(cm.activeNetwork?.let { cm.getNetworkCapabilities(it) })
    } catch (_: Exception) {
        false
    }

    fun unregister() {
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }
}
