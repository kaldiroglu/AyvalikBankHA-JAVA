package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Transaction;

import java.time.YearMonth;

public interface AccrueInterestUseCase {
    record Command(AccountId accountId, YearMonth month) {}
    Transaction accrueInterest(Command command);
}
