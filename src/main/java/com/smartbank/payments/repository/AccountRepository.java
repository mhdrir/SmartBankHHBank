package com.smartbank.payments.repository;

import com.smartbank.payments.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA generates the implementation at runtime (findById, save, ...).
 * No SQL needed for simple CRUD.
 */
public interface AccountRepository extends JpaRepository<Account, String> {
}
