package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InsufficientFundsException;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.BankUserDetailsService;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.SecurityConfig;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.*;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean BankUserDetailsService userDetailsService;
    @MockitoBean OpenCheckingAccountUseCase openChecking;
    @MockitoBean OpenSavingsAccountUseCase openSavings;
    @MockitoBean OpenTimeDepositAccountUseCase openTimeDeposit;
    @MockitoBean DepositMoneyUseCase depositMoney;
    @MockitoBean WithdrawMoneyUseCase withdrawMoney;
    @MockitoBean GetBalanceUseCase getBalance;
    @MockitoBean GetTransactionsUseCase getTransactions;
    @MockitoBean TransferMoneyUseCase transferMoney;
    @MockitoBean ListAccountsUseCase listAccounts;

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
    @WithMockUser(roles = "CUSTOMER")
    void openChecking_returnsCreated() throws Exception {
        CustomerId ownerId = CustomerId.generate();
        CheckingAccount account = CheckingAccount.open(ownerId, Currency.USD);
        when(openChecking.openChecking(any())).thenReturn(account);

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
    @WithMockUser(roles = "CUSTOMER")
    void openChecking_returnsBadRequestOnMissingCurrency() throws Exception {
        mockMvc.perform(post("/api/accounts/checking")
                        .param("ownerId", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(openChecking);
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
    @WithMockUser(roles = "CUSTOMER")
    void openSavings_returnsCreated() throws Exception {
        CustomerId ownerId = CustomerId.generate();
        SavingsAccount account = SavingsAccount.open(ownerId, Currency.EUR, new BigDecimal("0.03"));
        when(openSavings.openSavings(any())).thenReturn(account);

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
    @WithMockUser(roles = "CUSTOMER")
    void openTimeDeposit_returnsCreated() throws Exception {
        CustomerId ownerId = CustomerId.generate();
        TimeDepositAccount account = TimeDepositAccount.open(
                ownerId, Currency.USD,
                Money.of(1000.0, Currency.USD),
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                new BigDecimal("0.05"));
        when(openTimeDeposit.openTimeDeposit(any())).thenReturn(account);

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
    @WithMockUser(roles = "CUSTOMER")
    void listAccounts_returnsOkWithList() throws Exception {
        CustomerId ownerId = CustomerId.generate();
        when(listAccounts.listAccounts(any())).thenReturn(List.of(
                usdAccount(ownerId), CheckingAccount.open(ownerId, Currency.EUR)));

        mockMvc.perform(get("/api/customers/{id}/accounts", ownerId.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[1].currency").value("EUR"));
    }

    // ── GET /api/accounts/{id}/balance ────────────────────────────────────

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getBalance_returnsOk() throws Exception {
        when(getBalance.getBalance(any())).thenReturn(Money.of(250.0, Currency.USD));

        mockMvc.perform(get("/api/accounts/{id}/balance", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(250.0))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getBalance_returnsNotFoundForUnknownAccount() throws Exception {
        doThrow(new AccountNotFoundException("Account not found"))
                .when(getBalance).getBalance(any());

        mockMvc.perform(get("/api/accounts/{id}/balance", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/accounts/{id}/deposit ───────────────────────────────────

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void deposit_returnsCreated() throws Exception {
        AccountId accountId = AccountId.generate();
        when(depositMoney.deposit(any())).thenReturn(depositTx(accountId));

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
    @WithMockUser(roles = "CUSTOMER")
    void deposit_returnsBadRequestOnNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/accounts/{id}/deposit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":-50.00,"currency":"USD"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(depositMoney);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void deposit_returnsNotFoundForUnknownAccount() throws Exception {
        doThrow(new AccountNotFoundException("Account not found"))
                .when(depositMoney).deposit(any());

        mockMvc.perform(post("/api/accounts/{id}/deposit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100.00,"currency":"USD"}
                                """))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/accounts/{id}/withdraw ──────────────────────────────────

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void withdraw_returnsCreated() throws Exception {
        AccountId accountId = AccountId.generate();
        Transaction tx = Transaction.create(accountId, TransactionType.WITHDRAWAL,
                Money.of(50.0, Currency.USD), "Withdrawal");
        when(withdrawMoney.withdraw(any())).thenReturn(tx);

        mockMvc.perform(post("/api/accounts/{id}/withdraw", accountId.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50.00,"currency":"USD"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void withdraw_returnsUnprocessableEntityOnInsufficientFunds() throws Exception {
        doThrow(new InsufficientFundsException("Insufficient funds"))
                .when(withdrawMoney).withdraw(any());

        mockMvc.perform(post("/api/accounts/{id}/withdraw", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":9999.00,"currency":"USD"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── POST /api/accounts/{id}/transfer ──────────────────────────────────

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void transfer_returnsOk() throws Exception {
        doNothing().when(transferMoney).transfer(any());

        mockMvc.perform(post("/api/accounts/{id}/transfer", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAccountId":"%s","amount":200.00,"currency":"USD"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());

        verify(transferMoney).transfer(any());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void transfer_returnsBadRequestOnMissingTarget() throws Exception {
        mockMvc.perform(post("/api/accounts/{id}/transfer", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":200.00,"currency":"USD"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transferMoney);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void transfer_returnsUnprocessableEntityOnInsufficientFunds() throws Exception {
        doThrow(new InsufficientFundsException("Insufficient funds"))
                .when(transferMoney).transfer(any());

        mockMvc.perform(post("/api/accounts/{id}/transfer", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetAccountId":"%s","amount":9999.00,"currency":"USD"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── GET /api/accounts/{id}/transactions ───────────────────────────────

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getTransactions_returnsOkWithList() throws Exception {
        AccountId accountId = AccountId.generate();
        when(getTransactions.getTransactions(any())).thenReturn(List.of(
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
    @WithMockUser(roles = "CUSTOMER")
    void getTransactions_returnsNotFoundForUnknownAccount() throws Exception {
        doThrow(new AccountNotFoundException("Account not found"))
                .when(getTransactions).getTransactions(any());

        mockMvc.perform(get("/api/accounts/{id}/transactions", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransactions_returnsUnauthorizedWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/transactions", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
