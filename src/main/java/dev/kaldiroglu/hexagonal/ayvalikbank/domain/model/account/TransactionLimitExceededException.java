package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * The requested amount exceeds the per-transaction cap carried by the customer's tier.
 *
 * <p>Thrown by {@code TransferDomainService}, which lives in {@code domain/service/account}. Only
 * the <i>permitted subtypes</i> of a sealed hierarchy must share a package with their base — the
 * code that throws them may live anywhere.
 *
 * <p>Translated to {@code LimitExceededException} → HTTP 422.
 */
public final class TransactionLimitExceededException extends AccountRuleViolation {
    public TransactionLimitExceededException(String message) {
        super(message);
    }
}
