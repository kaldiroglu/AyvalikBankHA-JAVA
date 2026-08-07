package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * The account's lifecycle state forbids the operation — it is frozen or closed, or the requested
 * transition is invalid from where it currently stands.
 *
 * <p>Thrown by the {@link AccountState} implementations, and by the two system actions that refuse
 * to run on a closed account ({@link SavingsAccount#accrueInterest} and
 * {@link TimeDepositAccount#mature}).
 *
 * <p>Translated to {@code AccountNotOperableException} → HTTP 422.
 */
public final class AccountNotActiveException extends AccountRuleViolation {
    public AccountNotActiveException(String message) {
        super(message);
    }
}
