package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Account;

import java.math.BigDecimal;

public record AccountResponse(String id, String ownerId, String currency, BigDecimal balance, String status) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId().toString(),
                account.getOwnerId().toString(),
                account.getCurrency().name(),
                account.getBalance().amount(),
                account.getStatus().name()
        );
    }
}
