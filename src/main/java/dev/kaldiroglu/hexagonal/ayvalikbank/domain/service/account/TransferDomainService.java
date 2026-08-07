package dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Money;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.TransactionAmount;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerTier;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates transfer fees and validates per-transaction limits.
 * Intra-customer transfers are free; cross-customer transfers incur a percentage fee
 * scaled by the source customer's tier multiplier.
 */
public class TransferDomainService {

    public Money calculateFee(TransactionAmount amount, boolean sameCustomer, BigDecimal feePercent, CustomerTier sourceTier) {
        if (sameCustomer) {
            return Money.zero(amount.currency());
        }
        BigDecimal scaledPercent = feePercent.multiply(sourceTier.feeMultiplier());
        BigDecimal feeAmount = amount.asMoney().amount()
                .multiply(scaledPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return Money.of(feeAmount, amount.currency());
    }

    public void requireTransferWithinLimit(TransactionAmount amount, CustomerTier tier) {
        tier.maxPerTransfer().ifPresent(cap -> {
            if (amount.asMoney().amount().compareTo(cap) > 0)
                throw new IllegalStateException(
                        "Transfer amount " + amount + " exceeds " + tier + " tier limit of " + cap);
        });
    }

    public void requireWithdrawalWithinLimit(TransactionAmount amount, CustomerTier tier) {
        tier.maxPerWithdrawal().ifPresent(cap -> {
            if (amount.asMoney().amount().compareTo(cap) > 0)
                throw new IllegalStateException(
                        "Withdrawal amount " + amount + " exceeds " + tier + " tier limit of " + cap);
        });
    }
}
