package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AccountStateTest {

    // ── Factory ───────────────────────────────────────────────────────────

    @Test
    void shouldMapEachAccountStatusToItsState() {
        assertThat(AccountState.of(AccountStatus.ACTIVE)).isSameAs(ActiveState.INSTANCE);
        assertThat(AccountState.of(AccountStatus.FROZEN)).isSameAs(FrozenState.INSTANCE);
        assertThat(AccountState.of(AccountStatus.CLOSED)).isSameAs(ClosedState.INSTANCE);
    }

    @Nested
    class Active {

        @Test
        void shouldExposeActiveStatus() {
            assertThat(ActiveState.INSTANCE.status()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void shouldNotBeTerminal() {
            assertThat(ActiveState.INSTANCE.isTerminal()).isFalse();
        }

        @Test
        void shouldAllowOperations() {
            assertThatCode(ActiveState.INSTANCE::requireOperable).doesNotThrowAnyException();
        }

        @Test
        void shouldTransitionToFrozenOnFreeze() {
            assertThat(ActiveState.INSTANCE.freeze()).isSameAs(FrozenState.INSTANCE);
        }

        @Test
        void shouldRejectUnfreezeOnActive() {
            assertThatThrownBy(ActiveState.INSTANCE::unfreeze)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not frozen");
        }

        @Test
        void shouldTransitionToClosedOnClose() {
            assertThat(ActiveState.INSTANCE.close()).isSameAs(ClosedState.INSTANCE);
        }
    }

    @Nested
    class Frozen {

        @Test
        void shouldExposeFrozenStatus() {
            assertThat(FrozenState.INSTANCE.status()).isEqualTo(AccountStatus.FROZEN);
        }

        @Test
        void shouldNotBeTerminal() {
            assertThat(FrozenState.INSTANCE.isTerminal()).isFalse();
        }

        @Test
        void shouldRejectOperationsWhenFrozen() {
            assertThatThrownBy(FrozenState.INSTANCE::requireOperable)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("frozen");
        }

        @Test
        void shouldRejectFreezeOnFrozen() {
            assertThatThrownBy(FrozenState.INSTANCE::freeze)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already frozen");
        }

        @Test
        void shouldTransitionToActiveOnUnfreeze() {
            assertThat(FrozenState.INSTANCE.unfreeze()).isSameAs(ActiveState.INSTANCE);
        }

        @Test
        void shouldTransitionToClosedOnClose() {
            assertThat(FrozenState.INSTANCE.close()).isSameAs(ClosedState.INSTANCE);
        }
    }

    @Nested
    class Closed {

        @Test
        void shouldExposeClosedStatus() {
            assertThat(ClosedState.INSTANCE.status()).isEqualTo(AccountStatus.CLOSED);
        }

        @Test
        void shouldBeTerminal() {
            assertThat(ClosedState.INSTANCE.isTerminal()).isTrue();
        }

        @Test
        void shouldRejectOperationsWhenClosed() {
            assertThatThrownBy(ClosedState.INSTANCE::requireOperable)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        void shouldRejectFreezeOnClosed() {
            assertThatThrownBy(ClosedState.INSTANCE::freeze)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        void shouldRejectUnfreezeOnClosed() {
            assertThatThrownBy(ClosedState.INSTANCE::unfreeze)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        void shouldRejectCloseOnClosed() {
            assertThatThrownBy(ClosedState.INSTANCE::close)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already closed");
        }
    }

    @Nested
    class StatesAreSingletons {

        @Test
        void factoryReturnsSameInstanceForRepeatedCalls() {
            assertThat(AccountState.of(AccountStatus.ACTIVE)).isSameAs(AccountState.of(AccountStatus.ACTIVE));
            assertThat(AccountState.of(AccountStatus.FROZEN)).isSameAs(AccountState.of(AccountStatus.FROZEN));
            assertThat(AccountState.of(AccountStatus.CLOSED)).isSameAs(AccountState.of(AccountStatus.CLOSED));
        }
    }
}
