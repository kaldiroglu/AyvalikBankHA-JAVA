package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.TransactionAmount;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Transaction;

public interface DepositMoneyUseCase {
    record Command(AccountId accountId, TransactionAmount amount) {}
    Transaction deposit(Command command);
}
