package com.tickethunter

object TierMatcher {
    private val soldOut = listOf("缺货", "售罄", "登记")

    fun matchesText(text: String, tier: Int): Boolean {
        if (soldOut.any { text.contains(it) }) return false
        return Regex("""(?<!\d)${tier}(?!\d)""").containsMatchIn(text)
    }
}
