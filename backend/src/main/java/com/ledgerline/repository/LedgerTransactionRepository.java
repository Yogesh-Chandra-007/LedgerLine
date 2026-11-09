package com.ledgerline.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.ledgerline.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);
    List<LedgerTransaction> findTop100ByOrderByCreatedAtDesc();
}
