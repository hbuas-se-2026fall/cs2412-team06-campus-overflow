package com.campusoverflow.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BountyAndAccountEdgeTest {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");
    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);

    @Test
    void openRejectsOutOfRangePointsAndDays() {
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 501, 7, NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 50, 0, NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, true, 50, 15, NOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void openRejectsSponsorWhoDoesNotManageCourse() {
        assertThatThrownBy(() -> Bounty.open(10, true, teacher, false, 50, 7, NOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void awardAndCloseRejectNonOpenBounty() {
        Bounty b = Bounty.open(10, true, teacher, true, 50, 7, NOW);
        b.award(50, 3, NOW);
        assertThatThrownBy(() -> b.award(51, 4, NOW)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> b.close(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void assignIdRejectsSecondAssignment() {
        Bounty b = Bounty.open(10, true, teacher, true, 50, 7, NOW);
        b.assignId(1L);
        assertThatThrownBy(() -> b.assignId(2L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reconstituteKeepsPersistedFields() {
        Bounty b = Bounty.reconstitute(7L, 10, 9, 50, BountyStatus.AWARDED, NOW,
                NOW.plusSeconds(3600), 3L, 50L, NOW);
        assertThat(b.id()).isEqualTo(7L);
        assertThat(b.questionId()).isEqualTo(10);
        assertThat(b.sponsorId()).isEqualTo(9);
        assertThat(b.points()).isEqualTo(50);
        assertThat(b.status()).isEqualTo(BountyStatus.AWARDED);
        assertThat(b.winnerId()).isEqualTo(3L);
        assertThat(b.answerId()).isEqualTo(50L);
        assertThat(b.closedAt()).isEqualTo(NOW);
    }

    @Test
    void accountRejectsNonPositiveAndInsufficientFrozen() {
        ReputationAccount acc = ReputationAccount.open(9, 100);
        assertThatThrownBy(() -> acc.freezePoints(0)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> acc.spendFrozen(0)).isInstanceOf(BusinessRuleException.class);
        acc.freezePoints(40);
        assertThatThrownBy(() -> acc.spendFrozen(50)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> acc.refundFrozen(50)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void applyReputationAndReconstituteKeepState() {
        ReputationAccount acc = ReputationAccount.reconstitute(9, 120, 60, 40, 3L);
        acc.applyReputation(15);
        assertThat(acc.reputation()).isEqualTo(135);
        assertThat(acc.userId()).isEqualTo(9);
        assertThat(acc.availablePoints()).isEqualTo(60);
        assertThat(acc.frozenPoints()).isEqualTo(40);
        assertThat(acc.version()).isEqualTo(3L);
    }
}
