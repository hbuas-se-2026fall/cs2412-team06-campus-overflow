package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryAnswers;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryComments;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.qa.domain.Tags;
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

/** 评论用例测试：问题/回答两种挂载目标、父评论解析、二级回复与 404 分支。 */
class CommentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryComments comments = new InMemoryComments();
    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryAnswers answers = new InMemoryAnswers();
    private final RecordingEvents events = new RecordingEvents();

    private CommentService service;
    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);

    @BeforeEach
    void setUp() {
        service = new CommentService(comments, questions, answers, events, clock);
    }

    private long seedQuestion() {
        Question q = questions.save(Question.post(ming.userId(), 100L,
                new Title("红黑树插入后为什么必须旋转？"),
                MarkdownBody.ofQuestion("变色为什么不够？希望有直观解释，最好能给出反例。"),
                Tags.of(List.of("数据结构", "红黑树")), NOW));
        return q.id();
    }

    private long seedAnswer(long questionId) {
        Question q = questions.findById(questionId).orElseThrow();
        Answer a = answers.save(Answer.submit(q, hong.userId(), MarkdownBody.ofAnswer("黑高不变量才是关键。"), NOW));
        return a.id();
    }

    @Test
    @DisplayName("挂在问题下：直接以问题为 questionId 并发布 CommentPostedEvent")
    void postOnQuestionPublishesEvent() {
        long questionId = seedQuestion();

        long commentId = service.post(hong, new PostCommentCommand(TargetType.QUESTION, questionId, null, "同意这个思路"));

        List<CommentPostedEvent> posted = events.ofType(CommentPostedEvent.class);
        assertThat(posted).hasSize(1);
        assertThat(posted.get(0).commentId()).isEqualTo(commentId);
        assertThat(posted.get(0).questionId()).isEqualTo(questionId);
        assertThat(posted.get(0).authorId()).isEqualTo(hong.userId());
    }

    @Test
    @DisplayName("挂在回答下：由回答反查所属问题作为 questionId")
    void postOnAnswerResolvesQuestionId() {
        long questionId = seedQuestion();
        long answerId = seedAnswer(questionId);

        long commentId = service.post(ming, new PostCommentCommand(TargetType.ANSWER, answerId, null, "补充一个反例"));

        List<CommentPostedEvent> posted = events.ofType(CommentPostedEvent.class);
        assertThat(posted).hasSize(1);
        assertThat(posted.get(0).commentId()).isEqualTo(commentId);
        assertThat(posted.get(0).questionId()).isEqualTo(questionId);
    }

    @Test
    @DisplayName("二级回复：保存父评论引用")
    void replyLinksToParent() {
        long questionId = seedQuestion();
        long parentId = service.post(hong, new PostCommentCommand(TargetType.QUESTION, questionId, null, "父评论"));

        long replyId = service.post(ming, new PostCommentCommand(TargetType.QUESTION, questionId, parentId, "回复父评论"));

        assertThat(comments.findById(replyId).orElseThrow().parentId()).isEqualTo(parentId);
    }

    @Test
    @DisplayName("父评论不存在：抛 QA-4003 404")
    void missingParentThrowsNotFound() {
        long questionId = seedQuestion();
        assertThatThrownBy(() -> service.post(hong,
                new PostCommentCommand(TargetType.QUESTION, questionId, 999L, "找不到父评论")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("回答不存在：抛 QA-4002 404")
    void missingAnswerThrowsNotFound() {
        assertThatThrownBy(() -> service.post(ming,
                new PostCommentCommand(TargetType.ANSWER, 999L, null, "回答不存在")))
                .isInstanceOf(NotFoundException.class);
    }
}
