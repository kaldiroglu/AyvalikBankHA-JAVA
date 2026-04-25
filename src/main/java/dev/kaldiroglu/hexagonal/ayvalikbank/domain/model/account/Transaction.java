package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import java.time.LocalDateTime;

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
