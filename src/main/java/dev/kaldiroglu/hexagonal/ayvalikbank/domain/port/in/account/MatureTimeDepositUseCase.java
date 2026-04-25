package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Transaction;

public interface MatureTimeDepositUseCase {
    record Command(AccountId accountId) {}
    Transaction mature(Command command);
}
