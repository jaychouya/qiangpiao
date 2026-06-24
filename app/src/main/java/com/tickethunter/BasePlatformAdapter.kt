package com.tickethunter

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

abstract class BasePlatformAdapter : PlatformAdapter {
    protected open val buyTexts = listOf(
        "立即购买", "立即预订", "立即预定", "选座购买", "立即抢购", "抢票", "购买", "去抢票"
    )
    private val confirmTexts = listOf("确定", "确认", "提交订单", "立即提交", "去支付", "确认购买")
    private val captchaTexts = listOf("验证码", "滑块", "安全验证", "拖动", "请完成验证")
    private val retryTexts = listOf("重试", "点击重试", "重新加载")
    private val tierPageTexts = listOf("票档", "票价", "选择票档")

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
        return matchAnyTier(root, listOf(tier))
    }

    override fun matchAnyTier(root: AccessibilityNodeInfo, tiers: List<Int>): TierMatch? {
        for (tier in tiers) {
            NodeUtils.findAvailableTierNode(root, tier)?.let { return it }
        }
        return null
    }

    override fun stepBuy(root: AccessibilityNodeInfo): StepResult {
        val buy = NodeUtils.findByTexts(root, buyTexts) ?: return StepResult.NotFound
        return clickNode(buy)
    }

    override fun stepSession(root: AccessibilityNodeInfo): StepResult {
        if (NodeUtils.findByTexts(root, tierPageTexts) != null) return StepResult.Skipped
        NodeUtils.findByTexts(root, buyTexts)?.let { return clickNode(it) }
        val session = NodeUtils.findSessionOption(root) ?: return StepResult.Skipped
        return clickNode(session)
    }

    override fun stepTier(root: AccessibilityNodeInfo, tier: Int): StepResult {
        NodeUtils.findByTexts(root, buyTexts)?.let { return clickNode(it) }
        val match = NodeUtils.findAvailableTierNode(root, tier) ?: return StepResult.NotFound
        return clickNode(match.node)
    }

    override fun stepQuantity(root: AccessibilityNodeInfo, quantity: Int): StepResult {
        if (quantity <= 1) return StepResult.Skipped
        val plus = NodeUtils.findPlusButton(root) ?: return StepResult.Skipped
        repeat(quantity - 1) {
            if (clickNode(plus) != StepResult.Success) return StepResult.Retry
        }
        return StepResult.Success
    }

    override fun stepConfirm(root: AccessibilityNodeInfo): StepResult {
        val retry = NodeUtils.findByTexts(root, retryTexts)
        if (retry != null) return clickNode(retry)
        val confirm = NodeUtils.findByTexts(root, confirmTexts) ?: return StepResult.NotFound
        return clickNode(confirm)
    }

    override fun isPaymentPage(root: AccessibilityNodeInfo): Boolean {
        return NodeUtils.hasText(root, paymentTexts)
    }

    override fun isCaptchaPage(root: AccessibilityNodeInfo): Boolean {
        return NodeUtils.hasText(root, captchaTexts)
    }

    private fun clickNode(node: AccessibilityNodeInfo): StepResult {
        val target = NodeUtils.bestClickTarget(node) ?: node
        return if (TicketMonitorService.instance?.clickNode(target) == true) {
            StepResult.Success
        } else {
            StepResult.Retry
        }
    }
}
