package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;

public interface CloseAccountUseCase {
    void closeAccount(AccountId accountId);
}
