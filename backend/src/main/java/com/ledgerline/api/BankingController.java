package com.ledgerline.api;

import java.util.List;
import java.util.UUID;

import com.ledgerline.domain.BankAccount;
import com.ledgerline.service.BankingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account-holder")
public class BankingController {
    private final BankingService banking;

    public BankingController(BankingService banking) {
        this.banking = banking;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse openAccount(Authentication authentication,
                                       @Valid @RequestBody OpenAccountRequest request) {
        return AccountResponse.from(banking.openAccount(authentication.getName(), request.currency()));
    }

    @GetMapping("/accounts")
    public List<AccountResponse> accounts(Authentication authentication) {
        return banking.listOwnAccounts(authentication.getName()).stream().map(AccountResponse::from).toList();
    }

    @PostMapping("/transactions/deposit")
    public BankingService.TransactionResult deposit(Authentication auth,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody MoneyRequest request) {
        return banking.deposit(auth.getName(), request.accountId(), request.amountMinor(), key);
    }

    @PostMapping("/transactions/withdraw")
    public BankingService.TransactionResult withdraw(Authentication auth,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody MoneyRequest request) {
        return banking.withdraw(auth.getName(), request.accountId(), request.amountMinor(), key);
    }

    @PostMapping("/transactions/transfer")
    public BankingService.TransactionResult transfer(Authentication auth,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TransferRequest request) {
        return banking.transfer(auth.getName(), request.fromAccountId(), request.toAccountId(),
                request.amountMinor(), key);
    }

    @GetMapping("/accounts/{accountId}/statement")
    public List<BankingService.StatementLine> statement(Authentication auth, @PathVariable UUID accountId) {
        return banking.statement(auth.getName(), accountId);
    }

    public record OpenAccountRequest(
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a three-letter code")
            String currency) {}

    public record MoneyRequest(@NotNull UUID accountId, @Positive long amountMinor) {}

    public record TransferRequest(@NotNull UUID fromAccountId, @NotNull UUID toAccountId,
                                  @Positive long amountMinor) {}

    public record AccountResponse(UUID id, String accountNumber, String currency,
                                  long balanceMinor, String status, String createdAt) {
        static AccountResponse from(BankAccount a) {
            return new AccountResponse(a.getId(), a.getAccountNumber(), a.getCurrency(),
                    a.getBalanceMinor(), a.getStatus().name(), a.getCreatedAt().toString());
        }
    }
}
