package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

public sealed abstract class Account
        permits CheckingAccount, SavingsAccount, TimeDepositAccount {

    protected final AccountId id;
    protected final CustomerId ownerId;
    protected final Currency currency;
    protected Money balance;
    protected AccountState state;

    protected Account(AccountId id, CustomerId ownerId, Currency currency, Money balance, AccountStatus status) {
        if (!balance.currency().equals(currency))
            throw new IllegalArgumentException("Balance currency must match account currency");
        this.id = id;
        this.ownerId = ownerId;
        this.currency = currency;
        this.balance = balance;
        this.state = AccountState.of(status);
    }

    public abstract AccountType type();

    // ── Status transitions (delegated to the State) ───────────────────────

    public final void freeze() { this.state = state.freeze(); }

    public final void unfreeze() { this.state = state.unfreeze(); }

    public final void close() { this.state = state.close(); }

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

    protected final void requireActive() { state.requireOperable(); }

    protected final void requireSameCurrency(Money amount) {
        if (!amount.currency().equals(this.currency))
            throw new IllegalArgumentException("Currency " + amount.currency() + " does not match account currency " + this.currency);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public AccountId getId() { return id; }
    public CustomerId getOwnerId() { return ownerId; }
    public Currency getCurrency() { return currency; }
    public Money getBalance() { return balance; }
    public AccountStatus getStatus() { return state.status(); }
    public AccountState getState() { return state; }
}
