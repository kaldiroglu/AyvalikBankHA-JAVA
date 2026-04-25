package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.mapper;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity.AccountJpaEntity;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.*;
import org.springframework.stereotype.Component;

@Component
public class AccountPersistenceMapper {

    public Account toDomain(AccountJpaEntity entity) {
        Currency currency = Currency.valueOf(entity.getCurrency());
        Money balance = Money.of(entity.getBalance(), currency);
        AccountStatus status = AccountStatus.valueOf(entity.getStatus());
        return new CheckingAccount(
                AccountId.of(entity.getId()),
                CustomerId.of(entity.getOwnerId()),
                currency,
                balance,
                status,
                Money.zero(currency)
        );
    }

    public AccountJpaEntity toJpaEntity(Account account) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.setId(account.getId().value());
        entity.setOwnerId(account.getOwnerId().value());
        entity.setCurrency(account.getCurrency().name());
        entity.setBalance(account.getBalance().amount());
        entity.setStatus(account.getStatus().name());
        return entity;
    }
}
