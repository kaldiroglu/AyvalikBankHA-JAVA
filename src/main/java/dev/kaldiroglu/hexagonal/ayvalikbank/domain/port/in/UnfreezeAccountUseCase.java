package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.AccountId;

public interface UnfreezeAccountUseCase {
    void unfreezeAccount(AccountId accountId);
}
