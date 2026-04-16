package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.AccountId;

public interface FreezeAccountUseCase {
    void freezeAccount(AccountId accountId);
}
