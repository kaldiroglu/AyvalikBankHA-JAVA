package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Account;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

import java.util.List;

public interface ListAccountsUseCase {
    List<Account> listAccounts(CustomerId ownerId);
}
