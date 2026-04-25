package dev.kaldiroglu.hexagonal.ayvalikbank.application.service;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.CustomerNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InvalidPasswordException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.PasswordReusedException;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Password;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer.ChangePasswordUseCase;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer.CreateCustomerUseCase;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer.CustomerRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer.PasswordHasherPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.account.SettingsRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.customer.PasswordValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerApplicationServiceTest {

    @Mock private CustomerRepositoryPort customerRepository;
    @Mock private PasswordHasherPort passwordHasher;
    @Mock private SettingsRepositoryPort settingsRepository;

    private CustomerApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CustomerApplicationService(
                customerRepository, passwordHasher,
                new PasswordValidationService(), settingsRepository);
    }

    @Test
    void shouldCreateCustomerWithHashedPassword() {
        when(passwordHasher.hash("Valid@123")).thenReturn("hashed");
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Customer result = service.createCustomer(
                new CreateCustomerUseCase.Command("Ali", "ali@test.com", "Valid@123"));

        assertThat(result.getEmail()).isEqualTo("ali@test.com");
        assertThat(result.getCurrentPassword().hashedValue()).isEqualTo("hashed");
        verify(customerRepository).save(any());
    }

    @Test
    void shouldThrowInvalidPasswordExceptionForWeakPassword() {
        assertThatThrownBy(() -> service.createCustomer(
                new CreateCustomerUseCase.Command("Ali", "ali@test.com", "weak")))
                .isInstanceOf(InvalidPasswordException.class);
        verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldDeleteExistingCustomer() {
        CustomerId id = CustomerId.generate();
        when(customerRepository.existsById(id)).thenReturn(true);

        service.deleteCustomer(id);

        verify(customerRepository).deleteById(id);
    }

    @Test
    void shouldThrowCustomerNotFoundOnDeleteOfMissingCustomer() {
        CustomerId id = CustomerId.generate();
        when(customerRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteCustomer(id))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void shouldChangePasswordSuccessfully() {
        CustomerId id = CustomerId.generate();
        Customer customer = new Customer(id, "Ali", "ali@test.com", "CUSTOMER",
                Password.ofHashed("old-hash"), new ArrayList<>());
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));
        when(passwordHasher.matches("Valid@123", "old-hash")).thenReturn(false);
        when(passwordHasher.hash("Valid@123")).thenReturn("new-hash");
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword(new ChangePasswordUseCase.Command(id, "Valid@123"));

        assertThat(customer.getCurrentPassword().hashedValue()).isEqualTo("new-hash");
        verify(customerRepository).save(customer);
    }

    @Test
    void shouldThrowPasswordReusedExceptionWhenNewPasswordMatchesCurrent() {
        CustomerId id = CustomerId.generate();
        Customer customer = new Customer(id, "Ali", "ali@test.com", "CUSTOMER",
                Password.ofHashed("same-hash"), new ArrayList<>());
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));
        when(passwordHasher.matches("Valid@123", "same-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(
                new ChangePasswordUseCase.Command(id, "Valid@123")))
                .isInstanceOf(PasswordReusedException.class);
    }
}
