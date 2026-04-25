package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import java.util.UUID;

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
