package com.tickethunter

import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.*

class RefreshLoop(
    private val service: AccessibilityService,
    private val adapter: PlatformAdapter,
    private val task: TicketTask,
    private val isMonitoring: () -> Boolean,
    private val onRefreshed: () -> Unit,
    private val onResting: (Boolean) -> Unit
) {
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshCount = 0

    fun start() {
        stop()
        refreshCount = 0
        job = scope.launch {
            while (isActive) {
                val delayMs = HumanBehavior.refreshDelayMs(task.refreshMinMs, task.refreshMaxMs)
                delay(delayMs)
                if (!isMonitoring()) continue

                if (HumanBehavior.shouldRest(refreshCount)) {
                    onResting(true)
                    delay(HumanBehavior.restDelayMs())
                    refreshCount = 0
                    onResting(false)
                    if (!isMonitoring()) continue
                }

                val root = service.rootInActiveWindow ?: continue
                val pkg = root.packageName?.toString() ?: continue
                if (pkg !in task.platform.packageNames) continue

                adapter.pullToRefresh(service, root)
                refreshCount++
                onRefreshed()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        refreshCount = 0
    }
}
