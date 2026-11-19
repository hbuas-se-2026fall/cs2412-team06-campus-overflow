package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.domain.BadgeAward;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {

    @Mock
    BadgeRepository badges;
    @Mock
    IntegrationEventPublisher events;
    Clock clock = Clock.fixed(Instant.parse("2026-10-08T02:00:00Z"), java.time.ZoneOffset.UTC);

    private BadgeService service() {
        return new BadgeService(badges, events, clock);
    }

    @Test
    void alreadyOwnedBadgeIsSkipped() {
        when(badges.has(7, BadgeType.FIRST_ACCEPTED)).thenReturn(true);

        service().grant(7, BadgeType.FIRST_ACCEPTED);

        verify(badges, never()).award(any(BadgeAward.class));
        verify(events, never()).publish(any());
    }

    @Test
    void newBadgeIsAwardedAndEventPublished() {
        when(badges.has(7, BadgeType.HELPER_10)).thenReturn(false);

        service().grant(7, BadgeType.HELPER_10);

        ArgumentCaptor<BadgeAward> awardCaptor = ArgumentCaptor.forClass(BadgeAward.class);
        verify(badges, times(1)).award(awardCaptor.capture());
        assertThat(awardCaptor.getValue().userId()).isEqualTo(7);
        assertThat(awardCaptor.getValue().type()).isEqualTo(BadgeType.HELPER_10);

        ArgumentCaptor<BadgeAwardedEvent> eventCaptor = ArgumentCaptor.forClass(BadgeAwardedEvent.class);
        verify(events, times(1)).publish(eventCaptor.capture());
        BadgeAwardedEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(7);
        assertThat(event.badgeCode()).isEqualTo(BadgeType.HELPER_10.name());
        assertThat(event.badgeName()).isEqualTo(BadgeType.HELPER_10.displayName());
    }
}
