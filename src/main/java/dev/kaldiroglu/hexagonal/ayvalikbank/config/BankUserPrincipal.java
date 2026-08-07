package dev.kaldiroglu.hexagonal.ayvalikbank.config;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * The authenticated principal, carrying the caller's {@link CustomerId}.
 *
 * <p>Spring Security identifies users by username — here, the email address. Authorization rules
 * need the {@code CustomerId} instead, and resolving email to id per request would mean a database
 * query on every authorized call to recover something login already knew:
 * {@link BankUserDetailsService} loads the whole {@code Customer} in order to read its password
 * hash, then previously discarded everything except the email and the role. Carrying the id on the
 * principal costs nothing and saves that query.
 *
 * <p>Controllers read it with {@code @AuthenticationPrincipal BankUserPrincipal caller}. Note that
 * {@code @WithMockUser} in tests produces a plain {@link User}, not this type, so any test covering
 * an endpoint that reads the caller must use the {@code @WithBankUser} fixture instead.
 */
public class BankUserPrincipal extends User {

    private final transient CustomerId customerId;

    public BankUserPrincipal(CustomerId customerId, String email, String passwordHash,
                             Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, authorities);
        this.customerId = customerId;
    }

    public CustomerId customerId() {
        return customerId;
    }
}
