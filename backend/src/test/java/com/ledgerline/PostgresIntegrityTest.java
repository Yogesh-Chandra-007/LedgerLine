package com.ledgerline;

import static org.junit.jupiter.api.Assertions.*;

import com.ledgerline.domain.*;
import com.ledgerline.repository.*;
import com.ledgerline.service.BankingService;
import java.util.UUID;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("postgres-it")
class PostgresIntegrityTest {
    @Autowired BankingService banking;
    @Autowired UserAccountRepository users;
    @Autowired BankAccountRepository accounts;
    @Autowired LedgerEntryRepository entries;
    @Autowired LedgerTransactionRepository transactions;
    @Autowired AuditEventRepository auditEvents;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private String username;

    @BeforeEach
    void createIsolatedUser() {
        username = "pg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        users.save(new UserAccount(username, encoder.encode("Postgres-Test-Password-123!"),
                UserRole.ACCOUNT_HOLDER));
    }

    @Test
    void postgresMigrationsAndDeferredReconciliationAcceptValidMoneyMovement() {
        BankAccount account = banking.openAccount(username, "INR");
        banking.deposit(username, account.getId(), 12_345L, "pg-valid-" + UUID.randomUUID());
        assertEquals(12_345L, accounts.findById(account.getId()).orElseThrow().getBalanceMinor());
        var accountEntries = entries.findAllByAccountIdOrderByCreatedAtDesc(account.getId());
        assertEquals(1, accountEntries.size(), "A customer account receives one side of a double-entry posting");
        assertEquals(2, entries.findAllByTransactionId(accountEntries.get(0).getTransaction().getId()).size(),
                "The transaction must contain the customer posting and its system counter-posting");
        assertTrue(auditEvents.findTop200ByOrderByOccurredAtDesc().stream()
                .anyMatch(event -> "DEPOSIT".equals(event.getActionType())
                        && username.equals(event.getActorUsername())));
    }

    @Test
    void runtimeRoleCannotMutateLedgerAndOwnerCannotBypassAppendOnlyTriggers() throws Exception {
        BankAccount account = banking.openAccount(username, "INR");
        banking.deposit(username, account.getId(), 1_000L, "pg-immutable-" + UUID.randomUUID());
        LedgerEntry entry = entries.findAllByAccountIdOrderByCreatedAtDesc(account.getId()).get(0);

        // The runtime role is deliberately not granted UPDATE or DELETE on immutable tables.
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE ledger_entries SET amount_minor = amount_minor + 1 WHERE id = ?", entry.getId()));

        // Verify the database trigger itself using the separate migration-owner connection.
        try (Connection connection = DriverManager.getConnection(
                System.getenv("MIGRATION_DB_URL"), System.getenv("MIGRATION_DB_USERNAME"),
                System.getenv("MIGRATION_DB_PASSWORD"))) {
            connection.setAutoCommit(true);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE ledger_entries SET amount_minor = amount_minor + 1 WHERE id = ?")) {
                update.setObject(1, entry.getId());
                assertThrows(SQLException.class, update::executeUpdate);
            }
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM ledger_entries WHERE id = ?")) {
                delete.setObject(1, entry.getId());
                assertThrows(SQLException.class, delete::executeUpdate);
            }
            var audit = auditEvents.findTop200ByOrderByOccurredAtDesc().stream()
                    .filter(event -> username.equals(event.getActorUsername()))
                    .findFirst().orElseThrow();
            try (PreparedStatement updateAudit = connection.prepareStatement(
                    "UPDATE audit_events SET reason = 'tampered' WHERE id = ?")) {
                updateAudit.setObject(1, audit.getId());
                assertThrows(SQLException.class, updateAudit::executeUpdate);
            }
        }
    }

    @Test
    void postgresRejectsUnbalancedTransactionAtCommit() {
        BankAccount account = banking.openAccount(username, "INR");
        UUID txId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThrows(RuntimeException.class, () -> tx.execute(status -> {
            jdbc.update("""
                INSERT INTO ledger_transactions
                (id, idempotency_key, request_fingerprint, type, created_by)
                VALUES (?, ?, ?, 'DEPOSIT', ?)
                """, txId, "pg-unbalanced-" + txId, "0".repeat(64), username);
            jdbc.update("""
                INSERT INTO ledger_entries (id, transaction_id, account_id, amount_minor)
                VALUES (?, ?, ?, 100)
                """, entryId, txId, account.getId());
            return null;
        }));
        assertFalse(transactions.findById(txId).isPresent());
    }

    @Test
    void postgresRejectsDirectBalanceTampering() {
        BankAccount account = banking.openAccount(username, "INR");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () -> tx.execute(status -> {
            jdbc.update("UPDATE bank_accounts SET balance_minor = 777 WHERE id = ?", account.getId());
            return null;
        }));
        assertEquals(0L, accounts.findById(account.getId()).orElseThrow().getBalanceMinor());
    }

    @Test
    void postgresSerializesConcurrentDuplicateKeysAcrossDifferentAccounts() throws Exception {
        BankAccount first = banking.openAccount(username, "INR");
        BankAccount second = banking.openAccount(username, "INR");
        String sharedKey = "pg-global-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = pool.submit(() -> attemptDeposit(first.getId(), sharedKey, start));
            Future<Boolean> b = pool.submit(() -> attemptDeposit(second.getId(), sharedKey, start));
            start.countDown();
            int successful = (a.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (b.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successful);
            long total = accounts.findById(first.getId()).orElseThrow().getBalanceMinor()
                    + accounts.findById(second.getId()).orElseThrow().getBalanceMinor();
            assertEquals(500L, total);
            assertEquals(1, transactions.findAll().stream()
                    .filter(transaction -> sharedKey.equals(transaction.getIdempotencyKey())).count());
        } finally {
            pool.shutdownNow();
        }
    }


    @Test
    void runtimeRoleCannotRewriteAccountOwnershipOrCurrency() {
        BankAccount account = banking.openAccount(username, "INR");
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE bank_accounts SET owner_username = ? WHERE id = ?",
                        username + "-other", account.getId()));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE bank_accounts SET currency = 'USD' WHERE id = ?", account.getId()));
    }

    @Test
    void postgresRejectsAccountInsertedWithUnbackedOpeningBalance() {
        UUID accountId = UUID.randomUUID();
        assertThrows(DataAccessException.class, () -> jdbc.update("""
            INSERT INTO bank_accounts
            (id, account_number, owner_username, currency, balance_minor, status, created_at)
            VALUES (?, ?, ?, 'INR', 777, 'ACTIVE', CURRENT_TIMESTAMP)
            """, accountId, "LL" + UUID.randomUUID().toString().replace("-", "").substring(0, 22),
                username));
        assertFalse(accounts.findById(accountId).isPresent());
    }


    @Test
    void postgresSerializesConcurrentWithdrawalsWithoutOverdraft() throws Exception {
        BankAccount account = banking.openAccount(username, "INR");
        banking.deposit(username, account.getId(), 1_000L, "pg-withdraw-seed-" + UUID.randomUUID());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> attemptWithdrawal(account.getId(), "pg-withdraw-a-" + UUID.randomUUID(), start));
            Future<Boolean> second = pool.submit(() -> attemptWithdrawal(account.getId(), "pg-withdraw-b-" + UUID.randomUUID(), start));
            start.countDown();
            int successful = (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successful);
            assertEquals(300L, accounts.findById(account.getId()).orElseThrow().getBalanceMinor());
            assertEveryTransactionBalances();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void postgresLocksOppositeDirectionTransfersInDeterministicOrder() throws Exception {
        BankAccount first = banking.openAccount(username, "INR");
        BankAccount second = banking.openAccount(username, "INR");
        banking.deposit(username, first.getId(), 1_000L, "pg-opposite-seed-a-" + UUID.randomUUID());
        banking.deposit(username, second.getId(), 1_000L, "pg-opposite-seed-b-" + UUID.randomUUID());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> forward = pool.submit(() -> {
                await(start);
                banking.transfer(username, first.getId(), second.getId(), 100L,
                        "pg-opposite-forward-" + UUID.randomUUID());
            });
            Future<?> reverse = pool.submit(() -> {
                await(start);
                banking.transfer(username, second.getId(), first.getId(), 100L,
                        "pg-opposite-reverse-" + UUID.randomUUID());
            });
            start.countDown();
            forward.get(15, TimeUnit.SECONDS);
            reverse.get(15, TimeUnit.SECONDS);
            assertEquals(1_000L, accounts.findById(first.getId()).orElseThrow().getBalanceMinor());
            assertEquals(1_000L, accounts.findById(second.getId()).orElseThrow().getBalanceMinor());
            assertEveryTransactionBalances();
        } finally {
            pool.shutdownNow();
        }
    }


    @Test
    void postgresRejectsBalancedTransferThatDebitsAnotherUsersAccount() {
        String otherUsername = "pg-other-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        users.save(new UserAccount(otherUsername, encoder.encode("Postgres-Test-Password-123!"),
                UserRole.ACCOUNT_HOLDER));
        BankAccount source = banking.openAccount(otherUsername, "INR");
        banking.deposit(otherUsername, source.getId(), 500L, "pg-owner-seed-" + UUID.randomUUID());
        BankAccount destination = banking.openAccount(username, "INR");

        UUID txId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () -> tx.execute(status -> {
            jdbc.update("UPDATE bank_accounts SET balance_minor = 400 WHERE id = ?", source.getId());
            jdbc.update("UPDATE bank_accounts SET balance_minor = 100 WHERE id = ?", destination.getId());
            jdbc.update("""
                INSERT INTO ledger_transactions
                (id, idempotency_key, request_fingerprint, type, created_by, result_account_id, result_balance_minor)
                VALUES (?, ?, ?, 'TRANSFER', ?, ?, 400)
                """, txId, "pg-forged-owner-" + txId, "1".repeat(64), username, source.getId());
            jdbc.update("""
                INSERT INTO ledger_entries (id, transaction_id, account_id, amount_minor)
                VALUES (?, ?, ?, -100)
                """, UUID.randomUUID(), txId, source.getId());
            jdbc.update("""
                INSERT INTO ledger_entries (id, transaction_id, account_id, amount_minor)
                VALUES (?, ?, ?, 100)
                """, UUID.randomUUID(), txId, destination.getId());
            return null;
        }));
        assertFalse(transactions.findById(txId).isPresent());
        assertEquals(500L, accounts.findById(source.getId()).orElseThrow().getBalanceMinor());
        assertEquals(0L, accounts.findById(destination.getId()).orElseThrow().getBalanceMinor());
    }

    private boolean attemptWithdrawal(UUID accountId, String key, CountDownLatch start) {
        try {
            await(start);
            banking.withdraw(username, accountId, 700L, key);
            return true;
        } catch (ResponseStatusException expectedRejection) {
            return false;
        }
    }

    private void await(CountDownLatch start) {
        try { start.await(); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency test interrupted", interrupted);
        }
    }

    private void assertEveryTransactionBalances() {
        for (LedgerTransaction transaction : transactions.findAll()) {
            var postings = entries.findAllByTransactionId(transaction.getId());
            assertEquals(2, postings.size(), "Every transaction must have two postings");
            assertEquals(0L, postings.stream().mapToLong(LedgerEntry::getAmountMinor).sum(),
                    "Every transaction must sum to zero");
        }
    }

    private boolean attemptDeposit(UUID accountId, String key, CountDownLatch start) {
        try {
            start.await();
            banking.deposit(username, accountId, 500L, key);
            return true;
        } catch (ResponseStatusException expectedConflict) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
