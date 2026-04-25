package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;

public interface FreezeAccountUseCase {
    void freezeAccount(AccountId accountId);
}
