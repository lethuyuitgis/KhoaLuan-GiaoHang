package com.shop.delivery.bot.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedUpdateRepository extends JpaRepository<ProcessedUpdate, Long> {
}
