package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

public final class TimeDepositAccount extends Account {
    TimeDepositAccount(AccountId id, CustomerId ownerId, Currency currency, Money balance, AccountStatus status) {
        super(id, ownerId, currency, balance, status);
    }
    @Override public AccountType type() { return AccountType.TIME_DEPOSIT; }
    @Override public Transaction deposit(Money amount) { throw new UnsupportedOperationException("not yet implemented"); }
    @Override public Transaction withdraw(Money amount) { throw new UnsupportedOperationException("not yet implemented"); }
    @Override public Transaction transferOut(Money amount, Money fee, String targetAccountId) { throw new UnsupportedOperationException("not yet implemented"); }
}
