package com.tickethunter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
        private const val EVENT_DEBOUNCE_MS = 200L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshLoop: RefreshLoop? = null
    private var purchaseMachine: PurchaseStateMachine? = null
    private var adapter: PlatformAdapter? = null
    private var fallbackRunnable: Runnable? = null
    private var debounceRunnable: Runnable? = null
    private var lastEventAt = 0L
    private var pendingEvent: AccessibilityEvent? = null

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
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in task.platform.packageNames) return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        pendingEvent = AccessibilityEvent.obtain(event)
        val now = SystemClock.uptimeMillis()
        if (now - lastEventAt < EVENT_DEBOUNCE_MS) {
            scheduleDebounce()
            return
        }
        lastEventAt = now
        processEvent()
    }

    private fun scheduleDebounce() {
        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            lastEventAt = SystemClock.uptimeMillis()
            processEvent()
        }
        debounceRunnable = runnable
        mainHandler.postDelayed(runnable, EVENT_DEBOUNCE_MS)
    }

    private fun processEvent() {
        pendingEvent?.recycle()
        pendingEvent = null
        val task = currentTask ?: return
        val root = rootInActiveWindow ?: return
        cancelFallback()

        when (monitorState) {
            MonitorState.MONITORING, MonitorState.SCANNING -> scanForTier(root, task)
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
        statusText = "监控中: ${task.eventName} ${task.tiersLabel()}"

        val fgIntent = Intent(this, MonitorForegroundService::class.java).apply {
            putExtra(MonitorForegroundService.EXTRA_EVENT, task.eventName)
            putExtra(MonitorForegroundService.EXTRA_TIERS, task.tiersLabel())
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
            isMonitoring = { isRunning && monitorState == MonitorState.MONITORING },
            onRefreshed = { scheduleFallback() },
            onResting = { resting ->
                statusText = if (resting) "休息中..." else "监控中: ${task.eventName} ${task.tiersLabel()}"
            }
        )
        refreshLoop?.start()
    }

    fun stopMonitor() {
        isRunning = false
        refreshLoop?.stop()
        refreshLoop = null
        purchaseMachine?.destroy()
        purchaseMachine = null
        adapter = null
        cancelFallback()
        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        debounceRunnable = null
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
        val match = adp.matchAnyTier(root, task.targetTiers)
        if (match != null) {
            statusText = "命中 ¥${match.tier}，开始下单"
            clickNode(match.node)
            purchaseMachine?.start(match.tier)
            monitorState = MonitorState.BUYING
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

    private fun stateLabel(state: MonitorState, task: TicketTask): String {
        val tier = purchaseMachine?.matchedTier ?: task.targetTiers.first()
        return when (state) {
            MonitorState.MONITORING -> "监控中: ${task.eventName} ${task.tiersLabel()}"
            MonitorState.SCANNING -> "扫描票档..."
            MonitorState.BUYING -> "点击购买..."
            MonitorState.SELECTING_SESSION -> "选择场次..."
            MonitorState.SELECTING_TIER -> "选择票档 ¥$tier..."
            MonitorState.SELECTING_QUANTITY -> "选择数量 ${task.quantity}..."
            MonitorState.CONFIRMING -> "确认订单..."
            MonitorState.PAUSED -> "等待验证码"
            MonitorState.DONE -> "已到支付页"
        }
    }

    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        try {
            Thread.sleep(HumanBehavior.clickDelayMs())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = parent.parent
        }
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        val jx = HumanBehavior.jitterPx()
        val jy = HumanBehavior.jitterPx()
        val path = Path().apply {
            moveTo(rect.centerX().toFloat() + jx, rect.centerY().toFloat() + jy)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, HumanBehavior.pullDurationMs().coerceAtMost(120)))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
