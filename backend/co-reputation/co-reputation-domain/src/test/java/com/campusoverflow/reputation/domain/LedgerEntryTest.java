package com.campusoverflow.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LedgerEntryTest {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");

    @Test
    void ofKeepsAllFieldsAndIdIsNull() {
        LedgerEntry entry = LedgerEntry.of(7, "evt-1", ReputationReason.ANSWER_ACCEPTED, 15, "answer", 3, NOW);
        assertThat(entry.id()).isNull();
        assertThat(entry.userId()).isEqualTo(7);
        assertThat(entry.eventId()).isEqualTo("evt-1");
        assertThat(entry.reason()).isEqualTo(ReputationReason.ANSWER_ACCEPTED);
        assertThat(entry.delta()).isEqualTo(15);
        assertThat(entry.refType()).isEqualTo("answer");
        assertThat(entry.refId()).isEqualTo(3);
        assertThat(entry.createdAt()).isEqualTo(NOW);
    }

    @Test
    void nullEventIdIsRejected() {
        assertThatThrownBy(() -> LedgerEntry.of(7, null, ReputationReason.ANSWER_ACCEPTED, 15, "answer", 3, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullReasonIsRejected() {
        assertThatThrownBy(() -> LedgerEntry.of(7, "evt-1", null, 15, "answer", 3, NOW))
                .isInstanceOf(NullPointerException.class);
    }
}
