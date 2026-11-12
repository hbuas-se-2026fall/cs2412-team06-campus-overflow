package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.reputation.domain.BadgeService;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReputationEventHandlersTest {

    @Mock
    ReputationAccountRepository accounts;
    @Mock
    LedgerRepository ledger;
    @Mock
    ReputationLedgerService ledgerService;
    @Mock
    BadgeService badges;
    @Mock
    BountyService bounties;
    Clock clock = Clock.systemUTC();

    @Test
    void 重复注册事件不重复开户() {
        ReputationEventHandlers handlers = new ReputationEventHandlers(accounts, ledger, ledgerService, badges, bounties, clock);
        UserRegisteredEvent event = new UserRegisteredEvent("evt-1", Instant.now(), 42L, "测试用户", Role.STUDENT);

        when(accounts.findByUserId(42L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ReputationAccount.open(42L, 0)));

        handlers.on(event);
        handlers.on(event);

        verify(accounts, times(1)).save(any(ReputationAccount.class));
    }
}
