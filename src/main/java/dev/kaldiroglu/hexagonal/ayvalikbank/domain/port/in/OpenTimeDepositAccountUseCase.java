package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Currency;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Money;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.TimeDepositAccount;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface OpenTimeDepositAccountUseCase {
    record Command(CustomerId ownerId, Currency currency, Money principal,
                   LocalDate maturityDate, BigDecimal annualInterestRate) {}
    TimeDepositAccount openTimeDeposit(Command command);
}
