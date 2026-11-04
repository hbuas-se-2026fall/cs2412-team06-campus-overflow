package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.event.ProcessedEventStore;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 排行榜：周/月/学期榜来自 Redis ZSET，总榜直接读账户表。 */
@Service
public class LeaderboardService {

    private final LeaderboardStore store;
    private final ReputationAccountRepository accounts;
    private final IdentityApi identity;
    private final ProcessedEventStore processed;
    private final Clock clock;

    public LeaderboardService(LeaderboardStore store, ReputationAccountRepository accounts, IdentityApi identity,
                              ProcessedEventStore processed, Clock clock) {
        this.store = store;
        this.accounts = accounts;
        this.identity = identity;
        this.processed = processed;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void on(ReputationChangedEvent e) {
        store.increment(e.userId(), e.delta(), e.occurredAt());
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryView> top(LeaderboardPeriod period, int limit) {
        Instant now = clock.instant();
        List<LeaderboardStore.Entry> entries;
        if (period == LeaderboardPeriod.ALL) {
            entries = accounts.topByReputation(limit).stream()
                    .map(a -> new LeaderboardStore.Entry(a.userId(), a.reputation()))
                    .toList();
        } else {
            entries = store.top(period, now, limit);
        }
        List<Long> userIds = entries.stream().map(LeaderboardStore.Entry::userId).toList();
        Map<Long, UserSummary> users = identity.findUsers(userIds);
        List<LeaderboardEntryView> result = new ArrayList<>();
        int rank = 1;
        for (LeaderboardStore.Entry e : entries) {
            UserSummary u = users.get(e.userId());
            result.add(new LeaderboardEntryView(rank++, e.userId(),
                    u != null ? u.displayName() : "?",
                    u != null && u.verified(),
                    e.score()));
        }
        return result;
    }
}
