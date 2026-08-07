package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * Runtime <b>State pattern</b> for an {@link Account}'s lifecycle status. Each concrete state
 * is a stateless singleton ({@link ActiveState#INSTANCE}, {@link FrozenState#INSTANCE},
 * {@link ClosedState#INSTANCE}) that owns the rules for which transitions are valid from it
 * and what operations are allowed in it.
 *
 * <p>This replaces an {@code if (status == FROZEN) ... else if (status == CLOSED) ...} ladder
 * scattered through the service layer (the LA-style approach). Each state implements
 * {@link #freeze()} / {@link #unfreeze()} / {@link #close()} either by returning the next state
 * or by throwing {@link IllegalStateException} — keeping invalid transitions impossible at runtime
 * without leaking the rules into callers.
 *
 * <p>The {@link AccountStatus} enum remains the boundary representation (persistence, REST);
 * {@link #of(AccountStatus)} / {@link #status()} translate at construction and serialization time.
 */
public sealed interface AccountState permits ActiveState, FrozenState, ClosedState {

    /** Boundary representation of this state — used by persistence and REST. */
    AccountStatus status();

    /**
     * @return the next state after a freeze
     * @throws IllegalStateException if freezing is invalid in the current state
     */
    AccountState freeze();

    /**
     * @return the next state after an unfreeze
     * @throws IllegalStateException if unfreezing is invalid in the current state (e.g. not currently frozen)
     */
    AccountState unfreeze();

    /**
     * @return the next state after a close
     * @throws IllegalStateException if closing is invalid (e.g. already closed)
     */
    AccountState close();

    /**
     * Guard called by {@link Account} before any customer operation.
     *
     * @throws IllegalStateException if the account is not currently operable (frozen or closed)
     */
    void requireOperable();

    /**
     * @return {@code true} only for {@link ClosedState} — terminal states refuse all transitions.
     */
    boolean isTerminal();

    /** Maps a persistence/boundary {@link AccountStatus} back to its runtime {@link AccountState} singleton. */
    static AccountState of(AccountStatus status) {
        return switch (status) {
            case ACTIVE -> ActiveState.INSTANCE;
            case FROZEN -> FrozenState.INSTANCE;
            case CLOSED -> ClosedState.INSTANCE;
        };
    }
}
