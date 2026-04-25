package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Transaction;

import java.time.YearMonth;

public interface AccrueInterestUseCase {
    record Command(AccountId accountId, YearMonth month) {}
    Transaction accrueInterest(Command command);
}
