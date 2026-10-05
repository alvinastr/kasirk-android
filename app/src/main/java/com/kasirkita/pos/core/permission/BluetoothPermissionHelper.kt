package com.kasirkita.pos.core.permission

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bluetooth permission handling across Android API levels.
 * Paired devices only, no scanning/discovery.
 */
@Singleton
class BluetoothPermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Check if all required Bluetooth permissions are granted.
     * API 26-30: BLUETOOTH, BLUETOOTH_ADMIN
     * API 31+: BLUETOOTH_CONNECT (for accessing paired devices)
     */
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: BLUETOOTH_CONNECT required for accessing bondedDevices
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // API 26-30: BLUETOOTH permission granted at install time
            true
        }
    }

    /**
     * Get permissions to request at runtime.
     * API 26-30: empty (BLUETOOTH granted at install)
     * API 31+: BLUETOOTH_CONNECT
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }
    }

    /**
     * Check if Bluetooth is available on device.
     */
    fun isBluetoothAvailable(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null
    }

    /**
     * Check if Bluetooth is currently enabled.
     */
    fun isBluetoothEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return adapter.isEnabled
    }
}
