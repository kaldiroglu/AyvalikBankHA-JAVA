package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.CheckingAccount;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Currency;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Money;

public interface OpenCheckingAccountUseCase {
    record Command(CustomerId ownerId, Currency currency, Money overdraftLimit) {}
    CheckingAccount openChecking(Command command);
}
