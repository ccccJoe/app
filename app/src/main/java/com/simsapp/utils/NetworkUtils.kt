/*
 * File: NetworkUtils.kt
 * Description: Network connectivity utility class for checking network status and connection quality.
 * Author: SIMS Team
 */
package com.simsapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NetworkUtils
 * 
 * Utility class for network connectivity detection and status checking.
 * Provides methods to check network availability and connection quality.
 */
@Singleton
class NetworkUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Check if device has network connectivity
     * 
     * @return true if network is available, false otherwise
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkNetworkAvailabilityModern(connectivityManager)
        } else {
            checkNetworkAvailabilityLegacy(connectivityManager)
        }
    }
    
    /**
     * Check network connection quality
     * 
     * @return NetworkQuality enum indicating connection quality
     */
    fun getNetworkQuality(): NetworkQuality {
        if (!isNetworkAvailable()) {
            return NetworkQuality.NO_CONNECTION
        }
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkNetworkQualityModern(connectivityManager)
        } else {
            checkNetworkQualityLegacy(connectivityManager)
        }
    }
    
    /**
     * Check if network is suitable for sync operations
     * 
     * @return true if network quality is good enough for sync, false otherwise
     */
    fun isNetworkSuitableForSync(): Boolean {
        val quality = getNetworkQuality()
        return quality == NetworkQuality.EXCELLENT || quality == NetworkQuality.GOOD
    }
    
    /**
     * Get network status message for user display
     * 
     * @return localized message describing current network status
     */
    fun getNetworkStatusMessage(): String {
        return when (getNetworkQuality()) {
            NetworkQuality.NO_CONNECTION -> "No network connection available"
            NetworkQuality.POOR -> "Poor network connection detected"
            NetworkQuality.GOOD -> "Good network connection"
            NetworkQuality.EXCELLENT -> "Excellent network connection"
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkNetworkAvailabilityModern(connectivityManager: ConnectivityManager): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    @Suppress("DEPRECATION")
    private fun checkNetworkAvailabilityLegacy(connectivityManager: ConnectivityManager): Boolean {
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo?.isConnected == true
    }
    
    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkNetworkQualityModern(connectivityManager: ConnectivityManager): NetworkQuality {
        val network = connectivityManager.activeNetwork ?: return NetworkQuality.NO_CONNECTION
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkQuality.NO_CONNECTION
        
        return when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                // WiFi connection - generally good quality
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    NetworkQuality.EXCELLENT
                } else {
                    NetworkQuality.POOR
                }
            }
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // Cellular connection - check signal strength if available
                NetworkQuality.GOOD
            }
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                // Ethernet connection - excellent quality
                NetworkQuality.EXCELLENT
            }
            else -> NetworkQuality.POOR
        }
    }
    
    @Suppress("DEPRECATION")
    private fun checkNetworkQualityLegacy(connectivityManager: ConnectivityManager): NetworkQuality {
        val networkInfo = connectivityManager.activeNetworkInfo ?: return NetworkQuality.NO_CONNECTION
        
        return when (networkInfo.type) {
            ConnectivityManager.TYPE_WIFI -> NetworkQuality.EXCELLENT
            ConnectivityManager.TYPE_MOBILE -> NetworkQuality.GOOD
            ConnectivityManager.TYPE_ETHERNET -> NetworkQuality.EXCELLENT
            else -> NetworkQuality.POOR
        }
    }
}

/**
 * Enum representing network connection quality levels
 */
enum class NetworkQuality {
    NO_CONNECTION,  // No network connection
    POOR,          // Poor connection quality
    GOOD,          // Good connection quality  
    EXCELLENT      // Excellent connection quality
}