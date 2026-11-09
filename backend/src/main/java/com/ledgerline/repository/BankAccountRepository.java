package com.ledgerline.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.ledgerline.domain.BankAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {
    List<BankAccount> findAllByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);
    List<BankAccount> findAllByOrderByCreatedAtDesc();
    Optional<BankAccount> findByIdAndOwnerUsername(UUID id, String ownerUsername);
    Optional<BankAccount> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccount a where a.id in :ids order by a.id")
    List<BankAccount> lockAllByIds(@Param("ids") Collection<UUID> ids);
}
