package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.AccountResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.BalanceResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.TransactionResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Money;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final OpenCheckingAccountUseCase openChecking;
    private final OpenSavingsAccountUseCase openSavings;
    private final OpenTimeDepositAccountUseCase openTimeDeposit;
    private final DepositMoneyUseCase depositMoney;
    private final WithdrawMoneyUseCase withdrawMoney;
    private final GetBalanceUseCase getBalance;
    private final GetTransactionsUseCase getTransactions;
    private final TransferMoneyUseCase transferMoney;
    private final ListAccountsUseCase listAccounts;

    public AccountController(OpenCheckingAccountUseCase openChecking,
                             OpenSavingsAccountUseCase openSavings,
                             OpenTimeDepositAccountUseCase openTimeDeposit,
                             DepositMoneyUseCase depositMoney,
                             WithdrawMoneyUseCase withdrawMoney,
                             GetBalanceUseCase getBalance,
                             GetTransactionsUseCase getTransactions,
                             TransferMoneyUseCase transferMoney,
                             ListAccountsUseCase listAccounts) {
        this.openChecking = openChecking;
        this.openSavings = openSavings;
        this.openTimeDeposit = openTimeDeposit;
        this.depositMoney = depositMoney;
        this.withdrawMoney = withdrawMoney;
        this.getBalance = getBalance;
        this.getTransactions = getTransactions;
        this.transferMoney = transferMoney;
        this.listAccounts = listAccounts;
    }

    @PostMapping("/accounts/checking")
    public ResponseEntity<AccountResponse> openCheckingAccount(@RequestParam String ownerId,
                                                                @Valid @RequestBody OpenCheckingAccountRequest request) {
        Money overdraft = request.overdraftLimit() == null
                ? Money.zero(request.currency())
                : Money.of(request.overdraftLimit(), request.currency());
        var account = openChecking.openChecking(new OpenCheckingAccountUseCase.Command(
                CustomerId.of(ownerId), request.currency(), overdraft));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @PostMapping("/accounts/savings")
    public ResponseEntity<AccountResponse> openSavingsAccount(@RequestParam String ownerId,
                                                                @Valid @RequestBody OpenSavingsAccountRequest request) {
        var account = openSavings.openSavings(new OpenSavingsAccountUseCase.Command(
                CustomerId.of(ownerId), request.currency(), request.annualInterestRate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @PostMapping("/accounts/time-deposit")
    public ResponseEntity<AccountResponse> openTimeDepositAccount(@RequestParam String ownerId,
                                                                    @Valid @RequestBody OpenTimeDepositAccountRequest request) {
        var account = openTimeDeposit.openTimeDeposit(new OpenTimeDepositAccountUseCase.Command(
                CustomerId.of(ownerId), request.currency(),
                Money.of(request.principal(), request.currency()),
                request.maturityDate(), request.annualInterestRate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping("/customers/{customerId}/accounts")
    public ResponseEntity<List<AccountResponse>> listAccounts(@PathVariable String customerId) {
        var accounts = listAccounts.listAccounts(CustomerId.of(customerId)).stream()
                .map(AccountResponse::from).toList();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        Money balance = getBalance.getBalance(AccountId.of(accountId));
        return ResponseEntity.ok(BalanceResponse.from(balance));
    }

    @PostMapping("/accounts/{accountId}/deposit")
    public ResponseEntity<TransactionResponse> deposit(@PathVariable String accountId,
                                                        @Valid @RequestBody MoneyOperationRequest request) {
        var tx = depositMoney.deposit(new DepositMoneyUseCase.Command(
                AccountId.of(accountId), Money.of(request.amount(), request.currency())));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(tx));
    }

    @PostMapping("/accounts/{accountId}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@PathVariable String accountId,
                                                         @Valid @RequestBody MoneyOperationRequest request) {
        var tx = withdrawMoney.withdraw(new WithdrawMoneyUseCase.Command(
                AccountId.of(accountId), Money.of(request.amount(), request.currency())));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(tx));
    }

    @PostMapping("/accounts/{accountId}/transfer")
    public ResponseEntity<Void> transfer(@PathVariable String accountId,
                                          @Valid @RequestBody TransferRequest request) {
        transferMoney.transfer(new TransferMoneyUseCase.Command(
                AccountId.of(accountId),
                AccountId.of(request.targetAccountId()),
                Money.of(request.amount(), request.currency())));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@PathVariable String accountId) {
        var txs = getTransactions.getTransactions(AccountId.of(accountId)).stream()
                .map(TransactionResponse::from).toList();
        return ResponseEntity.ok(txs);
    }
}
