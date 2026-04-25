package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class TimeDepositAccount extends Account {

    private final Money principal;
    private final LocalDate openedOn;
    private final LocalDate maturityDate;
    private final BigDecimal annualInterestRate;
    private boolean matured;

    public TimeDepositAccount(AccountId id, CustomerId ownerId, Currency currency,
                              Money balance, AccountStatus status,
                              Money principal, LocalDate openedOn, LocalDate maturityDate,
                              BigDecimal annualInterestRate, boolean matured) {
        super(id, ownerId, currency, balance, status);
        if (!principal.currency().equals(currency))
            throw new IllegalArgumentException("Principal currency must match account currency");
        if (principal.amount().signum() <= 0)
            throw new IllegalArgumentException("Principal must be positive");
        if (annualInterestRate == null || annualInterestRate.signum() < 0)
            throw new IllegalArgumentException("Annual interest rate must be non-negative");
        if (openedOn == null || maturityDate == null)
            throw new IllegalArgumentException("Opened-on and maturity dates are required");
        if (!maturityDate.isAfter(openedOn))
            throw new IllegalArgumentException("Maturity date must be after opened-on date");
        this.principal = principal;
        this.openedOn = openedOn;
        this.maturityDate = maturityDate;
        this.annualInterestRate = annualInterestRate;
        this.matured = matured;
    }

    public static TimeDepositAccount open(CustomerId ownerId, Currency currency,
                                          Money principal, LocalDate openedOn,
                                          LocalDate maturityDate, BigDecimal annualInterestRate) {
        return new TimeDepositAccount(
                AccountId.generate(), ownerId, currency,
                principal, AccountStatus.ACTIVE,
                principal, openedOn, maturityDate, annualInterestRate, false);
    }

    @Override
    public AccountType type() { return AccountType.TIME_DEPOSIT; }

    public Money getPrincipal() { return principal; }
    public LocalDate getOpenedOn() { return openedOn; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public boolean isMatured() { return matured; }

    @Override
    public Transaction deposit(Money amount) {
        throw new IllegalStateException("Time deposit principal is locked — further deposits are not allowed");
    }

    @Override
    public Transaction withdraw(Money amount) {
        requireActive();
        if (!matured)
            throw new IllegalStateException("Time deposit has not matured");
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
        throw new IllegalStateException("Time deposit accounts do not support transfers");
    }

    public Transaction mature(LocalDate today) {
        // FROZEN accounts can still mature: maturation is a date-driven system action.
        if (state.isTerminal())
            throw new IllegalStateException("Cannot mature a closed account");
        if (matured)
            throw new IllegalStateException("Account is already matured");
        if (today.isBefore(maturityDate))
            throw new IllegalStateException("Maturity date not yet reached");
        long months = ChronoUnit.MONTHS.between(openedOn, maturityDate);
        BigDecimal years = BigDecimal.valueOf(months).divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        // Final rounding to 2 decimal places is applied by Money.multiply.
        Money interest = principal.multiply(annualInterestRate.multiply(years));
        this.balance = this.balance.add(interest);
        this.matured = true;
        return Transaction.create(this.id, TransactionType.INTEREST, interest,
                "Maturity interest credit");
    }
}
