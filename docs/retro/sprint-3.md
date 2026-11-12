# Sprint 3 Retrospective 回顾纪要（Lab6 · QA/SM 王）

> 对应实验 Lab6 Step 3「Sprint Retrospective」。主持 25 分钟回顾，输出改进项并落到下个 Sprint。
> 主持：王（SM，xh314159261）｜ 记录：王 ｜ 周期：Sprint 3（Lab6）

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

1. **文档/流程沉淀完整**：站会、工作坊、Planning、评审清单、冒烟用例、PR 汇总全部落仓，PR 合并记录可追溯，评审清单 v1.0 在 S2 评审中直接复用。
2. **CI 骨架稳定运转**：后端（编译/单测/架构约束/覆盖率/集成）、前端（类型检查/构建）、镜像构建三段流水线在 main 上长期通过；S2 验收阶段王通过 PR #8 主动解除了 S3 占位模块对流水线的阻断。
3. **跨模块契约先行**：identity / qa / reputation 的集成事件 record 在编码前先定，后端龙/何的实现与王的测试骨架引用同一套事件签名，契约对齐成本低。
4. **任务到人、看板透明**：每个遗留项都有责任人和 Issue 编号，没有"无主任务"。

## 三、待改进的（礁石/搁浅，下个 Sprint 必须解开）

1. **声誉/发现模块长期占位**：co-reputation 应用层与规则层大量方法仍是 `TODO(S3)` 抛异常，导致事件链路测试无法转绿、#20 长期 open。
2. **前端联调起步晚**：`feature/lab5-frontend` 到 Sprint 收尾仍未提 PR，E2E 验收和录屏（#15）被整体推迟。
3. **本地环境不齐**：王/从本机无 JDK21 + Docker，冒烟（#14）和集成测试只能在 CI 跑，回填不及时。
4. **PR 评审等待**：文档类 PR（#21）开出后评审响应偏慢，合并滞后。

## 四、下个 Sprint 行动项（落到人和事）

| # | 行动项 | 责任人 | 对应 Issue | 验收标准 |
| --- | --- | --- | --- | --- |
| 1 | 实现 reputation 规则层（voteDelta/initialPoints/applyDailyCap）与 ReputationAccount.open/applyReputation，使事件链路测试可移除 @Disabled | 龙、何 | #20 | `ReputationEventFlowTest` 4 类场景全绿、CI 纳入 co-reputation 单测 |
| 2 | 提 `feature/lab5-frontend` PR，与后端联调通过 | 从 | #19 | PR open、页面可走通评论/标签筛选主流程 |
| 3 | 联调环境就绪后实跑冒烟并回填结果 | 王 | #14 | `docs/qa/冒烟测试用例清单.md` 每条标注通过/失败 |
| 4 | 录制 Lab5 E2E ≤30s 演示视频 | 王、从 | #15 | 视频入库、附在 Review 材料 |
| 5 | 在 GitHub 仓库设置里配 main 分支保护（要求 PR + 1 评审 + CI 通过） | 喻向鹏（Owner） | #16 | main 直接 push 被拒绝 |
| 6 | #21（Lab6 文档/规约/测试骨架）评审合并 | 喻向鹏 | — | main 含 `docs/retro/`、事件链路规约、`ReputationEventFlowTest` |

## 五、数据支撑

- **Sprint 承诺完成率：6 / 9**（按 Planning 承诺口径，3 项未完成均为上述被阻塞项）。
- **PR 统计**：本周期合并入 main 的 PR 共 **10 个**（#1 从、#2 何、#4 龙、#5 王、#6 何、#7 龙、#8 王、#9 何、#10 何、#11 王）；#3（龙 Question 域）关闭未合并；进行中 PR **1 个**（#21 王，Open）。
- **CI 通过率**：main 分支 push 的后端/前端构建历史通过；#21 首次运行时后端 job 因 co-reputation 全量构建失败，王在分支内修复后复跑（见 #21 检查记录）。
- **遗留 Issue（截至本纪要）**：#14 冒烟回填、#15 E2E 录屏、#16 分支保护、#19 前端联调、#20 声誉/发现实现，共 **5 个 open**，均已列入上表行动项并指定责任人。

---

> 本纪要由王依据真实 PR / Issue / CI 状态填写；Sailboat 匿名卡片底稿见 `sprint-3-sailboat.md`，未保留个人对应关系。行动项在 Sprint 4 Planning 时逐条复核完成情况。
