package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Currency;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Money;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.TimeDepositAccount;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface OpenTimeDepositAccountUseCase {
    record Command(CustomerId ownerId, Currency currency, Money principal,
                   LocalDate maturityDate, BigDecimal annualInterestRate) {}
    TimeDepositAccount openTimeDeposit(Command command);
}
