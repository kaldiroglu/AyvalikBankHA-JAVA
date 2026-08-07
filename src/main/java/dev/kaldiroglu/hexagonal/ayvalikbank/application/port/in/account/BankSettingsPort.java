package dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account;

import java.math.BigDecimal;

/**
 * Bank-wide configuration an <b>administrator</b> can change.
 *
 * <p>Its own port because the transfer fee is a property of the bank — not of any customer and not
 * of any account. Before this refactoring {@code setTransferFee} lived on
 * {@code CustomerApplicationService}, purely because an admin invoked it, and that service injected
 * {@code SettingsRepositoryPort} solely to serve it. That is what "grouped by whoever happens to
 * call it" looks like in practice.
 *
 * <p>Placed in the {@code account} sub-package rather than a new {@code settings} one, to stay
 * consistent with {@code SettingsRepositoryPort} at {@code domain/port/out/account}.
 */
public interface BankSettingsPort {

    record SetTransferFeeCommand(BigDecimal feePercent) {}

    void setTransferFee(SetTransferFeeCommand command);
}
