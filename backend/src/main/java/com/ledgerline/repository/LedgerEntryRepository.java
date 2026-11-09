package com.ledgerline.repository;

import java.util.List;
import java.util.UUID;
import com.ledgerline.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findAllByAccountIdOrderByCreatedAtDesc(UUID accountId);
    List<LedgerEntry> findAllByAccountIdOrderByCreatedAtAsc(UUID accountId);
    List<LedgerEntry> findAllByTransactionId(UUID transactionId);
}
