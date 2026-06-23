package com.tickethunter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path

object GestureUtils {
    fun pullToRefresh(service: AccessibilityService, screenWidth: Int) {
        val path = Path().apply {
            moveTo(screenWidth / 2f, 400f)
            quadTo(screenWidth / 2f, 900f, screenWidth / 2f, 1400f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        service.dispatchGesture(gesture, null, null)
    }
}
