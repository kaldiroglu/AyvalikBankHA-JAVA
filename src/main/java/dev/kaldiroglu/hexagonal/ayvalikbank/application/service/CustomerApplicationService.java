package dev.kaldiroglu.hexagonal.ayvalikbank.application.service;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.CustomerNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InvalidPasswordException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.PasswordReusedException;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Password;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.account.SettingsRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer.CustomerRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer.PasswordHasherPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.customer.PasswordValidationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CustomerApplicationService implements
        CreateCustomerUseCase,
        DeleteCustomerUseCase,
        ListCustomersUseCase,
        ChangePasswordUseCase,
        SetTransferFeeUseCase {

    private final CustomerRepositoryPort customerRepository;
    private final PasswordHasherPort passwordHasher;
    private final PasswordValidationService passwordValidationService;
    private final SettingsRepositoryPort settingsRepository;

    public CustomerApplicationService(CustomerRepositoryPort customerRepository,
                                      PasswordHasherPort passwordHasher,
                                      PasswordValidationService passwordValidationService,
                                      SettingsRepositoryPort settingsRepository) {
        this.customerRepository = customerRepository;
        this.passwordHasher = passwordHasher;
        this.passwordValidationService = passwordValidationService;
        this.settingsRepository = settingsRepository;
    }

    @Override
    public Customer createCustomer(CreateCustomerUseCase.Command command) {
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
    public void changePassword(ChangePasswordUseCase.Command command) {
        Customer customer = customerRepository.findById(command.customerId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + command.customerId()));

        validatePassword(command.rawNewPassword());
        checkPasswordReuse(customer, command.rawNewPassword());

        String newHash = passwordHasher.hash(command.rawNewPassword());
        customer.changePassword(Password.ofHashed(newHash));
        customerRepository.save(customer);
    }

    @Override
    public void setTransferFee(SetTransferFeeUseCase.Command command) {
        if (command.feePercent().compareTo(java.math.BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Transfer fee percent cannot be negative");
        settingsRepository.setTransferFeePercent(command.feePercent());
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
