package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class TimeDepositAccountTest {

    private TimeDepositAccount openOneYearUsdDeposit() {
        return TimeDepositAccount.open(
                CustomerId.generate(), Currency.USD,
                Money.of(1000.0, Currency.USD),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2027, 4, 1),
                new BigDecimal("0.05"));
    }

    @Test
    void shouldOpenWithPrincipalAsBalance() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        assertThat(account.type()).isEqualTo(AccountType.TIME_DEPOSIT);
        assertThat(account.getBalance().amount()).isEqualByComparingTo("1000.00");
        assertThat(account.isMatured()).isFalse();
    }

    @Test
    void shouldRejectFurtherDeposits() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        assertThatThrownBy(() -> account.deposit(Money.of(100.0, Currency.USD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void shouldRejectWithdrawalBeforeMaturity() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        assertThatThrownBy(() -> account.withdraw(Money.of(100.0, Currency.USD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("matured");
    }

    @Test
    void shouldRejectMaturationBeforeMaturityDate() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        assertThatThrownBy(() -> account.mature(LocalDate.of(2027, 3, 31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not yet");
    }

    @Test
    void shouldMatureOnOrAfterMaturityDateAndCreditInterest() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        Transaction tx = account.mature(LocalDate.of(2027, 4, 1));
        assertThat(tx.getType()).isEqualTo(TransactionType.INTEREST);
        assertThat(tx.getAmount().amount()).isEqualByComparingTo("50.00"); // 1000 * 0.05 * 1yr
        assertThat(account.getBalance().amount()).isEqualByComparingTo("1050.00");
        assertThat(account.isMatured()).isTrue();
    }

    @Test
    void shouldAllowWithdrawalAfterMaturity() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        account.mature(LocalDate.of(2027, 4, 1));
        account.withdraw(Money.of(500.0, Currency.USD));
        assertThat(account.getBalance().amount()).isEqualByComparingTo("550.00");
    }

    @Test
    void shouldRejectDoubleMaturation() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        account.mature(LocalDate.of(2027, 4, 1));
        assertThatThrownBy(() -> account.mature(LocalDate.of(2027, 4, 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already matured");
    }

    @Test
    void shouldMatureWhenFrozen() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        account.freeze();
        Transaction tx = account.mature(LocalDate.of(2027, 4, 1));
        assertThat(tx.getType()).isEqualTo(TransactionType.INTEREST);
        assertThat(account.isMatured()).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void shouldRejectMaturationOnClosedAccount() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        account.close();
        assertThatThrownBy(() -> account.mature(LocalDate.of(2027, 4, 1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
