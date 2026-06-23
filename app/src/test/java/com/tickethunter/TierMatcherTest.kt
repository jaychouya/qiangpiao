package com.tickethunter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TierMatcherTest {
    @Test
    fun matchesYuan580() {
        assertTrue(TierMatcher.matchesText("¥580", 580))
    }

    @Test
    fun matches580Yuan() {
        assertTrue(TierMatcher.matchesText("580元", 580))
    }

    @Test
    fun matches1580_false() {
        assertFalse(TierMatcher.matchesText("1580元", 580))
    }

    @Test
    fun soldOut_false() {
        assertFalse(TierMatcher.matchesText("580元 缺货登记", 580))
    }

    @Test
    fun soldOutContext() {
        assertTrue(TierMatcher.isSoldOutContext("缺货登记"))
        assertFalse(TierMatcher.isSoldOutContext("580元 有票"))
    }
}
