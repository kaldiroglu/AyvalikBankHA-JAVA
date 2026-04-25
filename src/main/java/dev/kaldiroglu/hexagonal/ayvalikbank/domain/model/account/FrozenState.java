package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

public final class FrozenState implements AccountState {

    public static final FrozenState INSTANCE = new FrozenState();

    private FrozenState() {}

    @Override
    public AccountStatus status() { return AccountStatus.FROZEN; }

    @Override
    public AccountState freeze() {
        throw new IllegalStateException("Account is already frozen");
    }

    @Override
    public AccountState unfreeze() { return ActiveState.INSTANCE; }

    @Override
    public AccountState close() { return ClosedState.INSTANCE; }

    @Override
    public void requireOperable() {
        throw new IllegalStateException("Account is frozen");
    }

    @Override
    public boolean isTerminal() { return false; }
}
