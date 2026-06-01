package com.shop.delivery.bot.fsm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ConversationStateRepository extends JpaRepository<ConversationState, Long> {

    /**
     * Bulk-delete FSM rows older than {@code cutoff}. Used by a future scheduled
     * cleanup task (TTL ~30 min). For P8 we only call this in the IT; production
     * cleanup is a separate ticket (out of scope here).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ConversationState s WHERE s.updatedAt < :cutoff")
    int deleteStaleSince(@Param("cutoff") Instant cutoff);
}
