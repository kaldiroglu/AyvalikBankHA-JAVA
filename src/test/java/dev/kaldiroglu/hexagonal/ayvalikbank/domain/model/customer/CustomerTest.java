package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

class CustomerTest {

    private Customer createCustomer(String passwordHash) {
        return new Customer(CustomerId.generate(), "Ali", "ali@test.com",
                "CUSTOMER", Password.ofHashed(passwordHash), new ArrayList<>());
    }

    @Test
    void shouldChangePasswordAndMoveCurrentToHistory() {
        Customer customer = createCustomer("hash-1");
        customer.changePassword(Password.ofHashed("hash-2"));
        assertThat(customer.getCurrentPassword().hashedValue()).isEqualTo("hash-2");
        assertThat(customer.getPasswordHistory()).hasSize(1);
        assertThat(customer.getPasswordHistory().get(0).hashedValue()).isEqualTo("hash-1");
    }

    @Test
    void shouldKeepAtMostThreePasswordsInHistory() {
        Customer customer = createCustomer("hash-0");
        customer.changePassword(Password.ofHashed("hash-1"));
        customer.changePassword(Password.ofHashed("hash-2"));
        customer.changePassword(Password.ofHashed("hash-3"));
        customer.changePassword(Password.ofHashed("hash-4"));

        assertThat(customer.getCurrentPassword().hashedValue()).isEqualTo("hash-4");
        assertThat(customer.getPasswordHistory()).hasSize(3);
        assertThat(customer.getPasswordHistory().get(0).hashedValue()).isEqualTo("hash-3");
        assertThat(customer.getPasswordHistory().get(1).hashedValue()).isEqualTo("hash-2");
        assertThat(customer.getPasswordHistory().get(2).hashedValue()).isEqualTo("hash-1");
    }

    @Test
    void getAllPasswordsForReuseCheckShouldIncludeCurrentAndHistory() {
        Customer customer = createCustomer("hash-0");
        customer.changePassword(Password.ofHashed("hash-1"));
        customer.changePassword(Password.ofHashed("hash-2"));

        var all = customer.getAllPasswordsForReuseCheck();
        assertThat(all).hasSize(3);
        assertThat(all.get(0).hashedValue()).isEqualTo("hash-2"); // current
    }
}
