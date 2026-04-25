package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.mapper;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity.TransactionJpaEntity;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.*;
import org.springframework.stereotype.Component;

@Component
public class TransactionPersistenceMapper {

    public Transaction toDomain(TransactionJpaEntity entity) {
        Currency currency = Currency.valueOf(entity.getCurrency());
        Money amount = Money.of(entity.getAmount(), currency);
        return new Transaction(
                TransactionId.of(entity.getId()),
                AccountId.of(entity.getAccountId()),
                TransactionType.valueOf(entity.getType()),
                amount,
                entity.getTimestamp(),
                entity.getDescription()
        );
    }

    public TransactionJpaEntity toJpaEntity(Transaction transaction) {
        TransactionJpaEntity entity = new TransactionJpaEntity();
        entity.setId(transaction.getId().value());
        entity.setAccountId(transaction.getAccountId().value());
        entity.setType(transaction.getType().name());
        entity.setAmount(transaction.getAmount().amount());
        entity.setCurrency(transaction.getAmount().currency().name());
        entity.setTimestamp(transaction.getTimestamp());
        entity.setDescription(transaction.getDescription());
        return entity;
    }
}
