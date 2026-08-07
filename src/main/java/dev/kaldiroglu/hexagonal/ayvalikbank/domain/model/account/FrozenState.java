package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * The account is on hold — typically by an admin acting on a fraud alert, sanctions hit,
 * or compliance hold. Customer-initiated operations are blocked, but system-driven actions
 * (savings interest accrual, time-deposit maturation) may still run.
 *
 * <p>Valid transitions: <b>{@code → ActiveState}</b> via {@link #unfreeze()},
 *                       <b>{@code → ClosedState}</b> via {@link #close()}.
 * Invalid: {@link #freeze()} (already frozen).
 * Stateless singleton — there is only one instance, {@link #INSTANCE}.
 */
public final class FrozenState implements AccountState {

    public static final FrozenState INSTANCE = new FrozenState();

    private FrozenState() {}

    @Override
    public AccountStatus status() { return AccountStatus.FROZEN; }

    @Override
    public AccountState freeze() {
        throw new AccountNotActiveException("Account is already frozen");
    }

    @Override
    public AccountState unfreeze() { return ActiveState.INSTANCE; }

    @Override
    public AccountState close() { return ClosedState.INSTANCE; }

    @Override
    public void requireOperable() {
        throw new AccountNotActiveException("Account is frozen");
    }

    @Override
    public boolean isTerminal() { return false; }
}
