package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Transaction;

import java.util.List;

public interface TransactionRepositoryPort {
    Transaction save(Transaction transaction);
    List<Transaction> findByAccountId(AccountId accountId);
}
