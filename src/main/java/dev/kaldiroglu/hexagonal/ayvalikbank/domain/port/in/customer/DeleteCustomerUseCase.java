package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

public interface DeleteCustomerUseCase {
    void deleteCustomer(CustomerId customerId);
}
