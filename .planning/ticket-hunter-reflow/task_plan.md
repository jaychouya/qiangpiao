# Task Plan: TicketHunter 回流票监控重构

## Goal

按设计规格实现事件驱动回流票监控：自动刷新、指定票档匹配、状态机购买漏斗、前台保活；单元测试通过，`assembleDebug` 成功。

## Current Phase

Phase 1 — 基础模型与测试

## Spec

`docs/superpowers/specs/2026-06-22-ticket-hunter-design.md`

## Implementation Plan

`docs/superpowers/plans/2026-06-22-ticket-hunter.md`

## Phases

### Phase 1: 基础模型与 TierMatcher 测试
- [ ] 新增 `MonitorState`、`TierMatch`、`StepResult`
- [ ] `TicketTask` 改为 `targetTier` + `quantity`
- [ ] 实现 `TierMatcher` + JVM 单元测试
- [ ] `app/build.gradle.kts` 添加 `testImplementation`
- **Status:** pending

### Phase 2: PlatformAdapter 平台层
- [ ] 定义 `PlatformAdapter` 接口
- [ ] `DamaiAdapter` 替换 `DamaiHandler`
- [ ] `MaoyanAdapter` 替换 `MaoyanHandler`
- [ ] 删除旧 Handler 文件
- **Status:** pending

### Phase 3: 状态机与服务重构
- [ ] 实现 `PurchaseStateMachine`
- [ ] 实现 `RefreshLoop`（定时下拉刷新）
- [ ] 重构 `TicketMonitorService`：事件驱动 + 删除 `tick()` 轮询
- [ ] 5s 事件丢失兜底、3s 步骤超时
- **Status:** pending

### Phase 4: 前台保活与 UI
- [ ] 新增 `MonitorForegroundService` + WakeLock
- [ ] 更新 `AndroidManifest.xml`
- [ ] 更新 `MainActivity` + `activity_main.xml`（指定票档、数量、继续按钮）
- [ ] 验证码/支付页通知
- **Status:** pending

### Phase 5: 验证与收尾
- [ ] `./gradlew test assembleDebug` 通过
- [ ] 删除未使用代码（`maxPrice`、`targetTexts` 等）
- **Status:** pending
