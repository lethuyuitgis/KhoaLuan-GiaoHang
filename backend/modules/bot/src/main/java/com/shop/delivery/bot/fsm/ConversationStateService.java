package com.shop.delivery.bot.fsm;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Thin facade around {@link ConversationStateRepository}. P8 only uses
 * {@link #put}, {@link #get}, {@link #clear} — enough for the rating-comment FSM.
 *
 * <p>Generic on purpose: future flows pass any state name + any payload map.
 */
@Service
public class ConversationStateService {

    private final ConversationStateRepository repo;

    public ConversationStateService(ConversationStateRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Optional<ConversationState> get(Long userId) {
        return repo.findById(userId);
    }

    @Transactional
    public void put(Long userId, String state, Map<String, Object> data) {
        ConversationState cs = repo.findById(userId).orElseGet(() -> {
            ConversationState n = new ConversationState();
            n.setTelegramUserId(userId);
            return n;
        });
        cs.setState(state);
        cs.setData(data);
        repo.save(cs);
    }

    @Transactional
    public void clear(Long userId) {
        if (repo.existsById(userId)) repo.deleteById(userId);
    }
}
