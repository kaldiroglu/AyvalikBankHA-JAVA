package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Transaction;

import java.util.List;

public interface GetTransactionsUseCase {
    List<Transaction> getTransactions(AccountId accountId);
}
