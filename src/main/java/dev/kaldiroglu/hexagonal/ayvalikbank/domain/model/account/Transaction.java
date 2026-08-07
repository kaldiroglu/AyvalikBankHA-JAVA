package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import java.time.LocalDateTime;

/**
 * Immutable record of one financial event on an {@link Account}.
 *
 * <p>Transactions are append-only — a {@code Transaction} object, once created, is never modified.
 * Editing the past is not how a ledger works; corrections are made with compensating transactions.
 *
 * <p>Each {@link Account} mutating operation ({@link Account#deposit}, {@link Account#withdraw},
 * {@link Account#transferOut}, {@link Account#transferIn}, savings accrual, time-deposit maturation)
 * returns one of these so the application service can persist it via
 * {@code TransactionRepositoryPort}.
 */
public class Transaction {
    private final TransactionId id;
    private final AccountId accountId;
    private final TransactionType type;
    private final Money amount;
    private final LocalDateTime timestamp;
    private final String description;

    public Transaction(TransactionId id, AccountId accountId, TransactionType type,
                       Money amount, LocalDateTime timestamp, String description) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp;
        this.description = description;
    }

    public static Transaction create(AccountId accountId, TransactionType type, Money amount, String description) {
        return new Transaction(TransactionId.generate(), accountId, type, amount, LocalDateTime.now(), description);
    }

    public TransactionId getId() { return id; }
    public AccountId getAccountId() { return accountId; }
    public TransactionType getType() { return type; }
    public Money getAmount() { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getDescription() { return description; }
}
