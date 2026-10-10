package com.kasirkita.pos.presentation.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptPreviewWidthPolicyTest {
    @Test
    fun targetsKeep58MmNarrowerThan80Mm() {
        val width58 = ReceiptPreviewWidthPolicy.widthDp(58, availableWidthDp = 1_200f)
        val width80 = ReceiptPreviewWidthPolicy.widthDp(80, availableWidthDp = 1_200f)

        assertEquals(ReceiptPreviewWidthPolicy.TARGET_58_MM_DP, width58)
        assertEquals(ReceiptPreviewWidthPolicy.TARGET_80_MM_DP, width80)
        assertTrue(width58 < width80)
    }

    @Test
    fun targetNeverExpandsToArbitraryTabletWidth() {
        assertEquals(
            ReceiptPreviewWidthPolicy.TARGET_58_MM_DP,
            ReceiptPreviewWidthPolicy.widthDp(58, availableWidthDp = 1_600f),
        )
        assertEquals(
            ReceiptPreviewWidthPolicy.TARGET_80_MM_DP,
            ReceiptPreviewWidthPolicy.widthDp(80, availableWidthDp = 1_600f),
        )
    }

    @Test
    fun targetsClampToAvailablePhoneWidth() {
        assertEquals(264f, ReceiptPreviewWidthPolicy.widthDp(58, availableWidthDp = 264f))
        assertEquals(264f, ReceiptPreviewWidthPolicy.widthDp(80, availableWidthDp = 264f))
    }

    @Test
    fun unsupportedPaperWidthUses58MmTarget() {
        assertEquals(
            ReceiptPreviewWidthPolicy.TARGET_58_MM_DP,
            ReceiptPreviewWidthPolicy.widthDp(57, availableWidthDp = 1_200f),
        )
    }
}
