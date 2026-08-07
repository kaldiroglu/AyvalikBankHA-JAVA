package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import java.math.BigDecimal;

/**
 * A <b>strictly positive</b> monetary amount — the magnitude of a requested money movement.
 *
 * <p>This type exists because {@link Money} deliberately allows negative values: a
 * {@link CheckingAccount} balance goes negative under overdraft, and {@link Money#negate()} builds
 * the overdraft lower bound. {@code Money} therefore cannot enforce positivity. Before this type
 * existed, every method taking an amount re-asserted the rule by hand — the same guard written out
 * seven times across the three account subclasses.
 *
 * <p>Making the constraint a property of the <i>type</i> means it is checked once, at construction,
 * and every method downstream can simply trust it: an amount that is not positive cannot be built,
 * so it cannot be passed. Impossible states become unrepresentable.
 *
 * <p><b>Zero is rejected as well as negative.</b> Direction is already carried by which operation
 * was called ({@link Account#deposit} versus {@link Account#withdraw}), so a signed amount is
 * meaningless — and a zero-value transfer would write two ledger rows recording no movement of money.
 *
 * <p>Wraps {@link Money} rather than re-implementing it, so all arithmetic and the 2-decimal
 * HALF_UP scaling stay in one place. Note the ordering: scaling happens first, so an amount of
 * {@code 0.001} scales to {@code 0.00} and is then rejected.
 *
 * <h2>What deliberately keeps using Money</h2>
 * <ul>
 *   <li>{@link Account#getBalance()} — signed by design; negative is a real overdraft position.
 *   <li>The {@code fee} argument of {@link Account#transferOut} — legitimately zero for
 *       same-customer transfers.
 *   <li>{@link Transaction#getAmount()} — a zero-interest accrual on a zero balance is a legal
 *       ledger entry.
 * </ul>
 *
 * <p>The type covers <i>requests to move money</i>, not <i>records of money having moved</i>.
 * That boundary is why this refactoring does not reach the persistence layer at all.
 *
 * @see Money for the signed value object this wraps
 */
public record TransactionAmount(Money value) {

    public TransactionAmount {
        if (value == null)
            throw new IllegalArgumentException("Transaction amount must not be null");
        if (value.amount().signum() <= 0)
            throw new IllegalArgumentException("Transaction amount must be positive, was " + value);
    }

    public static TransactionAmount of(Money money) {
        return new TransactionAmount(money);
    }

    public static TransactionAmount of(BigDecimal amount, Currency currency) {
        return new TransactionAmount(Money.of(amount, currency));
    }

    public static TransactionAmount of(double amount, Currency currency) {
        return new TransactionAmount(Money.of(amount, currency));
    }

    /** The underlying {@link Money}, for arithmetic against balances. */
    public Money asMoney() { return value; }

    public Currency currency() { return value.currency(); }

    @Override
    public String toString() { return value.toString(); }
}
