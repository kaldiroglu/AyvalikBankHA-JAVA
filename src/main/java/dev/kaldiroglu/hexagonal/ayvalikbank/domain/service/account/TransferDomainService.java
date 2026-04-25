package dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates transfer fees. Intra-customer transfers are free.
 * Cross-customer transfers incur a percentage fee set by the admin.
 */
public class TransferDomainService {

    public Money calculateFee(Money amount, boolean sameCustomer, BigDecimal feePercent) {
        if (sameCustomer) {
            return Money.zero(amount.currency());
        }
        BigDecimal feeAmount = amount.amount()
                .multiply(feePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return Money.of(feeAmount, amount.currency());
    }
}
