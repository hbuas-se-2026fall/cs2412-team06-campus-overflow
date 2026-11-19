package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyRepository;
import com.campusoverflow.reputation.domain.BountyStatus;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BountyServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");
    private static final Actor TEACHER = new Actor(9, "王老师", Role.TEACHER);

    @Mock
    BountyRepository bounties;
    @Mock
    ReputationAccountRepository accounts;
    @Mock
    ReputationLedgerService ledgerService;
    @Mock
    QaQueryApi qa;
    @Mock
    IdentityApi identity;
    @Mock
    IntegrationEventPublisher events;
    @Mock
    AuditTrail audit;
    Clock clock = Clock.fixed(NOW, java.time.ZoneOffset.UTC);

    private BountyService service() {
        return new BountyService(bounties, accounts, ledgerService, qa, identity, events, audit, clock);
    }

    private Bounty openBounty(long questionId, long sponsorId, int points) {
        Bounty bounty = Bounty.open(questionId, true, TEACHER, true, points, 7, NOW);
        return Bounty.reconstitute(1L, questionId, sponsorId, points, BountyStatus.OPEN, NOW,
                NOW.plusSeconds(3600), null, null, null);
    }

    @Test
    void settleOnAcceptSkipsWhenNoOpenBounty() {
        when(bounties.findOpenByQuestion(10)).thenReturn(Optional.empty());

        service().settleOnAccept(10, 3, 7, "evt-1");

        verify(accounts, never()).save(any());
        verify(ledgerService, never()).record(anyLong(), any(), any(), anyInt(), any(), anyLong());
        verify(events, never()).publish(any());
    }

    @Test
    void settleOnAcceptSavesFrozenPointsAndPublishesAwardedEvent() {
        Bounty bounty = openBounty(10, 9, 50);
        ReputationAccount sponsor = ReputationAccount.reconstitute(9, 100, 50, 50, 0);
        when(bounties.findOpenByQuestion(10)).thenReturn(Optional.of(bounty));
        when(accounts.findByUserId(9)).thenReturn(Optional.of(sponsor));

        service().settleOnAccept(10, 3, 7, "evt-1");

        verify(accounts, times(1)).save(sponsor);
        verify(bounties, times(1)).save(bounty);
        verify(ledgerService, times(1)).record(eq(7L), eq("evt-1"), eq(ReputationReason.BOUNTY_AWARDED),
                eq(50), eq("answer"), eq(3L));

        ArgumentCaptor<BountyAwardedEvent> captor = ArgumentCaptor.forClass(BountyAwardedEvent.class);
        verify(events, times(1)).publish(captor.capture());
        BountyAwardedEvent event = captor.getValue();
        assertThat(event.sponsorId()).isEqualTo(9);
        assertThat(event.winnerId()).isEqualTo(7);
        assertThat(event.points()).isEqualTo(50);
    }

    @Test
    void settleOnAcceptSelfWinnerClosesAndRefunds() {
        Bounty bounty = openBounty(10, 9, 50);
        ReputationAccount sponsor = ReputationAccount.reconstitute(9, 100, 50, 50, 0);
        when(bounties.findOpenByQuestion(10)).thenReturn(Optional.of(bounty));
        when(accounts.findByUserId(9)).thenReturn(Optional.of(sponsor));

        service().settleOnAccept(10, 3, 9, "evt-2");

        assertThat(bounty.status()).isEqualTo(BountyStatus.EXPIRED);
        verify(accounts, times(1)).save(sponsor);
        verify(bounties, times(1)).save(bounty);
        verify(ledgerService, never()).record(anyLong(), any(), any(), anyInt(), any(), anyLong());

        ArgumentCaptor<BountyClosedEvent> captor = ArgumentCaptor.forClass(BountyClosedEvent.class);
        verify(events, times(1)).publish(captor.capture());
        assertThat(captor.getValue().refundedPoints()).isEqualTo(50);
    }

    @Test
    void expireDueClosesEveryDueBountyAndReturnsCount() {
        Bounty b1 = openBounty(10, 9, 20);
        Bounty b2 = openBounty(11, 9, 30);
        ReputationAccount sponsor = ReputationAccount.reconstitute(9, 100, 50, 50, 0);
        when(bounties.findDue(any(), eq(100))).thenReturn(List.of(b1, b2));
        when(accounts.findByUserId(9)).thenReturn(Optional.of(sponsor));

        int count = service().expireDue();

        assertThat(count).isEqualTo(2);
        verify(events, times(2)).publish(any(BountyClosedEvent.class));
    }

    @Test
    void latestReturnsViewWithSponsorName() {
        Bounty bounty = openBounty(10, 9, 50);
        when(bounties.findLatestByQuestion(10)).thenReturn(Optional.of(bounty));
        when(identity.findUser(9)).thenReturn(Optional.of(new UserSummary(9, "王老师", Role.TEACHER, true, "CS")));

        Optional<BountyView> view = service().latest(10);

        assertThat(view).isPresent();
        assertThat(view.get().sponsorName()).isEqualTo("王老师");
        assertThat(view.get().points()).isEqualTo(50);
    }

    @Test
    void latestReturnsEmptyWhenNoBounty() {
        when(bounties.findLatestByQuestion(10)).thenReturn(Optional.empty());
        assertThat(service().latest(10)).isEmpty();
    }
}
