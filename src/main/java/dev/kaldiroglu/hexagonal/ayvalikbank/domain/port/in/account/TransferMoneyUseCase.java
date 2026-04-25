package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Money;

public interface TransferMoneyUseCase {
    record Command(AccountId sourceAccountId, AccountId targetAccountId, Money amount) {}
    void transfer(Command command);
}
