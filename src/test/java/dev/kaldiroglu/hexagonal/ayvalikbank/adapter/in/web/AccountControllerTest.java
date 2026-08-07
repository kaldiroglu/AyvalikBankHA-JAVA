package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InsufficientFundsException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.UnauthorizedAccessException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.CustomerAccountPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.BankUserDetailsService;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.SecurityConfig;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerTest {

    static final String CALLER_ID = "11111111-1111-1111-1111-111111111111";
    static final CustomerId CALLER = CustomerId.of(CALLER_ID);
    static final CustomerId OTHER_CUSTOMER = CustomerId.of("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mockMvc;

    @MockitoBean BankUserDetailsService userDetailsService;
    @MockitoBean CustomerAccountPort customerAccount;

    // ── helpers ───────────────────────────────────────────────────────────

    private static Account usdAccount(CustomerId ownerId) {
        return CheckingAccount.open(ownerId, Currency.USD);
    }

    private static Transaction depositTx(AccountId accountId) {
        return Transaction.create(accountId, TransactionType.DEPOSIT,
                Money.of(100.0, Currency.USD), "Deposit");
    }

    // ── POST /api/accounts/checking ───────────────────────────────────────

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void openChecking_returnsCreated() throws Exception {
        CustomerId ownerId = CustomerId.generate();
        CheckingAccount account = CheckingAccount.open(ownerId, Currency.USD);
        when(customerAccount.openChecking(any())).thenReturn(account);

        mockMvc.perform(post("/api/accounts/checking")
                        .param("ownerId", ownerId.value().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"USD","overdraftLimit":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CHECKING"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.overdraftLimit").value(0));
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void openChecking_returnsBadRequestOnMissingCurrency() throws Exception {
        mockMvc.perform(post("/api/accounts/checking")
                        .param("ownerId", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerAccount);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void openChecking_returnsForbiddenForAdminRole() throws Exception {
        mockMvc.perform(post("/api/accounts/checking")
                        .param("ownerId", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"USD"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/accounts/savings ────────────────────────────────────────

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void openSavings_returnsCreated() throws Exception {
        CustomerId ownerId = CustomerId.generate();
        SavingsAccount account = SavingsAccount.open(ownerId, Currency.EUR, new BigDecimal("0.03"));
        when(customerAccount.openSavings(any())).thenReturn(account);

        mockMvc.perform(post("/api/accounts/savings")
                        .param("ownerId", ownerId.value().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"EUR","annualInterestRate":0.03}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SAVINGS"))
                .andExpect(jsonPath("$.interestRate").value(0.03));
    }

    // ── POST /api/accounts/time-deposit ───────────────────────────────────

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void openTimeDeposit_returnsCreated() throws Exception {
        CustomerId ownerId = CustomerId.generate();
        TimeDepositAccount account = TimeDepositAccount.open(
                ownerId, Currency.USD,
                Money.of(1000.0, Currency.USD),
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                new BigDecimal("0.05"));
        when(customerAccount.openTimeDeposit(any())).thenReturn(account);

        mockMvc.perform(post("/api/accounts/time-deposit")
                        .param("ownerId", ownerId.value().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"USD","principal":1000,"maturityDate":"%s","annualInterestRate":0.05}
                                """.formatted(LocalDate.now().plusYears(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TIME_DEPOSIT"))
                .andExpect(jsonPath("$.principal").value(1000));
    }

    // ── GET /api/customers/{id}/accounts ─────────────────────────────────

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void listAccounts_returnsOkWithList() throws Exception {
        CustomerId ownerId = CustomerId.generate();
        when(customerAccount.listAccounts(any(), any())).thenReturn(List.of(
                usdAccount(ownerId), CheckingAccount.open(ownerId, Currency.EUR)));

        mockMvc.perform(get("/api/customers/{id}/accounts", ownerId.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[1].currency").value("EUR"));
    }

    // ── GET /api/accounts/{id}/balance ────────────────────────────────────

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void getBalance_returnsOk() throws Exception {
        when(customerAccount.getBalance(any(), any())).thenReturn(Money.of(250.0, Currency.USD));

        mockMvc.perform(get("/api/accounts/{id}/balance", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(250.0))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void getBalance_returnsNotFoundForUnknownAccount() throws Exception {
        doThrow(new AccountNotFoundException("Account not found"))
                .when(customerAccount).getBalance(any(), any());

        mockMvc.perform(get("/api/accounts/{id}/balance", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/accounts/{id}/deposit ───────────────────────────────────

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void deposit_returnsCreated() throws Exception {
        AccountId accountId = AccountId.generate();
        when(customerAccount.deposit(any())).thenReturn(depositTx(accountId));

        mockMvc.perform(post("/api/accounts/{id}/deposit", accountId.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100.00,"currency":"USD"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(100.0))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void deposit_returnsBadRequestOnNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/accounts/{id}/deposit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":-50.00,"currency":"USD"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerAccount);
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void deposit_returnsNotFoundForUnknownAccount() throws Exception {
        doThrow(new AccountNotFoundException("Account not found"))
                .when(customerAccount).deposit(any());

        mockMvc.perform(post("/api/accounts/{id}/deposit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100.00,"currency":"USD"}
                                """))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/accounts/{id}/withdraw ──────────────────────────────────

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void withdraw_returnsCreated() throws Exception {
        AccountId accountId = AccountId.generate();
        Transaction tx = Transaction.create(accountId, TransactionType.WITHDRAWAL,
                Money.of(50.0, Currency.USD), "Withdrawal");
        when(customerAccount.withdraw(any())).thenReturn(tx);

        mockMvc.perform(post("/api/accounts/{id}/withdraw", accountId.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50.00,"currency":"USD"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"));
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void withdraw_returnsUnprocessableEntityOnInsufficientFunds() throws Exception {
        doThrow(new InsufficientFundsException("Insufficient funds"))
                .when(customerAccount).withdraw(any());

        mockMvc.perform(post("/api/accounts/{id}/withdraw", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":9999.00,"currency":"USD"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── POST /api/accounts/{id}/transfer ──────────────────────────────────

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void transfer_returnsOk() throws Exception {
        doNothing().when(customerAccount).transfer(any());

        mockMvc.perform(post("/api/accounts/{id}/transfer", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAccountId":"%s","amount":200.00,"currency":"USD"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());

        verify(customerAccount).transfer(any());
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void transfer_returnsBadRequestOnMissingTarget() throws Exception {
        mockMvc.perform(post("/api/accounts/{id}/transfer", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":200.00,"currency":"USD"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerAccount);
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void transfer_returnsUnprocessableEntityOnInsufficientFunds() throws Exception {
        doThrow(new InsufficientFundsException("Insufficient funds"))
                .when(customerAccount).transfer(any());

        mockMvc.perform(post("/api/accounts/{id}/transfer", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAccountId":"%s","amount":9999.00,"currency":"USD"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── GET /api/accounts/{id}/transactions ───────────────────────────────

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void getTransactions_returnsOkWithList() throws Exception {
        AccountId accountId = AccountId.generate();
        when(customerAccount.getTransactions(any(), any())).thenReturn(List.of(
                depositTx(accountId),
                Transaction.create(accountId, TransactionType.WITHDRAWAL,
                        Money.of(30.0, Currency.USD), "Withdrawal")));

        mockMvc.perform(get("/api/accounts/{id}/transactions", accountId.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[1].type").value("WITHDRAWAL"));
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void getTransactions_returnsNotFoundForUnknownAccount() throws Exception {
        doThrow(new AccountNotFoundException("Account not found"))
                .when(customerAccount).getTransactions(any(), any());

        mockMvc.perform(get("/api/accounts/{id}/transactions", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransactions_returnsUnauthorizedWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/transactions", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── the controller's own job: forward the authenticated caller ────────

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void deposit_passesTheAuthenticatedCallerIntoTheCommand() throws Exception {
        Transaction tx = Transaction.create(AccountId.generate(), TransactionType.DEPOSIT,
                Money.of(50.0, Currency.USD), "Deposit");
        when(customerAccount.deposit(any())).thenReturn(tx);

        mockMvc.perform(post("/api/accounts/{id}/deposit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50.00,"currency":"USD"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CustomerAccountPort.DepositCommand> captor =
                ArgumentCaptor.forClass(CustomerAccountPort.DepositCommand.class);
        verify(customerAccount).deposit(captor.capture());
        assertThat(captor.getValue().callerId()).isEqualTo(CALLER);
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void deposit_returnsForbiddenWhenTheServiceDeniesAccess() throws Exception {
        doThrow(new UnauthorizedAccessException("Account does not belong to the caller"))
                .when(customerAccount).deposit(any());

        mockMvc.perform(post("/api/accounts/{id}/deposit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50.00,"currency":"USD"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void openChecking_opensTheAccountForTheAuthenticatedCaller() throws Exception {
        when(customerAccount.openChecking(any()))
                .thenReturn(CheckingAccount.open(CALLER, Currency.USD));

        mockMvc.perform(post("/api/accounts/checking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"USD"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CustomerAccountPort.OpenCheckingCommand> captor =
                ArgumentCaptor.forClass(CustomerAccountPort.OpenCheckingCommand.class);
        verify(customerAccount).openChecking(captor.capture());
        assertThat(captor.getValue().callerId()).isEqualTo(CALLER);
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    @DisplayName("a concurrent modification is reported as 409, without leaking entity internals")
    void deposit_returnsConflictOnOptimisticLockFailure() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(
                        "dev.kaldiroglu.hexagonal.ayvalikbank...AccountJpaEntity", UUID.randomUUID()))
                .when(customerAccount).deposit(any());

        mockMvc.perform(post("/api/accounts/{id}/deposit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50.00,"currency":"USD"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("The account was modified by another operation. Please retry."));
    }
}
