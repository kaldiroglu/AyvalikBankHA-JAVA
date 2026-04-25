package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        String id,
        String accountId,
        String type,
        BigDecimal amount,
        String currency,
        LocalDateTime timestamp,
        String description
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId().toString(),
                tx.getAccountId().toString(),
                tx.getType().name(),
                tx.getAmount().amount(),
                tx.getAmount().currency().name(),
                tx.getTimestamp(),
                tx.getDescription()
        );
    }
}
