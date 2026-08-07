package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * The "happy path" account state — all customer operations are permitted.
 *
 * <p>Valid transitions: <b>{@code → FrozenState}</b> via {@link #freeze()},
 *                       <b>{@code → ClosedState}</b> via {@link #close()}.
 * Invalid: {@link #unfreeze()} (already active).
 * Stateless singleton — there is only one instance, {@link #INSTANCE}.
 */
public final class ActiveState implements AccountState {

    public static final ActiveState INSTANCE = new ActiveState();

    private ActiveState() {}

    @Override
    public AccountStatus status() { return AccountStatus.ACTIVE; }

    @Override
    public AccountState freeze() { return FrozenState.INSTANCE; }

    @Override
    public AccountState unfreeze() {
        throw new IllegalStateException("Account is not frozen");
    }

    @Override
    public AccountState close() { return ClosedState.INSTANCE; }

    @Override
    public void requireOperable() { /* active accounts are operable */ }

    @Override
    public boolean isTerminal() { return false; }
}
