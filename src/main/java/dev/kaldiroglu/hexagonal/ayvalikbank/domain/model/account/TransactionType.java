package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * Classifies what produced a {@link Transaction} record on an account.
 *
 * <ul>
 *   <li>{@link #DEPOSIT}      — customer or third party credited the account.
 *   <li>{@link #WITHDRAWAL}   — customer debited the account (cash out, card spend, etc.).
 *   <li>{@link #TRANSFER_OUT} — debit leg of a transfer to another account.
 *   <li>{@link #TRANSFER_IN}  — credit leg of a transfer from another account.
 *   <li>{@link #INTEREST}     — system-credited interest (savings monthly accrual or
 *                               time-deposit maturity payout).
 * </ul>
 *
 * <p>A single transfer always produces <b>two</b> transaction records — one
 * {@code TRANSFER_OUT} on the source account and one {@code TRANSFER_IN} on the target.
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_OUT,
    TRANSFER_IN,
    INTEREST
}
