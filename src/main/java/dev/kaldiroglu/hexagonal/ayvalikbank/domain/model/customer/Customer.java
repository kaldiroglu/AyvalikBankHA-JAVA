package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Customer {
    private static final int PASSWORD_HISTORY_SIZE = 3;

    private final CustomerId id;
    private String name;
    private final String email;
    private String role;
    private Password currentPassword;
    private final List<Password> passwordHistory;

    public Customer(CustomerId id, String name, String email, String role,
                    Password currentPassword, List<Password> passwordHistory) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.currentPassword = currentPassword;
        this.passwordHistory = new ArrayList<>(passwordHistory);
    }

    public static Customer create(String name, String email, Password initialPassword) {
        return new Customer(CustomerId.generate(), name, email, "CUSTOMER", initialPassword, new ArrayList<>());
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
    public Password getCurrentPassword() { return currentPassword; }
    public List<Password> getPasswordHistory() { return Collections.unmodifiableList(passwordHistory); }
}
