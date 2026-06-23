package com.tickethunter

import org.junit.Assert.assertEquals
import org.junit.Test

class TierParserTest {
    @Test
    fun parseCommaSeparated() {
        assertEquals(listOf(580, 380, 880), TierParser.parse("580,380,880"))
    }

    @Test
    fun parseChineseComma() {
        assertEquals(listOf(580, 380), TierParser.parse("580，380"))
    }

    @Test
    fun dedupe() {
        assertEquals(listOf(580), TierParser.parse("580,580"))
    }
}
