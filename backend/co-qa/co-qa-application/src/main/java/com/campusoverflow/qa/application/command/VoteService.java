package com.campusoverflow.qa.application.command;

import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.AnswerRepository;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.qa.domain.Vote;
import com.campusoverflow.qa.domain.VoteRepository;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：投票 / 改投 / 撤销（value ∈ {-1, 0, 1}，0 表示撤销）。
 * 发布的事件同时携带旧值与新值，下游据此计算声誉增量。
 */
@Service
public class VoteService {

    private final VoteRepository votes;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public VoteService(VoteRepository votes, QuestionRepository questions, AnswerRepository answers,
                       IntegrationEventPublisher events, Clock clock) {
        this.votes = votes;
        this.questions = questions;
        this.answers = answers;
        this.events = events;
        this.clock = clock;
    }

    public record VoteResult(int myVote, int scoreDelta) {
    }

    private record Target(long authorId, long questionId) {
    }

    @Transactional
    public VoteResult cast(Actor actor, TargetType type, long targetId, int value) {
        Target t = resolve(type, targetId);
        Instant now = clock.instant();
        Optional<Vote> existing = votes.find(actor.userId(), type, targetId);

        int oldValue;
        int newValue;
        int scoreDelta;

        if (value == 0) {
            if (existing.isEmpty()) {
                return new VoteResult(0, 0);
            }
            Vote v = existing.get();
            oldValue = v.value();
            newValue = 0;
            scoreDelta = -oldValue;
            votes.delete(v);
        } else {
            if (existing.isPresent() && existing.get().value() == value) {
                return new VoteResult(value, 0); // 幂等
            }
            if (existing.isPresent()) {
                Vote v = existing.get();
                oldValue = v.value();
                v.change(value, now);
                votes.save(v);
                newValue = value;
                scoreDelta = newValue - oldValue;
            } else {
                Vote v = Vote.cast(actor.userId(), t.authorId(), type, targetId, value, now);
                votes.save(v);
                oldValue = 0;
                newValue = value;
                scoreDelta = newValue;
            }
        }

        if (type == TargetType.ANSWER) {
            answers.adjustScore(targetId, scoreDelta);
        } else {
            questions.adjustScore(targetId, scoreDelta);
        }
        events.publish(new VoteCastEvent(EventIds.next(), now, type.name(), targetId, t.authorId(),
                actor.userId(), t.questionId(), oldValue, newValue));
        return new VoteResult(newValue, scoreDelta);
    }

    private Target resolve(TargetType type, long targetId) {
        if (type == TargetType.QUESTION) {
            Question q = questions.findById(targetId)
                    .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在"));
            return new Target(q.authorId(), q.id());
        }
        Answer a = answers.findById(targetId)
                .orElseThrow(() -> new NotFoundException("QA-4002", "回答不存在"));
        Question q = questions.findById(a.questionId())
                .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在"));
        return new Target(a.authorId(), q.id());
    }
}
