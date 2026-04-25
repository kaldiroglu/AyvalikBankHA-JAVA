package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CheckingAccountTest {

    @Test
    void shouldOpenWithZeroBalanceAndNoOverdraftByDefault() {
        CheckingAccount account = CheckingAccount.open(CustomerId.generate(), Currency.USD);
        assertThat(account.type()).isEqualTo(AccountType.CHECKING);
        assertThat(account.getBalance().amount()).isEqualByComparingTo("0.00");
        assertThat(account.getOverdraftLimit().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldWithdrawIntoOverdraftWhenLimitAllows() {
        CheckingAccount account = CheckingAccount.open(
                CustomerId.generate(), Currency.USD, Money.of(100.0, Currency.USD));
        account.deposit(Money.of(50.0, Currency.USD));
        account.withdraw(Money.of(120.0, Currency.USD));
        assertThat(account.getBalance().amount()).isEqualByComparingTo("-70.00");
    }

    @Test
    void shouldRejectWithdrawalBeyondOverdraftLimit() {
        CheckingAccount account = CheckingAccount.open(
                CustomerId.generate(), Currency.USD, Money.of(50.0, Currency.USD));
        assertThatThrownBy(() -> account.withdraw(Money.of(60.0, Currency.USD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overdraft");
    }

    @Test
    void shouldRejectWithdrawalWhenNoOverdraftAndInsufficientFunds() {
        CheckingAccount account = CheckingAccount.open(CustomerId.generate(), Currency.USD);
        account.deposit(Money.of(50.0, Currency.USD));
        assertThatThrownBy(() -> account.withdraw(Money.of(60.0, Currency.USD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient");
    }
}
