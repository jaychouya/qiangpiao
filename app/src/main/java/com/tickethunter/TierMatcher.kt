package com.tickethunter

object TierMatcher {
    val soldOutMarkers = listOf("缺货", "售罄", "无票", "暂不可售", "已约满")

    fun matchesText(text: String, tier: Int): Boolean {
        if (isSoldOutContext(text)) return false
        return Regex("""(?<!\d)${tier}(?!\d)""").containsMatchIn(text)
    }

    fun isSoldOutContext(text: String): Boolean =
        soldOutMarkers.any { text.contains(it) }
}
