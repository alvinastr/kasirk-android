package com.kasirkita.pos.data.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.kasirkita.pos.domain.model.PrinterError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bluetooth Classic RFCOMM/SPP transport for ESC/POS printers.
 * Uses paired devices only, no discovery.
 * Connect-write-close lifecycle per operation.
 */
@Singleton
class BluetoothPrinterTransport @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    /**
     * Standard Serial Port Profile UUID for generic ESC/POS printers.
     */
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * Connection timeout: 10 seconds.
     * Prevents indefinite hang if printer is powered off.
     */
    private val CONNECTION_TIMEOUT_MS = 10_000L

    /**
     * Write timeout: 15 seconds.
     * Allows time for printer buffer processing.
     */
    private val WRITE_TIMEOUT_MS = 15_000L

    /**
     * Get list of paired Bluetooth devices.
     * Requires BLUETOOTH_CONNECT permission on API 31+.
     */
    suspend fun getPairedDevices(): Result<List<BluetoothDevice>> = withContext(Dispatchers.IO) {
        try {
            if (adapter == null) {
                return@withContext Result.failure(
                    PrinterException(PrinterError.BLUETOOTH_UNAVAILABLE),
                )
            }

            if (!adapter.isEnabled) {
                return@withContext Result.failure(
                    PrinterException(PrinterError.BLUETOOTH_DISABLED),
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@withContext Result.failure(
                        PrinterException(PrinterError.PERMISSION_DENIED),
                    )
                }
            }

            val devices = adapter.bondedDevices?.toList() ?: emptyList()
            Result.success(devices)
        } catch (e: SecurityException) {
            Result.failure(PrinterException(PrinterError.PERMISSION_DENIED))
        } catch (e: Exception) {
            Result.failure(PrinterException(PrinterError.UNKNOWN))
        }
    }

    /**
     * Print bytes to specified Bluetooth device.
     * Lifecycle: connect → write → flush → close.
     * Runs off main thread with bounded timeouts.
     */
    suspend fun print(deviceAddress: String, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                if (adapter == null) {
                    return@withContext Result.failure(
                        PrinterException(PrinterError.BLUETOOTH_UNAVAILABLE),
                    )
                }

                if (!adapter.isEnabled) {
                    return@withContext Result.failure(
                        PrinterException(PrinterError.BLUETOOTH_DISABLED),
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return@withContext Result.failure(
                            PrinterException(PrinterError.PERMISSION_DENIED),
                        )
                    }
                }

                val device = adapter.getRemoteDevice(deviceAddress)
                    ?: return@withContext Result.failure(
                        PrinterException(PrinterError.DEVICE_NOT_FOUND),
                    )

                // Create RFCOMM socket using SPP UUID
                socket = withTimeout(CONNECTION_TIMEOUT_MS) {
                    device.createRfcommSocketToServiceRecord(SPP_UUID).apply {
                        connect()
                    }
                }

                // Write data with timeout
                withTimeout(WRITE_TIMEOUT_MS) {
                    socket.outputStream.apply {
                        write(data)
                        flush()
                    }
                }

                Result.success(Unit)
            } catch (e: SecurityException) {
                Result.failure(PrinterException(PrinterError.PERMISSION_DENIED))
            } catch (e: IOException) {
                Result.failure(PrinterException(PrinterError.CONNECTION_FAILED))
            } catch (e: Exception) {
                Result.failure(PrinterException(PrinterError.WRITE_FAILED))
            } finally {
                try {
                    socket?.close()
                } catch (e: IOException) {
                    // Best effort close
                }
            }
        }
}

/**
 * Printer-specific exception wrapping PrinterError.
 */
class PrinterException(val error: PrinterError) : Exception(error.name)
