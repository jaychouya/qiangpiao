# TicketHunter 回流票监控 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将现有盲轮询原型重构为事件驱动回流票监控，支持指定票档、自动刷新、购买状态机、前台保活。

**Architecture:** `RefreshLoop` 定时下拉 → `onAccessibilityEvent` 触发 `TierMatcher` → 命中后 `PurchaseStateMachine` 走平台无关漏斗 → `PlatformAdapter` 隔离大麦/猫眼差异。

**Tech Stack:** Kotlin, Android AccessibilityService, ForegroundService, JUnit4, Robolectric(optional)/纯 JVM 测试

**Spec:** `docs/superpowers/specs/2026-06-22-ticket-hunter-design.md`

---

## File Map

| File | Responsibility |
|------|----------------|
| `MonitorState.kt` | 状态枚举 |
| `StepResult.kt` | 步骤结果 + TierMatch |
| `TicketTask.kt` | 任务配置 |
| `TierMatcher.kt` | 票档正则匹配（纯逻辑，可单测） |
| `PlatformAdapter.kt` | 平台接口 |
| `DamaiAdapter.kt` | 大麦实现 |
| `MaoyanAdapter.kt` | 猫眼实现 |
| `NodeUtils.kt` | 节点遍历/点击辅助 |
| `GestureUtils.kt` | 下拉刷新手势 |
| `PurchaseStateMachine.kt` | 购买漏斗状态机 |
| `RefreshLoop.kt` | 定时刷新协程 |
| `TicketMonitorService.kt` | 无障碍服务入口 |
| `MonitorForegroundService.kt` | 前台通知 + WakeLock |
| `MainActivity.kt` | 配置 UI |

---

### Task 1: 测试基础设施 + TierMatcher

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/tickethunter/TierMatcher.kt`
- Create: `app/src/test/java/com/tickethunter/TierMatcherTest.kt`

- [ ] **Step 1: 添加 test 依赖**

```kotlin
// app/build.gradle.kts dependencies 块追加
testImplementation("junit:junit:4.13.2")
```

```kotlin
// android {} 块追加
testOptions {
    unitTests.isReturnDefaultValues = true
}
```

- [ ] **Step 2: 写失败测试**

```kotlin
// TierMatcherTest.kt
package com.tickethunter

import org.junit.Assert.*
import org.junit.Test

class TierMatcherTest {
    @Test fun matchesYuan580() {
        assertTrue(TierMatcher.matchesText("¥580", 580))
    }
    @Test fun matches580Yuan() {
        assertTrue(TierMatcher.matchesText("580元", 580))
    }
    @Test fun matches1580_false() {
        assertFalse(TierMatcher.matchesText("1580元", 580))
    }
    @Test fun soldOut_false() {
        assertFalse(TierMatcher.matchesText("580元 缺货登记", 580))
    }
}
```

- [ ] **Step 3: 实现 TierMatcher**

```kotlin
// TierMatcher.kt
package com.tickethunter

object TierMatcher {
    private val soldOut = listOf("缺货", "售罄", "登记")

    fun matchesText(text: String, tier: Int): Boolean {
        if (soldOut.any { text.contains(it) }) return false
        return Regex("""(?<!\d)${tier}(?!\d)""").containsMatchIn(text)
    }
}
```

- [ ] **Step 4: 运行测试**

```bash
./gradlew :app:testDebugUnitTest --tests com.tickethunter.TierMatcherTest
```

- [ ] **Step 5: Commit**

---

### Task 2: 数据模型重构

**Files:**
- Create: `app/src/main/java/com/tickethunter/MonitorState.kt`
- Create: `app/src/main/java/com/tickethunter/StepResult.kt`
- Modify: `app/src/main/java/com/tickethunter/TicketTask.kt`

- [ ] **Step 1: MonitorState**

```kotlin
package com.tickethunter

enum class MonitorState {
    MONITORING, SCANNING, BUYING, SELECTING_SESSION,
    SELECTING_TIER, CONFIRMING, PAUSED, DONE
}
```

- [ ] **Step 2: StepResult + TierMatch**

```kotlin
package com.tickethunter

import android.view.accessibility.AccessibilityNodeInfo

data class TierMatch(val tier: Int, val node: AccessibilityNodeInfo)

sealed class StepResult {
    data object Success : StepResult()
    data object Retry : StepResult()
    data object NotFound : StepResult()
    data object Skipped : StepResult()
}
```

- [ ] **Step 3: 更新 TicketTask**

```kotlin
data class TicketTask(
    val platform: Platform,
    val eventName: String,
    val targetTier: Int,
    val refreshIntervalMs: Long = 2000,
    val quantity: Int = 1
)
```

---

### Task 3: PlatformAdapter + DamaiAdapter

**Files:**
- Create: `app/src/main/java/com/tickethunter/PlatformAdapter.kt`
- Create: `app/src/main/java/com/tickethunter/DamaiAdapter.kt`
- Create: `app/src/main/java/com/tickethunter/GestureUtils.kt`
- Delete: `app/src/main/java/com/tickethunter/DamaiHandler.kt`

- [ ] **Step 1: PlatformAdapter 接口**（见 spec §9）

- [ ] **Step 2: GestureUtils.pullToRefresh**

```kotlin
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
```

- [ ] **Step 3: DamaiAdapter** — 实现 `matchTier`（遍历节点 + `TierMatcher.matchesText`）、`stepBuy`/`stepSession`/`stepTier`/`stepConfirm`、`isPaymentPage`/`isCaptchaPage`

- [ ] **Step 4: 删除 DamaiHandler.kt**

---

### Task 4: MaoyanAdapter

**Files:**
- Create: `app/src/main/java/com/tickethunter/MaoyanAdapter.kt`
- Delete: `app/src/main/java/com/tickethunter/MaoyanHandler.kt`

- [ ] 同 DamaiAdapter 结构，文案差异见 spec §9.1

---

### Task 5: PurchaseStateMachine

**Files:**
- Create: `app/src/main/java/com/tickethunter/PurchaseStateMachine.kt`

- [ ] **核心逻辑**

```kotlin
class PurchaseStateMachine(
    private val adapter: PlatformAdapter,
    private val task: TicketTask,
    private val click: (AccessibilityNodeInfo) -> Boolean,
    private val onStateChange: (MonitorState) -> Unit,
    private val onDone: () -> Unit,
    private val onPaused: () -> Unit
) {
    var state: MonitorState = MonitorState.BUYING
    private var stepDeadline = 0L

    fun onContentChanged(root: AccessibilityNodeInfo) {
        if (state == MonitorState.PAUSED || state == MonitorState.DONE) return
        if (adapter.isCaptchaPage(root)) { state = MonitorState.PAUSED; onPaused(); return }
        if (adapter.isPaymentPage(root)) { state = MonitorState.DONE; onDone(); return }
        if (System.currentTimeMillis() > stepDeadline) { resetToMonitoring(); return }

        val result = when (state) {
            MonitorState.BUYING -> adapter.stepBuy(root)
            MonitorState.SELECTING_SESSION -> adapter.stepSession(root)
            MonitorState.SELECTING_TIER -> adapter.stepTier(root, task.targetTier)
            MonitorState.CONFIRMING -> adapter.stepConfirm(root)
            else -> return
        }
        when (result) {
            is StepResult.Success -> advance()
            is StepResult.Skipped -> advance()
            is StepResult.Retry -> { /* wait next event */ }
            is StepResult.NotFound -> { /* wait next event */ }
        }
    }

    private fun advance() {
        state = when (state) {
            MonitorState.BUYING -> MonitorState.SELECTING_SESSION
            MonitorState.SELECTING_SESSION -> MonitorState.SELECTING_TIER
            MonitorState.SELECTING_TIER -> MonitorState.CONFIRMING
            else -> state
        }
        stepDeadline = System.currentTimeMillis() + 3000
        onStateChange(state)
    }

    fun resetToMonitoring() {
        state = MonitorState.MONITORING
        onStateChange(state)
    }
}
```

---

### Task 6: RefreshLoop + TicketMonitorService 重构

**Files:**
- Create: `app/src/main/java/com/tickethunter/RefreshLoop.kt`
- Modify: `app/src/main/java/com/tickethunter/TicketMonitorService.kt`

- [ ] **RefreshLoop** — 仅在 `state == MONITORING` 时按 `refreshIntervalMs` 调用 `adapter.pullToRefresh`

- [ ] **TicketMonitorService 改动要点:**
  - 删除 `tick()` 和盲轮询 `while` 循环
  - `onAccessibilityEvent`: 处理 `TYPE_WINDOW_CONTENT_CHANGED`
  - `MONITORING` → `SCANNING` → `adapter.matchTier` → 命中启动 `PurchaseStateMachine`
  - 刷新后 5s 无事件 → `handler.postDelayed` 强制 scan
  - 保留 `clickNode()` 供 adapter 使用

---

### Task 7: MonitorForegroundService

**Files:**
- Create: `app/src/main/java/com/tickethunter/MonitorForegroundService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] 低优先级通知 channel
- [ ] `PARTIAL_WAKE_LOCK` 在 `startMonitor` 获取，`stopMonitor` 释放
- [ ] `TicketMonitorService.startMonitor` 时 `startForegroundService`

---

### Task 8: UI 更新

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/tickethunter/MainActivity.kt`

- [ ] `maxPriceInput` → `tierInput`（指定票档）
- [ ] 新增 `quantityInput`
- [ ] 新增 `btnResume`（PAUSED 时显示）
- [ ] `intervalInput` 默认值 2000

---

### Task 9: 构建验证

- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- [ ] 更新 `task_plan.md` 各 Phase Status → complete
