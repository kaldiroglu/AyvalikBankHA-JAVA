package dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.customer;

/**
 * Pure domain service — no framework dependencies.
 * Validates raw (plain-text) password format rules.
 */
public class PasswordValidationService {

    public void validate(String rawPassword) {
        if (rawPassword == null)
            throw new IllegalArgumentException("Password must not be null");

        if (rawPassword.length() < 8 || rawPassword.length() > 16)
            throw new IllegalArgumentException("Password must be between 8 and 16 characters");

        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        String specialChars = "!@#$%^&*()_+-=[]{}|;':\",./<>?~`\\";

        for (char c : rawPassword.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (specialChars.indexOf(c) >= 0) hasSpecial = true;
        }

        if (!hasUpper)   throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        if (!hasLower)   throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        if (!hasDigit)   throw new IllegalArgumentException("Password must contain at least one digit");
        if (!hasSpecial) throw new IllegalArgumentException("Password must contain at least one special character");
    }
}
