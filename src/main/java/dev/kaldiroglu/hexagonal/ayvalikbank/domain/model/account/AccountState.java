package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

public sealed interface AccountState permits ActiveState, FrozenState, ClosedState {

    AccountStatus status();

    AccountState freeze();

    AccountState unfreeze();

    AccountState close();

    void requireOperable();

    boolean isTerminal();

    static AccountState of(AccountStatus status) {
        return switch (status) {
            case ACTIVE -> ActiveState.INSTANCE;
            case FROZEN -> FrozenState.INSTANCE;
            case CLOSED -> ClosedState.INSTANCE;
        };
    }
}
