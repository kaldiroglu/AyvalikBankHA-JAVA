package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.mapper;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity.AccountJpaEntity;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.*;
import org.springframework.stereotype.Component;

@Component
public class AccountPersistenceMapper {

    public Account toDomain(AccountJpaEntity entity) {
        Currency currency = Currency.valueOf(entity.getCurrency());
        Money balance = Money.of(entity.getBalance(), currency);
        AccountStatus status = AccountStatus.valueOf(entity.getStatus());
        AccountType type = AccountType.valueOf(entity.getType());
        return switch (type) {
            case CHECKING -> new CheckingAccount(
                    AccountId.of(entity.getId()),
                    CustomerId.of(entity.getOwnerId()),
                    currency, balance, status,
                    Money.of(entity.getOverdraftLimit(), currency));
            case SAVINGS -> new SavingsAccount(
                    AccountId.of(entity.getId()),
                    CustomerId.of(entity.getOwnerId()),
                    currency, balance, status,
                    entity.getInterestRate(),
                    entity.getLastAccrualDate());
            case TIME_DEPOSIT -> new TimeDepositAccount(
                    AccountId.of(entity.getId()),
                    CustomerId.of(entity.getOwnerId()),
                    currency, balance, status,
                    Money.of(entity.getPrincipal(), currency),
                    entity.getOpenedOn(),
                    entity.getMaturityDate(),
                    entity.getInterestRate(),
                    Boolean.TRUE.equals(entity.getMatured()));
        };
    }

    public AccountJpaEntity toJpaEntity(Account account) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.setId(account.getId().value());
        copyOnto(account, entity);
        return entity;
    }

    /**
     * Copies every mutable column from the domain object onto an existing entity.
     *
     * <p>Never touches {@code id} or {@code version}. The id is immutable; the version belongs to
     * Hibernate, and overwriting it would defeat the optimistic-lock check this exists to support.
     *
     * <p>The switch stays exhaustive over the sealed {@code Account} hierarchy — no {@code default},
     * so a fourth account product breaks the build here until it is mapped.
     */
    public void copyOnto(Account account, AccountJpaEntity entity) {
        entity.setOwnerId(account.getOwnerId().value());
        entity.setCurrency(account.getCurrency().name());
        entity.setBalance(account.getBalance().amount());
        entity.setStatus(account.getStatus().name());
        entity.setType(account.type().name());
        switch (account) {
            case CheckingAccount c -> entity.setOverdraftLimit(c.getOverdraftLimit().amount());
            case SavingsAccount s -> {
                entity.setInterestRate(s.getAnnualInterestRate());
                entity.setLastAccrualDate(s.getLastAccrualDate());
            }
            case TimeDepositAccount t -> {
                entity.setPrincipal(t.getPrincipal().amount());
                entity.setOpenedOn(t.getOpenedOn());
                entity.setMaturityDate(t.getMaturityDate());
                entity.setInterestRate(t.getAnnualInterestRate());
                entity.setMatured(t.isMatured());
            }
        }
    }
}
