package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.CustomerNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InvalidPasswordException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.PasswordReusedException;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.BankUserDetailsService;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.SecurityConfig;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer.CustomerSelfServicePort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CustomerController.class)
@Import(SecurityConfig.class)
class CustomerControllerTest {

    static final String CALLER_ID = "11111111-1111-1111-1111-111111111111";
    static final CustomerId CALLER = CustomerId.of(CALLER_ID);
    static final String OTHER_CUSTOMER_ID = "22222222-2222-2222-2222-222222222222";

    @Autowired MockMvc mockMvc;

    @MockitoBean BankUserDetailsService userDetailsService;
    @MockitoBean CustomerSelfServicePort customerSelfService;

    private String customerId() {
        return UUID.randomUUID().toString();
    }

    // ── PUT /api/customers/{id}/password ─────────────────────────────────

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void changePassword_returnsOk() throws Exception {
        doNothing().when(customerSelfService).changePassword(any());

        mockMvc.perform(put("/api/customers/{id}/password", customerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Valid@123"}
                                """))
                .andExpect(status().isOk());

        verify(customerSelfService).changePassword(any());
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void changePassword_returnsBadRequestOnBlankPassword() throws Exception {
        mockMvc.perform(put("/api/customers/{id}/password", customerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":""}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerSelfService);
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void changePassword_returnsBadRequestOnWeakPassword() throws Exception {
        doThrow(new InvalidPasswordException("Password too weak"))
                .when(customerSelfService).changePassword(any());

        mockMvc.perform(put("/api/customers/{id}/password", customerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"weakpass"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void changePassword_returnsConflictOnPasswordReuse() throws Exception {
        doThrow(new PasswordReusedException("Password recently used"))
                .when(customerSelfService).changePassword(any());

        mockMvc.perform(put("/api/customers/{id}/password", customerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Valid@123"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void changePassword_returnsNotFoundForUnknownCustomer() throws Exception {
        doThrow(new CustomerNotFoundException("Customer not found"))
                .when(customerSelfService).changePassword(any());

        mockMvc.perform(put("/api/customers/{id}/password", customerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Valid@123"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void changePassword_returnsForbiddenForAdminRole() throws Exception {
        mockMvc.perform(put("/api/customers/{id}/password", customerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Valid@123"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void changePassword_returnsUnauthorizedWithoutCredentials() throws Exception {
        mockMvc.perform(put("/api/customers/{id}/password", customerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Valid@123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void changePassword_forwardsBothTheCallerAndTheSubject() throws Exception {
        mockMvc.perform(put("/api/customers/{id}/password", OTHER_CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"NewPass@123!"}
                                """))
                .andExpect(status().isOk());

        // The controller forwards faithfully; refusing is the service's job, covered by
        // CustomerApplicationServiceTest.shouldRejectChangingAnotherCustomersPassword.
        ArgumentCaptor<CustomerSelfServicePort.ChangePasswordCommand> captor =
                ArgumentCaptor.forClass(CustomerSelfServicePort.ChangePasswordCommand.class);
        verify(customerSelfService).changePassword(captor.capture());
        assertThat(captor.getValue().callerId()).isEqualTo(CALLER);
        assertThat(captor.getValue().customerId()).isEqualTo(CustomerId.of(OTHER_CUSTOMER_ID));
    }
}
