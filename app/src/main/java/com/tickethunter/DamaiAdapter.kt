package com.tickethunter

object DamaiAdapter : BasePlatformAdapter() {
    override val soldOutExtra = listOf("缺货登记", "暂时缺货")
    override val paymentTexts = listOf("支付宝", "确认付款", "立即付款", "提交订单")
    override val buyTexts = listOf(
        "立即购买", "立即预订", "立即预定", "选座购买", "立即抢购", "抢票", "购买"
    )
}
