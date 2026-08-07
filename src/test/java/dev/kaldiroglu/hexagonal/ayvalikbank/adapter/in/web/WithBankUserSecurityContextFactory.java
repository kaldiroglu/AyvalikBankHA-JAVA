package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.config.BankUserPrincipal;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;

/**
 * Builds the {@link SecurityContext} behind {@link WithBankUser}, populating it with a real
 * {@link BankUserPrincipal} so {@code @AuthenticationPrincipal} resolves to a non-null value.
 */
public class WithBankUserSecurityContextFactory implements WithSecurityContextFactory<WithBankUser> {

    @Override
    public SecurityContext createSecurityContext(WithBankUser annotation) {
        BankUserPrincipal principal = new BankUserPrincipal(
                CustomerId.of(annotation.customerId()),
                annotation.email(),
                "test-password-hash",
                List.of(new SimpleGrantedAuthority("ROLE_" + annotation.role())));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, principal.getPassword(), principal.getAuthorities()));
        return context;
    }
}
