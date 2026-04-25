package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record MoneyOperationRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull Currency currency
) {}
