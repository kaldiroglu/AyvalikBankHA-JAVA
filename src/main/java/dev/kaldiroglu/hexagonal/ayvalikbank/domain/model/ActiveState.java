package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

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
