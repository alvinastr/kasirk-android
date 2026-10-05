package com.kasirkita.pos.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.PrinterConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.printerConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "printer_config",
)

/**
 * Device-local printer configuration storage.
 * Not tenant-scoped because Bluetooth printer is physical device paired to Android device.
 */
@Singleton
class PrinterConfigDataStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.printerConfigDataStore

    val configFlow: Flow<PrinterConfig> = dataStore.data.map { preferences ->
        PrinterConfig(
            deviceAddress = preferences[DEVICE_ADDRESS],
            deviceName = preferences[DEVICE_NAME],
            paperWidthMm = preferences[PAPER_WIDTH_MM] ?: 58,
            autoPrint = preferences[AUTO_PRINT] ?: false,
            autoDrawer = preferences[AUTO_DRAWER] ?: false,
            drawerPulseProfile = preferences[DRAWER_PULSE_PROFILE]
                ?.let { name -> DrawerPulseProfile.entries.firstOrNull { it.name == name } }
                ?: DrawerPulseProfile.DEFAULT,
        )
    }

    suspend fun saveConfig(config: PrinterConfig) {
        dataStore.edit { preferences ->
            config.deviceAddress
                ?.let { preferences[DEVICE_ADDRESS] = it }
                ?: preferences.remove(DEVICE_ADDRESS)
            config.deviceName
                ?.let { preferences[DEVICE_NAME] = it }
                ?: preferences.remove(DEVICE_NAME)
            preferences[PAPER_WIDTH_MM] = config.paperWidthMm
            preferences[AUTO_PRINT] = config.autoPrint
            preferences[AUTO_DRAWER] = config.autoDrawer
            preferences[DRAWER_PULSE_PROFILE] = config.drawerPulseProfile.name
        }
    }

    private companion object {
        val DEVICE_ADDRESS = stringPreferencesKey("device_address")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val PAPER_WIDTH_MM = intPreferencesKey("paper_width_mm")
        val AUTO_PRINT = booleanPreferencesKey("auto_print")
        val AUTO_DRAWER = booleanPreferencesKey("auto_drawer")
        val DRAWER_PULSE_PROFILE = stringPreferencesKey("drawer_pulse_profile")
    }
}
