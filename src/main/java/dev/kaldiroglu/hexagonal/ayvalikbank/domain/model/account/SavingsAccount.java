package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A <b>savings account</b> — a liquid, interest-bearing account.
 *
 * <p>The customer can deposit and withdraw freely, but unlike {@link CheckingAccount} the balance
 * <b>cannot go negative</b>: there is no overdraft. In return, the bank pays interest at a
 * contractually fixed {@link #annualInterestRate annual rate}.
 *
 * <p>Interest is credited monthly via {@link #accrueInterest(YearMonth)}, which is normally
 * driven by an admin-triggered batch (see {@code AccrueInterestUseCase}). The monthly amount
 * is {@code balance × annualInterestRate / 12}, rounded to two decimal places by {@link Money}.
 * The {@link #lastAccrualDate} field guarantees idempotency — the same month cannot be accrued twice.
 *
 * <p>Comparison to the other products:
 * <ul>
 *   <li>{@link CheckingAccount}    — free movement, but no interest, and overdraft is allowed.
 *   <li>{@link TimeDepositAccount} — higher interest, but the principal is locked until maturity.
 * </ul>
 */
public final class SavingsAccount extends Account {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    /**
     * Contractually fixed annual interest rate as a decimal — e.g. {@code 0.05} for 5% APR.
     * Used to compute the monthly accrual: {@code balance × rate / 12}.
     */
    private final BigDecimal annualInterestRate;

    /**
     * The first day of the month <i>after</i> the most recently accrued month.
     * Set by {@link #accrueInterest(YearMonth)} so the same month can never be accrued twice.
     * {@code null} until the first accrual.
     */
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
    public Transaction deposit(TransactionAmount amount) {
        requireActive();
        requireSameCurrency(amount);
        this.balance = this.balance.add(amount.asMoney());
        return Transaction.create(this.id, TransactionType.DEPOSIT, amount.asMoney(), "Deposit");
    }

    @Override
    public Transaction withdraw(TransactionAmount amount) {
        requireActive();
        requireSameCurrency(amount);
        if (!this.balance.isGreaterThanOrEqualTo(amount.asMoney()))
            throw new InsufficientBalanceException("Insufficient funds");
        this.balance = this.balance.subtract(amount.asMoney());
        return Transaction.create(this.id, TransactionType.WITHDRAWAL, amount.asMoney(), "Withdrawal");
    }

    @Override
    public Transaction transferOut(TransactionAmount amount, Money fee, String targetAccountId) {
        requireActive();
        requireSameCurrency(amount);
        Money totalDebit = fee.isZero() ? amount.asMoney() : amount.asMoney().add(fee);
        if (!this.balance.isGreaterThanOrEqualTo(totalDebit))
            throw new InsufficientBalanceException("Insufficient funds for transfer including fee");
        this.balance = this.balance.subtract(totalDebit);
        String desc = "Transfer out to account " + targetAccountId +
                (fee.isZero() ? "" : " (fee: " + fee + ")");
        return Transaction.create(this.id, TransactionType.TRANSFER_OUT, amount.asMoney(), desc);
    }

    /**
     * Credits one month's worth of interest to the balance and emits an
     * {@link TransactionType#INTEREST} transaction. Idempotent per month: the same {@code month}
     * (or any earlier month) cannot be accrued twice.
     *
     * <p>Frozen accounts may still accrue — accrual is a system action, not a customer one.
     * Closed accounts may not.
     *
     * @param month the calendar month being accrued (e.g. {@code 2026-04} for April 2026)
     * @throws IllegalStateException if the account is closed, or if the given month has already been accrued
     */
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
