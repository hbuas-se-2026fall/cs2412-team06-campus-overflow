# S2（Q&A）代码评审报告

- 评审人：王（QA）
- 评审对象：commit `91b934c`「feat: 实现Q&A领域层与应用层用例(S2)」（12 个文件，+331 / −72）
- 评审范围：`co-qa` 领域层 + 应用层（不涉及 infrastructure / interfaces，留待下一轮）
- 评审日期：2026-10-26

## 一、总体结论

S2 的 Q&A 实现整体质量良好，可以进入验收：

1. **领域规则内聚**：投票 ±1（QA-1003）、不能投自己（QA-1015）、采纳权限（提问者或课程教师/助教，QA-3001）、被采纳回答不能由回答者删除（QA-1011）、评论最多两级（QA-1007）、有回答的问题提问者不能删（QA-1010）等规则都集中在聚合根内部，而不是散落在应用层。
2. **分层符合架构约束**：应用服务只做编排（加载聚合 → 调用领域方法 → 保存 → 发布事件 → 失效缓存），领域层不依赖 Spring/JPA，与 ArchitectureTest 的 FF-1/FF-4 一致。
3. **事件驱动正确**：`AnswerAcceptedEvent` 携带 answerId/questionId/双方作者，供声誉上下文结算；`VoteCastEvent` 携带 oldValue/newValue，支持幂等结算；`QuestionPostedEvent` 携带纯文本正文用于搜索投影。
4. **已有测试**：领域层 18 个 + 应用层 4 个（`AnswerAndVoteServiceTest`），覆盖提交回答、采纳权限、改投增量、自投拒绝等核心路径，CI 阶段 2 全部通过。

## 二、问题清单

| 级别 | 位置 | 问题 | 建议 |
| --- | --- | --- | --- |
| 中 | `CommentService.post` | 评论**问题**时未校验目标问题是否存在（`questionId = cmd.targetId()` 直接透传），传不存在的 ID 会产生孤儿评论 | 先 `questions.findById` 校验，不存在抛 `QA-4001` |
| 中 | `AnswerCommandService.accept` | 采纳后未调用 `listCache.invalidateAll()`，列表页的 `accepted` 标记与详情不一致（对比 `QuestionCommandService` 的 post/edit/delete 都有失效） | 补缓存失效，并加断言 |
| 中 | `VoteService.cast` | 投票/改投/撤票改变 score 后未失效列表缓存，按分数排序的列表会 stale | 补缓存失效 |
| 低 | `QuestionQueryService.detail` | `a.endorsedBy() == null ? 0 : a.endorsedBy()` 把哨兵值 0 加入 `authorIds`，产生一次无效的用户查询 | 用条件流过滤 null |
| 低 | `Question.accept` | `answer.questionId() != this.id` 中 `this.id` 为 `Long`，存在自动拆箱路径，若聚合尚未保存（id=null）会 NPE | 先判 `this.id == null` 或统一用 `Objects.equals` |
| 低 | `AnswerCommandService.submit` | `submit` 在应用层与 `Answer.submit` 内各调一次 `ensureOpenForAnswers()`，属防御性重复 | 可保留，仅作提示 |

## 三、测试缺口（QA 认领补测）

1. **评论校验**：评论不存在的问题应抛 `QA-4001`（当前实现与测试都缺失）。
2. **采纳后缓存失效**：`AnswerAndVoteServiceTest.onlyAskerOrCourseManagerCanAccept` 只断言事件与审计，未断言 `CountingCache.invalidations` 在 accept 后 +1。
3. **投票后缓存失效**：`changingVoteProducesCorrectDelta` 未断言缓存失效。
4. **边界用例**：对已删除问题/回答执行投票、采纳、评论的拒绝路径（当前仅有部分覆盖）。

## 四、与 E2E 验收的关系

`CampusOverflowFlowIT` 主链路（注册→提问→回答→采纳→声誉+15→搜索命中→通知）**不经过**列表缓存与评论路径，因此上述「中」级问题不阻塞 S2 E2E 验收；建议在 S3 开发前完成修复并补对应测试。

## 五、结论

**通过（有条件）**：E2E 验收测试通过即可合入；「中」级 3 项建议在下一迭代修复，修复后由 QA 复核。
