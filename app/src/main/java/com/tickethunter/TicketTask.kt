package com.tickethunter

enum class Platform(val packageName: String, val label: String, val packageNames: List<String>) {
    DAMAI("cn.damai", "大麦", listOf("cn.damai")),
    MAOYAN("com.sankuai.movie", "猫眼", listOf("com.sankuai.movie", "com.maoyan.android"))
}

data class TicketTask(
    val platform: Platform,
    val eventName: String,
    val targetTiers: List<Int>,
    val refreshMinMs: Long = 2000,
    val refreshMaxMs: Long = 6000,
    val quantity: Int = 1
) {
    fun tiersLabel(): String = targetTiers.joinToString(",") { "¥$it" }
}
