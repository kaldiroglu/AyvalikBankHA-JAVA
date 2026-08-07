package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import java.util.UUID;

/**
 * Strongly-typed identifier for an {@link Account}. Wraps a {@link UUID} to prevent accidental
 * cross-type ID swaps (e.g. passing a {@link CustomerId} where an {@code AccountId} is expected
 * compiles fine if both are raw {@code UUID}s — wrapping eliminates that bug class).
 *
 * <p>Generated client-side via {@link #generate()} so the domain controls identity rather than
 * waiting for a database-assigned key.
 */
public record AccountId(UUID value) {
    public AccountId {
        if (value == null) throw new IllegalArgumentException("AccountId value must not be null");
    }

    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId of(String value) {
        return new AccountId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
