package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record OpenSavingsAccountRequest(
        @NotNull Currency currency,
        @NotNull @PositiveOrZero BigDecimal annualInterestRate) {}
