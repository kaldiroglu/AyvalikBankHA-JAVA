package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

public sealed abstract class Account
        permits CheckingAccount, SavingsAccount, TimeDepositAccount {

    protected final AccountId id;
    protected final CustomerId ownerId;
    protected final Currency currency;
    protected Money balance;
    protected AccountStatus status;

    protected Account(AccountId id, CustomerId ownerId, Currency currency, Money balance, AccountStatus status) {
        if (!balance.currency().equals(currency))
            throw new IllegalArgumentException("Balance currency must match account currency");
        this.id = id;
        this.ownerId = ownerId;
        this.currency = currency;
        this.balance = balance;
        this.status = status;
    }

    public abstract AccountType type();

    // ── Status transitions (shared, final) ────────────────────────────────

    public final void freeze() {
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Cannot freeze a closed account");
        if (status == AccountStatus.FROZEN)
            throw new IllegalStateException("Account is already frozen");
        this.status = AccountStatus.FROZEN;
    }

    public final void unfreeze() {
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Cannot unfreeze a closed account");
        if (status == AccountStatus.ACTIVE)
            throw new IllegalStateException("Account is not frozen");
        this.status = AccountStatus.ACTIVE;
    }

    public final void close() {
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Account is already closed");
        this.status = AccountStatus.CLOSED;
    }

    // ── Operations: each subtype overrides ────────────────────────────────

    public abstract Transaction deposit(Money amount);

    public abstract Transaction withdraw(Money amount);

    public abstract Transaction transferOut(Money amount, Money fee, String targetAccountId);

    public final Transaction transferIn(Money amount, String sourceAccountId) {
        requireActive();
        requireSameCurrency(amount);
        this.balance = this.balance.add(amount);
        return Transaction.create(this.id, TransactionType.TRANSFER_IN, amount, "Transfer in from account " + sourceAccountId);
    }

    // ── Guards (visible to subclasses) ────────────────────────────────────

    protected final void requireActive() {
        if (status == AccountStatus.FROZEN)
            throw new IllegalStateException("Account is frozen");
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Account is closed");
    }

    protected final void requireSameCurrency(Money amount) {
        if (!amount.currency().equals(this.currency))
            throw new IllegalArgumentException("Currency " + amount.currency() + " does not match account currency " + this.currency);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public AccountId getId() { return id; }
    public CustomerId getOwnerId() { return ownerId; }
    public Currency getCurrency() { return currency; }
    public Money getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
}
