package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OpenTimeDepositAccountRequest(
        @NotNull Currency currency,
        @NotNull @Positive BigDecimal principal,
        @NotNull LocalDate maturityDate,
        @NotNull @PositiveOrZero BigDecimal annualInterestRate) {}
