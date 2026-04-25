package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank String targetAccountId,
        @NotNull @Positive BigDecimal amount,
        @NotNull Currency currency
) {}
