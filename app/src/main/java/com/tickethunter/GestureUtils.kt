package com.tickethunter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path

object GestureUtils {
    fun pullToRefresh(service: AccessibilityService, screenWidth: Int) {
        val startX = HumanBehavior.pullStartX(screenWidth)
        val startY = HumanBehavior.pullStartY()
        val endY = HumanBehavior.pullEndY()
        val duration = HumanBehavior.pullDurationMs()
        val path = Path().apply {
            moveTo(startX, startY)
            quadTo(startX + HumanBehavior.jitterPx(), (startY + endY) / 2, startX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        service.dispatchGesture(gesture, null, null)
    }
}
