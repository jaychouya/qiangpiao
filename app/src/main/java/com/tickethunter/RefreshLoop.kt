package com.tickethunter

import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.*

class RefreshLoop(
    private val service: AccessibilityService,
    private val adapter: PlatformAdapter,
    private val task: TicketTask,
    private val isMonitoring: () -> Boolean,
    private val onRefreshed: () -> Unit
) {
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        stop()
        job = scope.launch {
            while (isActive) {
                delay(task.refreshIntervalMs)
                if (!isMonitoring()) continue
                val root = service.rootInActiveWindow ?: continue
                if (root.packageName?.toString() != task.platform.packageName) continue
                adapter.pullToRefresh(service, root)
                onRefreshed()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
