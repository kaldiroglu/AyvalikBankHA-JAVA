package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

public final class SavingsAccount extends Account {
    SavingsAccount(AccountId id, CustomerId ownerId, Currency currency, Money balance, AccountStatus status) {
        super(id, ownerId, currency, balance, status);
    }
    @Override public AccountType type() { return AccountType.SAVINGS; }
    @Override public Transaction deposit(Money amount) { throw new UnsupportedOperationException("not yet implemented"); }
    @Override public Transaction withdraw(Money amount) { throw new UnsupportedOperationException("not yet implemented"); }
    @Override public Transaction transferOut(Money amount, Money fee, String targetAccountId) { throw new UnsupportedOperationException("not yet implemented"); }
}
