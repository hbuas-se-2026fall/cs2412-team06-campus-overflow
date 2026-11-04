package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.search.RelatedQuestionPolicy;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.shared.page.PageResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：搜索与筛选、相关问题推荐。 */
@Service
@Transactional(readOnly = true)
public class DiscoveryQueryService {

    private final SearchPort search;
    private final SearchIndex index;
    private final IdentityApi identity;

    public DiscoveryQueryService(SearchPort search, SearchIndex index, IdentityApi identity) {
        this.search = search;
        this.index = index;
        this.identity = identity;
    }

    public PageResult<SearchResultView> search(SearchQuery query) {
        PageResult<SearchHit> hits = search.search(query);
        Map<Long, UserSummary> users = identity.findUsers(hits.items().stream().map(SearchHit::authorId).toList());
        return hits.map(h -> toView(h, users.get(h.authorId())));
    }

    public List<RelatedQuestionView> related(long questionId, int limit) {
        List<String> tags = index.findTags(questionId);
        if (tags.isEmpty()) {
            return List.of();
        }
        List<SearchIndex.RelatedCandidate> candidates =
                index.findCandidatesSharingTags(questionId, tags, limit * 5);
        return RelatedQuestionPolicy.rank(candidates, tags.size(), limit).stream()
                .map(c -> new RelatedQuestionView(c.questionId(), c.title(), c.answerCount(), c.accepted(),
                        RelatedQuestionPolicy.jaccard(c.sharedTags(), c.tagCount(), tags.size())))
                .toList();
    }

    private static SearchResultView toView(SearchHit h, UserSummary author) {
        String displayName = author == null ? "用户" + h.authorId() : author.displayName();
        String role = author == null ? "" : author.role().name();
        boolean verified = author != null && author.verified();
        return new SearchResultView(h.questionId(), h.title(), h.snippet(), h.tags(), h.courseId(),
                new SearchResultView.Author(h.authorId(), displayName, role, verified),
                h.score(), h.answerCount(), h.accepted(), h.bountyOpen(), h.createdAt());
    }
}
