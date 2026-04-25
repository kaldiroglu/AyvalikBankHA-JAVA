package dev.kaldiroglu.hexagonal.ayvalikbank.application.service;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotOperableException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.CustomerNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InvalidAccountOperationException;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.account.AccountRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer.CustomerRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.account.SettingsRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.account.TransactionRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.account.TransferDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class AccountApplicationService implements
        OpenCheckingAccountUseCase,
        OpenSavingsAccountUseCase,
        OpenTimeDepositAccountUseCase,
        DepositMoneyUseCase,
        WithdrawMoneyUseCase,
        GetBalanceUseCase,
        GetTransactionsUseCase,
        TransferMoneyUseCase,
        ListAccountsUseCase,
        FreezeAccountUseCase,
        UnfreezeAccountUseCase,
        CloseAccountUseCase,
        AccrueInterestUseCase,
        MatureTimeDepositUseCase {

    private final AccountRepositoryPort accountRepository;
    private final CustomerRepositoryPort customerRepository;
    private final TransactionRepositoryPort transactionRepository;
    private final SettingsRepositoryPort settingsRepository;
    private final TransferDomainService transferDomainService;

    public AccountApplicationService(AccountRepositoryPort accountRepository,
                                     CustomerRepositoryPort customerRepository,
                                     TransactionRepositoryPort transactionRepository,
                                     SettingsRepositoryPort settingsRepository,
                                     TransferDomainService transferDomainService) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.settingsRepository = settingsRepository;
        this.transferDomainService = transferDomainService;
    }

    @Override
    public CheckingAccount openChecking(OpenCheckingAccountUseCase.Command command) {
        requireCustomerExists(command.ownerId());
        CheckingAccount account = CheckingAccount.open(command.ownerId(), command.currency(), command.overdraftLimit());
        return (CheckingAccount) accountRepository.save(account);
    }

    @Override
    public SavingsAccount openSavings(OpenSavingsAccountUseCase.Command command) {
        requireCustomerExists(command.ownerId());
        SavingsAccount account = SavingsAccount.open(command.ownerId(), command.currency(), command.annualInterestRate());
        return (SavingsAccount) accountRepository.save(account);
    }

    @Override
    public TimeDepositAccount openTimeDeposit(OpenTimeDepositAccountUseCase.Command command) {
        requireCustomerExists(command.ownerId());
        TimeDepositAccount account = TimeDepositAccount.open(
                command.ownerId(), command.currency(), command.principal(),
                LocalDate.now(), command.maturityDate(), command.annualInterestRate());
        return (TimeDepositAccount) accountRepository.save(account);
    }

    @Override
    public Transaction deposit(DepositMoneyUseCase.Command command) {
        Account account = findAccountOrThrow(command.accountId());
        Transaction tx;
        try {
            tx = account.deposit(command.amount());
        } catch (IllegalStateException e) {
            throw new InvalidAccountOperationException(e.getMessage());
        }
        accountRepository.save(account);
        return transactionRepository.save(tx);
    }

    @Override
    public Transaction withdraw(WithdrawMoneyUseCase.Command command) {
        Account account = findAccountOrThrow(command.accountId());
        Transaction tx;
        try {
            tx = account.withdraw(command.amount());
        } catch (IllegalStateException e) {
            throw new InvalidAccountOperationException(e.getMessage());
        }
        accountRepository.save(account);
        return transactionRepository.save(tx);
    }

    @Override
    @Transactional(readOnly = true)
    public Money getBalance(AccountId accountId) {
        return findAccountOrThrow(accountId).getBalance();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getTransactions(AccountId accountId) {
        findAccountOrThrow(accountId);
        return transactionRepository.findByAccountId(accountId);
    }

    @Override
    public void transfer(TransferMoneyUseCase.Command command) {
        Account source = findAccountOrThrow(command.sourceAccountId());
        Account target = findAccountOrThrow(command.targetAccountId());

        boolean sameCustomer = source.getOwnerId().equals(target.getOwnerId());
        BigDecimal feePercent = settingsRepository.getTransferFeePercent();
        Money fee = transferDomainService.calculateFee(command.amount(), sameCustomer, feePercent);

        Transaction outTx, inTx;
        try {
            outTx = source.transferOut(command.amount(), fee, target.getId().toString());
            inTx = target.transferIn(command.amount(), source.getId().toString());
        } catch (IllegalStateException e) {
            throw new InvalidAccountOperationException(e.getMessage());
        }

        accountRepository.save(source);
        accountRepository.save(target);
        transactionRepository.save(outTx);
        transactionRepository.save(inTx);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> listAccounts(CustomerId ownerId) {
        requireCustomerExists(ownerId);
        return accountRepository.findByOwnerId(ownerId);
    }

    @Override
    public void freezeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try { account.freeze(); }
        catch (IllegalStateException e) { throw new AccountNotOperableException(e.getMessage()); }
        accountRepository.save(account);
    }

    @Override
    public void unfreezeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try { account.unfreeze(); }
        catch (IllegalStateException e) { throw new AccountNotOperableException(e.getMessage()); }
        accountRepository.save(account);
    }

    @Override
    public void closeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try { account.close(); }
        catch (IllegalStateException e) { throw new AccountNotOperableException(e.getMessage()); }
        accountRepository.save(account);
    }

    @Override
    public Transaction accrueInterest(AccrueInterestUseCase.Command command) {
        Account account = findAccountOrThrow(command.accountId());
        if (!(account instanceof SavingsAccount savings))
            throw new InvalidAccountOperationException("Account is not a savings account");
        Transaction tx;
        try { tx = savings.accrueInterest(command.month()); }
        catch (IllegalStateException e) { throw new InvalidAccountOperationException(e.getMessage()); }
        accountRepository.save(savings);
        return transactionRepository.save(tx);
    }

    @Override
    public Transaction mature(MatureTimeDepositUseCase.Command command) {
        Account account = findAccountOrThrow(command.accountId());
        if (!(account instanceof TimeDepositAccount td))
            throw new InvalidAccountOperationException("Account is not a time deposit");
        Transaction tx;
        try { tx = td.mature(LocalDate.now()); }
        catch (IllegalStateException e) { throw new InvalidAccountOperationException(e.getMessage()); }
        accountRepository.save(td);
        return transactionRepository.save(tx);
    }

    private void requireCustomerExists(CustomerId id) {
        if (!customerRepository.existsById(id))
            throw new CustomerNotFoundException("Customer not found: " + id);
    }

    private Account findAccountOrThrow(AccountId accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }
}
