package com.tickethunter

object TierParser {
    fun parse(input: String): List<Int> {
        return input.split(",", "，", " ", "、")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..99999 }
            .distinct()
    }
}
