package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * Terminal account state — the account is permanently closed. No further deposits, withdrawals,
 * transfers, accruals, or maturations are allowed; no transitions out exist (any of
 * {@link #freeze()}, {@link #unfreeze()}, {@link #close()} throws).
 *
 * <p>Stateless singleton — there is only one instance, {@link #INSTANCE}.
 */
public final class ClosedState implements AccountState {

    public static final ClosedState INSTANCE = new ClosedState();

    private ClosedState() {}

    @Override
    public AccountStatus status() { return AccountStatus.CLOSED; }

    @Override
    public AccountState freeze() {
        throw new AccountNotActiveException("Cannot freeze a closed account");
    }

    @Override
    public AccountState unfreeze() {
        throw new AccountNotActiveException("Cannot unfreeze a closed account");
    }

    @Override
    public AccountState close() {
        throw new AccountNotActiveException("Account is already closed");
    }

    @Override
    public void requireOperable() {
        throw new AccountNotActiveException("Account is closed");
    }

    @Override
    public boolean isTerminal() { return true; }
}
