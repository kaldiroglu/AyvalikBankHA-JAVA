package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

public final class ClosedState implements AccountState {

    public static final ClosedState INSTANCE = new ClosedState();

    private ClosedState() {}

    @Override
    public AccountStatus status() { return AccountStatus.CLOSED; }

    @Override
    public AccountState freeze() {
        throw new IllegalStateException("Cannot freeze a closed account");
    }

    @Override
    public AccountState unfreeze() {
        throw new IllegalStateException("Cannot unfreeze a closed account");
    }

    @Override
    public AccountState close() {
        throw new IllegalStateException("Account is already closed");
    }

    @Override
    public void requireOperable() {
        throw new IllegalStateException("Account is closed");
    }

    @Override
    public boolean isTerminal() { return true; }
}
