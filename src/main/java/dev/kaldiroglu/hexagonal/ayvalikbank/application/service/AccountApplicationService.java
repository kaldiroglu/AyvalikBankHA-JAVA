package dev.kaldiroglu.hexagonal.ayvalikbank.application.service;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotOperableException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.CustomerNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.AccountRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.CustomerRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.SettingsRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.TransactionRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.TransferDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AccountApplicationService implements
        CreateAccountUseCase,
        DepositMoneyUseCase,
        WithdrawMoneyUseCase,
        GetBalanceUseCase,
        GetTransactionsUseCase,
        TransferMoneyUseCase,
        ListAccountsUseCase,
        FreezeAccountUseCase,
        UnfreezeAccountUseCase,
        CloseAccountUseCase {

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
    public Account createAccount(CreateAccountUseCase.Command command) {
        if (!customerRepository.existsById(command.ownerId()))
            throw new CustomerNotFoundException("Customer not found: " + command.ownerId());
        Account account = CheckingAccount.open(command.ownerId(), command.currency());
        return accountRepository.save(account);
    }

    @Override
    public Transaction deposit(DepositMoneyUseCase.Command command) {
        Account account = findAccountOrThrow(command.accountId());
        Transaction tx = account.deposit(command.amount());
        accountRepository.save(account);
        return transactionRepository.save(tx);
    }

    @Override
    public Transaction withdraw(WithdrawMoneyUseCase.Command command) {
        Account account = findAccountOrThrow(command.accountId());
        Transaction tx = account.withdraw(command.amount());
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
        findAccountOrThrow(accountId); // existence check
        return transactionRepository.findByAccountId(accountId);
    }

    @Override
    public void transfer(TransferMoneyUseCase.Command command) {
        Account source = findAccountOrThrow(command.sourceAccountId());
        Account target = findAccountOrThrow(command.targetAccountId());

        boolean sameCustomer = source.getOwnerId().equals(target.getOwnerId());
        java.math.BigDecimal feePercent = settingsRepository.getTransferFeePercent();
        Money fee = transferDomainService.calculateFee(command.amount(), sameCustomer, feePercent);

        Transaction outTx = source.transferOut(command.amount(), fee, target.getId().toString());
        Transaction inTx = target.transferIn(command.amount(), source.getId().toString());

        accountRepository.save(source);
        accountRepository.save(target);
        transactionRepository.save(outTx);
        transactionRepository.save(inTx);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> listAccounts(CustomerId ownerId) {
        if (!customerRepository.existsById(ownerId))
            throw new CustomerNotFoundException("Customer not found: " + ownerId);
        return accountRepository.findByOwnerId(ownerId);
    }

    @Override
    public void freezeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try {
            account.freeze();
        } catch (IllegalStateException e) {
            throw new AccountNotOperableException(e.getMessage());
        }
        accountRepository.save(account);
    }

    @Override
    public void unfreezeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try {
            account.unfreeze();
        } catch (IllegalStateException e) {
            throw new AccountNotOperableException(e.getMessage());
        }
        accountRepository.save(account);
    }

    @Override
    public void closeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try {
            account.close();
        } catch (IllegalStateException e) {
            throw new AccountNotOperableException(e.getMessage());
        }
        accountRepository.save(account);
    }

    private Account findAccountOrThrow(AccountId accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }
}
