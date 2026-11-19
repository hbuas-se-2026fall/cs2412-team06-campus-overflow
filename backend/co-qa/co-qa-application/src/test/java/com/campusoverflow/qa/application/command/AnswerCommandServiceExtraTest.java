package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryAnswers;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingAudit;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.Title;
import com.campusoverflow.shared.domain.ForbiddenException;
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

/** 回答编辑 / 删除 / 教师认证用例测试（补充 submit、accept 之外的编排分支）。 */
class AnswerCommandServiceExtraTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryAnswers answers = new InMemoryAnswers();
    private final RecordingEvents events = new RecordingEvents();
    private final CountingCache cache = new CountingCache();
    private final RecordingAudit audit = new RecordingAudit();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT).withUser(2, "小红", Role.STUDENT)
            .withUser(9, "王老师", Role.TEACHER).withCourseManager(9);

    private AnswerCommandService service;
    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);
    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);
    private long questionId;

    @BeforeEach
    void setUp() {
        service = new AnswerCommandService(questions, answers, identity, events, cache, audit, clock);
        Question q = questions.save(Question.post(ming.userId(), 100L,
                new Title("红黑树插入后为什么必须旋转？"),
                MarkdownBody.ofQuestion("变色为什么不够？希望有直观解释，最好能给出反例。"),
                Tags.of(List.of("数据结构", "红黑树")), NOW));
        questionId = q.id();
    }

    private long seedAnswer() {
        Question q = questions.findById(questionId).orElseThrow();
        Answer a = answers.save(Answer.submit(q, hong.userId(), MarkdownBody.ofAnswer("黑高不变量才是关键。"), NOW));
        return a.id();
    }

    @Test
    @DisplayName("编辑：回答者本人可改并保存新正文")
    void editByAuthorSavesNewBody() {
        long answerId = seedAnswer();

        service.edit(hong, answerId, "修订后的回答正文内容，补充了具体推导过程。");

        assertThat(answers.findById(answerId).orElseThrow().body().value())
                .isEqualTo("修订后的回答正文内容，补充了具体推导过程。");
    }

    @Test
    @DisplayName("编辑：非回答者且非课程管理者被拒绝")
    void editByOutsiderIsForbidden() {
        long answerId = seedAnswer();
        assertThatThrownBy(() -> service.edit(ming, answerId, "越权改写的回答正文。"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("删除：回答者本人可删，缓存失效并发布 ContentDeletedEvent")
    void deleteByAuthorPublishesEvent() {
        long answerId = seedAnswer();

        service.delete(hong, answerId);

        assertThat(cache.invalidations).isEqualTo(1);
        List<ContentDeletedEvent> deleted = events.ofType(ContentDeletedEvent.class);
        assertThat(deleted).hasSize(1);
        assertThat(deleted.get(0).targetType()).isEqualTo("ANSWER");
        assertThat(deleted.get(0).questionId()).isEqualTo(questionId);
    }

    @Test
    @DisplayName("认证：课程教师打上认证标记并发布 AnswerEndorsedEvent")
    void endorseByCourseTeacherPublishesEvent() {
        long answerId = seedAnswer();

        service.endorse(teacher, answerId);

        Answer endorsed = answers.findById(answerId).orElseThrow();
        assertThat(endorsed.endorsedBy()).isEqualTo(teacher.userId());
        List<AnswerEndorsedEvent> endorsedEvents = events.ofType(AnswerEndorsedEvent.class);
        assertThat(endorsedEvents).hasSize(1);
        assertThat(endorsedEvents.get(0).answerId()).isEqualTo(answerId);
        assertThat(endorsedEvents.get(0).answerAuthorId()).isEqualTo(hong.userId());
        assertThat(endorsedEvents.get(0).endorserId()).isEqualTo(teacher.userId());
    }

    @Test
    @DisplayName("认证：学生不能认证回答")
    void endorseByStudentIsForbidden() {
        long answerId = seedAnswer();
        assertThatThrownBy(() -> service.endorse(hong, answerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("编辑/认证/删除：回答不存在抛 NotFoundException")
    void missingAnswerThrowsNotFound() {
        assertThatThrownBy(() -> service.edit(hong, 999L, "找不到的回答正文。"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.endorse(teacher, 999L))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(hong, 999L))
                .isInstanceOf(NotFoundException.class);
    }
}
