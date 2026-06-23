package com.tickethunter

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

interface PlatformAdapter {
    fun pullToRefresh(service: AccessibilityService, root: AccessibilityNodeInfo)
    fun isSoldOut(root: AccessibilityNodeInfo): Boolean
    fun matchTier(root: AccessibilityNodeInfo, tier: Int): TierMatch?
    fun matchAnyTier(root: AccessibilityNodeInfo, tiers: List<Int>): TierMatch?
    fun stepBuy(root: AccessibilityNodeInfo): StepResult
    fun stepSession(root: AccessibilityNodeInfo): StepResult
    fun stepTier(root: AccessibilityNodeInfo, tier: Int): StepResult
    fun stepConfirm(root: AccessibilityNodeInfo): StepResult
    fun isPaymentPage(root: AccessibilityNodeInfo): Boolean
    fun isCaptchaPage(root: AccessibilityNodeInfo): Boolean
}
