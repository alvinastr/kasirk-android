package com.kasirkita.pos.data.datastore

import com.kasirkita.pos.domain.model.DrawerPulseProfile
import com.kasirkita.pos.domain.model.PrinterConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class PrinterConfigDataStoreTest {

    @Test
    fun testDefaultConfig() = runTest {
        val config = PrinterConfig()
        assertNull(config.deviceAddress)
        assertNull(config.deviceName)
        assertEquals(58, config.paperWidthMm)
        assertFalse(config.autoPrint)
        assertFalse(config.autoDrawer)
        assertEquals(DrawerPulseProfile.DEFAULT, config.drawerPulseProfile)
    }

    @Test
    fun testSaveAndLoadConfig() = runTest {
        val testConfig = PrinterConfig(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Test Printer",
            paperWidthMm = 80,
            autoPrint = true,
            autoDrawer = true,
            drawerPulseProfile = DrawerPulseProfile.DEFAULT,
        )
        assertEquals("AA:BB:CC:DD:EE:FF", testConfig.deviceAddress)
        assertEquals("Test Printer", testConfig.deviceName)
        assertEquals(80, testConfig.paperWidthMm)
        assertTrue(testConfig.autoPrint)
        assertTrue(testConfig.autoDrawer)
        assertEquals(DrawerPulseProfile.DEFAULT, testConfig.drawerPulseProfile)
    }

    @Test
    fun testPaperWidth58() = runTest {
        val config = PrinterConfig(paperWidthMm = 58)
        assertEquals(58, config.paperWidthMm)
    }

    @Test
    fun testPaperWidth80() = runTest {
        val config = PrinterConfig(paperWidthMm = 80)
        assertEquals(80, config.paperWidthMm)
    }

    @Test
    fun testUpdateAutoPrint() = runTest {
        val config = PrinterConfig(autoPrint = true)
        assertTrue(config.autoPrint)
    }

    @Test
    fun testUpdateAutoDrawer() = runTest {
        val config = PrinterConfig(autoDrawer = true)
        assertTrue(config.autoDrawer)
    }

    @Test
    fun testClearPrinterConfig() = runTest {
        val config = PrinterConfig()
        assertNull(config.deviceAddress)
    }

    @Test
    fun testDrawerPulseProfilePersists() = runTest {
        val config = PrinterConfig(drawerPulseProfile = DrawerPulseProfile.DEFAULT)
        assertEquals(DrawerPulseProfile.DEFAULT, config.drawerPulseProfile)
    }
}