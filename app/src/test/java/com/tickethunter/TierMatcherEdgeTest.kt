package com.tickethunter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TierMatcherEdgeTest {
    @Test
    fun availableTierText() {
        assertTrue(TierMatcher.matchesText("看台 580元", 580))
        assertTrue(TierMatcher.matchesText("¥580 可购", 580))
    }

    @Test
    fun soldOutBlocksMatch() {
        assertFalse(TierMatcher.matchesText("580元 售罄", 580))
        assertFalse(TierMatcher.matchesText("580元 缺货", 580))
        assertFalse(TierMatcher.matchesText("580元 无票", 580))
    }

    @Test
    fun partialSoldOutContext() {
        assertTrue(TierMatcher.isSoldOutContext("缺货登记"))
        assertTrue(TierMatcher.isSoldOutContext("暂时无票"))
        assertFalse(TierMatcher.isSoldOutContext("580元"))
    }
}
