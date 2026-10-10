package com.kasirkita.pos.presentation.receipt

import kotlin.math.max
import kotlin.math.min

object ReceiptPreviewWidthPolicy {
    const val TARGET_58_MM_DP = 300f
    const val TARGET_80_MM_DP = 432f

    fun widthDp(paperWidthMm: Int, availableWidthDp: Float): Float {
        val target = if (paperWidthMm == 80) TARGET_80_MM_DP else TARGET_58_MM_DP
        return min(target, max(0f, availableWidthDp))
    }
}
