# ADR-010：跨上下文事件契约与投递可靠性

**状态**：已接受 ｜ **驱动特征**：可演进性、团队并行 ｜ **相关场景**：QS-04、R-2

## 背景

五个限界上下文（Identity / Q&A / Reputation / Discovery / Bootstrap）通过集成事件协作：
Q&A 发布"提问/回答/投票/采纳"，Reputation 据此结算声誉，Discovery 据此更新搜索投影与站内通知。
事件既是**运行时数据流**，也是**团队间的接口**——没有契约，任何一方改字段都会让下游静默出错。
ADR-002 已决定"用事务性发件箱投递"，但未约定两件事：事件本身长什么样、消费方如何安全地消费。

## 决策

### 一、事件契约格式

1. **不可变 record，仅含传输安全类型**：字段只允许基本类型、`String`、`java.time.Instant`、`List<String>`，
   禁止领域对象与 JPA 实体。理由：可 JSON 序列化、不泄露发布方内部模型、天然线程安全。
   由 `IntegrationEvent` 接口强制：`eventId()` 全局唯一（UUID 字符串），`occurredAt()` 为 UTC `Instant`。
2. **命名约定**：`<实体><过去式领域动作>Event`，如 `QuestionPostedEvent`、`VoteCastEvent`、`BountyAwardedEvent`。
3. **存放位置**：事件类放在**发布方上下文**的 `*-api` 模块 `event` 包中，消费方只依赖 `-api`（ArchUnit FF-2 守护，
   禁止跨模块依赖内部实现）。
4. **自包含原则**：事件携带消费者重建状态所需的最少冗余字段，避免消费者反查发布方数据库。
   例：`AnswerSubmittedEvent` 同时带 `questionTitle`，`VoteCastEvent` 带 `questionId`（投票目标可能是回答）。
5. **语义化字段**：目标类型用枚举字符串（`targetType` 取值 `QUESTION` / `ANSWER`）；增量变化用
   `oldValue` / `newValue` 表达（撤销、改投可正确推导 delta，而非只发"当前值"）。

### 二、投递可靠性契约（消费端）

1. **至少一次投递**（ADR-002 已定）：消费方**必须幂等**，不得假设"每个事件恰好收到一次"。
2. **幂等守卫**：投影类用 `processed_event(event_id, consumer)` 复合主键，处理前 `markProcessed` 去重；
   通知类用业务唯一键 `(sourceEventId, recipientId)` 去重。
3. **计数器必须用增量**：搜索投影的投票分/回答数调整使用 `adjustScore(delta)` / `adjustAnswerCount(delta)`，
   禁止把"当前绝对值"写入事件（重复投递会重复累加）。
4. **顺序假设**：发件箱在**批内**按 `id` 保序（ADR-002），消费者只可靠序保证同一批次内的事件顺序，
   不得依赖跨类型/跨批次顺序；`occurredAt` 仅作业务时间，不作排序依据。
5. **可观测性**：投递失败留痕 `last_error`、指数退避重试、超限死信由 `co_outbox_dead_letters` 指标告警；
   已投递事件保留 7 天供排查与重放。

### 三、契约演进

1. **向后兼容变更**（新增可选字段）：允许原地修改 record，消费方反序列化须容忍未知字段
   （Jackson 默认 `FAIL_ON_UNKNOWN_PROPERTIES=false`）。
2. **破坏性变更**（删字段、改语义）：禁止原地修改。必须发布新事件类型（如 `QuestionPostedV2Event`）
   或采用双写迁移期，旧消费者迁移完成后再下线旧事件。

## 后果

- 正面：跨团队并行互不阻塞；消费方可独立演进与部署；故障可依赖 `outbox_event.last_error` 与指标定位。
- 负面：事件携带冗余字段使 payload 略膨胀；record 不可变意味着每个契约改动都需评审；
  `event_type` 存类全限定名，重命名事件类需同步历史数据或容忍反序列化失败（靠死信告警兜底）。

## 被否决的替代方案

- **事件直接暴露领域模型 / ORM 实体**：序列化泄露内部结构，实体变更即破坏下游，耦合最强。
- **自由 JSON 无 Schema 契约**：无类型安全与静态检查，字段拼错只能运行时发现。
- **消费者反查发布方数据库**（用 Query API 替代）：把边界内的表结构变成跨团队隐式契约，违反上下文隔离。
