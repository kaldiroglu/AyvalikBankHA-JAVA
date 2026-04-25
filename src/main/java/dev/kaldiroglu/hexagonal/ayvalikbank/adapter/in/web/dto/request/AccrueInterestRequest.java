package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;

public record AccrueInterestRequest(@NotNull YearMonth month) {}
