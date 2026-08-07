package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * The account product's own rules forbid the operation — a time deposit's principal is locked, it
 * has not matured yet, or a month's interest has already been accrued.
 *
 * <p>Distinct from {@link AccountNotActiveException}: the account may be perfectly active and the
 * operation still be meaningless for this particular product. Keeping the two apart is what lets a
 * frozen account and a locked time deposit report different things.
 *
 * <p>Translated to {@code InvalidAccountOperationException} → HTTP 422.
 */
public final class OperationNotPermittedException extends AccountRuleViolation {
    public OperationNotPermittedException(String message) {
        super(message);
    }
}
