package com.ledgerline.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.ledgerline.domain.*;
import com.ledgerline.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BankingService {
    private static final String CASH_CLEARING = "CASH_CLEARING";

    private final BankAccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final SystemLedgerAccountRepository systemAccounts;

    public BankingService(BankAccountRepository accounts,
                          LedgerTransactionRepository transactions,
                          LedgerEntryRepository entries,
                          SystemLedgerAccountRepository systemAccounts) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
        this.systemAccounts = systemAccounts;
    }

    @Transactional
    public BankAccount openAccount(String username, String currency) {
        String normalizedCurrency = currency == null ? "INR" : currency.trim().toUpperCase();
        if (!normalizedCurrency.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency must be a three-letter ISO code");
        }
        String number = "LL" + UUID.randomUUID().toString().replace("-", "").substring(0, 22).toUpperCase();
        return accounts.save(new BankAccount(number, username, normalizedCurrency));
    }

    @Transactional(readOnly = true)
    public List<BankAccount> listOwnAccounts(String username) {
        return accounts.findAllByOwnerUsernameOrderByCreatedAtDesc(username);
    }

    @Transactional
    public TransactionResult deposit(String username, UUID accountId, long amountMinor, String key) {
        BankAccount account = lockOwnedAccount(accountId, username);
        validateAmount(amountMinor);
        ensureActive(account);
        SystemLedgerAccount clearing = lockClearingAccount();
        String fingerprint = fingerprint("DEPOSIT", accountId, null, amountMinor);
        LedgerTransaction existing = findIdempotent(key, fingerprint);
        if (existing != null) return result(existing, account);
        account.credit(amountMinor);
        clearing.applyDelta(-amountMinor);
        LedgerTransaction tx = saveTransaction(key, fingerprint, TransactionType.DEPOSIT, username);
        postBalanced(tx, new LedgerEntry(tx, account, amountMinor),
                new LedgerEntry(tx, clearing, -amountMinor));
        return result(tx, account);
    }

    @Transactional
    public TransactionResult withdraw(String username, UUID accountId, long amountMinor, String key) {
        BankAccount account = lockOwnedAccount(accountId, username);
        validateAmount(amountMinor);
        ensureActive(account);
        SystemLedgerAccount clearing = lockClearingAccount();
        String fingerprint = fingerprint("WITHDRAWAL", accountId, null, amountMinor);
        LedgerTransaction existing = findIdempotent(key, fingerprint);
        if (existing != null) return result(existing, account);
        if (account.getBalanceMinor() < amountMinor) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient funds");
        }
        account.debit(amountMinor);
        clearing.applyDelta(amountMinor);
        LedgerTransaction tx = saveTransaction(key, fingerprint, TransactionType.WITHDRAWAL, username);
        postBalanced(tx, new LedgerEntry(tx, account, -amountMinor),
                new LedgerEntry(tx, clearing, amountMinor));
        return result(tx, account);
    }

    @Transactional
    public TransactionResult transfer(String username, UUID fromId, UUID toId, long amountMinor, String key) {
        if (fromId.equals(toId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and destination accounts must differ");
        }
        validateAmount(amountMinor);
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
        if (existing != null) return result(existing, from);
        if (from.getBalanceMinor() < amountMinor) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient funds");
        }
        from.debit(amountMinor);
        to.credit(amountMinor);
        LedgerTransaction tx = saveTransaction(key, fingerprint, TransactionType.TRANSFER, username);
        postBalanced(tx, new LedgerEntry(tx, from, -amountMinor),
                new LedgerEntry(tx, to, amountMinor));
        return result(tx, from);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> statement(String username, UUID accountId) {
        BankAccount account = accounts.findByIdAndOwnerUsername(accountId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        return entries.findAllByAccountIdOrderByCreatedAtDesc(account.getId());
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
        if (first.getAmountMinor() + second.getAmountMinor() != 0L) {
            throw new IllegalStateException("Ledger transaction is not balanced");
        }
        entries.saveAll(List.of(first, second));
    }

    private LedgerTransaction findIdempotent(String key, String fingerprint) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required");
        }
        return transactions.findByIdempotencyKey(key).map(existing -> {
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency key was already used for a different request");
            }
            return existing;
        }).orElse(null);
    }

    private LedgerTransaction saveTransaction(String key, String fingerprint, TransactionType type, String username) {
        return transactions.saveAndFlush(new LedgerTransaction(key, fingerprint, type, username));
    }

    private TransactionResult result(LedgerTransaction tx, BankAccount account) {
        return new TransactionResult(tx.getId(), tx.getType().name(), account.getId(),
                account.getBalanceMinor(), account.getCurrency(), tx.getCreatedAt().toString());
    }

    private void validateAmount(long amountMinor) {
        if (amountMinor <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive minor units");
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
}
