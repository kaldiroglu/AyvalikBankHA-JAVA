package dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerTier;

import java.util.List;

/**
 * Everything an <b>administrator</b> can do to the customer roster.
 *
 * <p>Distinct from {@link CustomerSelfServicePort}: the actor here is the bank acting on a
 * customer's record, not the customer acting on their own.
 */
public interface CustomerAdministrationPort {

    record CreateCustomerCommand(String name, String email, String rawPassword) {}

    record ChangeCustomerTierCommand(CustomerId customerId, CustomerTier tier) {}

    Customer createCustomer(CreateCustomerCommand command);

    void deleteCustomer(CustomerId customerId);

    List<Customer> listCustomers();

    void changeCustomerTier(ChangeCustomerTierCommand command);
}
