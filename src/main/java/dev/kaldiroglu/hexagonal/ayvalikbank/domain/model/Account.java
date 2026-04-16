package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import java.math.BigDecimal;

public class Account {
    private final AccountId id;
    private final CustomerId ownerId;
    private final Currency currency;
    private Money balance;
    private AccountStatus status;

    public Account(AccountId id, CustomerId ownerId, Currency currency, Money balance, AccountStatus status) {
        if (!balance.currency().equals(currency))
            throw new IllegalArgumentException("Balance currency must match account currency");
        this.id = id;
        this.ownerId = ownerId;
        this.currency = currency;
        this.balance = balance;
        this.status = status;
    }

    public static Account open(CustomerId ownerId, Currency currency) {
        return new Account(AccountId.generate(), ownerId, currency, Money.zero(currency), AccountStatus.ACTIVE);
    }

    // ── Status transitions ────────────────────────────────────────────────

    public void freeze() {
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Cannot freeze a closed account");
        if (status == AccountStatus.FROZEN)
            throw new IllegalStateException("Account is already frozen");
        this.status = AccountStatus.FROZEN;
    }

    public void unfreeze() {
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Cannot unfreeze a closed account");
        if (status == AccountStatus.ACTIVE)
            throw new IllegalStateException("Account is not frozen");
        this.status = AccountStatus.ACTIVE;
    }

    public void close() {
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Account is already closed");
        this.status = AccountStatus.CLOSED;
    }

    // ── Operations (all require ACTIVE status) ────────────────────────────

    public Transaction deposit(Money amount) {
        requireActive();
        if (!amount.currency().equals(this.currency))
            throw new IllegalArgumentException("Deposit currency " + amount.currency() + " does not match account currency " + this.currency);
        this.balance = this.balance.add(amount);
        return Transaction.create(this.id, TransactionType.DEPOSIT, amount, "Deposit");
    }

    public Transaction withdraw(Money amount) {
        requireActive();
        if (!amount.currency().equals(this.currency))
            throw new IllegalArgumentException("Withdrawal currency " + amount.currency() + " does not match account currency " + this.currency);
        if (!this.balance.isGreaterThanOrEqualTo(amount))
            throw new IllegalArgumentException("Insufficient funds");
        this.balance = this.balance.subtract(amount);
        return Transaction.create(this.id, TransactionType.WITHDRAWAL, amount, "Withdrawal");
    }

    public Transaction transferOut(Money amount, Money fee, String targetAccountId) {
        requireActive();
        if (!amount.currency().equals(this.currency))
            throw new IllegalArgumentException("Transfer currency does not match account currency");
        Money totalDebit = fee.amount().compareTo(BigDecimal.ZERO) > 0 ? amount.add(fee) : amount;
        if (!this.balance.isGreaterThanOrEqualTo(totalDebit))
            throw new IllegalArgumentException("Insufficient funds for transfer including fee");
        this.balance = this.balance.subtract(totalDebit);
        String desc = "Transfer out to account " + targetAccountId +
                (fee.amount().compareTo(BigDecimal.ZERO) > 0 ? " (fee: " + fee + ")" : "");
        return Transaction.create(this.id, TransactionType.TRANSFER_OUT, amount, desc);
    }

    public Transaction transferIn(Money amount, String sourceAccountId) {
        requireActive();
        if (!amount.currency().equals(this.currency))
            throw new IllegalArgumentException("Transfer currency does not match account currency");
        this.balance = this.balance.add(amount);
        return Transaction.create(this.id, TransactionType.TRANSFER_IN, amount, "Transfer in from account " + sourceAccountId);
    }

    // ── Guard ─────────────────────────────────────────────────────────────

    private void requireActive() {
        if (status == AccountStatus.FROZEN)
            throw new IllegalStateException("Account is frozen");
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Account is closed");
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public AccountId getId() { return id; }
    public CustomerId getOwnerId() { return ownerId; }
    public Currency getCurrency() { return currency; }
    public Money getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
}
