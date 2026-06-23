package com.tickethunter

object MaoyanAdapter : BasePlatformAdapter() {
    override val soldOutExtra = listOf("暂时无票", "缺货")
    override val paymentTexts = listOf("去支付", "确认订单", "立即付款", "微信支付")
    override val buyTexts = listOf(
        "立即购买", "立即预订", "抢票", "去抢票", "立即抢购", "购买"
    )
}
