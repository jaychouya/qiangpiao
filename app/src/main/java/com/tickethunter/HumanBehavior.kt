package com.tickethunter

import kotlin.random.Random

object HumanBehavior {
    private const val REST_AFTER_REFRESHES = 30

    fun refreshDelayMs(minMs: Long, maxMs: Long): Long {
        val lo = minOf(minMs, maxMs)
        val hi = maxOf(minMs, maxMs)
        return Random.nextLong(lo, hi + 1)
    }

    fun clickDelayMs(): Long = Random.nextLong(100, 501)

    fun stepDelayMs(): Long = Random.nextLong(200, 801)

    fun restDelayMs(): Long = Random.nextLong(10_000, 20_001)

    fun shouldRest(refreshCount: Int): Boolean = refreshCount >= REST_AFTER_REFRESHES

    fun jitterPx(): Int = Random.nextInt(-10, 11)

    fun pullStartX(screenWidth: Int): Float = screenWidth * Random.nextDouble(0.4, 0.61).toFloat()

    fun pullStartY(): Float = Random.nextInt(300, 501).toFloat()

    fun pullEndY(): Float = Random.nextInt(1200, 1601).toFloat()

    fun pullDurationMs(): Long = Random.nextLong(250, 451)
}
