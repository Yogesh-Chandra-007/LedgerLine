package com.ledgerline;

import static org.junit.jupiter.api.Assertions.*;

import com.ledgerline.domain.*;
import com.ledgerline.repository.*;
import com.ledgerline.service.BankingService;
import org.junit.jupiter.api.BeforeEach;
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
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
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

    private void assertEveryTransactionBalances() {
        transactions.findAll().forEach(tx -> {
            long sum = entries.findAllByTransactionId(tx.getId()).stream()
                    .mapToLong(LedgerEntry::getAmountMinor).sum();
            assertEquals(0L, sum, "Ledger transaction must conserve money");
        });
    }
}
