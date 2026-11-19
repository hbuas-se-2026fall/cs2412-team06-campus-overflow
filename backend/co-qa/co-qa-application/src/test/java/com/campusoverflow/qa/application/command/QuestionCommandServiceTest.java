package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionEditedEvent;
import com.campusoverflow.qa.api.event.QuestionPostedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryTags;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingAudit;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubRenderer;
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

/** 问题发布 / 编辑 / 删除用例的编排逻辑测试：标签计数、缓存失效、审计、事件发布与权限判定。 */
class QuestionCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryTags tags = new InMemoryTags();
    private final RecordingEvents events = new RecordingEvents();
    private final CountingCache cache = new CountingCache();
    private final RecordingAudit audit = new RecordingAudit();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT).withUser(2, "小红", Role.STUDENT)
            .withUser(9, "王老师", Role.TEACHER).withCourseManager(9);
    private final StubRenderer renderer = new StubRenderer();

    private QuestionCommandService service;
    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);

    @BeforeEach
    void setUp() {
        service = new QuestionCommandService(questions, tags, identity, events, renderer, cache, audit, clock);
    }

    private long seedQuestion(Actor author) {
        Question q = questions.save(Question.post(author.userId(), 100L,
                new Title("红黑树插入后为什么必须旋转？"),
                MarkdownBody.ofQuestion("变色为什么不够？希望有直观解释，最好能给出反例。"),
                Tags.of(List.of("数据结构", "红黑树")), NOW));
        return q.id();
    }

    @Test
    @DisplayName("发布问题：标签计数 +1、缓存失效、留审计并发布 QuestionPostedEvent")
    void postRegistersTagUsageAndPublishesEvent() {
        long id = service.post(ming, new PostQuestionCommand(100L, "新问题：红黑树插入为何要旋转",
                "这是一段至少十个字的问题正文，用于通过领域正文校验。", List.of("算法", "树")));

        assertThat(id).isPositive();
        assertThat(questions.findById(id)).isPresent();
        assertThat(tags.usage).containsEntry("算法", 1).containsEntry("树", 1);
        assertThat(cache.invalidations).isEqualTo(1);
        assertThat(audit.actions).contains("QUESTION_POSTED");
        List<QuestionPostedEvent> posted = events.ofType(QuestionPostedEvent.class);
        assertThat(posted).hasSize(1);
        assertThat(posted.get(0).questionId()).isEqualTo(id);
        assertThat(posted.get(0).authorId()).isEqualTo(ming.userId());
    }

    @Test
    @DisplayName("编辑：作者本人可改，标签旧值回退新值入账，并发布 QuestionEditedEvent")
    void editByAuthorRefreshesTagsAndPublishesEvent() {
        long id = seedQuestion(ming);

        service.edit(ming, id, new EditQuestionCommand("修订后的问题标题与表述",
                "修订后的正文也要凑够十个字符以上的内容长度。", List.of("数据结构", "二叉树")));

        assertThat(tags.usage).containsEntry("红黑树", -1).containsEntry("二叉树", 1);
        assertThat(cache.invalidations).isEqualTo(1);
        List<QuestionEditedEvent> edited = events.ofType(QuestionEditedEvent.class);
        assertThat(edited).hasSize(1);
        assertThat(edited.get(0).questionId()).isEqualTo(id);
        Question saved = questions.findById(id).orElseThrow();
        assertThat(saved.title().value()).isEqualTo("修订后的问题标题与表述");
    }

    @Test
    @DisplayName("编辑：非作者且非课程管理者被拒绝")
    void editByOutsiderIsForbidden() {
        long id = seedQuestion(ming);
        assertThatThrownBy(() -> service.edit(hong, id,
                new EditQuestionCommand("越权改写的问题标题内容", "越权正文同样需要超过十个字符的长度。", List.of("算法"))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("删除：作者本人可删除无回答问题，标签计数 -1 并发布 ContentDeletedEvent")
    void deleteByAuthorRemovesTagsAndPublishesEvent() {
        long id = seedQuestion(ming);

        service.delete(ming, id);

        assertThat(tags.usage).containsEntry("数据结构", -1).containsEntry("红黑树", -1);
        assertThat(cache.invalidations).isEqualTo(1);
        assertThat(audit.actions).contains("QUESTION_DELETED");
        List<ContentDeletedEvent> deleted = events.ofType(ContentDeletedEvent.class);
        assertThat(deleted).hasSize(1);
        assertThat(deleted.get(0).targetType()).isEqualTo("QUESTION");
        assertThat(deleted.get(0).targetId()).isEqualTo(id);
    }

    @Test
    @DisplayName("编辑/删除：问题不存在抛 NotFoundException")
    void missingQuestionThrowsNotFound() {
        assertThatThrownBy(() -> service.edit(ming, 999L,
                new EditQuestionCommand("标题", "正文也要凑够十个字符以上的长度才行。", List.of("算法"))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(ming, 999L))
                .isInstanceOf(NotFoundException.class);
    }
}
