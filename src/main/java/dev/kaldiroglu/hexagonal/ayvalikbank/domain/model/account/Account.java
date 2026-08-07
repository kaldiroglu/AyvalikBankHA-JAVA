package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

/**
 * Aggregate root of the Account aggregate.
 *
 * <p>Models a customer's bank account. The system supports three product types — each represented
 * by a dedicated subclass:
 * <ul>
 *   <li>{@link CheckingAccount}    — everyday transactional account; supports overdraft.
 *   <li>{@link SavingsAccount}     — interest-bearing account; partial withdrawals allowed; rejects overdraft.
 *   <li>{@link TimeDepositAccount} — fixed-term locked deposit (a.k.a. CD/term deposit); illiquid until maturity.
 * </ul>
 *
 * <p>The hierarchy is {@code sealed} so that exhaustive {@code switch} / {@code instanceof} pattern matching
 * over the three subtypes is safe at compile time, and so that adding a new account product requires a
 * deliberate extension of {@code permits}.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>The {@code balance} currency always matches the account {@link #currency}.
 *   <li>Status transitions go through the {@link AccountState State pattern} —
 *       {@code Active ↔ Frozen} and either may move to {@code Closed}, but {@code Closed} is terminal.
 *   <li>Operations that mutate balance must call {@link #requireActive()} (delegated to the state)
 *       before doing anything; subtypes additionally enforce per-product rules.
 * </ul>
 *
 * <h2>Why deposit / withdraw / transferOut are abstract</h2>
 * Each product has fundamentally different rules — a time deposit refuses deposits, a savings
 * account refuses overdraft, a checking account allows balance to go negative. Declaring these
 * abstract means subclasses can express their own contracts honestly without strengthening the
 * preconditions of an inherited implementation (Liskov-safe).
 */
public sealed abstract class Account
        permits CheckingAccount, SavingsAccount, TimeDepositAccount {

    protected final AccountId id;
    protected final CustomerId ownerId;
    protected final Currency currency;
    protected Money balance;
    protected AccountState state;

    protected Account(AccountId id, CustomerId ownerId, Currency currency, Money balance, AccountStatus status) {
        if (!balance.currency().equals(currency))
            throw new IllegalArgumentException("Balance currency must match account currency");
        this.id = id;
        this.ownerId = ownerId;
        this.currency = currency;
        this.balance = balance;
        this.state = AccountState.of(status);
    }

    /** Discriminator returned to the persistence and REST boundaries. */
    public abstract AccountType type();

    // ── Status transitions (delegated to the State) ───────────────────────

    /**
     * Freezes the account: customer-initiated operations (deposit/withdraw/transfer)
     * are blocked, but system actions like accrual and maturity may still run.
     *
     * @throws IllegalStateException if the current state cannot transition to FROZEN
     */
    public final void freeze() { this.state = state.freeze(); }

    /**
     * Returns a frozen account to ACTIVE. Only valid from FROZEN.
     *
     * @throws IllegalStateException if the account is not currently frozen
     */
    public final void unfreeze() { this.state = state.unfreeze(); }

    /**
     * Closes the account permanently. {@code CLOSED} is a terminal state — no transitions out.
     *
     * @throws IllegalStateException if the account is already closed
     */
    public final void close() { this.state = state.close(); }

    // ── Operations: each subtype overrides ────────────────────────────────

    /**
     * Credits {@code amount} to the balance and returns a {@link TransactionType#DEPOSIT} record.
     * Subtypes define which preconditions apply; e.g. {@link TimeDepositAccount#deposit(TransactionAmount)}
     * always rejects.
     *
     * @throws IllegalStateException    if the subtype refuses (e.g. account not active, product locked)
     * @throws IllegalArgumentException if the currency does not match (a negative amount cannot be constructed)
     */
    public abstract Transaction deposit(TransactionAmount amount);

    /**
     * Debits {@code amount} from the balance and returns a {@link TransactionType#WITHDRAWAL} record.
     * Subtypes define overdraft and maturity rules. Callers must be ready to handle
     * {@link IllegalStateException} (e.g. {@link TimeDepositAccount} refuses until matured).
     */
    public abstract Transaction withdraw(TransactionAmount amount);

    /**
     * Outbound side of a transfer. Debits {@code amount + fee} from this account
     * and returns a {@link TransactionType#TRANSFER_OUT} record. The matching
     * {@link #transferIn(TransactionAmount, String)} runs on the target account.
     *
     * @param fee             the transfer fee already calculated by {@code TransferDomainService} (may be zero)
     * @param targetAccountId the destination account's id (used only for the transaction description)
     * @throws IllegalStateException if this product does not support outbound transfers
     */
    public abstract Transaction transferOut(TransactionAmount amount, Money fee, String targetAccountId);

    /**
     * Inbound side of a transfer. Credits {@code amount} to this account and records a
     * {@link TransactionType#TRANSFER_IN} transaction. Final because all account types behave
     * identically on the receiving end — the only requirement is that the account is active.
     */
    public final Transaction transferIn(TransactionAmount amount, String sourceAccountId) {
        requireActive();
        requireSameCurrency(amount);
        this.balance = this.balance.add(amount.asMoney());
        return Transaction.create(this.id, TransactionType.TRANSFER_IN, amount.asMoney(),
                "Transfer in from account " + sourceAccountId);
    }

    // ── Guards (visible to subclasses) ────────────────────────────────────

    protected final void requireActive() { state.requireOperable(); }

    protected final void requireSameCurrency(TransactionAmount amount) {
        if (!amount.currency().equals(this.currency))
            throw new IllegalArgumentException("Currency " + amount.currency() + " does not match account currency " + this.currency);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public AccountId getId() { return id; }
    public CustomerId getOwnerId() { return ownerId; }
    public Currency getCurrency() { return currency; }
    public Money getBalance() { return balance; }
    public AccountStatus getStatus() { return state.status(); }
    public AccountState getState() { return state; }
}
