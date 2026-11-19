package com.smartbank.payments.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data derives the SQL from the method name:
 * SELECT ... WHERE published_at IS NULL ORDER BY created_at ASC LIMIT 100
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
