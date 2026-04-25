package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

public final class SavingsAccount extends Account {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private final BigDecimal annualInterestRate;
    private LocalDate lastAccrualDate;

    public SavingsAccount(AccountId id, CustomerId ownerId, Currency currency,
                          Money balance, AccountStatus status,
                          BigDecimal annualInterestRate, LocalDate lastAccrualDate) {
        super(id, ownerId, currency, balance, status);
        if (annualInterestRate == null || annualInterestRate.signum() < 0)
            throw new IllegalArgumentException("Annual interest rate must be non-negative");
        this.annualInterestRate = annualInterestRate;
        this.lastAccrualDate = lastAccrualDate;
    }

    public static SavingsAccount open(CustomerId ownerId, Currency currency, BigDecimal annualInterestRate) {
        return new SavingsAccount(
                AccountId.generate(), ownerId, currency,
                Money.zero(currency), AccountStatus.ACTIVE,
                annualInterestRate, null);
    }

    @Override
    public AccountType type() { return AccountType.SAVINGS; }

    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public LocalDate getLastAccrualDate() { return lastAccrualDate; }

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
        if (!this.balance.isGreaterThanOrEqualTo(amount))
            throw new IllegalArgumentException("Insufficient funds");
        this.balance = this.balance.subtract(amount);
        return Transaction.create(this.id, TransactionType.WITHDRAWAL, amount, "Withdrawal");
    }

    @Override
    public Transaction transferOut(Money amount, Money fee, String targetAccountId) {
        requireActive();
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Transfer amount cannot be negative");
        Money totalDebit = fee.isZero() ? amount : amount.add(fee);
        if (!this.balance.isGreaterThanOrEqualTo(totalDebit))
            throw new IllegalArgumentException("Insufficient funds for transfer including fee");
        this.balance = this.balance.subtract(totalDebit);
        String desc = "Transfer out to account " + targetAccountId +
                (fee.isZero() ? "" : " (fee: " + fee + ")");
        return Transaction.create(this.id, TransactionType.TRANSFER_OUT, amount, desc);
    }

    public Transaction accrueInterest(YearMonth month) {
        if (state.isTerminal())
            throw new IllegalStateException("Cannot accrue interest on a closed account");
        LocalDate firstOfNextMonth = month.plusMonths(1).atDay(1);
        if (lastAccrualDate != null && !firstOfNextMonth.isAfter(lastAccrualDate))
            throw new IllegalStateException("Interest already accrued for or after " + month);
        BigDecimal monthlyRate = annualInterestRate.divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP);
        Money interest = this.balance.multiply(monthlyRate);
        this.balance = this.balance.add(interest);
        this.lastAccrualDate = firstOfNextMonth;
        return Transaction.create(this.id, TransactionType.INTEREST, interest,
                "Interest accrual for " + month);
    }
}
