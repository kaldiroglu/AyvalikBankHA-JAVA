package dev.kaldiroglu.hexagonal.ayvalikbank.application.service;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotOperableException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.CustomerNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InsufficientFundsException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InvalidAccountOperationException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.LimitExceededException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.UnauthorizedAccessException;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.AccountAdministrationPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.BankSettingsPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.CustomerAccountPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.CustomerAccountPort.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.AccountAdministrationPort.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.BankSettingsPort.*;
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
        CustomerAccountPort,
        AccountAdministrationPort,
        BankSettingsPort {

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
    public CheckingAccount openChecking(OpenCheckingCommand command) {
        requireCustomerExists(command.callerId());
        CheckingAccount account = CheckingAccount.open(command.callerId(), command.currency(), command.overdraftLimit());
        return (CheckingAccount) accountRepository.save(account);
    }

    @Override
    public SavingsAccount openSavings(OpenSavingsCommand command) {
        requireCustomerExists(command.callerId());
        SavingsAccount account = SavingsAccount.open(command.callerId(), command.currency(), command.annualInterestRate());
        return (SavingsAccount) accountRepository.save(account);
    }

    @Override
    public TimeDepositAccount openTimeDeposit(OpenTimeDepositCommand command) {
        requireCustomerExists(command.callerId());
        TimeDepositAccount account = TimeDepositAccount.open(
                command.callerId(), command.currency(), command.principal(),
                LocalDate.now(), command.maturityDate(), command.annualInterestRate());
        return (TimeDepositAccount) accountRepository.save(account);
    }

    @Override
    public Transaction deposit(DepositCommand command) {
        Account account = findAccountOrThrow(command.accountId());
        requireOwner(account, command.callerId());
        Transaction tx;
        try {
            tx = account.deposit(command.amount());
        } catch (AccountRuleViolation e) {
            throw translate(e);
        }
        accountRepository.save(account);
        return transactionRepository.save(tx);
    }

    @Override
    public Transaction withdraw(WithdrawCommand command) {
        Account account = findAccountOrThrow(command.accountId());
        requireOwner(account, command.callerId());
        Customer owner = findCustomerOrThrow(account.getOwnerId());
        Transaction tx;
        try {
            transferDomainService.requireWithdrawalWithinLimit(command.amount(), owner.getTier());
            tx = account.withdraw(command.amount());
        } catch (AccountRuleViolation e) {
            throw translate(e);
        }
        accountRepository.save(account);
        return transactionRepository.save(tx);
    }

    @Override
    @Transactional(readOnly = true)
    public Money getBalance(CustomerId callerId, AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        requireOwner(account, callerId);
        return account.getBalance();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getTransactions(CustomerId callerId, AccountId accountId) {
        requireOwner(findAccountOrThrow(accountId), callerId);
        return transactionRepository.findByAccountId(accountId);
    }

    @Override
    public void transfer(TransferCommand command) {
        Account source = findAccountOrThrow(command.sourceAccountId());
        Account target = findAccountOrThrow(command.targetAccountId());
        requireOwner(source, command.callerId());
        // The TARGET is deliberately not ownership-checked: sending money to another customer is
        // the entire point of a transfer. shouldAllowTransferToAnotherCustomersAccount pins this,
        // so "tightening" the check to cover both accounts fails loudly instead of silently
        // breaking the product.
        Customer sourceOwner = findCustomerOrThrow(source.getOwnerId());

        Transaction outTx, inTx;
        try {
            transferDomainService.requireTransferWithinLimit(command.amount(), sourceOwner.getTier());
            boolean sameCustomer = source.getOwnerId().equals(target.getOwnerId());
            BigDecimal feePercent = settingsRepository.getTransferFeePercent();
            Money fee = transferDomainService.calculateFee(command.amount(), sameCustomer, feePercent, sourceOwner.getTier());
            outTx = source.transferOut(command.amount(), fee, target.getId().toString());
            inTx = target.transferIn(command.amount(), source.getId().toString());
        } catch (AccountRuleViolation e) {
            throw translate(e);
        }

        accountRepository.save(source);
        accountRepository.save(target);
        transactionRepository.save(outTx);
        transactionRepository.save(inTx);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> listAccounts(CustomerId callerId, CustomerId ownerId) {
        requireSelf(ownerId, callerId);
        requireCustomerExists(ownerId);
        return accountRepository.findByOwnerId(ownerId);
    }

    @Override
    public void freezeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try { account.freeze(); }
        catch (AccountRuleViolation e) { throw translate(e); }
        accountRepository.save(account);
    }

    @Override
    public void unfreezeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try { account.unfreeze(); }
        catch (AccountRuleViolation e) { throw translate(e); }
        accountRepository.save(account);
    }

    @Override
    public void closeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try { account.close(); }
        catch (AccountRuleViolation e) { throw translate(e); }
        accountRepository.save(account);
    }

    @Override
    public Transaction accrueInterest(AccrueInterestCommand command) {
        Account account = findAccountOrThrow(command.accountId());
        if (!(account instanceof SavingsAccount savings))
            throw new InvalidAccountOperationException("Account is not a savings account");
        Transaction tx;
        try { tx = savings.accrueInterest(command.month()); }
        catch (AccountRuleViolation e) { throw translate(e); }
        accountRepository.save(savings);
        return transactionRepository.save(tx);
    }

    @Override
    public Transaction mature(MatureCommand command) {
        Account account = findAccountOrThrow(command.accountId());
        if (!(account instanceof TimeDepositAccount td))
            throw new InvalidAccountOperationException("Account is not a time deposit");
        Transaction tx;
        try { tx = td.mature(LocalDate.now()); }
        catch (AccountRuleViolation e) { throw translate(e); }
        accountRepository.save(td);
        return transactionRepository.save(tx);
    }

    @Override
    public void setTransferFee(SetTransferFeeCommand command) {
        if (command.feePercent().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Transfer fee percent cannot be negative");
        settingsRepository.setTransferFeePercent(command.feePercent());
    }

    /**
     * Maps a domain refusal to the application exception that carries its HTTP meaning.
     *
     * <p>No {@code default} clause: {@link AccountRuleViolation} is sealed, so the compiler proves
     * this switch total. Adding a fifth refusal type breaks the build here until it is handled —
     * the same technique {@code AccountPersistenceMapper} uses over the sealed {@code Account}
     * hierarchy.
     *
     * <p>Catching {@code AccountRuleViolation} rather than {@code IllegalStateException} is what
     * stops a JDK or framework exception being reported to the client as a 422 business error.
     */
    private RuntimeException translate(AccountRuleViolation violation) {
        return switch (violation) {
            case AccountNotActiveException e         -> new AccountNotOperableException(e.getMessage());
            case InsufficientBalanceException e      -> new InsufficientFundsException(e.getMessage());
            case OperationNotPermittedException e    -> new InvalidAccountOperationException(e.getMessage());
            case TransactionLimitExceededException e -> new LimitExceededException(e.getMessage());
        };
    }

    /**
     * The caller must own the account. The domain supplies the fact via
     * {@link Account#isOwnedBy}; deciding to refuse is an application concern, because a "caller"
     * is a session notion the domain knows nothing about.
     *
     * <p>The message names neither the account nor its owner — an error response is not the place
     * to confirm which accounts exist.
     */
    private void requireOwner(Account account, CustomerId callerId) {
        if (!account.isOwnedBy(callerId))
            throw new UnauthorizedAccessException("Account does not belong to the caller");
    }

    /** The caller may only act on their own customer record. */
    private void requireSelf(CustomerId subject, CustomerId callerId) {
        if (!subject.equals(callerId))
            throw new UnauthorizedAccessException("Callers may only act on their own customer record");
    }

    private void requireCustomerExists(CustomerId id) {
        if (!customerRepository.existsById(id))
            throw new CustomerNotFoundException("Customer not found: " + id);
    }

    private Customer findCustomerOrThrow(CustomerId id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + id));
    }

    private Account findAccountOrThrow(AccountId accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }
}
