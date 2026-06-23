package com.tickethunter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TicketMonitorService : AccessibilityService() {

    companion object {
        @Volatile var instance: TicketMonitorService? = null
        @Volatile var isRunning = false
        @Volatile var currentTask: TicketTask? = null
        @Volatile var statusText = "待命"
        @Volatile var monitorState = MonitorState.MONITORING

        private const val EVENT_FALLBACK_MS = 5000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshLoop: RefreshLoop? = null
    private var purchaseMachine: PurchaseStateMachine? = null
    private var adapter: PlatformAdapter? = null
    private var fallbackRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        stopMonitor()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return
        val task = currentTask ?: return
        if (event.packageName?.toString() != task.platform.packageName) return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        val root = rootInActiveWindow ?: return
        cancelFallback()

        when (monitorState) {
            MonitorState.MONITORING -> scanForTier(root, task)
            MonitorState.SCANNING -> scanForTier(root, task)
            else -> purchaseMachine?.onContentChanged(root)
        }
    }

    override fun onInterrupt() {
        stopMonitor()
    }

    fun startMonitor(task: TicketTask) {
        stopMonitor()
        currentTask = task
        isRunning = true
        monitorState = MonitorState.MONITORING
        adapter = adapterFor(task.platform)
        statusText = "监控中: ${task.eventName} ¥${task.targetTier}"

        val fgIntent = Intent(this, MonitorForegroundService::class.java).apply {
            putExtra(MonitorForegroundService.EXTRA_EVENT, task.eventName)
            putExtra(MonitorForegroundService.EXTRA_TIER, task.targetTier)
        }
        startForegroundService(fgIntent)

        purchaseMachine = PurchaseStateMachine(
            adapter = adapter!!,
            task = task,
            onStateChange = { state ->
                monitorState = state
                statusText = stateLabel(state, task)
            },
            onDone = {
                statusText = "已到支付页，请手动付款"
                NotifyHelper.notify(this, "TicketHunter", "已到支付页，请手动完成付款")
                stopMonitor()
            },
            onPaused = {
                NotifyHelper.notify(this, "TicketHunter", "请手动完成验证码")
            }
        )

        refreshLoop = RefreshLoop(
            service = this,
            adapter = adapter!!,
            task = task,
            isMonitoring = { isRunning && monitorState == MonitorState.MONITORING }
        ) {
            scheduleFallback()
        }
        refreshLoop?.start()
    }

    fun stopMonitor() {
        isRunning = false
        refreshLoop?.stop()
        refreshLoop = null
        purchaseMachine = null
        adapter = null
        cancelFallback()
        monitorState = MonitorState.MONITORING
        stopService(Intent(this, MonitorForegroundService::class.java))
        statusText = "已停止"
    }

    fun resumeFromPause() {
        purchaseMachine?.resume()
    }

    private fun scanForTier(root: AccessibilityNodeInfo, task: TicketTask) {
        val adp = adapter ?: return
        monitorState = MonitorState.SCANNING
        val match = adp.matchTier(root, task.targetTier)
        if (match != null) {
            statusText = "命中 ¥${task.targetTier}，开始下单"
            clickNode(match.node)
            purchaseMachine?.start()
            monitorState = MonitorState.BUYING
            purchaseMachine?.onContentChanged(root)
        } else {
            monitorState = MonitorState.MONITORING
        }
    }

    private fun scheduleFallback() {
        cancelFallback()
        val runnable = Runnable {
            if (!isRunning || monitorState != MonitorState.MONITORING) return@Runnable
            val root = rootInActiveWindow ?: return@Runnable
            scanForTier(root, currentTask ?: return@Runnable)
        }
        fallbackRunnable = runnable
        mainHandler.postDelayed(runnable, EVENT_FALLBACK_MS)
    }

    private fun cancelFallback() {
        fallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        fallbackRunnable = null
    }

    private fun adapterFor(platform: Platform): PlatformAdapter = when (platform) {
        Platform.DAMAI -> DamaiAdapter
        Platform.MAOYAN -> MaoyanAdapter
    }

    private fun stateLabel(state: MonitorState, task: TicketTask): String = when (state) {
        MonitorState.MONITORING -> "监控中: ${task.eventName} ¥${task.targetTier}"
        MonitorState.SCANNING -> "扫描票档..."
        MonitorState.BUYING -> "点击购买..."
        MonitorState.SELECTING_SESSION -> "选择场次..."
        MonitorState.SELECTING_TIER -> "选择票档 ¥${task.targetTier}..."
        MonitorState.CONFIRMING -> "确认订单..."
        MonitorState.PAUSED -> "等待验证码"
        MonitorState.DONE -> "已到支付页"
    }

    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = parent.parent
        }
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        val path = Path().apply {
            moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
