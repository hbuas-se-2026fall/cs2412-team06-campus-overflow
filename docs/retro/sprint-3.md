# Sprint 3 Retrospective 回顾纪要（Lab6 · QA/SM 王）

> 对应实验 Lab6 Step 3「Sprint Retrospective」。主持 25 分钟回顾，输出改进项并落到下个 Sprint。
> 主持：王（SM，xh314159261）｜ 记录：王 ｜ 周期：Sprint 3（Lab6）
> 仓库：hbuas-se-2026fall/cs2412-team06-campus-overflow

## 一、参会人与时间

- **时间**：2026-11-12（Sprint 3 收尾实验课），时长 25 分钟。
- **参会人（5 人）**：
  - 喻向鹏（PO，baga1234-q）
  - 王陈焓（SM + QA，xh314159261）
  - 龙（后端，long2005hawk）
  - 何（后端，bozhi0709）
  - 从（前端，NDGWCH666）
- **缺席**：无。
- **输入材料**：Sprint3 Planning 承诺表（`docs/team/Sprint3-Planning.md`）、看板 Issue 清单、Sailboat 匿名卡片（`sprint-3-sailboat.md`）、PR 列表与 CI 记录。

## 二、做得好的（顺风，下个 Sprint 继续保持）

1. **跨模块契约先行**：identity / qa / reputation 的集成事件 record 在编码前先定，后端龙/何的实现与王的测试骨架引用同一套事件签名，契约对齐成本低。
2. **reputation 模块端到端落地**：规则手册（点赞 +10 / 点踩 −2 / 采纳 +15 / 日上限 200 / 教师开户 1000）、账户、账本、事件处理器、徽章、悬赏、排行榜全部由龙/何在新仓库完成，事件链路单测（`ReputationEventFlowTest`）按真实签名真跑绿。
3. **CI 骨架稳定运转**：后端（编译/单测/架构约束/集成）、前端（类型检查/构建）流水线在 main 上长期通过；王在 #8 解除了 S3 占位模块对流水线的阻断，#30 把后端 job 固定到 ubuntu-22.04 修掉集成测试无 Docker 必挂的问题。
4. **文档/流程沉淀完整**：站会、Planning、评审清单 v1.0、冒烟用例、ADR 001–011、Postman 运行说明、演示记录全部落仓，PR 合并记录可追溯。

## 三、待改进的（礁石/搁浅，下个 Sprint 必须解开）

1. **新仓库迁移期 issue 号对不齐**：从旧仓库迁文档时直接带了旧 issue 号，评委对照看板发现 #14/#15/#16/#19/#20 在新仓库不存在；本版已按新仓库看板重写（遗留项见第四节）。
2. **前端联调起步晚**：前端分支到 Sprint 收尾仍未提 PR，E2E 验收和录屏被整体推迟。
3. **本地环境不齐**：王/从本机无 JDK21 + Docker，冒烟和集成测试只能在 CI 跑，回填不及时。
4. **PR 评审等待**：文档类 PR 开出后评审响应偏慢，合并滞后。

## 四、下个 Sprint 行动项（落到人和事）

| # | 行动项 | 责任人 | 对应 Issue | 验收标准 |
| --- | --- | --- | --- | --- |
| 1 | 跨模块事件链路单测按新仓库真实签名落地：注册开户 / 投票增量 / 采纳结算 / 幂等，全绿并纳入 CI | 王 | #5 | `ReputationEventFlowTest` 11 个用例通过、CI 后端 job 覆盖 co-reputation 单测 |
| 2 | CI 环境与流水线稳定性继续维护（集成测试 Docker 依赖、后端 runner 版本） | 王 + 龙 | #8 | main 上后端 job 稳定绿 |
| 3 | 联调环境就绪后实跑冒烟并回填结果，录制 E2E ≤30s 演示视频 | 王、从 | #10 | 冒烟清单每条标注通过/失败、演示视频入库 |
| 4 | 主持 Sprint 4 Planning，按本次回顾行动项逐条复核 | 王 | #12 | Sprint4 Planning 文档落地、行动项闭环 |

## 五、数据支撑

- **Sprint 承诺完成率**：US-001 ~ US-009 全部后端接口在 main 可演示；reputation 规则层、账户、事件处理器、徽章、悬赏、排行榜均已实现。
- **PR 统计（新仓库 main）**：Sprint 周期内合并入 main 的 PR 包括 #27（进度记录同步）、#28（Postman 全链路运行结果）、#29（王 Lab6 文档迁移：Retro/Sailboat/事件规约/Sprint3 Planning）、#30（CI runner 固定 ubuntu-22.04）；王补迁测试与评审清单的分支待本 PR 合并。
- **CI 通过率**：main 分支后端 / 前端构建历史通过；后端 job 在 #30 修掉集成测试无 Docker 必挂后稳定。
- **遗留 Issue（截至本纪要）**：#5 事件链路单测、#8 CI 环境、#10 冒烟/E2E、#12 Retro 收尾，共 **4 个 open**，均已列入上表行动项并指定责任人。

---

> 本纪要由王依据新仓库真实 PR / Issue / CI 状态填写；Sailboat 匿名卡片底稿见 `sprint-3-sailboat.md`，未保留个人对应关系。行动项在 Sprint 4 Planning 时逐条复核完成情况。
