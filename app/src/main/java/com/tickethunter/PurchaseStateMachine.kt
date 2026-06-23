package com.tickethunter

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

    private var stepDeadline = 0L

    fun start() {
        state = MonitorState.BUYING
        stepDeadline = System.currentTimeMillis() + STEP_TIMEOUT_MS
        onStateChange(state)
    }

    fun resume() {
        if (state != MonitorState.PAUSED) return
        state = MonitorState.CONFIRMING
        stepDeadline = System.currentTimeMillis() + STEP_TIMEOUT_MS
        onStateChange(state)
    }

    fun onContentChanged(root: AccessibilityNodeInfo) {
        if (state == MonitorState.PAUSED || state == MonitorState.DONE) return

        if (adapter.isCaptchaPage(root)) {
            state = MonitorState.PAUSED
            onStateChange(state)
            onPaused()
            return
        }

        if (adapter.isPaymentPage(root)) {
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
            MonitorState.SELECTING_TIER -> adapter.stepTier(root, task.targetTier)
            MonitorState.CONFIRMING -> adapter.stepConfirm(root)
            else -> return
        }

        when (result) {
            StepResult.Success, StepResult.Skipped -> advance()
            StepResult.Retry, StepResult.NotFound -> Unit
        }
    }

    fun resetToMonitoring() {
        state = MonitorState.MONITORING
        onStateChange(state)
    }

    private fun advance() {
        state = when (state) {
            MonitorState.BUYING -> MonitorState.SELECTING_SESSION
            MonitorState.SELECTING_SESSION -> MonitorState.SELECTING_TIER
            MonitorState.SELECTING_TIER -> MonitorState.CONFIRMING
            else -> state
        }
        stepDeadline = System.currentTimeMillis() + STEP_TIMEOUT_MS
        onStateChange(state)
    }

    companion object {
        const val STEP_TIMEOUT_MS = 3000L
    }
}
