package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;

import java.util.List;

public interface ListCustomersUseCase {
    List<Customer> listCustomers();
}
