package com.kasirkita.pos.domain.model

/**
 * Printer configuration persisted in DataStore.
 * Device-local scope (not tenant-scoped).
 */
data class PrinterConfig(
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val paperWidthMm: Int = 58,
    val autoPrint: Boolean = false,
    val autoDrawer: Boolean = false,
    val drawerPulseProfile: DrawerPulseProfile = DrawerPulseProfile.DEFAULT,
)

enum class PaperWidth(val mm: Int) {
    WIDTH_58MM(58),
    WIDTH_80MM(80),
}

enum class DrawerPulseProfile {
    /**
     * Generic ESC/POS drawer kick using pin 2.
     * Physical compatibility with VSC TM-58D unverified until M16.
     */
    DEFAULT,
}
