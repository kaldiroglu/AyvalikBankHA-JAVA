package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * Signals that an account's balance — plus any overdraft allowance — cannot cover a requested debit.
 *
 * <p>Extends {@link IllegalStateException} rather than {@link IllegalArgumentException} because a
 * shortfall is a property of the account's current <i>state</i>, not a defect in the argument: the
 * requested amount is perfectly well-formed, there is simply not enough money behind it. That
 * distinction is what lets {@code AccountApplicationService} translate this into
 * {@code InsufficientFundsException} and, in turn, HTTP 422 (Unprocessable Entity) rather than
 * HTTP 400 (Bad Request).
 *
 * <p>Named {@code InsufficientBalance} rather than {@code InsufficientFunds} to stay distinct from
 * {@code application.exception.InsufficientFundsException}, the outward-facing application-layer
 * exception it is translated into. Two identically-named classes in different layers would make
 * every import ambiguous.
 *
 * <p>One of the four {@link AccountRuleViolation} subtypes. It remains an
 * {@link IllegalStateException} by inheritance, so nothing that caught it before is affected.
 *
 * @see dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InsufficientFundsException
 */
public final class InsufficientBalanceException extends AccountRuleViolation {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
