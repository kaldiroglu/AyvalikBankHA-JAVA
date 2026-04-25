package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

public interface ChangePasswordUseCase {
    record Command(CustomerId customerId, String rawNewPassword) {}
    void changePassword(Command command);
}
