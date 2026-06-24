package com.tickethunter

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

class PurchaseStateMachine(
    private val adapter: PlatformAdapter,
    private val task: TicketTask,
    private val onStateChange: (MonitorState) -> Unit,
    private val onDone: () -> Unit,
    private val onPaused: () -> Unit
) {
    var state: MonitorState = MonitorState.BUYING
        private set

    var matchedTier: Int = 0
        private set

    private var stepDeadline = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var pendingStep: Runnable? = null
    private var tickRunnable: Runnable? = null

    fun start(tier: Int) {
        matchedTier = tier
        state = MonitorState.BUYING
        stepDeadline = System.currentTimeMillis() + STEP_TIMEOUT_MS
        onStateChange(state)
        startTick()
        tickNow()
    }

    fun resume() {
        if (state != MonitorState.PAUSED) return
        state = MonitorState.CONFIRMING
        stepDeadline = System.currentTimeMillis() + STEP_TIMEOUT_MS
        onStateChange(state)
        startTick()
        tickNow()
    }

    fun onContentChanged(root: AccessibilityNodeInfo) {
        if (state == MonitorState.PAUSED || state == MonitorState.DONE) return

        if (adapter.isCaptchaPage(root)) {
            stopTick()
            state = MonitorState.PAUSED
            onStateChange(state)
            onPaused()
            return
        }

        if (adapter.isPaymentPage(root)) {
            stopTick()
            state = MonitorState.DONE
            onStateChange(state)
            onDone()
            return
        }

        if (System.currentTimeMillis() > stepDeadline) {
            resetToMonitoring()
            return
        }

        val result = when (state) {
            MonitorState.BUYING -> adapter.stepBuy(root)
            MonitorState.SELECTING_SESSION -> adapter.stepSession(root)
            MonitorState.SELECTING_TIER -> adapter.stepTier(root, matchedTier)
            MonitorState.SELECTING_QUANTITY -> adapter.stepQuantity(root, task.quantity)
            MonitorState.CONFIRMING -> adapter.stepConfirm(root)
            else -> return
        }

        when (result) {
            StepResult.Success, StepResult.Skipped -> advance()
            StepResult.Retry, StepResult.NotFound -> Unit
        }
    }

    fun resetToMonitoring() {
        cancelPending()
        stopTick()
        state = MonitorState.MONITORING
        onStateChange(state)
    }

    fun destroy() {
        cancelPending()
        stopTick()
    }

    private fun advance() {
        cancelPending()
        val next = when (state) {
            MonitorState.BUYING -> MonitorState.SELECTING_SESSION
            MonitorState.SELECTING_SESSION -> MonitorState.SELECTING_TIER
            MonitorState.SELECTING_TIER -> MonitorState.SELECTING_QUANTITY
            MonitorState.SELECTING_QUANTITY -> MonitorState.CONFIRMING
            else -> state
        }
        val runnable = Runnable {
            state = next
            stepDeadline = System.currentTimeMillis() + STEP_TIMEOUT_MS
            onStateChange(state)
            tickNow()
        }
        pendingStep = runnable
        handler.postDelayed(runnable, HumanBehavior.stepDelayMs())
    }

    private fun startTick() {
        stopTick()
        val runnable = object : Runnable {
            override fun run() {
                tickNow()
                if (state != MonitorState.PAUSED &&
                    state != MonitorState.DONE &&
                    state != MonitorState.MONITORING
                ) {
                    handler.postDelayed(this, TICK_MS)
                }
            }
        }
        tickRunnable = runnable
        handler.postDelayed(runnable, TICK_MS)
    }

    private fun tickNow() {
        val root = TicketMonitorService.instance?.rootInActiveWindow ?: return
        onContentChanged(root)
    }

    private fun stopTick() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun cancelPending() {
        pendingStep?.let { handler.removeCallbacks(it) }
        pendingStep = null
    }

    companion object {
        const val STEP_TIMEOUT_MS = 12000L
        private const val TICK_MS = 400L
    }
}
