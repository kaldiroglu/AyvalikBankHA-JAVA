package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.mapper.TransactionPersistenceMapper;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.repository.TransactionJpaRepository;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Transaction;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.account.TransactionRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransactionPersistenceAdapter implements TransactionRepositoryPort {

    private final TransactionJpaRepository repository;
    private final TransactionPersistenceMapper mapper;

    public TransactionPersistenceAdapter(TransactionJpaRepository repository, TransactionPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Transaction save(Transaction transaction) {
        var entity = mapper.toJpaEntity(transaction);
        var saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<Transaction> findByAccountId(AccountId accountId) {
        return repository.findByAccountIdOrderByTimestampDesc(accountId.value())
                .stream().map(mapper::toDomain).toList();
    }
}
