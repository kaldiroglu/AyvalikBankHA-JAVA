package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

import java.util.List;
import java.util.Optional;

public interface CustomerRepositoryPort {
    Customer save(Customer customer);
    Optional<Customer> findById(CustomerId id);
    Optional<Customer> findByEmail(String email);
    List<Customer> findAll();
    void deleteById(CustomerId id);
    boolean existsById(CustomerId id);
}
