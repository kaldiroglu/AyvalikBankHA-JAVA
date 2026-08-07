package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Authenticates a test as a real {@code BankUserPrincipal} carrying a {@code CustomerId}.
 *
 * <p>Spring's {@code @WithMockUser} builds a plain {@code User}, so a controller parameter declared
 * {@code @AuthenticationPrincipal BankUserPrincipal} resolves to {@code null} under it. Any test
 * covering an endpoint that reads the caller's identity therefore needs this annotation instead.
 *
 * <p>{@code customerId} must be a compile-time constant — annotation values cannot be computed — so
 * tests declare a {@code static final String} and reuse it rather than calling
 * {@code CustomerId.generate()}. That is a feature here: a controller test needs a <i>stable</i>
 * identity in order to assert the principal was forwarded correctly.
 *
 * <p>Tests that assert role separation on admin routes should keep {@code @WithMockUser} — those
 * requests are rejected by Spring Security before any controller runs, so no principal is needed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithBankUserSecurityContextFactory.class)
public @interface WithBankUser {

    String customerId();

    String email() default "customer@ayvalikbank.dev";

    String role() default "CUSTOMER";
}
