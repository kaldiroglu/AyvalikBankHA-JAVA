package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * Persistence- and REST-boundary representation of an account's lifecycle status.
 * The runtime equivalent inside the domain is {@link AccountState} — converted at
 * the boundary via {@link AccountState#of(AccountStatus)} / {@link AccountState#status()}.
 *
 * <ul>
 *   <li>{@link #ACTIVE}  — normal operation; all customer actions are allowed.
 *   <li>{@link #FROZEN}  — customer actions blocked (e.g. fraud hold);
 *                         system actions like accrual / maturity may still run.
 *   <li>{@link #CLOSED}  — terminal; no transitions out, no operations allowed.
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
