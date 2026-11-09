package com.ledgerline.repository;

import java.util.Optional;
import com.ledgerline.domain.SystemLedgerAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemLedgerAccountRepository extends JpaRepository<SystemLedgerAccount, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SystemLedgerAccount a where a.code = :code")
    Optional<SystemLedgerAccount> lockByCode(@Param("code") String code);
}
