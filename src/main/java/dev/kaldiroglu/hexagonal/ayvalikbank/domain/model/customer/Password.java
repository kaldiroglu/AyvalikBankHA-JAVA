package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer;

/**
 * Holds the BCrypt-hashed password value.
 * Format validation of the raw password is done by PasswordValidationService
 * before this record is constructed.
 */
public record Password(String hashedValue) {

    public Password {
        if (hashedValue == null || hashedValue.isBlank())
            throw new IllegalArgumentException("Hashed password value must not be blank");
    }

    public static Password ofHashed(String hashedValue) {
        return new Password(hashedValue);
    }
}
