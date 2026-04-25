package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Currency;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.SavingsAccount;

import java.math.BigDecimal;

public interface OpenSavingsAccountUseCase {
    record Command(CustomerId ownerId, Currency currency, BigDecimal annualInterestRate) {}
    SavingsAccount openSavings(Command command);
}
