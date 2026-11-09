package com.ledgerline.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

import com.ledgerline.domain.*;
import com.ledgerline.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BankingService {
    private static final String CASH_CLEARING = "CASH_CLEARING";
    private static final long MAX_TRANSACTION_MINOR = 9_007_199_254_740_991L;

    private final BankAccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final SystemLedgerAccountRepository systemAccounts;
    private final AuditEventService auditEvents;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final boolean advisoryLocksEnabled;

    public BankingService(BankAccountRepository accounts,
                          LedgerTransactionRepository transactions,
                          LedgerEntryRepository entries,
                          SystemLedgerAccountRepository systemAccounts,
                          AuditEventService auditEvents,
                          AuditEventRepository auditEventRepository,
                          JdbcTemplate jdbcTemplate,
                          @Value("${ledgerline.database.advisory-locks:false}") boolean advisoryLocksEnabled) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
        this.systemAccounts = systemAccounts;
        this.auditEvents = auditEvents;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.advisoryLocksEnabled = advisoryLocksEnabled;
    }

    @Transactional
    public BankAccount openAccount(String username, String currency) {
        String normalizedCurrency = currency == null ? "INR" : currency.trim().toUpperCase();
        if (!normalizedCurrency.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency must be a three-letter ISO code");
        }
        String number = "LL" + UUID.randomUUID().toString().replace("-", "").substring(0, 22).toUpperCase();
        BankAccount saved = accounts.save(new BankAccount(number, username, normalizedCurrency));
        auditEvents.recordSuccess(username, "ACCOUNT_OPENED", "ACCOUNT", saved.getId().toString(),
                null, "Currency=" + normalizedCurrency);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<BankAccount> listOwnAccounts(String username) {
        return accounts.findAllByOwnerUsernameOrderByCreatedAtDesc(username);
    }

    @Transactional
    public TransactionResult deposit(String username, UUID accountId, long amountMinor, String key) {
        lockIdempotencyKey(key);
        BankAccount account = lockOwnedAccount(accountId, username);
        validateAmount(amountMinor);
        ensureActive(account);
        SystemLedgerAccount clearing = lockClearingAccount();
        String fingerprint = fingerprint("DEPOSIT", accountId, null, amountMinor);
        LedgerTransaction existing = findIdempotent(key, fingerprint);
        if (existing != null) {
            auditEvents.recordSuccess(username, "IDEMPOTENT_REPLAY", "TRANSACTION",
                    existing.getId().toString(), amountMinor, "DEPOSIT replay");
            return result(existing, account);
        }

        account.credit(amountMinor);
        clearing.applyDelta(-amountMinor);
        LedgerTransaction tx = saveTransaction(key, fingerprint, TransactionType.DEPOSIT, username, account);
        postBalanced(tx, new LedgerEntry(tx, account, amountMinor),
                new LedgerEntry(tx, clearing, -amountMinor));
        auditEvents.recordSuccess(username, "DEPOSIT", "ACCOUNT", account.getId().toString(),
                amountMinor, "Transaction=" + tx.getId());
        return result(tx, account);
    }

    @Transactional
    public TransactionResult withdraw(String username, UUID accountId, long amountMinor, String key) {
        lockIdempotencyKey(key);
        BankAccount account = lockOwnedAccount(accountId, username);
        validateAmount(amountMinor);
        ensureActive(account);
        SystemLedgerAccount clearing = lockClearingAccount();
        String fingerprint = fingerprint("WITHDRAWAL", accountId, null, amountMinor);
        LedgerTransaction existing = findIdempotent(key, fingerprint);
        if (existing != null) {
            auditEvents.recordSuccess(username, "IDEMPOTENT_REPLAY", "TRANSACTION",
                    existing.getId().toString(), amountMinor, "WITHDRAWAL replay");
            return result(existing, account);
        }
        if (account.getBalanceMinor() < amountMinor) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient funds");
        }

        account.debit(amountMinor);
        clearing.applyDelta(amountMinor);
        LedgerTransaction tx = saveTransaction(key, fingerprint, TransactionType.WITHDRAWAL, username, account);
        postBalanced(tx, new LedgerEntry(tx, account, -amountMinor),
                new LedgerEntry(tx, clearing, amountMinor));
        auditEvents.recordSuccess(username, "WITHDRAWAL", "ACCOUNT", account.getId().toString(),
                amountMinor, "Transaction=" + tx.getId());
        return result(tx, account);
    }

    @Transactional
    public TransactionResult transfer(String username, UUID fromId, UUID toId, long amountMinor, String key) {
        if (fromId.equals(toId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and destination accounts must differ");
        }
        validateAmount(amountMinor);
        lockIdempotencyKey(key);
        List<BankAccount> locked = accounts.lockAllByIds(List.of(fromId, toId));
        if (locked.size() != 2) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        BankAccount from = locked.stream().filter(a -> a.getId().equals(fromId)).findFirst().orElseThrow();
        BankAccount to = locked.stream().filter(a -> a.getId().equals(toId)).findFirst().orElseThrow();
        if (!from.getOwnerUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own the source account");
        }
        ensureActive(from);
        ensureActive(to);
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Accounts must use the same currency");
        }
        String fingerprint = fingerprint("TRANSFER", fromId, toId, amountMinor);
        LedgerTransaction existing = findIdempotent(key, fingerprint);
        if (existing != null) {
            auditEvents.recordSuccess(username, "IDEMPOTENT_REPLAY", "TRANSACTION",
                    existing.getId().toString(), amountMinor, "TRANSFER replay");
            return result(existing, from);
        }
        if (from.getBalanceMinor() < amountMinor) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient funds");
        }

        from.debit(amountMinor);
        to.credit(amountMinor);
        LedgerTransaction tx = saveTransaction(key, fingerprint, TransactionType.TRANSFER, username, from);
        postBalanced(tx, new LedgerEntry(tx, from, -amountMinor),
                new LedgerEntry(tx, to, amountMinor));
        auditEvents.recordSuccess(username, "TRANSFER", "ACCOUNT", from.getId().toString(),
                amountMinor, "Destination=" + to.getId() + "; transaction=" + tx.getId());
        return result(tx, from);
    }

    @Transactional(readOnly = true)
    public List<StatementLine> statement(String username, UUID accountId) {
        BankAccount account = accounts.findByIdAndOwnerUsername(accountId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        List<StatementItem> timeline = new ArrayList<>();
        entries.findAllByAccountIdOrderByCreatedAtAsc(account.getId()).forEach(entry ->
                timeline.add(new StatementItem(entry.getCreatedAt(), entry, null)));
        auditEventRepository.findAllByResourceIdAndOutcomeOrderByOccurredAtAsc(
                account.getId().toString(), "REJECTED").forEach(event ->
                timeline.add(new StatementItem(event.getOccurredAt(), null, event)));
        timeline.sort(Comparator.comparing(StatementItem::occurredAt));

        long runningBalance = 0L;
        List<StatementLine> lines = new ArrayList<>();
        for (StatementItem item : timeline) {
            if (item.entry() != null) {
                LedgerEntry entry = item.entry();
                runningBalance = Math.addExact(runningBalance, entry.getAmountMinor());
                LedgerTransaction tx = entry.getTransaction();
                lines.add(new StatementLine(entry.getId().toString(), tx.getId().toString(),
                        tx.getType().name(), tx.getCreatedBy(), "SUCCESS", entry.getAmountMinor(),
                        runningBalance, null, entry.getCreatedAt().toString()));
            } else {
                AuditEvent event = item.auditEvent();
                String path = event.getRequestPath() == null ? "" : event.getRequestPath();
                String type = path.substring(path.lastIndexOf('/') + 1).toUpperCase(java.util.Locale.ROOT);
                lines.add(new StatementLine(event.getId().toString(), null, type,
                        event.getActorUsername(), "REJECTED", event.getAmountMinor(),
                        runningBalance, event.getReason(), event.getOccurredAt().toString()));
            }
        }
        Collections.reverse(lines);
        return lines;
    }

    private BankAccount lockOwnedAccount(UUID id, String username) {
        BankAccount account = accounts.lockAllByIds(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!account.getOwnerUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this account");
        }
        return account;
    }

    private SystemLedgerAccount lockClearingAccount() {
        return systemAccounts.lockByCode(CASH_CLEARING)
                .orElseThrow(() -> new IllegalStateException("System clearing account is not provisioned"));
    }

    private void postBalanced(LedgerTransaction tx, LedgerEntry first, LedgerEntry second) {
        if (Math.addExact(first.getAmountMinor(), second.getAmountMinor()) != 0L) {
            throw new IllegalStateException("Ledger transaction is not balanced");
        }
        entries.saveAll(List.of(first, second));
    }

    private void lockIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required");
        }
        if (advisoryLocksEnabled) {
            jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                    statement -> statement.setString(1, key), resultSet -> null);
        }
    }

    private LedgerTransaction findIdempotent(String key, String fingerprint) {
        return transactions.findByIdempotencyKey(key).map(existing -> {
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency key was already used for a different request");
            }
            return existing;
        }).orElse(null);
    }

    private LedgerTransaction saveTransaction(String key, String fingerprint, TransactionType type,
                                               String username, BankAccount resultAccount) {
        LedgerTransaction tx = new LedgerTransaction(key, fingerprint, type, username);
        tx.setResultSnapshot(resultAccount.getId(), resultAccount.getBalanceMinor());
        return transactions.saveAndFlush(tx);
    }

    private TransactionResult result(LedgerTransaction tx, BankAccount account) {
        UUID resultAccountId = tx.getResultAccountId() == null ? account.getId() : tx.getResultAccountId();
        long resultBalance = tx.getResultBalanceMinor() == null
                ? account.getBalanceMinor() : tx.getResultBalanceMinor();
        return new TransactionResult(tx.getId(), tx.getType().name(), resultAccountId,
                resultBalance, account.getCurrency(), tx.getCreatedAt().toString());
    }

    private void validateAmount(long amountMinor) {
        if (amountMinor <= 0 || amountMinor > MAX_TRANSACTION_MINOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Amount must be positive and within the supported precision limit");
        }
    }

    private void ensureActive(BankAccount account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not active");
        }
    }

    private String fingerprint(String type, UUID from, UUID to, long amount) {
        String value = type + "|" + from + "|" + (to == null ? "" : to) + "|" + amount;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record TransactionResult(UUID transactionId, String type, UUID accountId,
                                    long balanceMinor, String currency, String createdAt) {}
    private record StatementItem(Instant occurredAt, LedgerEntry entry, AuditEvent auditEvent) {}
    public record StatementLine(String id, String transactionId, String type, String createdBy,
                                String outcome, Long amountMinor, long balanceAfterMinor,
                                String reason, String createdAt) {}
}
