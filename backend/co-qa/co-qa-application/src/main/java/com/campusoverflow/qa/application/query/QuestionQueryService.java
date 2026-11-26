package com.campusoverflow.qa.application.query;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.application.port.ContentRenderer;
import com.campusoverflow.qa.application.port.QaReadModel;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.application.port.ViewCounter;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 问答查询用例（读路径）。 */
@Service
@Transactional(readOnly = true)
public class QuestionQueryService {

    private static final int EXCERPT_LENGTH = 160;

    private final QaReadModel readModel;
    private final IdentityApi identity;
    private final ContentRenderer renderer;
    private final ViewCounter viewCounter;
    private final QuestionListCache cache;

    public QuestionQueryService(QaReadModel readModel, IdentityApi identity, ContentRenderer renderer,
                                ViewCounter viewCounter, QuestionListCache cache) {
        this.readModel = readModel;
        this.identity = identity;
        this.renderer = renderer;
        this.viewCounter = viewCounter;
        this.cache = cache;
    }

    public PageResult<QuestionSummaryView> list(Long courseId, PageRequest page) {
        Optional<PageResult<QuestionSummaryView>> cached = cache.get(courseId, page);
        if (cached.isPresent()) {
            return cached.get();
        }
        PageResult<QuestionRow> rows = readModel.listQuestions(courseId, page);
        Map<Long, UserSummary> users = identity.findUsers(
                rows.items().stream().map(QuestionRow::authorId).collect(Collectors.toSet()));
        PageResult<QuestionSummaryView> result = rows.map(r -> new QuestionSummaryView(
                r.id(), r.title(), renderer.toPlainText(r.body(), EXCERPT_LENGTH), r.tags(), r.courseId(),
                AuthorView.of(r.authorId(), users.get(r.authorId())), r.score(), r.answerCount(), r.viewCount(),
                r.acceptedAnswerId() != null, r.createdAt()));
        cache.put(courseId, page, result);
        return result;
    }

    public QuestionDetailView detail(long questionId, Actor actorOrNull) {
        QuestionRow q = readModel.findQuestion(questionId)
                .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在"));
        viewCounter.increment(questionId);
        List<AnswerRow> answerRows = readModel.findAnswers(questionId);
        List<CommentRow> commentRows = readModel.findComments(questionId);

        Set<Long> authorIds = new HashSet<>();
        authorIds.add(q.authorId());
        answerRows.forEach(a -> authorIds.add(a.authorId()));
        commentRows.forEach(c -> authorIds.add(c.authorId()));
        if (q.acceptedAnswerId() != null) {
            answerRows.stream().filter(a -> a.id() == q.acceptedAnswerId())
                    .forEach(a -> authorIds.add(a.endorsedBy() == null ? 0 : a.endorsedBy()));
        }
        Map<Long, UserSummary> users = identity.findUsers(authorIds);

        long voterId = actorOrNull == null ? 0 : actorOrNull.userId();
        Map<String, Integer> votes = readModel.findMyVotes(voterId, questionId,
                answerRows.stream().map(AnswerRow::id).toList());

        AuthorView qAuthor = AuthorView.of(q.authorId(), users.get(q.authorId()));
        boolean canManage = actorOrNull != null && identity.canManageCourse(actorOrNull.userId(), q.courseId());
        CourseRefView course = q.courseId() == null ? null
                : identity.findCourse(q.courseId())
                        .map(c -> new CourseRefView(c.id(), c.code(), c.name()))
                        .orElse(null);

        List<CommentView> questionComments = commentRows.stream()
                .filter(c -> "QUESTION".equals(c.targetType()))
                .map(c -> new CommentView(c.id(), AuthorView.of(c.authorId(), users.get(c.authorId())),
                        c.parentId(), c.body(), c.createdAt()))
                .toList();

        Map<Long, List<CommentRow>> commentsByAnswer = commentRows.stream()
                .filter(c -> "ANSWER".equals(c.targetType()))
                .collect(Collectors.groupingBy(CommentRow::targetId));

        List<AnswerView> answers = answerRows.stream().map(a -> {
            boolean accepted = q.acceptedAnswerId() != null && q.acceptedAnswerId() == a.id();
            AuthorView endorsed = a.endorsedBy() == null ? null
                    : AuthorView.of(a.endorsedBy(), users.get(a.endorsedBy()));
            int myVote = votes.getOrDefault("ANSWER:" + a.id(), 0);
            boolean canEdit = actorOrNull != null && actorOrNull.is(a.authorId());
            List<CommentView> ac = commentsByAnswer.getOrDefault(a.id(), List.of()).stream()
                    .map(c -> new CommentView(c.id(), AuthorView.of(c.authorId(), users.get(c.authorId())),
                            c.parentId(), c.body(), c.createdAt()))
                    .toList();
            return new AnswerView(a.id(), a.body(), renderer.toSafeHtml(a.body()),
                    AuthorView.of(a.authorId(), users.get(a.authorId())), a.score(), accepted, endorsed, myVote,
                    canEdit, a.createdAt(), a.updatedAt(), ac);
        }).toList();

        boolean canEditQ = actorOrNull != null && (actorOrNull.is(q.authorId()) || canManage);
        PermissionsView permissions = new PermissionsView(canEditQ, canEditQ, canEditQ, canManage);
        int myVote = votes.getOrDefault("QUESTION:" + questionId, 0);

        return new QuestionDetailView(q.id(), q.title(), q.body(), renderer.toSafeHtml(q.body()), q.tags(), course,
                qAuthor, q.score(), q.answerCount(), q.viewCount(), q.acceptedAnswerId(), myVote, q.createdAt(),
                q.updatedAt(), permissions, questionComments, answers);
    }

    public List<TagView> popularTags(int limit) {
        return readModel.popularTags(limit);
    }
}
