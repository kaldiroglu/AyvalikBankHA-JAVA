package dev.kaldiroglu.hexagonal.ayvalikbank.application.service;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotOperableException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.CustomerNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InvalidAccountOperationException;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.AccrueInterestUseCase;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.DepositMoneyUseCase;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.MatureTimeDepositUseCase;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.OpenCheckingAccountUseCase;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.OpenSavingsAccountUseCase;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.OpenTimeDepositAccountUseCase;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.TransferMoneyUseCase;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.WithdrawMoneyUseCase;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.AccountRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.CustomerRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.SettingsRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.TransactionRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.TransferDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceTest {

    @Mock private AccountRepositoryPort accountRepository;
    @Mock private CustomerRepositoryPort customerRepository;
    @Mock private TransactionRepositoryPort transactionRepository;
    @Mock private SettingsRepositoryPort settingsRepository;

    private AccountApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AccountApplicationService(
                accountRepository, customerRepository,
                transactionRepository, settingsRepository,
                new TransferDomainService());
    }

    // ── open* ─────────────────────────────────────────────────────────────

    @Test
    void shouldOpenCheckingAccountForExistingCustomer() {
        CustomerId ownerId = CustomerId.generate();
        when(customerRepository.existsById(ownerId)).thenReturn(true);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CheckingAccount account = service.openChecking(new OpenCheckingAccountUseCase.Command(
                ownerId, Currency.USD, Money.of(100.0, Currency.USD)));

        assertThat(account.type()).isEqualTo(AccountType.CHECKING);
        assertThat(account.getCurrency()).isEqualTo(Currency.USD);
        assertThat(account.getOverdraftLimit().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldOpenSavingsAccountForExistingCustomer() {
        CustomerId ownerId = CustomerId.generate();
        when(customerRepository.existsById(ownerId)).thenReturn(true);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SavingsAccount account = service.openSavings(new OpenSavingsAccountUseCase.Command(
                ownerId, Currency.EUR, new BigDecimal("0.03")));

        assertThat(account.type()).isEqualTo(AccountType.SAVINGS);
        assertThat(account.getAnnualInterestRate()).isEqualByComparingTo("0.03");
    }

    @Test
    void shouldOpenTimeDepositAccountForExistingCustomer() {
        CustomerId ownerId = CustomerId.generate();
        when(customerRepository.existsById(ownerId)).thenReturn(true);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TimeDepositAccount account = service.openTimeDeposit(new OpenTimeDepositAccountUseCase.Command(
                ownerId, Currency.USD, Money.of(1000.0, Currency.USD),
                LocalDate.now().plusYears(1), new BigDecimal("0.05")));

        assertThat(account.type()).isEqualTo(AccountType.TIME_DEPOSIT);
        assertThat(account.getBalance().amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void shouldThrowCustomerNotFoundWhenOpeningCheckingForMissingOwner() {
        CustomerId ownerId = CustomerId.generate();
        when(customerRepository.existsById(ownerId)).thenReturn(false);

        assertThatThrownBy(() -> service.openChecking(new OpenCheckingAccountUseCase.Command(
                ownerId, Currency.EUR, Money.zero(Currency.EUR))))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    // ── deposit / withdraw / transfer ─────────────────────────────────────

    @Test
    void shouldDepositMoneyToAccount() {
        CustomerId ownerId = CustomerId.generate();
        Account account = CheckingAccount.open(ownerId, Currency.USD);
        AccountId accountId = account.getId();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = service.deposit(new DepositMoneyUseCase.Command(accountId, Money.of(200.0, Currency.USD)));

        assertThat(tx.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(account.getBalance().amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void shouldThrowAccountNotFoundOnDepositToMissingAccount() {
        AccountId id = AccountId.generate();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deposit(
                new DepositMoneyUseCase.Command(id, Money.of(100.0, Currency.USD))))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void shouldTransferBetweenAccountsOfSameCustomerFreeOfCharge() {
        CustomerId ownerId = CustomerId.generate();
        Account source = CheckingAccount.open(ownerId, Currency.USD);
        Account target = CheckingAccount.open(ownerId, Currency.USD);
        source.deposit(Money.of(500.0, Currency.USD));

        when(accountRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(settingsRepository.getTransferFeePercent()).thenReturn(new BigDecimal("1.0"));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(new TransferMoneyUseCase.Command(
                source.getId(), target.getId(), Money.of(200.0, Currency.USD)));

        // Same customer — no fee
        assertThat(source.getBalance().amount()).isEqualByComparingTo("300.00");
        assertThat(target.getBalance().amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void shouldDeductFeeForTransferBetweenDifferentCustomers() {
        CustomerId owner1 = CustomerId.generate();
        CustomerId owner2 = CustomerId.generate();
        Account source = CheckingAccount.open(owner1, Currency.USD);
        Account target = CheckingAccount.open(owner2, Currency.USD);
        source.deposit(Money.of(1000.0, Currency.USD));

        when(accountRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(settingsRepository.getTransferFeePercent()).thenReturn(new BigDecimal("1.0"));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(new TransferMoneyUseCase.Command(
                source.getId(), target.getId(), Money.of(200.0, Currency.USD)));

        // 200 transferred + 2 fee (1%) = 202 deducted from source
        assertThat(source.getBalance().amount()).isEqualByComparingTo("798.00");
        assertThat(target.getBalance().amount()).isEqualByComparingTo("200.00");
    }

    // ── freeze / unfreeze / close ─────────────────────────────────────────

    @Test
    void shouldFreezeAccount() {
        CustomerId ownerId = CustomerId.generate();
        Account account = CheckingAccount.open(ownerId, Currency.USD);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.freezeAccount(account.getId());

        assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);
        verify(accountRepository).save(account);
    }

    @Test
    void shouldUnfreezeAccount() {
        CustomerId ownerId = CustomerId.generate();
        Account account = CheckingAccount.open(ownerId, Currency.USD);
        account.freeze();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.unfreezeAccount(account.getId());

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void shouldCloseAccount() {
        CustomerId ownerId = CustomerId.generate();
        Account account = CheckingAccount.open(ownerId, Currency.USD);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.closeAccount(account.getId());

        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void shouldThrowAccountNotOperableWhenFreezingClosedAccount() {
        CustomerId ownerId = CustomerId.generate();
        Account account = CheckingAccount.open(ownerId, Currency.USD);
        account.close();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.freezeAccount(account.getId()))
                .isInstanceOf(AccountNotOperableException.class);
    }

    @Test
    void shouldThrowAccountNotFoundWhenFreezingMissingAccount() {
        AccountId id = AccountId.generate();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.freezeAccount(id))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void shouldThrowOnWithdrawExceedingBalance() {
        CustomerId ownerId = CustomerId.generate();
        Account account = CheckingAccount.open(ownerId, Currency.USD);
        account.deposit(Money.of(100.0, Currency.USD));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.withdraw(
                new WithdrawMoneyUseCase.Command(account.getId(), Money.of(500.0, Currency.USD))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient");
    }

    // ── accrue interest / mature ──────────────────────────────────────────

    @Test
    void shouldAccrueInterestOnSavingsAccount() {
        CustomerId ownerId = CustomerId.generate();
        SavingsAccount account = SavingsAccount.open(ownerId, Currency.USD, new BigDecimal("0.12"));
        account.deposit(Money.of(1000.0, Currency.USD));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = service.accrueInterest(new AccrueInterestUseCase.Command(
                account.getId(), YearMonth.of(2026, 4)));

        assertThat(tx.getType()).isEqualTo(TransactionType.INTEREST);
        assertThat(tx.getAmount().amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldRejectAccrueInterestOnNonSavingsAccount() {
        CustomerId ownerId = CustomerId.generate();
        CheckingAccount account = CheckingAccount.open(ownerId, Currency.USD);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.accrueInterest(new AccrueInterestUseCase.Command(
                account.getId(), YearMonth.of(2026, 4))))
                .isInstanceOf(InvalidAccountOperationException.class)
                .hasMessageContaining("not a savings");
    }

    @Test
    void shouldMatureTimeDepositOnOrAfterMaturityDate() {
        CustomerId ownerId = CustomerId.generate();
        // Open a deposit that matured yesterday so LocalDate.now() in the service is past maturity.
        LocalDate openedOn = LocalDate.now().minusYears(1).minusDays(1);
        LocalDate maturityDate = LocalDate.now().minusDays(1);
        TimeDepositAccount account = new TimeDepositAccount(
                AccountId.generate(), ownerId, Currency.USD,
                Money.of(1000.0, Currency.USD), AccountStatus.ACTIVE,
                Money.of(1000.0, Currency.USD), openedOn, maturityDate,
                new BigDecimal("0.05"), false);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = service.mature(new MatureTimeDepositUseCase.Command(account.getId()));

        assertThat(tx.getType()).isEqualTo(TransactionType.INTEREST);
        assertThat(account.isMatured()).isTrue();
    }

    @Test
    void shouldRejectMatureOnNonTimeDepositAccount() {
        CustomerId ownerId = CustomerId.generate();
        CheckingAccount account = CheckingAccount.open(ownerId, Currency.USD);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.mature(new MatureTimeDepositUseCase.Command(account.getId())))
                .isInstanceOf(InvalidAccountOperationException.class)
                .hasMessageContaining("not a time deposit");
    }
}
