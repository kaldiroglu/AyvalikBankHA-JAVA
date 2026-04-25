package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;

public interface CreateCustomerUseCase {
    record Command(String name, String email, String rawPassword) {}
    Customer createCustomer(Command command);
}
