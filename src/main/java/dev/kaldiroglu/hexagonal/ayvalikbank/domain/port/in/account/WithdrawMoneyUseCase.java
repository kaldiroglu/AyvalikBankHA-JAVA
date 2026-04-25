package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Money;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Transaction;

public interface WithdrawMoneyUseCase {
    record Command(AccountId accountId, Money amount) {}
    Transaction withdraw(Command command);
}
