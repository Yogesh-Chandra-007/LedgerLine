package com.ledgerline.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import com.ledgerline.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUsername(String username);
    List<UserAccount> findAllByOrderByCreatedAtDesc();
}
