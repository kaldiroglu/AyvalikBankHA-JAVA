package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Money;

import java.math.BigDecimal;

public record BalanceResponse(BigDecimal amount, String currency) {
    public static BalanceResponse from(Money money) {
        return new BalanceResponse(money.amount(), money.currency().name());
    }
}
