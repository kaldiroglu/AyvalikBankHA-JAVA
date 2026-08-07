package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import java.util.UUID;

/**
 * Strongly-typed identifier for a {@link Transaction}. Same reasoning as {@link AccountId}:
 * a UUID wrapped in a domain type so that IDs of different aggregates cannot be silently
 * substituted for each other.
 */
public record TransactionId(UUID value) {
    public TransactionId {
        if (value == null) throw new IllegalArgumentException("TransactionId value must not be null");
    }

    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId of(UUID value) {
        return new TransactionId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
