package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity.AccountJpaEntity;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.mapper.AccountPersistenceMapper;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.repository.AccountJpaRepository;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Account;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.account.AccountRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AccountPersistenceAdapter implements AccountRepositoryPort {

    private final AccountJpaRepository repository;
    private final AccountPersistenceMapper mapper;

    public AccountPersistenceAdapter(AccountJpaRepository repository, AccountPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Writes the account back, preserving the optimistic-lock version.
     *
     * <p>The entity is <b>not</b> rebuilt from scratch. Within the application service's
     * transaction the earlier {@code findById} left a managed entity in the persistence context, and
     * that instance carries the version loaded at the start of the transaction. Copying onto it lets
     * Hibernate emit {@code UPDATE ... WHERE version = ?} and detect a concurrent write. Merging a
     * freshly built detached entity would carry no version and silently overwrite the whole row —
     * which is exactly how two concurrent withdrawals used to lose one of themselves.
     *
     * <p>The lookup costs no extra query: it is served by the persistence context's first-level
     * cache. The {@code orElseGet} branch covers a genuinely new account, where no row exists yet.
     */
    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = repository.findById(account.getId().value())
                .map(managed -> {
                    mapper.copyOnto(account, managed);
                    return managed;
                })
                .orElseGet(() -> mapper.toJpaEntity(account));
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Account> findByOwnerId(CustomerId ownerId) {
        return repository.findByOwnerId(ownerId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsById(AccountId id) {
        return repository.existsById(id.value());
    }
}
