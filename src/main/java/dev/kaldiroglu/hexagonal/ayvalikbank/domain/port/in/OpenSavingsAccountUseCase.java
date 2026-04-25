package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Currency;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.SavingsAccount;

import java.math.BigDecimal;

public interface OpenSavingsAccountUseCase {
    record Command(CustomerId ownerId, Currency currency, BigDecimal annualInterestRate) {}
    SavingsAccount openSavings(Command command);
}
