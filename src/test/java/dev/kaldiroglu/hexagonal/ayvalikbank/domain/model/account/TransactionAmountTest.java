package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TransactionAmount")
class TransactionAmountTest {

    @Test
    void shouldAcceptPositiveAmount() {
        TransactionAmount amount = TransactionAmount.of(100.0, Currency.USD);
        assertThat(amount.asMoney().amount()).isEqualByComparingTo("100.00");
        assertThat(amount.currency()).isEqualTo(Currency.USD);
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> TransactionAmount.of(-50.0, Currency.USD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> TransactionAmount.of(0.0, Currency.USD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void shouldRejectNullMoney() {
        assertThatThrownBy(() -> TransactionAmount.of((Money) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("rejects an amount that rounds down to zero at 2-decimal scale")
    void shouldRejectAmountRoundingToZero() {
        assertThatThrownBy(() -> TransactionAmount.of(new BigDecimal("0.001"), Currency.USD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void shouldInheritTwoDecimalScalingFromMoney() {
        TransactionAmount amount = TransactionAmount.of(new BigDecimal("10.005"), Currency.EUR);
        assertThat(amount.asMoney().amount()).isEqualByComparingTo("10.01");
    }

    @Test
    void shouldBeEqualByValue() {
        assertThat(TransactionAmount.of(25.0, Currency.TL))
                .isEqualTo(TransactionAmount.of(25.0, Currency.TL))
                .isNotEqualTo(TransactionAmount.of(25.0, Currency.USD));
    }

    @Test
    @DisplayName("Money still allows negatives — the constraint belongs to TransactionAmount alone")
    void shouldNotConstrainMoneyItself() {
        Money overdrawnBalance = Money.of(-500.0, Currency.USD);
        assertThat(overdrawnBalance.isNegative()).isTrue();
    }
}
