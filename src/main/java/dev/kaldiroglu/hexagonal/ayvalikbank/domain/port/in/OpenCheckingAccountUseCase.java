package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.CheckingAccount;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Currency;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Money;

public interface OpenCheckingAccountUseCase {
    record Command(CustomerId ownerId, Currency currency, Money overdraftLimit) {}
    CheckingAccount openChecking(Command command);
}
