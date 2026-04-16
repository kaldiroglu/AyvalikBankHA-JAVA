package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotOperableException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.CustomerNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.BankUserDetailsService;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.SecurityConfig;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Customer;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Password;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean BankUserDetailsService userDetailsService;
    @MockitoBean CreateCustomerUseCase createCustomer;
    @MockitoBean DeleteCustomerUseCase deleteCustomer;
    @MockitoBean ListCustomersUseCase listCustomers;
    @MockitoBean SetTransferFeeUseCase setTransferFee;
    @MockitoBean FreezeAccountUseCase freezeAccount;
    @MockitoBean UnfreezeAccountUseCase unfreezeAccount;
    @MockitoBean CloseAccountUseCase closeAccount;

    // ── helpers ───────────────────────────────────────────────────────────

    private Customer stubCustomer(String name, String email) {
        return new Customer(CustomerId.generate(), name, email, "CUSTOMER",
                Password.ofHashed("hash"), new ArrayList<>());
    }

    // ── POST /api/admin/customers ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCustomer_returnsCreated() throws Exception {
        Customer saved = stubCustomer("Alice", "alice@test.com");
        when(createCustomer.createCustomer(any())).thenReturn(saved);

        mockMvc.perform(post("/api/admin/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"alice@test.com","password":"Valid@123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@test.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCustomer_returnsBadRequestOnMissingName() throws Exception {
        mockMvc.perform(post("/api/admin/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@test.com","password":"Valid@123"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createCustomer);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCustomer_returnsBadRequestOnInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/admin/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"not-an-email","password":"Valid@123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void createCustomer_returnsForbiddenForCustomerRole() throws Exception {
        mockMvc.perform(post("/api/admin/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"alice@test.com","password":"Valid@123"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCustomer_returnsUnauthorizedWithoutCredentials() throws Exception {
        mockMvc.perform(post("/api/admin/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","email":"alice@test.com","password":"Valid@123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /api/admin/customers/{id} ─────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCustomer_returnsNoContent() throws Exception {
        String id = UUID.randomUUID().toString();
        doNothing().when(deleteCustomer).deleteCustomer(any());

        mockMvc.perform(delete("/api/admin/customers/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCustomer_returnsNotFoundWhenMissing() throws Exception {
        String id = UUID.randomUUID().toString();
        doThrow(new CustomerNotFoundException("Customer not found"))
                .when(deleteCustomer).deleteCustomer(any());

        mockMvc.perform(delete("/api/admin/customers/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/admin/customers ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void listCustomers_returnsOkWithList() throws Exception {
        when(listCustomers.listCustomers()).thenReturn(List.of(
                stubCustomer("Alice", "alice@test.com"),
                stubCustomer("Bob", "bob@test.com")));

        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("alice@test.com"))
                .andExpect(jsonPath("$[1].email").value("bob@test.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listCustomers_returnsEmptyList() throws Exception {
        when(listCustomers.listCustomers()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── PUT /api/admin/settings/transfer-fee ─────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void setTransferFee_returnsOk() throws Exception {
        doNothing().when(setTransferFee).setTransferFee(any());

        mockMvc.perform(put("/api/admin/settings/transfer-fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feePercent":1.5}
                                """))
                .andExpect(status().isOk());

        verify(setTransferFee).setTransferFee(
                new SetTransferFeeUseCase.Command(new BigDecimal("1.5")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setTransferFee_returnsBadRequestForNegativeValue() throws Exception {
        mockMvc.perform(put("/api/admin/settings/transfer-fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feePercent":-1.0}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(setTransferFee);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setTransferFee_returnsBadRequestForValueAbove100() throws Exception {
        mockMvc.perform(put("/api/admin/settings/transfer-fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feePercent":101.0}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/admin/accounts/{id}/freeze ───────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void freezeAccount_returnsOk() throws Exception {
        String id = UUID.randomUUID().toString();
        doNothing().when(freezeAccount).freezeAccount(any());

        mockMvc.perform(put("/api/admin/accounts/{id}/freeze", id))
                .andExpect(status().isOk());

        verify(freezeAccount).freezeAccount(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void freezeAccount_returnsUnprocessableEntityForInvalidTransition() throws Exception {
        doThrow(new AccountNotOperableException("Account is already frozen"))
                .when(freezeAccount).freezeAccount(any());

        mockMvc.perform(put("/api/admin/accounts/{id}/freeze", UUID.randomUUID()))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── PUT /api/admin/accounts/{id}/unfreeze ─────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void unfreezeAccount_returnsOk() throws Exception {
        doNothing().when(unfreezeAccount).unfreezeAccount(any());

        mockMvc.perform(put("/api/admin/accounts/{id}/unfreeze", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unfreezeAccount_returnsUnprocessableEntityForInvalidTransition() throws Exception {
        doThrow(new AccountNotOperableException("Account is not frozen"))
                .when(unfreezeAccount).unfreezeAccount(any());

        mockMvc.perform(put("/api/admin/accounts/{id}/unfreeze", UUID.randomUUID()))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── PUT /api/admin/accounts/{id}/close ────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void closeAccount_returnsOk() throws Exception {
        doNothing().when(closeAccount).closeAccount(any());

        mockMvc.perform(put("/api/admin/accounts/{id}/close", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void closeAccount_returnsUnprocessableEntityForAlreadyClosed() throws Exception {
        doThrow(new AccountNotOperableException("Account is already closed"))
                .when(closeAccount).closeAccount(any());

        mockMvc.perform(put("/api/admin/accounts/{id}/close", UUID.randomUUID()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void closeAccount_returnsForbiddenForCustomerRole() throws Exception {
        mockMvc.perform(put("/api/admin/accounts/{id}/close", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
