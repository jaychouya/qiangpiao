# TicketHunter 回流票监控 — 设计规格

**日期**: 2026-06-22  
**状态**: 待实现  
**平台**: Android（大麦 / 猫眼）

## 1. 目标

在 Android 手机上自动蹲守大麦/猫眼演出详情页，定时刷新库存；当**指定票档**回流可买时，自动走完非选座购买漏斗至支付页，由用户手动完成付款。

## 2. 需求摘要

| 维度 | 决策 |
|------|------|
| 场景 | 回流票，长时间蹲守 |
| 购票方式 | 非选座（票档制） |
| 票档策略 | 仅抢用户预设的指定档位（如 580） |
| 库存刷新 | 工具自动下拉刷新 |
| 平台 | 大麦、猫眼（`PlatformAdapter` 抽象） |

## 3. 第一性原理

回流票的本质：**在「票从不可买 → 可买」的瞬间，比别人更快完成「选场次 → 选票档 → 锁单」。**

系统拆为三层：

1. **感知** — 刷新后发现 UI 变化（事件驱动，非盲轮询）
2. **决策** — 仅当指定票档可买时触发
3. **执行** — 状态机走完购买漏斗至支付页

## 4. 架构

```
┌─────────────────────────────────────────┐
│              MainActivity               │
│  配置：平台 / 演出 / 指定票档 / 刷新间隔   │
└─────────────────┬───────────────────────┘
                  │ start/stop
┌─────────────────▼───────────────────────┐
│         TicketMonitorService            │
│  ┌─────────────┐    ┌────────────────┐  │
│  │ RefreshLoop │───▶│ EventTrigger   │  │
│  │ (定时下拉)   │    │ (UI变化即扫描)  │  │
│  └─────────────┘    └───────┬────────┘  │
│                             │           │
│                    ┌────────▼────────┐  │
│                    │ AvailabilityScan│  │
│                    │ 匹配指定票档     │  │
│                    └────────┬────────┘  │
│                             │ 命中       │
│                    ┌────────▼────────┐  │
│                    │ PurchaseStateMachine │
│                    │ SESSION→TIER→QTY→CONFIRM │
│                    └────────┬────────┘  │
└─────────────────────────────┼───────────┘
                              ▼
                    支付页 → 通知用户手动付款
```

### 4.1 模块职责

| 模块 | 职责 |
|------|------|
| `RefreshLoop` | 售罄态定时下拉刷新，不扫描、不购买 |
| `EventTrigger` | 监听 `TYPE_WINDOW_CONTENT_CHANGED`，触发扫描 |
| `AvailabilityScan` | 判断指定票档是否可买 |
| `PurchaseStateMachine` | 命中后逐步走完购买漏斗 |
| `PlatformAdapter` | 大麦/猫眼 UI 差异隔离 |

### 4.2 对现有代码的改动

- 删除 `tick()` 盲轮询循环
- 启用 `onAccessibilityEvent`
- `DamaiHandler` / `MaoyanHandler` 重构为 `DamaiAdapter` / `MaoyanAdapter`，实现 `PlatformAdapter`
- `TicketTask.maxPrice` 替换为 `targetTier: Int`
- 新增 `PurchaseStateMachine`、`ForegroundService`

## 5. 状态机

```
MONITORING ──刷新完成──▶ SCANNING ──未命中──▶ MONITORING
                              │
                         命中指定票档
                              ▼
                          BUYING ──点「立即购买」──▶ SELECTING_SESSION
                                                          │
                                                     单场次跳过
                                                          ▼
                                                    SELECTING_TIER
                                                          │
                                                     确认票档匹配
                                                          ▼
                                                    CONFIRMING ──到支付页──▶ DONE
                                                          │
                                                     遇到验证码
                                                          ▼
                                                      PAUSED（通知用户）
```

| 状态 | 行为 | 退出条件 |
|------|------|----------|
| `MONITORING` | 等待下次定时刷新 | 刷新触发 |
| `SCANNING` | 扫描页面匹配指定票档 | 命中 → `BUYING`；未命中 → `MONITORING` |
| `BUYING` | 点击购买入口 | 进入选票页 |
| `SELECTING_SESSION` | 选择场次（多场次时） | 进入票档页；单场次自动跳过 |
| `SELECTING_TIER` | 点击指定票档 | 票档已选中 |
| `CONFIRMING` | 确认数量、提交订单 | 出现支付页 |
| `PAUSED` | 停止自动操作，等待人工 | 用户点击「继续」 |
| `DONE` | 发送通知，停止监控 | 终态 |

单步超时 **3 秒** → 回 `MONITORING`，防止卡死。

## 6. 数据模型

```kotlin
enum class Platform(val packageName: String) {
    DAMAI("cn.damai"),
    MAOYAN("com.sankuai.movie")
}

enum class MonitorState {
    MONITORING, SCANNING, BUYING, SELECTING_SESSION,
    SELECTING_TIER, CONFIRMING, PAUSED, DONE
}

data class TicketTask(
    val platform: Platform,
    val eventName: String,
    val targetTier: Int,
    val refreshIntervalMs: Long = 2000,
    val quantity: Int = 1
)

data class TierMatch(
    val tier: Int,
    val node: AccessibilityNodeInfo
)

sealed class StepResult {
    object Success : StepResult()
    object Retry : StepResult()
    object NotFound : StepResult()
}
```

## 7. 核心数据流

### 7.1 刷新循环（MONITORING）

```
every task.refreshIntervalMs:
  adapter.pullToRefresh(service, root)
  // 不立即扫描，等待 CONTENT_CHANGED 事件
```

### 7.2 事件触发（SCANNING）

```
onAccessibilityEvent(TYPE_WINDOW_CONTENT_CHANGED):
  if state == MONITORING:
    state = SCANNING
    match = adapter.matchTier(root, task.targetTier)
    if match != null:
      state = BUYING
      stateMachine.executeFrom(BUYING)
    else:
      state = MONITORING
```

### 7.3 购买漏斗

每步：等待 `CONTENT_CHANGED` → 执行当前步 `adapter.stepXxx()` → 成功则进下一状态。  
刷新后 **5 秒**内无事件 → 强制扫描一次（防事件丢失）。

## 8. 票档匹配规则

```kotlin
// 匹配 "¥580"、"580元"、"580看台" 等，排除 "1580"
Regex("""(?<!\d)${targetTier}(?!\d)""")

// 同行或父节点文本不含：缺货、售罄、登记
```

其他票档有票但指定档缺货 → 忽略，继续 `MONITORING`。

## 9. 平台适配接口

```kotlin
interface PlatformAdapter {
    fun pullToRefresh(service: AccessibilityService, root: AccessibilityNodeInfo)
    fun isSoldOut(root: AccessibilityNodeInfo): Boolean
    fun matchTier(root: AccessibilityNodeInfo, tier: Int): TierMatch?
    fun stepBuy(root: AccessibilityNodeInfo): StepResult
    fun stepSession(root: AccessibilityNodeInfo): StepResult
    fun stepTier(root: AccessibilityNodeInfo, tier: Int): StepResult
    fun stepConfirm(root: AccessibilityNodeInfo): StepResult
    fun isPaymentPage(root: AccessibilityNodeInfo): Boolean
    fun isCaptchaPage(root: AccessibilityNodeInfo): Boolean
}
```

### 9.1 平台差异

| 能力 | 大麦 `cn.damai` | 猫眼 `com.sankuai.movie` |
|------|----------------|--------------------------|
| 下拉刷新 | 顶部下拉手势 | 顶部下拉手势 |
| 售罄标志 | 「缺货登记」「已售罄」 | 「暂时无票」「售罄」 |
| 购买入口 | 「立即购买」「选座购买」 | 「立即购买」「抢票」 |
| 支付页标志 | 「支付宝」「确认付款」 | 「去支付」「确认订单」 |

## 10. 异常处理

| 场景 | 处理 |
|------|------|
| 刷新后 5s 无 `CONTENT_CHANGED` | 强制扫描一次 |
| 购买某步 3s 超时 | 回 `MONITORING` |
| 用户切出目标 App | 通知提醒，不自动拉回 |
| 验证码 / 滑块 | → `PAUSED`，震动 + 通知 |
| 网络错误页 | 检测「重试」按钮并点击 |
| 系统杀进程 | `ForegroundService` + `WAKE_LOCK` 缓解 |

## 11. 前台保活

- `ForegroundService`，低优先级通知：「监控中: {演出名} ¥{票档}」
- `PARTIAL_WAKE_LOCK`，仅监控期间持有
- 首次启动引导用户加入电池优化白名单
- 不自动亮屏、不音量唤醒

## 12. UI 配置

| 字段 | 类型 | 说明 |
|------|------|------|
| 演出名称 | 文本 | 备注用 |
| 平台 | 单选 | 大麦 / 猫眼 |
| 指定票档 | 数字 | 如 580 |
| 刷新间隔 | 数字 | 默认 2000ms，范围 1000–5000 |
| 购票数量 | 数字 | 默认 1 |

按钮：开启无障碍 / 打开目标 App / 开始监控 / 停止 / 继续（PAUSED 时）

## 13. 测试策略

| 范围 | 方式 |
|------|------|
| 状态机流转 | 单元测试 + mock 节点 |
| 票档正则 | 单元测试：「¥580」「580元」「580看台」「1580 不匹配」 |
| 下拉刷新 | 人工：已售罄场次验证手势 |
| 完整漏斗 | 人工：有票场次验证到达支付页 |
| 真实付款 | 不自动化 |

## 14. 风险与约束

- 大麦/猫眼 UI 更新需同步维护 `PlatformAdapter` 文案与节点规则
- 高频刷新可能触发平台风控，默认 2s，用户可调
- 无障碍自动化可能违反平台 ToS，仅供个人学习使用
- 验证码必须人工处理，不在自动化范围内

## 15. 不在范围内（YAGNI）

- 选座购票
- 开票秒杀定时触发
- 多场次任务队列
- 云端监控 / 多设备协同
- 自动支付
- API 抓包 / Hook 方案
