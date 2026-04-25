package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import java.math.BigDecimal;

public interface SetTransferFeeUseCase {
    record Command(BigDecimal feePercent) {}
    void setTransferFee(Command command);
}
