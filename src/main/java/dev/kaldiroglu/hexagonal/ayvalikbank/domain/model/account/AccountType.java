package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * Discriminator returned by {@link Account#type()} so the persistence and REST layers can
 * recognise which subtype an account is. Mirrors the {@link Account} sealed hierarchy.
 *
 * <ul>
 *   <li>{@link #CHECKING}     — see {@link CheckingAccount}.
 *   <li>{@link #SAVINGS}      — see {@link SavingsAccount}.
 *   <li>{@link #TIME_DEPOSIT} — see {@link TimeDepositAccount}.
 * </ul>
 */
public enum AccountType {
    CHECKING,
    SAVINGS,
    TIME_DEPOSIT
}
