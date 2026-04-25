package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class AccountJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "type", nullable = false, length = 16)
    private String type;

    // Checking
    @Column(name = "overdraft_limit", precision = 19, scale = 2)
    private BigDecimal overdraftLimit;

    // Savings
    @Column(name = "interest_rate", precision = 10, scale = 6)
    private BigDecimal interestRate;

    @Column(name = "last_accrual_date")
    private LocalDate lastAccrualDate;

    // Time deposit
    @Column(name = "principal", precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(name = "opened_on")
    private LocalDate openedOn;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "matured")
    private Boolean matured;

    public AccountJpaEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getOverdraftLimit() { return overdraftLimit; }
    public void setOverdraftLimit(BigDecimal overdraftLimit) { this.overdraftLimit = overdraftLimit; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public LocalDate getLastAccrualDate() { return lastAccrualDate; }
    public void setLastAccrualDate(LocalDate lastAccrualDate) { this.lastAccrualDate = lastAccrualDate; }
    public BigDecimal getPrincipal() { return principal; }
    public void setPrincipal(BigDecimal principal) { this.principal = principal; }
    public LocalDate getOpenedOn() { return openedOn; }
    public void setOpenedOn(LocalDate openedOn) { this.openedOn = openedOn; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public Boolean getMatured() { return matured; }
    public void setMatured(Boolean matured) { this.matured = matured; }
}
