package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Account;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

import java.util.List;
import java.util.Optional;

public interface AccountRepositoryPort {
    Account save(Account account);
    Optional<Account> findById(AccountId id);
    List<Account> findByOwnerId(CustomerId ownerId);
    boolean existsById(AccountId id);
}
