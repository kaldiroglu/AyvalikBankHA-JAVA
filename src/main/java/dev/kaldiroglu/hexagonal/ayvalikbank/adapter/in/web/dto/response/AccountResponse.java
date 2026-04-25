package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountResponse(
        String id,
        String ownerId,
        String currency,
        BigDecimal balance,
        String status,
        String type,
        BigDecimal overdraftLimit,
        BigDecimal interestRate,
        LocalDate lastAccrualDate,
        BigDecimal principal,
        LocalDate openedOn,
        LocalDate maturityDate,
        Boolean matured) {

    public static AccountResponse from(Account account) {
        String id = account.getId().toString();
        String ownerId = account.getOwnerId().toString();
        String currency = account.getCurrency().name();
        BigDecimal balance = account.getBalance().amount();
        String status = account.getStatus().name();
        String type = account.type().name();
        return switch (account) {
            case CheckingAccount c -> new AccountResponse(
                    id, ownerId, currency, balance, status, type,
                    c.getOverdraftLimit().amount(),
                    null, null, null, null, null, null);
            case SavingsAccount s -> new AccountResponse(
                    id, ownerId, currency, balance, status, type,
                    null,
                    s.getAnnualInterestRate(), s.getLastAccrualDate(),
                    null, null, null, null);
            case TimeDepositAccount t -> new AccountResponse(
                    id, ownerId, currency, balance, status, type,
                    null,
                    t.getAnnualInterestRate(), null,
                    t.getPrincipal().amount(), t.getOpenedOn(), t.getMaturityDate(), t.isMatured());
        };
    }
}
