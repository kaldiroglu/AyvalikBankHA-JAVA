package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

public final class CheckingAccount extends Account {

    private final Money overdraftLimit;

    public CheckingAccount(AccountId id, CustomerId ownerId, Currency currency,
                           Money balance, AccountStatus status, Money overdraftLimit) {
        super(id, ownerId, currency, balance, status);
        if (!overdraftLimit.currency().equals(currency))
            throw new IllegalArgumentException("Overdraft limit currency must match account currency");
        if (overdraftLimit.isNegative())
            throw new IllegalArgumentException("Overdraft limit cannot be negative");
        this.overdraftLimit = overdraftLimit;
    }

    public static CheckingAccount open(CustomerId ownerId, Currency currency) {
        return open(ownerId, currency, Money.zero(currency));
    }

    public static CheckingAccount open(CustomerId ownerId, Currency currency, Money overdraftLimit) {
        return new CheckingAccount(
                AccountId.generate(), ownerId, currency,
                Money.zero(currency), AccountStatus.ACTIVE, overdraftLimit);
    }

    @Override
    public AccountType type() { return AccountType.CHECKING; }

    public Money getOverdraftLimit() { return overdraftLimit; }

    @Override
    public Transaction deposit(Money amount) {
        requireActive();
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Deposit amount cannot be negative");
        this.balance = this.balance.add(amount);
        return Transaction.create(this.id, TransactionType.DEPOSIT, amount, "Deposit");
    }

    @Override
    public Transaction withdraw(Money amount) {
        requireActive();
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Withdrawal amount cannot be negative");
        Money projected = this.balance.subtract(amount);
        Money lowerBound = overdraftLimit.negate();
        if (projected.amount().compareTo(lowerBound.amount()) < 0) {
            if (overdraftLimit.isZero())
                throw new IllegalArgumentException("Insufficient funds");
            throw new IllegalArgumentException("Withdrawal exceeds overdraft limit");
        }
        this.balance = projected;
        return Transaction.create(this.id, TransactionType.WITHDRAWAL, amount, "Withdrawal");
    }

    @Override
    public Transaction transferOut(Money amount, Money fee, String targetAccountId) {
        requireActive();
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Transfer amount cannot be negative");
        Money totalDebit = fee.isZero() ? amount : amount.add(fee);
        Money projected = this.balance.subtract(totalDebit);
        Money lowerBound = overdraftLimit.negate();
        if (projected.amount().compareTo(lowerBound.amount()) < 0) {
            if (overdraftLimit.isZero())
                throw new IllegalArgumentException("Insufficient funds for transfer including fee");
            throw new IllegalArgumentException("Transfer exceeds overdraft limit");
        }
        this.balance = projected;
        String desc = "Transfer out to account " + targetAccountId +
                (fee.isZero() ? "" : " (fee: " + fee + ")");
        return Transaction.create(this.id, TransactionType.TRANSFER_OUT, amount, desc);
    }
}
