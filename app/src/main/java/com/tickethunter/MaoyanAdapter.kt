package com.tickethunter

object MaoyanAdapter : BasePlatformAdapter() {
    override val soldOutExtra = emptyList<String>()
    override val paymentTexts = listOf("去支付", "确认订单", "立即付款")
}
