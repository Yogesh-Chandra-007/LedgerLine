package com.ledgerline.api;

import com.ledgerline.domain.*;
import com.ledgerline.repository.*;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auditor")
public class AuditorController {
    private final BankAccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final AuditEventRepository auditEvents;

    public AuditorController(BankAccountRepository accounts, LedgerTransactionRepository transactions,
                             LedgerEntryRepository entries, AuditEventRepository auditEvents) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
        this.auditEvents = auditEvents;
    }

    @GetMapping("/accounts")
    @Transactional(readOnly = true)
    public List<AccountView> accounts() {
        return accounts.findAllByOrderByCreatedAtDesc().stream()
                .map(a -> new AccountView(a.getId().toString(), a.getAccountNumber(),
                        a.getOwnerUsername(), a.getCurrency(), a.getBalanceMinor(),
                        a.getStatus().name(), a.getCreatedAt().toString()))
                .toList();
    }

    @GetMapping("/transactions")
    @Transactional(readOnly = true)
    public List<TransactionView> transactions() {
        return transactions.findTop100ByOrderByCreatedAtDesc().stream().map(tx ->
                new TransactionView(tx.getId().toString(), tx.getType().name(), tx.getCreatedBy(),
                        tx.getCreatedAt().toString(), entries.findAllByTransactionId(tx.getId()).stream()
                        .map(e -> new PostingView(e.getAccount() == null ? e.getSystemAccount().getCode()
                                        : e.getAccount().getId().toString(),
                                e.getAccount() == null ? "SYSTEM" : "CUSTOMER",
                                e.getAmountMinor())).toList()))
                .toList();
    }

    @GetMapping("/audit-events")
    public List<AuditView> auditEvents() {
        return auditEvents.findTop200ByOrderByOccurredAtDesc().stream()
                .map(e -> new AuditView(e.getId().toString(), e.getActorUsername(), e.getActionType(),
                        e.getResourceType(), e.getResourceId(), e.getOutcome(), e.getAmountMinor(),
                        e.getReason(), e.getRequestMethod(), e.getRequestPath(), e.getDetails(),
                        e.getOccurredAt().toString()))
                .toList();
    }

    public record AccountView(String id, String accountNumber, String ownerUsername, String currency,
                              long balanceMinor, String status, String createdAt) {}
    public record PostingView(String accountReference, String accountType, long amountMinor) {}
    public record TransactionView(String id, String type, String createdBy, String createdAt,
                                  List<PostingView> postings) {}
    public record AuditView(String id, String actorUsername, String actionType, String resourceType,
                            String resourceId, String outcome, Long amountMinor, String reason,
                            String requestMethod, String requestPath, String details, String occurredAt) {}
}
