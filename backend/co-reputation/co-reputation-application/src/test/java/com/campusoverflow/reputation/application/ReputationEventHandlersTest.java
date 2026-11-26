package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReputationEventHandlersTest {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");

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

    private ReputationEventHandlers handlers() {
        return new ReputationEventHandlers(accounts, ledger, ledgerService, badges, bounties, clock);
    }

    @Test
    void 重复注册事件不重复开户() {
        ReputationEventHandlers handlers = handlers();
        UserRegisteredEvent event = new UserRegisteredEvent("evt-1", Instant.now(), 42L, "测试用户", Role.STUDENT);

        when(accounts.findByUserId(42L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ReputationAccount.open(42L, 0)));

        handlers.on(event);
        handlers.on(event);

        verify(accounts, times(1)).save(any(ReputationAccount.class));
    }

    @Test
    void 问题投票记QUESTION_VOTE原因() {
        handlers().on(new VoteCastEvent("evt-4", NOW, "QUESTION", 10, 7, 1, 10, 0, 1));

        verify(ledgerService, times(1)).record(eq(7L), eq("evt-4"), eq(ReputationReason.QUESTION_VOTE),
                eq(10), eq("question"), eq(10L));
    }

    @Test
    void 回答投票记ANSWER_VOTE原因() {
        handlers().on(new VoteCastEvent("evt-5", NOW, "ANSWER", 3, 7, 1, 10, 0, -1));

        verify(ledgerService, times(1)).record(eq(7L), eq("evt-5"), eq(ReputationReason.ANSWER_VOTE),
                eq(-2), eq("answer"), eq(3L));
    }

    @Test
    void 提交回答授予初来乍到徽章() {
        handlers().on(new AnswerSubmittedEvent("evt-6", NOW, 3, 10, 7, 1, "问题标题"));

        verify(badges, times(1)).grant(7, BadgeType.FIRST_ANSWER);
    }

    @Test
    void 教师认证授予名师认可徽章() {
        handlers().on(new AnswerEndorsedEvent("evt-7", NOW, 3, 10, 7, 9, "问题标题"));

        verify(badges, times(1)).grant(7, BadgeType.TEACHER_ENDORSED);
    }

    @Test
    void 自问自答不授徽也不结算悬赏() {
        handlers().on(new AnswerAcceptedEvent("evt-3", NOW, 3, 10, 7, 7, 5, "问题标题"));

        verify(ledgerService, times(1)).record(eq(7L), eq("evt-3"), eq(ReputationReason.ANSWER_ACCEPTED),
                eq(0), eq("answer"), eq(3L));
        verify(badges, never()).grant(anyLong(), any());
        verify(bounties, never()).settleOnAccept(anyLong(), anyLong(), anyLong(), any());
    }
}
