package com.smartbank.payments.repository;

import com.smartbank.payments.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Used for idempotency: has a payment with this client key been created before? */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
