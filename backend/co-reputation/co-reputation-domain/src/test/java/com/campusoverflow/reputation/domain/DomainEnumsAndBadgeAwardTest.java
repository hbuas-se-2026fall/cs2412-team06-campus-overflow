package com.campusoverflow.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DomainEnumsAndBadgeAwardTest {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");

    @Test
    void badgeTypeExposesDisplayNameAndDescription() {
        for (BadgeType type : BadgeType.values()) {
            assertThat(type.displayName()).isNotBlank();
            assertThat(type.description()).isNotBlank();
        }
        assertThat(BadgeType.FIRST_ACCEPTED.displayName()).isEqualTo("初露锋芒");
    }

    @Test
    void reputationReasonVoteFlag() {
        assertThat(ReputationReason.QUESTION_VOTE.isVote()).isTrue();
        assertThat(ReputationReason.ANSWER_VOTE.isVote()).isTrue();
        assertThat(ReputationReason.ANSWER_ACCEPTED.isVote()).isFalse();
        assertThat(ReputationReason.BOUNTY_AWARDED.isVote()).isFalse();
    }

    @Test
    void bountyStatusHasThreeStates() {
        assertThat(BountyStatus.values()).containsExactly(BountyStatus.OPEN, BountyStatus.AWARDED, BountyStatus.EXPIRED);
    }

    @Test
    void badgeAwardKeepsFields() {
        BadgeAward award = new BadgeAward(7, BadgeType.FIRST_ACCEPTED, NOW);
        assertThat(award.userId()).isEqualTo(7);
        assertThat(award.type()).isEqualTo(BadgeType.FIRST_ACCEPTED);
        assertThat(award.awardedAt()).isEqualTo(NOW);
    }
}
