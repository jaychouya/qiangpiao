package com.tickethunter

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

abstract class BasePlatformAdapter : PlatformAdapter {
    private val buyTexts = listOf("立即购买", "立即预订", "选座购买", "立即抢购", "抢票", "购买")
    private val confirmTexts = listOf("确定", "确认", "提交订单", "立即提交", "去支付")
    private val captchaTexts = listOf("验证码", "滑块", "安全验证", "拖动")
    private val retryTexts = listOf("重试", "点击重试")

    abstract val soldOutExtra: List<String>
    abstract val paymentTexts: List<String>

    override fun pullToRefresh(service: AccessibilityService, root: AccessibilityNodeInfo) {
        val width = service.resources.displayMetrics.widthPixels
        GestureUtils.pullToRefresh(service, width)
    }

    override fun isSoldOut(root: AccessibilityNodeInfo): Boolean {
        return NodeUtils.findSoldOut(root, soldOutExtra)
    }

    override fun matchTier(root: AccessibilityNodeInfo, tier: Int): TierMatch? {
        if (isSoldOut(root)) return null
        return NodeUtils.findTierNode(root, tier)
    }

    override fun matchAnyTier(root: AccessibilityNodeInfo, tiers: List<Int>): TierMatch? {
        if (isSoldOut(root)) return null
        for (tier in tiers) {
            val match = NodeUtils.findTierNode(root, tier) ?: continue
            return match
        }
        return null
    }

    override fun stepBuy(root: AccessibilityNodeInfo): StepResult {
        return clickFirst(root, buyTexts)
    }

    override fun stepSession(root: AccessibilityNodeInfo): StepResult {
        val sessionMarkers = NodeUtils.findByTexts(root, listOf("场次"))
        if (sessionMarkers == null) return StepResult.Skipped
        val available = NodeUtils.findByTexts(root, listOf("可售", "有票", "可购"))
            ?: return StepResult.NotFound
        return clickNode(available)
    }

    override fun stepTier(root: AccessibilityNodeInfo, tier: Int): StepResult {
        val match = NodeUtils.findTierNode(root, tier) ?: return StepResult.NotFound
        return clickNode(match.node)
    }

    override fun stepConfirm(root: AccessibilityNodeInfo): StepResult {
        val retry = NodeUtils.findByTexts(root, retryTexts)
        if (retry != null) return clickNode(retry)
        return clickFirst(root, confirmTexts)
    }

    override fun isPaymentPage(root: AccessibilityNodeInfo): Boolean {
        return NodeUtils.hasText(root, paymentTexts)
    }

    override fun isCaptchaPage(root: AccessibilityNodeInfo): Boolean {
        return NodeUtils.hasText(root, captchaTexts)
    }

    private fun clickFirst(root: AccessibilityNodeInfo, texts: List<String>): StepResult {
        val node = NodeUtils.findByTexts(root, texts) ?: return StepResult.NotFound
        return clickNode(node)
    }

    private fun clickNode(node: AccessibilityNodeInfo): StepResult {
        return if (TicketMonitorService.instance?.clickNode(node) == true) {
            StepResult.Success
        } else {
            StepResult.Retry
        }
    }
}
