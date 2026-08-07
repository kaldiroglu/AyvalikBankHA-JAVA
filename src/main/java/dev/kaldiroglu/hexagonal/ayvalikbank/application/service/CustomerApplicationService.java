package dev.kaldiroglu.hexagonal.ayvalikbank.application.service;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.CustomerNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InvalidPasswordException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.PasswordReusedException;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Password;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer.CustomerAdministrationPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer.CustomerSelfServicePort;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer.CustomerAdministrationPort.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer.CustomerSelfServicePort.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer.CustomerRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer.PasswordHasherPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.customer.PasswordValidationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CustomerApplicationService implements
        CustomerAdministrationPort,
        CustomerSelfServicePort {

    private final CustomerRepositoryPort customerRepository;
    private final PasswordHasherPort passwordHasher;
    private final PasswordValidationService passwordValidationService;

    public CustomerApplicationService(CustomerRepositoryPort customerRepository,
                                      PasswordHasherPort passwordHasher,
                                      PasswordValidationService passwordValidationService) {
        this.customerRepository = customerRepository;
        this.passwordHasher = passwordHasher;
        this.passwordValidationService = passwordValidationService;
    }

    @Override
    public Customer createCustomer(CreateCustomerCommand command) {
        validatePassword(command.rawPassword());
        String hash = passwordHasher.hash(command.rawPassword());
        Customer customer = Customer.create(command.name(), command.email(), Password.ofHashed(hash));
        return customerRepository.save(customer);
    }

    @Override
    public void deleteCustomer(CustomerId customerId) {
        if (!customerRepository.existsById(customerId))
            throw new CustomerNotFoundException("Customer not found: " + customerId);
        customerRepository.deleteById(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> listCustomers() {
        return customerRepository.findAll();
    }

    @Override
    public void changePassword(ChangePasswordCommand command) {
        Customer customer = customerRepository.findById(command.customerId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + command.customerId()));

        validatePassword(command.rawNewPassword());
        checkPasswordReuse(customer, command.rawNewPassword());

        String newHash = passwordHasher.hash(command.rawNewPassword());
        customer.changePassword(Password.ofHashed(newHash));
        customerRepository.save(customer);
    }

    @Override
    public void changeCustomerTier(ChangeCustomerTierCommand command) {
        Customer customer = customerRepository.findById(command.customerId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + command.customerId()));
        customer.changeTier(command.tier());
        customerRepository.save(customer);
    }


    private void validatePassword(String rawPassword) {
        try {
            passwordValidationService.validate(rawPassword);
        } catch (IllegalArgumentException e) {
            throw new InvalidPasswordException(e.getMessage());
        }
    }

    private void checkPasswordReuse(Customer customer, String rawNewPassword) {
        for (Password previous : customer.getAllPasswordsForReuseCheck()) {
            if (passwordHasher.matches(rawNewPassword, previous.hashedValue())) {
                throw new PasswordReusedException("New password must not match any of the last 3 passwords");
            }
        }
    }
}
