package com.ledgerline.api;

import com.ledgerline.domain.BankAccount;
import com.ledgerline.repository.BankAccountRepository;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminAccountController {
    private final BankAccountRepository accounts;
    public AdminAccountController(BankAccountRepository accounts) { this.accounts = accounts; }

    @GetMapping("/accounts")
    public List<AccountSummary> accounts() {
        return accounts.findAllByOrderByCreatedAtDesc().stream()
                .map(a -> new AccountSummary(a.getId().toString(), a.getAccountNumber(),
                        a.getOwnerUsername(), a.getCurrency(), a.getBalanceMinor(),
                        a.getStatus().name(), a.getCreatedAt().toString()))
                .toList();
    }

    public record AccountSummary(String id, String accountNumber, String ownerUsername,
                                 String currency, long balanceMinor, String status, String createdAt) {}
}
