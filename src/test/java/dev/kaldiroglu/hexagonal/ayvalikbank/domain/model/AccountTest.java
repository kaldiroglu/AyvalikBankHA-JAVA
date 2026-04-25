package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AccountTest {

    // ── Status: initial state ─────────────────────────────────────────────

    @Test
    void shouldOpenAccountWithActiveStatus() {
        Account account = openUsdAccount();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    // ── Status: freeze / unfreeze ─────────────────────────────────────────

    @Test
    void shouldFreezeActiveAccount() {
        Account account = openUsdAccount();
        account.freeze();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void shouldUnfreezeAccount() {
        Account account = openUsdAccount();
        account.freeze();
        account.unfreeze();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void shouldRejectFreezingAlreadyFrozenAccount() {
        Account account = openUsdAccount();
        account.freeze();
        assertThatThrownBy(account::freeze)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already frozen");
    }

    @Test
    void shouldRejectUnfreezingActiveAccount() {
        Account account = openUsdAccount();
        assertThatThrownBy(account::unfreeze)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not frozen");
    }

    // ── Status: close ─────────────────────────────────────────────────────

    @Test
    void shouldCloseActiveAccount() {
        Account account = openUsdAccount();
        account.close();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void shouldCloseFrozenAccount() {
        Account account = openUsdAccount();
        account.freeze();
        account.close();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void shouldRejectClosingAlreadyClosedAccount() {
        Account account = openUsdAccount();
        account.close();
        assertThatThrownBy(account::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void shouldRejectFreezingClosedAccount() {
        Account account = openUsdAccount();
        account.close();
        assertThatThrownBy(account::freeze)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void shouldRejectUnfreezingClosedAccount() {
        Account account = openUsdAccount();
        account.close();
        assertThatThrownBy(account::unfreeze)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    // ── Guard: operations blocked on non-ACTIVE accounts ─────────────────

    @Test
    void shouldRejectDepositOnFrozenAccount() {
        Account account = openUsdAccount();
        account.freeze();
        assertThatThrownBy(() -> account.deposit(Money.of(100.0, Currency.USD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen");
    }

    @Test
    void shouldRejectWithdrawOnClosedAccount() {
        Account account = openUsdAccount();
        account.deposit(Money.of(200.0, Currency.USD));
        account.close();
        assertThatThrownBy(() -> account.withdraw(Money.of(50.0, Currency.USD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void shouldRejectTransferOutOnFrozenAccount() {
        Account account = openUsdAccount();
        account.deposit(Money.of(500.0, Currency.USD));
        account.freeze();
        assertThatThrownBy(() -> account.transferOut(
                Money.of(100.0, Currency.USD), Money.zero(Currency.USD), "target"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen");
    }

    @Test
    void shouldRejectTransferInOnClosedAccount() {
        Account account = openUsdAccount();
        account.close();
        assertThatThrownBy(() -> account.transferIn(Money.of(100.0, Currency.USD), "source"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }



    private Account openUsdAccount() {
        return CheckingAccount.open(CustomerId.generate(), Currency.USD);
    }

    @Test
    void shouldOpenAccountWithZeroBalance() {
        Account account = openUsdAccount();
        assertThat(account.getBalance().amount()).isEqualByComparingTo("0.00");
        assertThat(account.getBalance().currency()).isEqualTo(Currency.USD);
    }

    @Test
    void shouldDepositAndIncreaseBalance() {
        Account account = openUsdAccount();
        Transaction tx = account.deposit(Money.of(500.0, Currency.USD));
        assertThat(account.getBalance().amount()).isEqualByComparingTo("500.00");
        assertThat(tx.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(tx.getAmount().amount()).isEqualByComparingTo("500.00");
    }

    @Test
    void shouldWithdrawAndDecreaseBalance() {
        Account account = openUsdAccount();
        account.deposit(Money.of(500.0, Currency.USD));
        Transaction tx = account.withdraw(Money.of(200.0, Currency.USD));
        assertThat(account.getBalance().amount()).isEqualByComparingTo("300.00");
        assertThat(tx.getType()).isEqualTo(TransactionType.WITHDRAWAL);
    }

    @Test
    void shouldRejectWithdrawalExceedingBalance() {
        Account account = openUsdAccount();
        account.deposit(Money.of(100.0, Currency.USD));
        assertThatThrownBy(() -> account.withdraw(Money.of(200.0, Currency.USD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    void shouldRejectDepositWithWrongCurrency() {
        Account account = openUsdAccount();
        assertThatThrownBy(() -> account.deposit(Money.of(100.0, Currency.EUR)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void shouldTransferOutWithFeeDeducted() {
        Account account = openUsdAccount();
        account.deposit(Money.of(1000.0, Currency.USD));
        account.transferOut(Money.of(200.0, Currency.USD), Money.of(2.0, Currency.USD), "target-id");
        assertThat(account.getBalance().amount()).isEqualByComparingTo("798.00");
    }

    @Test
    void shouldTransferInAndIncreaseBalance() {
        Account account = openUsdAccount();
        account.transferIn(Money.of(300.0, Currency.USD), "source-id");
        assertThat(account.getBalance().amount()).isEqualByComparingTo("300.00");
    }

}
