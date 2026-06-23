package com.tickethunter

object DamaiAdapter : BasePlatformAdapter() {
    override val soldOutExtra = emptyList<String>()
    override val paymentTexts = listOf("支付宝", "确认付款", "立即付款")
}
