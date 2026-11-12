package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.reputation.domain.ReputationRulebook;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * 跨模块事件链路单元测试（QA · 王，对应 #5）。
 *
 * <p>覆盖 Backlog 约定的 4 类场景：注册开户、投票增量、采纳结算、重复事件幂等。
 * 仓储全部用 Mockito 替身，不依赖 Docker / Spring 容器，可在 CI 直接跑。
 *
 * <p>规则常量与生产 {@link ReputationRulebook} 对齐：
 * 采纳 +15、被赞 +10、被踩 -2、日正向投票上限 200、教师开户悬赏积分 1000。
 */
class ReputationEventFlowTest {

    private static final Instant NOW = Instant.parse("2026-11-12T08:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private ReputationAccountRepository accounts;
    @Mock
    private LedgerRepository ledger;
    @Mock
    private BadgeService badges;
    @Mock
    private BountyService bounties;
    @Mock
    private IntegrationEventPublisher events;

    private ReputationLedgerService ledgerService;
    private ReputationEventHandlers handlers;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ledgerService = new ReputationLedgerService(accounts, ledger, badges, events, clock);
        handlers = new ReputationEventHandlers(accounts, ledger, ledgerService, badges, bounties, clock);
    }

    // ── 场景 1：注册开户（学生 0 / 教师 1000，重复注册幂等）──────────────────────

    @Test
    @DisplayName("场景1：学生注册 → 开户 reputation=0、availablePoints=0")
    void registeredStudent_opensAccountWithZeroPoints() {
        when(accounts.findByUserId(101L)).thenReturn(Optional.empty());

        handlers.on(new UserRegisteredEvent("ev-reg-1", NOW, 101L, "小明", Role.STUDENT));

        ArgumentCaptor<ReputationAccount> captor = ArgumentCaptor.forClass(ReputationAccount.class);
        verify(accounts).save(captor.capture());
        ReputationAccount saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo(101L);
        assertThat(saved.reputation()).isZero();
        assertThat(saved.availablePoints()).isZero();
    }

    @Test
    @DisplayName("场景1：教师注册 → 开户 reputation=0、availablePoints=1000")
    void registeredTeacher_opensAccountWith1000BountyPoints() {
        when(accounts.findByUserId(102L)).thenReturn(Optional.empty());

        handlers.on(new UserRegisteredEvent("ev-reg-2", NOW, 102L, "王老师", Role.TEACHER));

        ArgumentCaptor<ReputationAccount> captor = ArgumentCaptor.forClass(ReputationAccount.class);
        verify(accounts).save(captor.capture());
        ReputationAccount saved = captor.getValue();
        assertThat(saved.reputation()).isZero();
        assertThat(saved.availablePoints()).isEqualTo(1000);
    }

    @Test
    @DisplayName("场景1：同一 userId 重复注册 → 幂等忽略，不重复开户")
    void duplicateRegistration_isIgnored() {
        when(accounts.findByUserId(101L)).thenReturn(
                Optional.of(ReputationAccount.reconstitute(101L, 0, 0, 0, 0L)));

        handlers.on(new UserRegisteredEvent("ev-reg-dup", NOW, 101L, "小明", Role.STUDENT));

        verify(accounts, never()).save(any());
    }

    // ── 场景 2：投票增量（赞 +10 / 踩 -2 / 改投按 new-old）──────────────────────

    @Test
    @DisplayName("场景2：回答被赞 → 回答者声誉 +10，写流水、发 ReputationChanged")
    void upvote_addsTenToAuthorAndAppendsLedger() {
        when(accounts.findByUserId(202L)).thenReturn(
                Optional.of(ReputationAccount.reconstitute(202L, 50, 0, 0, 0L)));
        when(ledger.exists("ev-vote-up", 202L, ReputationReason.ANSWER_VOTE)).thenReturn(false);

        handlers.on(new VoteCastEvent("ev-vote-up", NOW, "ANSWER", 50L, 202L, 303L, 404L, 0, 1));

        ArgumentCaptor<ReputationAccount> acctCaptor = ArgumentCaptor.forClass(ReputationAccount.class);
        verify(accounts).save(acctCaptor.capture());
        assertThat(acctCaptor.getValue().reputation()).isEqualTo(60);

        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledger).append(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().delta()).isEqualTo(10);

        ArgumentCaptor<ReputationChangedEvent> evtCaptor = ArgumentCaptor.forClass(ReputationChangedEvent.class);
        verify(events).publish(evtCaptor.capture());
        assertThat(evtCaptor.getValue().delta()).isEqualTo(10);
        assertThat(evtCaptor.getValue().reputation()).isEqualTo(60);
    }

    @Test
    @DisplayName("场景2：回答被踩 → 回答者声誉 -2")
    void downvote_subtractsTwoFromAuthor() {
        when(accounts.findByUserId(202L)).thenReturn(
                Optional.of(ReputationAccount.reconstitute(202L, 50, 0, 0, 0L)));
        when(ledger.exists("ev-vote-down", 202L, ReputationReason.ANSWER_VOTE)).thenReturn(false);

        handlers.on(new VoteCastEvent("ev-vote-down", NOW, "ANSWER", 50L, 202L, 303L, 405L, 0, -1));

        ArgumentCaptor<ReputationAccount> acctCaptor = ArgumentCaptor.forClass(ReputationAccount.class);
        verify(accounts).save(acctCaptor.capture());
        assertThat(acctCaptor.getValue().reputation()).isEqualTo(48);
    }

    @Test
    @DisplayName("场景2：改投（赞→踩）→ 按 new-old 结算，-2 - (+10) = -12")
    void flipVote_appliesDeltaAsNewMinusOld() {
        when(accounts.findByUserId(202L)).thenReturn(
                Optional.of(ReputationAccount.reconstitute(202L, 50, 0, 0, 0L)));
        when(ledger.exists("ev-vote-flip", 202L, ReputationReason.ANSWER_VOTE)).thenReturn(false);

        // oldValue=1（之前赞）newValue=-1（现在踩）→ voteEffect(-1) - voteEffect(1) = -2 - 10 = -12
        handlers.on(new VoteCastEvent("ev-vote-flip", NOW, "ANSWER", 50L, 202L, 303L, 406L, 1, -1));

        ArgumentCaptor<ReputationAccount> acctCaptor = ArgumentCaptor.forClass(ReputationAccount.class);
        verify(accounts).save(acctCaptor.capture());
        assertThat(acctCaptor.getValue().reputation()).isEqualTo(38);
    }

    // ── 场景 3：采纳结算（非自答 +15、授徽、悬赏结算；自答 0）───────────────────

    @Test
    @DisplayName("场景3：非自答被采纳 → 回答者 +15、授 FIRST_ACCEPTED 徽章、结算悬赏")
    void acceptedAnswer_grantsFifteenAndBadgeAndBounty() {
        when(accounts.findByUserId(202L)).thenReturn(
                Optional.of(ReputationAccount.reconstitute(202L, 0, 0, 0, 0L)));
        when(ledger.exists("ev-acc-1", 202L, ReputationReason.ANSWER_ACCEPTED)).thenReturn(false);

        handlers.on(new AnswerAcceptedEvent("ev-acc-1", NOW, 50L, 40L, 202L, 303L, 303L, "问题标题"));

        ArgumentCaptor<ReputationAccount> acctCaptor = ArgumentCaptor.forClass(ReputationAccount.class);
        verify(accounts).save(acctCaptor.capture());
        assertThat(acctCaptor.getValue().reputation()).isEqualTo(15);

        verify(badges).grant(eq(202L), any());
        verify(bounties).settleOnAccept(eq(40L), eq(50L), eq(202L), eq("ev-acc-1"));
    }

    @Test
    @DisplayName("场景3：自问自答被采纳 → delta=0、直接早返回、不写流水不授徽")
    void selfAccepted_isIgnored() {
        handlers.on(new AnswerAcceptedEvent("ev-acc-self", NOW, 50L, 40L, 202L, 202L, 202L, "自问题"));

        verify(ledger, never()).append(any(LedgerEntry.class));
        verify(accounts, never()).save(any());
        verify(badges, never()).grant(anyLong(), any());
    }

    // ── 场景 4：幂等（record 早返回路径）────────────────────────────────────────

    @Test
    @DisplayName("场景4：同一 (eventId,userId,reason) 重复投递 → 第二次忽略、不写流水")
    void duplicateEvent_isIdempotent() {
        when(ledger.exists("ev-dup", 7L, ReputationReason.ANSWER_VOTE)).thenReturn(true);

        boolean recorded = ledgerService.record(7L, "ev-dup",
                ReputationReason.ANSWER_VOTE, 10, "ANSWER", 50L);

        assertThat(recorded).isFalse();
        verify(ledger, never()).append(any(LedgerEntry.class));
        verify(accounts, never()).save(any());
        verify(events, never()).publish(any());
    }

    @Test
    @DisplayName("场景4：delta=0 不触发任何记账副作用")
    void zeroDelta_isIgnoredWithoutSideEffects() {
        boolean recorded = ledgerService.record(7L, "ev-zero",
                ReputationReason.ANSWER_VOTE, 0, "ANSWER", 50L);

        assertThat(recorded).isFalse();
        verify(ledger, never()).append(any(LedgerEntry.class));
        verify(accounts, never()).save(any());
    }
}
