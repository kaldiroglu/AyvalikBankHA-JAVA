package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence;

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

    @Override
    public Account save(Account account) {
        var entity = mapper.toJpaEntity(account);
        var saved = repository.save(entity);
        return mapper.toDomain(saved);
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
