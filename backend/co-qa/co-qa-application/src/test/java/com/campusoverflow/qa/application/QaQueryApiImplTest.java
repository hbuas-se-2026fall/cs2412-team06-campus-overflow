package com.campusoverflow.qa.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.Title;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 跨上下文只读查询适配：把领域问题映射成对外 QuestionRef，不存在时返回空。 */
class QaQueryApiImplTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final QaQueryApiImpl api = new QaQueryApiImpl(questions);

    private long questionId;

    @BeforeEach
    void setUp() {
        Question q = questions.save(Question.post(1L, 100L,
                new Title("问题标题至少十个字符以上"),
                MarkdownBody.ofQuestion("问题正文也要足够长一些内容才能通过校验。"),
                Tags.of(List.of("数据结构")), NOW));
        questionId = q.id();
    }

    @Test
    @DisplayName("存在：映射为携带作者、课程、标题与删除标记的 QuestionRef")
    void presentQuestionMapsToRef() {
        Optional<QuestionRef> ref = api.findQuestion(questionId);

        assertThat(ref).isPresent();
        QuestionRef value = ref.orElseThrow();
        assertThat(value.id()).isEqualTo(questionId);
        assertThat(value.authorId()).isEqualTo(1L);
        assertThat(value.courseId()).isEqualTo(100L);
        assertThat(value.title()).isEqualTo("问题标题至少十个字符以上");
        assertThat(value.deleted()).isFalse();
    }

    @Test
    @DisplayName("不存在：返回空 Optional")
    void missingQuestionReturnsEmpty() {
        assertThat(api.findQuestion(999L)).isEmpty();
    }
}
