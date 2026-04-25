package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.account;

import java.math.BigDecimal;

public interface SettingsRepositoryPort {
    BigDecimal getTransferFeePercent();
    void setTransferFeePercent(BigDecimal percent);
}
