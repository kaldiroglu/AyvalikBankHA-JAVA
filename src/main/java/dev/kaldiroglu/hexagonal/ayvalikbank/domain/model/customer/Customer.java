package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root of the Customer aggregate.
 *
 * <p>Represents a person or legal entity with a relationship to the bank — they own
 * {@link dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Account accounts}, log in
 * via Basic Auth using their {@link #email} and current {@link Password}, and are placed in a
 * {@link CustomerTier} that governs their fees and per-transaction limits.
 *
 * <h2>Password history (reuse prevention)</h2>
 * The aggregate keeps the {@link #currentPassword} plus up to {@value #PASSWORD_HISTORY_SIZE}
 * previous {@link Password} hashes. When the customer changes their password, the application
 * service consults {@link #getAllPasswordsForReuseCheck()} via the {@code PasswordHasherPort} to
 * reject reuse of any of the recent hashes. The aggregate itself owns only the rotation
 * mechanics (push current → history\[0], drop the oldest if size exceeds the cap); format
 * validation lives in {@code PasswordValidationService} and reuse-checking lives in the
 * application service (because it needs the outbound port).
 */
public class Customer {
    private static final int PASSWORD_HISTORY_SIZE = 3;

    private final CustomerId id;
    private String name;
    private final String email;
    private String role;
    private CustomerTier tier;
    private Password currentPassword;
    private final List<Password> passwordHistory;

    public Customer(CustomerId id, String name, String email, String role, CustomerTier tier,
                    Password currentPassword, List<Password> passwordHistory) {
        if (tier == null) throw new IllegalArgumentException("Tier must not be null");
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.tier = tier;
        this.currentPassword = currentPassword;
        this.passwordHistory = new ArrayList<>(passwordHistory);
    }

    public static Customer create(String name, String email, Password initialPassword) {
        return new Customer(CustomerId.generate(), name, email, "CUSTOMER", CustomerTier.STANDARD,
                initialPassword, new ArrayList<>());
    }

    /**
     * Replaces the current password. The caller (application service) is responsible
     * for validating format and ensuring the new hash is not reused.
     */
    public void changePassword(Password newPassword) {
        passwordHistory.add(0, this.currentPassword);
        if (passwordHistory.size() > PASSWORD_HISTORY_SIZE) {
            passwordHistory.remove(passwordHistory.size() - 1);
        }
        this.currentPassword = newPassword;
    }

    public void changeTier(CustomerTier newTier) {
        if (newTier == null) throw new IllegalArgumentException("Tier must not be null");
        this.tier = newTier;
    }

    /** Returns current + history — all hashes to check against for reuse. */
    public List<Password> getAllPasswordsForReuseCheck() {
        List<Password> all = new ArrayList<>();
        all.add(currentPassword);
        all.addAll(passwordHistory);
        return Collections.unmodifiableList(all);
    }

    public CustomerId getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public CustomerTier getTier() { return tier; }
    public Password getCurrentPassword() { return currentPassword; }
    public List<Password> getPasswordHistory() { return Collections.unmodifiableList(passwordHistory); }
}
