package dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything a <b>customer</b> can do with their own accounts — one conversation with one actor.
 *
 * <p>This is a port in Cockburn's sense: it groups the operations belonging to a single kind of
 * outside party, rather than devoting an interface to each individual method. The admin-facing
 * counterpart is {@link AccountAdministrationPort}, and keeping the two apart is what lets an
 * ownership rule be stated once — for this port — instead of method by method.
 *
 * <p>Note the direction of the dependency. This interface lives in the <b>application</b> layer,
 * not the domain, because a use case is an application concern: {@link OpenCheckingCommand} is a
 * request shape, not a domain concept. Driven ports go the other way and live in
 * {@code domain/port/out} — the domain declares the interfaces it requires and adapters implement
 * them. That asymmetry is deliberate; see {@code Refactorings.md} entry 2.
 */
public interface CustomerAccountPort {

    record OpenCheckingCommand(CustomerId ownerId, Currency currency, Money overdraftLimit) {}

    record OpenSavingsCommand(CustomerId ownerId, Currency currency, BigDecimal annualInterestRate) {}

    record OpenTimeDepositCommand(CustomerId ownerId, Currency currency, Money principal,
                                  LocalDate maturityDate, BigDecimal annualInterestRate) {}

    record DepositCommand(AccountId accountId, TransactionAmount amount) {}

    record WithdrawCommand(AccountId accountId, TransactionAmount amount) {}

    record TransferCommand(AccountId sourceAccountId, AccountId targetAccountId, TransactionAmount amount) {}

    CheckingAccount openChecking(OpenCheckingCommand command);

    SavingsAccount openSavings(OpenSavingsCommand command);

    TimeDepositAccount openTimeDeposit(OpenTimeDepositCommand command);

    Transaction deposit(DepositCommand command);

    Transaction withdraw(WithdrawCommand command);

    void transfer(TransferCommand command);

    Money getBalance(AccountId accountId);

    List<Account> listAccounts(CustomerId ownerId);

    List<Transaction> getTransactions(AccountId accountId);
}
