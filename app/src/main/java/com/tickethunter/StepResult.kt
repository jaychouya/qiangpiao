package com.tickethunter

import android.view.accessibility.AccessibilityNodeInfo

data class TierMatch(val tier: Int, val node: AccessibilityNodeInfo)

sealed class StepResult {
    data object Success : StepResult()
    data object Retry : StepResult()
    data object NotFound : StepResult()
    data object Skipped : StepResult()
}
