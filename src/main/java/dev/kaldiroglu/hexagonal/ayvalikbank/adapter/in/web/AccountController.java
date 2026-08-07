package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.AccountResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.BalanceResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.TransactionResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Money;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.TransactionAmount;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.CustomerAccountPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.BankUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final CustomerAccountPort customerAccount;

    public AccountController(CustomerAccountPort customerAccount) {
        this.customerAccount = customerAccount;
    }

    @PostMapping("/accounts/checking")
    public ResponseEntity<AccountResponse> openCheckingAccount(@AuthenticationPrincipal BankUserPrincipal caller,
                                                                @Valid @RequestBody OpenCheckingAccountRequest request) {
        Money overdraft = request.overdraftLimit() == null
                ? Money.zero(request.currency())
                : Money.of(request.overdraftLimit(), request.currency());
        var account = customerAccount.openChecking(new CustomerAccountPort.OpenCheckingCommand(
                caller.customerId(), request.currency(), overdraft));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @PostMapping("/accounts/savings")
    public ResponseEntity<AccountResponse> openSavingsAccount(@AuthenticationPrincipal BankUserPrincipal caller,
                                                                @Valid @RequestBody OpenSavingsAccountRequest request) {
        var account = customerAccount.openSavings(new CustomerAccountPort.OpenSavingsCommand(
                caller.customerId(), request.currency(), request.annualInterestRate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @PostMapping("/accounts/time-deposit")
    public ResponseEntity<AccountResponse> openTimeDepositAccount(@AuthenticationPrincipal BankUserPrincipal caller,
                                                                    @Valid @RequestBody OpenTimeDepositAccountRequest request) {
        var account = customerAccount.openTimeDeposit(new CustomerAccountPort.OpenTimeDepositCommand(
                caller.customerId(), request.currency(),
                Money.of(request.principal(), request.currency()),
                request.maturityDate(), request.annualInterestRate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping("/customers/{customerId}/accounts")
    public ResponseEntity<List<AccountResponse>> listAccounts(@AuthenticationPrincipal BankUserPrincipal caller,
                                                              @PathVariable String customerId) {
        var accounts = customerAccount.listAccounts(caller.customerId(), CustomerId.of(customerId)).stream()
                .map(AccountResponse::from).toList();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@AuthenticationPrincipal BankUserPrincipal caller,
                                                      @PathVariable String accountId) {
        Money balance = customerAccount.getBalance(caller.customerId(), AccountId.of(accountId));
        return ResponseEntity.ok(BalanceResponse.from(balance));
    }

    @PostMapping("/accounts/{accountId}/deposit")
    public ResponseEntity<TransactionResponse> deposit(@AuthenticationPrincipal BankUserPrincipal caller,
                                                        @PathVariable String accountId,
                                                        @Valid @RequestBody MoneyOperationRequest request) {
        var tx = customerAccount.deposit(new CustomerAccountPort.DepositCommand(
                caller.customerId(), AccountId.of(accountId),
                TransactionAmount.of(request.amount(), request.currency())));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(tx));
    }

    @PostMapping("/accounts/{accountId}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@AuthenticationPrincipal BankUserPrincipal caller,
                                                         @PathVariable String accountId,
                                                         @Valid @RequestBody MoneyOperationRequest request) {
        var tx = customerAccount.withdraw(new CustomerAccountPort.WithdrawCommand(
                caller.customerId(), AccountId.of(accountId),
                TransactionAmount.of(request.amount(), request.currency())));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(tx));
    }

    @PostMapping("/accounts/{accountId}/transfer")
    public ResponseEntity<Void> transfer(@AuthenticationPrincipal BankUserPrincipal caller,
                                          @PathVariable String accountId,
                                          @Valid @RequestBody TransferRequest request) {
        customerAccount.transfer(new CustomerAccountPort.TransferCommand(
                caller.customerId(), AccountId.of(accountId),
                AccountId.of(request.targetAccountId()),
                TransactionAmount.of(request.amount(), request.currency())));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@AuthenticationPrincipal BankUserPrincipal caller,
                                                                     @PathVariable String accountId) {
        var txs = customerAccount.getTransactions(caller.customerId(), AccountId.of(accountId)).stream()
                .map(TransactionResponse::from).toList();
        return ResponseEntity.ok(txs);
    }
}
