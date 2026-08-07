package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Classifies how a {@link Customer} is treated for fees and per-transaction limits.
 *
 * <p>Each tier carries:
 * <ul>
 *   <li><b>Fee multiplier</b> — scales the admin-configured transfer-fee percentage on outgoing
 *       cross-customer transfers. STANDARD pays full fee, PREMIUM pays half, PRIVATE pays none.
 *   <li><b>Max per transfer / max per withdrawal</b> — per-transaction caps. {@link Optional#empty()}
 *       means "no cap" (used for {@link #PRIVATE} clients).
 * </ul>
 *
 * <p>Tiers in order of seniority:
 * <ul>
 *   <li>{@link #STANDARD} — default tier for newly created customers.
 *   <li>{@link #PREMIUM}  — preferred banking; reduced fees and higher caps.
 *   <li>{@link #PRIVATE}  — private banking; no fees, no caps.
 * </ul>
 *
 * <p>Tier upgrades / downgrades are admin-only via {@code ChangeCustomerTierUseCase}.
 */
public enum CustomerTier {

    STANDARD(new BigDecimal("1.00"), new BigDecimal("5000"),  new BigDecimal("5000")),
    PREMIUM (new BigDecimal("0.50"), new BigDecimal("50000"), new BigDecimal("25000")),
    PRIVATE (new BigDecimal("0.00"), null,                    null);

    private final BigDecimal feeMultiplier;
    private final BigDecimal maxPerTransfer;
    private final BigDecimal maxPerWithdrawal;

    CustomerTier(BigDecimal feeMultiplier, BigDecimal maxPerTransfer, BigDecimal maxPerWithdrawal) {
        this.feeMultiplier = feeMultiplier;
        this.maxPerTransfer = maxPerTransfer;
        this.maxPerWithdrawal = maxPerWithdrawal;
    }

    public BigDecimal feeMultiplier() {
        return feeMultiplier;
    }

    public Optional<BigDecimal> maxPerTransfer() {
        return Optional.ofNullable(maxPerTransfer);
    }

    public Optional<BigDecimal> maxPerWithdrawal() {
        return Optional.ofNullable(maxPerWithdrawal);
    }
}
