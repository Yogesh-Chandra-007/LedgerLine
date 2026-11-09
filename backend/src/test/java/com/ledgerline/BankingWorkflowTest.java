package com.ledgerline;

import static org.junit.jupiter.api.Assertions.*;

import com.ledgerline.domain.*;
import com.ledgerline.repository.*;
import com.ledgerline.service.BankingService;
import org.junit.jupiter.api.BeforeEach;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class BankingWorkflowTest {
    private static final String PASSWORD = "Test-Only-Password-123!";

    @Autowired BankingService banking;
    @Autowired UserAccountRepository users;
    @Autowired BankAccountRepository accounts;
    @Autowired LedgerTransactionRepository transactions;
    @Autowired LedgerEntryRepository entries;
    @Autowired SystemLedgerAccountRepository systemAccounts;
    @Autowired AuditEventRepository auditEvents;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        auditEvents.deleteAll();
        entries.deleteAll();
        transactions.deleteAll();
        accounts.deleteAll();
        systemAccounts.deleteAll();
        users.deleteAll();
        users.save(new UserAccount("holder-one", encoder.encode(PASSWORD), UserRole.ACCOUNT_HOLDER));
        users.save(new UserAccount("holder-two", encoder.encode(PASSWORD), UserRole.ACCOUNT_HOLDER));
        systemAccounts.save(new SystemLedgerAccount("CASH_CLEARING", "External cash clearing counter-account"));
    }

    @Test
    void depositAndWithdrawalUpdateBalanceAndPostBalancedEntries() {
        BankAccount account = banking.openAccount("holder-one", "INR");
        banking.deposit("holder-one", account.getId(), 10_000L, "deposit-001");
        banking.withdraw("holder-one", account.getId(), 2_500L, "withdraw-001");

        assertEquals(7_500L, accounts.findById(account.getId()).orElseThrow().getBalanceMinor());
        assertEquals(4, entries.count());
        assertEquals(2, transactions.count());
        assertEveryTransactionBalances();
    }

    @Test
    void duplicateIdempotencyKeyDoesNotPostTwice() {
        BankAccount account = banking.openAccount("holder-one", "INR");
        var first = banking.deposit("holder-one", account.getId(), 500L, "same-key");
        var retry = banking.deposit("holder-one", account.getId(), 500L, "same-key");

        assertEquals(first.transactionId(), retry.transactionId());
        assertEquals(500L, accounts.findById(account.getId()).orElseThrow().getBalanceMinor());
        assertEquals(1, transactions.count());
        assertEquals(2, entries.count());
    }

    @Test
    void reusingIdempotencyKeyForDifferentAmountIsRejected() {
        BankAccount account = banking.openAccount("holder-one", "INR");
        banking.deposit("holder-one", account.getId(), 500L, "same-key");
        assertThrows(ResponseStatusException.class,
                () -> banking.deposit("holder-one", account.getId(), 700L, "same-key"));
    }

    @Test
    void withdrawalCannotOverdrawAccount() {
        BankAccount account = banking.openAccount("holder-one", "INR");
        assertThrows(ResponseStatusException.class,
                () -> banking.withdraw("holder-one", account.getId(), 1L, "withdraw-too-much"));
        assertEquals(0L, accounts.findById(account.getId()).orElseThrow().getBalanceMinor());
        assertEquals(0, transactions.count());
        assertEquals(0, entries.count());
    }

    @Test
    void transferCreatesBalancedEntriesAndMovesFundsAtomically() {
        BankAccount source = banking.openAccount("holder-one", "INR");
        BankAccount destination = banking.openAccount("holder-two", "INR");
        banking.deposit("holder-one", source.getId(), 10_000L, "seed");
        banking.transfer("holder-one", source.getId(), destination.getId(), 3_500L, "transfer-001");

        assertEquals(6_500L, accounts.findById(source.getId()).orElseThrow().getBalanceMinor());
        assertEquals(3_500L, accounts.findById(destination.getId()).orElseThrow().getBalanceMinor());
        assertEveryTransactionBalances();
    }

    @Test
    void userCannotReadOrDebitAnotherUsersAccount() {
        BankAccount account = banking.openAccount("holder-two", "INR");
        assertThrows(ResponseStatusException.class,
                () -> banking.deposit("holder-one", account.getId(), 100L, "unauthorized-deposit"));
        assertThrows(ResponseStatusException.class,
                () -> banking.statement("holder-one", account.getId()));
        assertEquals(0L, accounts.findById(account.getId()).orElseThrow().getBalanceMinor());
    }

    @Test
    void transferRejectsSameAccountAndCurrencyMismatch() {
        BankAccount inr = banking.openAccount("holder-one", "INR");
        BankAccount usd = banking.openAccount("holder-two", "USD");
        assertThrows(ResponseStatusException.class,
                () -> banking.transfer("holder-one", inr.getId(), inr.getId(), 1L, "same-account"));
        assertThrows(ResponseStatusException.class,
                () -> banking.transfer("holder-one", inr.getId(), usd.getId(), 1L, "currency-mismatch"));
    }


    @Test
    void concurrentWithdrawalsCannotOverdrawTheSameAccount() throws Exception {
        BankAccount account = banking.openAccount("holder-one", "INR");
        banking.deposit("holder-one", account.getId(), 1_000L, "concurrency-seed");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> attemptWithdrawal(account.getId(), "parallel-a", start));
            Future<Boolean> second = pool.submit(() -> attemptWithdrawal(account.getId(), "parallel-b", start));
            start.countDown();

            int successful = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successful, "Only one 700-minor-unit withdrawal may succeed");
            assertEquals(300L, accounts.findById(account.getId()).orElseThrow().getBalanceMinor());
            assertEveryTransactionBalances();
        } finally {
            pool.shutdownNow();
        }
    }


    @Test
    void idempotentReplayReturnsTheOriginalResultSnapshot() {
        BankAccount account = banking.openAccount("holder-one", "INR");
        var original = banking.deposit("holder-one", account.getId(), 500L, "snapshot-key");
        banking.deposit("holder-one", account.getId(), 100L, "later-deposit");

        var replay = banking.deposit("holder-one", account.getId(), 500L, "snapshot-key");
        assertEquals(original.transactionId(), replay.transactionId());
        assertEquals(500L, replay.balanceMinor(), "Replay must return the original response snapshot");
        assertEquals(600L, accounts.findById(account.getId()).orElseThrow().getBalanceMinor());
    }

    @Test
    void idempotencyKeyCannotBeReusedAcrossDifferentAccounts() {
        BankAccount first = banking.openAccount("holder-one", "INR");
        BankAccount second = banking.openAccount("holder-one", "INR");
        banking.deposit("holder-one", first.getId(), 500L, "global-key");
        assertThrows(ResponseStatusException.class,
                () -> banking.deposit("holder-one", second.getId(), 500L, "global-key"));
        assertEquals(0L, accounts.findById(second.getId()).orElseThrow().getBalanceMinor());
    }

    @Test
    void statementIncludesInitiatorAndRunningBalance() {
        BankAccount account = banking.openAccount("holder-one", "INR");
        banking.deposit("holder-one", account.getId(), 1_000L, "statement-deposit");
        banking.withdraw("holder-one", account.getId(), 250L, "statement-withdrawal");

        var statement = banking.statement("holder-one", account.getId());
        assertEquals(2, statement.size());
        assertEquals("holder-one", statement.get(0).createdBy());
        assertEquals(-250L, statement.get(0).amountMinor());
        assertEquals(750L, statement.get(0).balanceAfterMinor());
        assertEquals(1_000L, statement.get(1).balanceAfterMinor());
    }

    @Test
    void successfulMoneyMovementsAreAuditedAtomically() {
        BankAccount account = banking.openAccount("holder-one", "INR");
        banking.deposit("holder-one", account.getId(), 250L, "audit-deposit");
        var events = auditEvents.findTop200ByOrderByOccurredAtDesc();
        assertTrue(events.stream().anyMatch(e -> e.getActionType().equals("ACCOUNT_OPENED")
                && e.getOutcome().equals("SUCCESS")));
        assertTrue(events.stream().anyMatch(e -> e.getActionType().equals("DEPOSIT")
                && "holder-one".equals(e.getActorUsername())
                && Long.valueOf(250L).equals(e.getAmountMinor())));
    }


    @Test
    void rejectsAmountsBeyondSafeIntegerPrecision() {
        BankAccount account = banking.openAccount("holder-one", "INR");
        assertThrows(ResponseStatusException.class,
                () -> banking.deposit("holder-one", account.getId(), Long.MAX_VALUE, "unsafe-amount"));
        assertEquals(0L, accounts.findById(account.getId()).orElseThrow().getBalanceMinor());
        assertEquals(0, transactions.count());
    }

    private boolean attemptWithdrawal(java.util.UUID accountId, String key, CountDownLatch start) {
        try {
            start.await();
            banking.withdraw("holder-one", accountId, 700L, key);
            return true;
        } catch (ResponseStatusException expectedRejection) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void assertEveryTransactionBalances() {
        transactions.findAll().forEach(tx -> {
            long sum = entries.findAllByTransactionId(tx.getId()).stream()
                    .mapToLong(LedgerEntry::getAmountMinor).sum();
            assertEquals(0L, sum, "Ledger transaction must conserve money");
        });
    }
}
