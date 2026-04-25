package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.CustomerNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InvalidPasswordException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.PasswordReusedException;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.BankUserDetailsService;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.SecurityConfig;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer.ChangePasswordUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CustomerController.class)
@Import(SecurityConfig.class)
class CustomerControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean BankUserDetailsService userDetailsService;
    @MockitoBean ChangePasswordUseCase changePassword;

    private String customerId() {
        return UUID.randomUUID().toString();
    }

    // ── PUT /api/customers/{id}/password ─────────────────────────────────

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void changePassword_returnsOk() throws Exception {
        doNothing().when(changePassword).changePassword(any());

        mockMvc.perform(put("/api/customers/{id}/password", customerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Valid@123"}
                                """))
                .andExpect(status().isOk());

        verify(changePassword).changePassword(any());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void changePassword_returnsBadRequestOnBlankPassword() throws Exception {
        mockMvc.perform(put("/api/customers/{id}/password", customerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":""}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(changePassword);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void changePassword_returnsBadRequestOnWeakPassword() throws Exception {
        doThrow(new InvalidPasswordException("Password too weak"))
                .when(changePassword).changePassword(any());

        mockMvc.perform(put("/api/customers/{id}/password", customerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"weakpass"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void changePassword_returnsConflictOnPasswordReuse() throws Exception {
        doThrow(new PasswordReusedException("Password recently used"))
                .when(changePassword).changePassword(any());

        mockMvc.perform(put("/api/customers/{id}/password", customerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"Valid@123"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void changePassword_returnsNotFoundForUnknownCustomer() throws Exception {
        doThrow(new CustomerNotFoundException("Customer not found"))
                .when(changePassword).changePassword(any());

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
}
