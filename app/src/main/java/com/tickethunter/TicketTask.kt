package com.tickethunter

enum class Platform(val packageName: String, val label: String) {
    DAMAI("cn.damai", "大麦"),
    MAOYAN("com.sankuai.movie", "猫眼")
}

data class TicketTask(
    val platform: Platform,
    val eventName: String,
    val targetTier: Int,
    val refreshIntervalMs: Long = 2000,
    val quantity: Int = 1
)
