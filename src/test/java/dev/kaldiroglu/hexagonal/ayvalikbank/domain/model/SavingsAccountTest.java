package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.*;

class SavingsAccountTest {

    private SavingsAccount openUsdSavings(String annualRate) {
        return SavingsAccount.open(CustomerId.generate(), Currency.USD, new BigDecimal(annualRate));
    }

    @Test
    void shouldOpenWithGivenInterestRateAndZeroBalance() {
        SavingsAccount account = openUsdSavings("0.03");
        assertThat(account.type()).isEqualTo(AccountType.SAVINGS);
        assertThat(account.getAnnualInterestRate()).isEqualByComparingTo("0.03");
        assertThat(account.getBalance().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldRejectNegativeInterestRate() {
        assertThatThrownBy(() ->
                SavingsAccount.open(CustomerId.generate(), Currency.USD, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectWithdrawalThatWouldOverdraw() {
        SavingsAccount account = openUsdSavings("0.03");
        account.deposit(Money.of(100.0, Currency.USD));
        assertThatThrownBy(() -> account.withdraw(Money.of(101.0, Currency.USD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    void shouldAccrueInterestForAMonth() {
        SavingsAccount account = openUsdSavings("0.12"); // 1% per month
        account.deposit(Money.of(1000.0, Currency.USD));
        Transaction tx = account.accrueInterest(YearMonth.of(2026, 4));
        assertThat(tx.getType()).isEqualTo(TransactionType.INTEREST);
        assertThat(tx.getAmount().amount()).isEqualByComparingTo("10.00");
        assertThat(account.getBalance().amount()).isEqualByComparingTo("1010.00");
        assertThat(account.getLastAccrualDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void shouldAccrueInterestEvenWhenFrozen() {
        SavingsAccount account = openUsdSavings("0.12");
        account.deposit(Money.of(1000.0, Currency.USD));
        account.freeze();
        Transaction tx = account.accrueInterest(YearMonth.of(2026, 4));
        assertThat(tx.getAmount().amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldRejectAccrualOnClosedAccount() {
        SavingsAccount account = openUsdSavings("0.12");
        account.close();
        assertThatThrownBy(() -> account.accrueInterest(YearMonth.of(2026, 4)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void shouldRejectDoubleAccrualForSameMonth() {
        SavingsAccount account = openUsdSavings("0.12");
        account.deposit(Money.of(1000.0, Currency.USD));
        account.accrueInterest(YearMonth.of(2026, 4));
        assertThatThrownBy(() -> account.accrueInterest(YearMonth.of(2026, 4)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already accrued");
    }
}
