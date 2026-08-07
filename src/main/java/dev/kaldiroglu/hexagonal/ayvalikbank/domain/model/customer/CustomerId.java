package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer;

import java.util.UUID;

/**
 * Strongly-typed identifier for a {@link Customer}. Wraps a {@link UUID} so that customer IDs,
 * account IDs, and transaction IDs cannot be mixed up at compile time.
 */
public record CustomerId(UUID value) {
    public CustomerId {
        if (value == null) throw new IllegalArgumentException("CustomerId value must not be null");
    }

    public static CustomerId generate() {
        return new CustomerId(UUID.randomUUID());
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }

    public static CustomerId of(String value) {
        return new CustomerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
