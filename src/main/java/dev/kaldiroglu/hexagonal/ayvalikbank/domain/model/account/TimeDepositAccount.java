package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A <b>time deposit</b> account — also known as a <i>term deposit</i> or, in the U.S.,
 * a <i>certificate of deposit (CD)</i>.
 *
 * <p>The customer locks a fixed amount of money (the <b>principal</b>) for a fixed term,
 * from the {@link #openedOn open date} to the {@link #maturityDate maturity date}.
 * In exchange the bank pays a higher, contractually fixed {@link #annualInterestRate annual interest rate}
 * than a regular savings account. The deal is binding on both sides:
 *
 * <ul>
 *   <li>{@link #deposit(Money) Additional deposits are not allowed} — the principal is locked at open.
 *   <li>{@link #withdraw(Money) Withdrawals are not allowed before maturity} — the funds are illiquid for the term.
 *   <li>{@link #transferOut(Money, Money, String) Outbound transfers are not allowed} — for the same reason.
 *   <li>On or after the maturity date the account must be {@link #mature(LocalDate) matured},
 *       which credits the accrued interest to the balance and unlocks withdrawal.
 * </ul>
 *
 * <p>Once matured, the account behaves like a regular cash balance: {@link #withdraw(Money)}
 * is permitted, but deposits and outbound transfers remain rejected (the product itself
 * has reached the end of its life cycle).
 *
 * <h2>Liskov contract</h2>
 * {@code TimeDepositAccount} <b>does not</b> inherit a "default" deposit / withdraw /
 * transferOut implementation from {@link Account}; those methods are abstract on the base
 * class precisely so that this subtype can refuse them honestly without strengthening
 * preconditions. Callers of {@code Account} must already be prepared to handle
 * {@link IllegalStateException} from any of these operations.
 *
 * @see SavingsAccount  for a liquid interest-bearing account (no maturity, partial withdrawals allowed)
 * @see CheckingAccount for an everyday transactional account (overdraft, free movement)
 */
public final class TimeDepositAccount extends Account {

    /** The locked-in principal at the time the deposit was opened. Never changes. */
    private final Money principal;

    /** Date the deposit was opened — start of the interest term. */
    private final LocalDate openedOn;

    /**
     * Date the deposit term ends. {@link #mature(LocalDate)} can only be called on or after this date.
     * Withdrawals are forbidden before this date.
     */
    private final LocalDate maturityDate;

    /**
     * Contractually fixed annual interest rate (as a decimal — e.g. {@code 0.05} for 5%).
     * Total interest credited at maturity is {@code principal × annualInterestRate × (months / 12)}.
     */
    private final BigDecimal annualInterestRate;

    /**
     * {@code false} until {@link #mature(LocalDate)} runs. Once {@code true}, the interest has been
     * credited and the customer may withdraw the (now-unlocked) balance.
     */
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

    /**
     * Opens a brand-new time deposit. The opening balance is the full principal —
     * the customer transfers the principal in at open time and cannot touch it until maturity.
     */
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

    /**
     * Always rejected. The principal of a time deposit is locked at open and cannot be topped up.
     * (To deposit more cash, the customer must open a new time deposit.)
     *
     * @throws IllegalStateException always
     */
    @Override
    public Transaction deposit(Money amount) {
        throw new IllegalStateException("Time deposit principal is locked — further deposits are not allowed");
    }

    /**
     * Withdrawal is rejected until {@link #mature(LocalDate)} has run. After maturity it
     * behaves like a regular withdrawal: the amount is debited from the (now-unlocked) balance.
     *
     * @throws IllegalStateException if the account is not active or has not matured
     */
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

    /**
     * Always rejected. Time deposits are not transactional accounts — the customer cannot
     * push money out of them. To move funds elsewhere, the deposit must first be matured,
     * then the matured balance can be withdrawn.
     *
     * @throws IllegalStateException always
     */
    @Override
    public Transaction transferOut(Money amount, Money fee, String targetAccountId) {
        throw new IllegalStateException("Time deposit accounts do not support transfers");
    }

    /**
     * Closes the term of the deposit and credits the accrued simple interest to the balance.
     * After this call, {@link #withdraw(Money)} is permitted.
     *
     * <p>Interest formula: {@code principal × annualInterestRate × (months between openedOn and maturityDate / 12)}.
     * Final rounding to 2 decimal places is applied by {@link Money#multiply(BigDecimal)}.
     *
     * <p>A frozen account may still mature — maturation is a date-driven system action, not a
     * customer-initiated one. A closed account cannot mature.
     *
     * @param today the current date — used only to verify the maturity date has been reached
     * @throws IllegalStateException if the account is closed, already matured, or {@code today} is before {@link #maturityDate}
     */
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
