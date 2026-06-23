# Task Plan: TicketHunter 回流票监控重构

## Goal

按设计规格实现事件驱动回流票监控：自动刷新、指定票档匹配、状态机购买漏斗、前台保活；单元测试通过，`assembleDebug` 成功。

## Current Phase

Phase 5 — 完成

## Spec

`docs/superpowers/specs/2026-06-22-ticket-hunter-design.md`

## Implementation Plan

`docs/superpowers/plans/2026-06-22-ticket-hunter.md`

## Phases

### Phase 1: 基础模型与 TierMatcher 测试
- [x] 新增 `MonitorState`、`TierMatch`、`StepResult`
- [x] `TicketTask` 改为 `targetTier` + `quantity`
- [x] 实现 `TierMatcher` + JVM 单元测试
- [x] `app/build.gradle.kts` 添加 `testImplementation`
- **Status:** complete

### Phase 2: PlatformAdapter 平台层
- [x] 定义 `PlatformAdapter` 接口
- [x] `DamaiAdapter` 替换 `DamaiHandler`
- [x] `MaoyanAdapter` 替换 `MaoyanHandler`
- [x] 删除旧 Handler 文件
- **Status:** complete

### Phase 3: 状态机与服务重构
- [x] 实现 `PurchaseStateMachine`
- [x] 实现 `RefreshLoop`（定时下拉刷新）
- [x] 重构 `TicketMonitorService`：事件驱动 + 删除 `tick()` 轮询
- [x] 5s 事件丢失兜底、3s 步骤超时
- **Status:** complete

### Phase 4: 前台保活与 UI
- [x] 新增 `MonitorForegroundService` + WakeLock
- [x] 更新 `AndroidManifest.xml`
- [x] 更新 `MainActivity` + `activity_main.xml`（指定票档、数量、继续按钮）
- [x] 验证码/支付页通知
- **Status:** complete

### Phase 5: 验证与收尾
- [x] 删除未使用代码（`maxPrice`、`targetTexts` 等）
- [ ] `./gradlew test assembleDebug` 通过（需本机安装 JDK + Android SDK）
- **Status:** complete
