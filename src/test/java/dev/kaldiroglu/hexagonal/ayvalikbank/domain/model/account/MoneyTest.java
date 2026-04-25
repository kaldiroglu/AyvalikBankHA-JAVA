package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void shouldCreateMoneyWithValidAmountAndCurrency() {
        Money money = Money.of(100.0, Currency.USD);
        assertThat(money.amount()).isEqualByComparingTo("100.00");
        assertThat(money.currency()).isEqualTo(Currency.USD);
    }

    @Test
    void shouldAllowNegativeAmount() {
        Money money = Money.of(-50.0, Currency.EUR);
        assertThat(money.amount()).isEqualByComparingTo("-50.00");
        assertThat(money.isNegative()).isTrue();
    }

    @Test
    void shouldAddMoneyOfSameCurrency() {
        Money a = Money.of(100.0, Currency.USD);
        Money b = Money.of(50.0, Currency.USD);
        assertThat(a.add(b).amount()).isEqualByComparingTo("150.00");
    }

    @Test
    void shouldRejectAddingDifferentCurrencies() {
        Money usd = Money.of(100.0, Currency.USD);
        Money eur = Money.of(50.0, Currency.EUR);
        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void shouldSubtractMoneyOfSameCurrency() {
        Money a = Money.of(100.0, Currency.TL);
        Money b = Money.of(30.0, Currency.TL);
        assertThat(a.subtract(b).amount()).isEqualByComparingTo("70.00");
    }

    @Test
    void shouldSubtractLargerFromSmallerReturningNegative() {
        Money a = Money.of(50.0, Currency.USD);
        Money b = Money.of(100.0, Currency.USD);
        Money result = a.subtract(b);
        assertThat(result.amount()).isEqualByComparingTo("-50.00");
        assertThat(result.isNegative()).isTrue();
    }

    @Test
    void shouldReturnZeroMoneyForCurrency() {
        Money zero = Money.zero(Currency.EUR);
        assertThat(zero.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(zero.currency()).isEqualTo(Currency.EUR);
    }

    @Test
    void shouldNegatePositiveMoney() {
        Money positive = Money.of(75.0, Currency.USD);
        Money negated = positive.negate();
        assertThat(negated.amount()).isEqualByComparingTo("-75.00");
        assertThat(negated.isNegative()).isTrue();
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> Money.of(null, Currency.USD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
