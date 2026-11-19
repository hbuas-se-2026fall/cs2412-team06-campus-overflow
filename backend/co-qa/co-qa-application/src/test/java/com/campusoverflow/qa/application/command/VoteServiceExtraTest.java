package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryAnswers;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryVotes;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.qa.domain.Title;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 投票撤销、问题投票路径与目标不存在分支测试（补充首投/改投/幂等之外的用例）。 */
class VoteServiceExtraTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryVotes votes = new InMemoryVotes();
    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryAnswers answers = new InMemoryAnswers();
    private final RecordingEvents events = new RecordingEvents();

    private VoteService service;
    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);
    private long questionId;
    private long answerId;

    @BeforeEach
    void setUp() {
        service = new VoteService(votes, questions, answers, events, clock);
        Question q = questions.save(Question.post(ming.userId(), 100L,
                new Title("红黑树插入后为什么必须旋转？"),
                MarkdownBody.ofQuestion("变色为什么不够？希望有直观解释，最好能给出反例。"),
                Tags.of(List.of("数据结构", "红黑树")), NOW));
        questionId = q.id();
        Answer a = answers.save(Answer.submit(q, hong.userId(), MarkdownBody.ofAnswer("黑高不变量才是关键。"), NOW));
        answerId = a.id();
    }

    @Test
    @DisplayName("撤销已有票：删除选票并按旧值回退分数，事件携带 oldValue=1、newValue=0")
    void revokeExistingVoteDeletesVoteAndRevertsScore() {
        service.cast(ming, TargetType.ANSWER, answerId, 1);

        VoteService.VoteResult result = service.cast(ming, TargetType.ANSWER, answerId, 0);

        assertThat(result.myVote()).isZero();
        assertThat(result.scoreDelta()).isEqualTo(-1);
        assertThat(votes.find(ming.userId(), TargetType.ANSWER, answerId)).isEmpty();
        List<VoteCastEvent> voteEvents = events.ofType(VoteCastEvent.class);
        assertThat(voteEvents).hasSize(2);
        assertThat(voteEvents.get(1).oldValue()).isEqualTo(1);
        assertThat(voteEvents.get(1).newValue()).isZero();
    }

    @Test
    @DisplayName("撤销不存在的票：直接返回 0 且不发布事件")
    void revokeAbsentVoteIsNoOp() {
        VoteService.VoteResult result = service.cast(ming, TargetType.ANSWER, answerId, 0);

        assertThat(result.myVote()).isZero();
        assertThat(result.scoreDelta()).isZero();
        assertThat(events.ofType(VoteCastEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("对问题投票：累加问题分数并发布问题投票事件")
    void voteOnQuestionAdjustsQuestionScore() {
        VoteService.VoteResult result = service.cast(hong, TargetType.QUESTION, questionId, 1);

        assertThat(result.scoreDelta()).isEqualTo(1);
        assertThat(questions.scoreDeltas).containsEntry(questionId, 1);
        VoteCastEvent event = events.ofType(VoteCastEvent.class).get(0);
        assertThat(event.targetType()).isEqualTo("QUESTION");
        assertThat(event.targetId()).isEqualTo(questionId);
    }

    @Test
    @DisplayName("投票目标不存在：问题/回答分别抛 404")
    void missingTargetThrowsNotFound() {
        assertThatThrownBy(() -> service.cast(hong, TargetType.QUESTION, 999L, 1))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.cast(ming, TargetType.ANSWER, 999L, 1))
                .isInstanceOf(NotFoundException.class);
    }
}
