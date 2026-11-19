package com.campusoverflow.qa.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryReadModel;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubRenderer;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubViewCounter;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 问答读路径测试：列表缓存两条路径、详情装配（作者/评论分组/我的投票/权限）、热门标签与 404。 */
class QuestionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private static final PageRequest PAGE = new PageRequest(1, 20);

    private final InMemoryReadModel readModel = new InMemoryReadModel();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT).withUser(2, "小红", Role.STUDENT);
    private final StubRenderer renderer = new StubRenderer();
    private final StubViewCounter viewCounter = new StubViewCounter();
    private final PreloadedCache cache = new PreloadedCache();

    private QuestionQueryService service;

    @BeforeEach
    void setUp() {
        service = new QuestionQueryService(readModel, identity, renderer, viewCounter, cache);
    }

    private QuestionRow questionRow(long id, long authorId, Long acceptedAnswerId) {
        return new QuestionRow(id, authorId, 100L, "问题标题至少十个字符以上", "问题正文也要足够长一些内容。",
                List.of("数据结构"), acceptedAnswerId, 3, 1, 12, NOW, NOW);
    }

    @Test
    @DisplayName("列表：缓存未命中时读模型装配摘要并回填缓存")
    void listFallsBackToReadModelAndCaches() {
        readModel.questions.add(questionRow(10, 1, null));

        PageResult<QuestionSummaryView> result = service.list(100L, PAGE);

        assertThat(result.items()).hasSize(1);
        QuestionSummaryView view = result.items().get(0);
        assertThat(view.id()).isEqualTo(10);
        assertThat(view.author().displayName()).isEqualTo("小明");
        assertThat(view.tags()).containsExactly("数据结构");
        assertThat(cache.stored).isNotNull();
    }

    @Test
    @DisplayName("列表：缓存命中时直接返回预置结果，不再读模型")
    void listReturnsCachedResult() {
        QuestionSummaryView cached = new QuestionSummaryView(
                99L, "预置标题至少十个字符", "预置摘要", List.of("算法"), 100L,
                AuthorView.of(1, identity.findUser(1).orElseThrow()), 0, 0, 0, false, NOW);
        cache.preloaded = Optional.of(new PageResult<>(List.of(cached), 1, 1, 20));

        PageResult<QuestionSummaryView> result = service.list(100L, PAGE);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(99L);
        assertThat(readModel.questions).isEmpty();
    }

    @Test
    @DisplayName("详情：装配回答、问题评论与回答评论分组，并记录浏览量与我的投票")
    void detailAssemblesAnswersCommentsAndVotes() {
        readModel.questions.add(questionRow(10, 1, 50L));
        readModel.answers.add(new AnswerRow(50L, 2L, "回答正文内容足够长一些。", 2, null, NOW, NOW));
        readModel.comments.add(new CommentRow(1L, "QUESTION", 10L, 1L, null, "问题下的评论", NOW));
        readModel.comments.add(new CommentRow(2L, "ANSWER", 50L, 2L, null, "回答下的评论", NOW));
        readModel.myVotes.put("QUESTION:10", 1);
        readModel.myVotes.put("ANSWER:50", -1);

        Actor ming = new Actor(1, "小明", Role.STUDENT);
        QuestionDetailView detail = service.detail(10L, ming);

        assertThat(viewCounter.increments).containsExactly(10L);
        assertThat(detail.answers()).hasSize(1);
        AnswerView answer = detail.answers().get(0);
        assertThat(answer.id()).isEqualTo(50L);
        assertThat(answer.accepted()).isTrue();
        assertThat(answer.myVote()).isEqualTo(-1);
        assertThat(answer.comments()).hasSize(1);
        assertThat(detail.comments()).hasSize(1);
        assertThat(detail.myVote()).isEqualTo(1);
        assertThat(detail.permissions().canEdit()).isTrue();
        assertThat(detail.course()).isNotNull();
        assertThat(detail.course().code()).isEqualTo("CS2001");
    }

    @Test
    @DisplayName("详情：匿名访问时权限与我的投票均为空值")
    void detailForAnonymousActorHasNoPermissions() {
        readModel.questions.add(questionRow(10, 1, null));

        QuestionDetailView detail = service.detail(10L, null);

        assertThat(detail.permissions().canEdit()).isFalse();
        assertThat(detail.permissions().canManage()).isFalse();
        assertThat(detail.myVote()).isZero();
    }

    @Test
    @DisplayName("详情：问题不存在抛 NotFoundException")
    void missingDetailThrowsNotFound() {
        assertThatThrownBy(() -> service.detail(999L, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("列表：作者身份查不到时回退为已注销用户视图")
    void listUnknownAuthorFallsBackToAnonymousView() {
        readModel.questions.add(questionRow(11, 77L, null));

        PageResult<QuestionSummaryView> result = service.list(100L, PAGE);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).author().displayName()).isEqualTo("已注销用户");
        assertThat(result.items().get(0).author().verified()).isFalse();
    }

    @Test
    @DisplayName("热门标签：透传读模型结果")
    void popularTagsPassesThroughReadModel() {
        readModel.tags.add(new TagView("数据结构", 5));
        readModel.tags.add(new TagView("算法", 3));

        List<TagView> tags = service.popularTags(10);

        assertThat(tags).extracting(TagView::name).containsExactly("数据结构", "算法");
    }

    /** 可预置列表缓存的内存替身：覆盖命中与回填两条路径。 */
    static class PreloadedCache implements QuestionListCache {
        private Optional<PageResult<QuestionSummaryView>> preloaded = Optional.empty();
        private PageResult<QuestionSummaryView> stored;

        @Override
        public Optional<PageResult<QuestionSummaryView>> get(Long courseId, PageRequest page) {
            return preloaded;
        }

        @Override
        public void put(Long courseId, PageRequest page, PageResult<QuestionSummaryView> value) {
            this.stored = value;
        }

        @Override
        public void invalidateAll() {
        }
    }
}
