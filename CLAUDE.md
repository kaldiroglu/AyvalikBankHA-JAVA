# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Ayvalık Bank HA-1** — a hexagonal-architecture banking application in Java 25 / Spring Boot 3.4.

## Commands

```bash
# Start local PostgreSQL
docker compose up -d

# Build & test
mvn clean verify

# Run a single test class
mvn test -Dtest=CustomerApplicationServiceTest

# Run the application
mvn spring-boot:run
```

## Architecture

The project strictly enforces the Dependency Rule: all dependencies point inward toward the domain.

```
adapter/in/web/          → REST controllers + request/response DTOs
adapter/out/persistence/ → JPA entities (DTOs), Spring Data repos, mappers, adapters
adapter/out/security/    → BCryptPasswordHasherAdapter
application/service/     → CustomerApplicationService, AccountApplicationService
config/                  → SecurityConfig (Spring beans), BankUserDetailsService
domain/model/            → Entities (Customer, Account, Transaction) + value objects (records)
domain/service/          → PasswordValidationService, TransferDomainService (pure Java)
domain/port/in/          → Use-case interfaces with nested Command records
domain/port/out/         → Repository and infrastructure interfaces
```

**Domain layer has zero Spring/JPA imports.** Domain services are instantiated as `@Bean` in `SecurityConfig` so they can be injected without becoming Spring components.

## Key Design Decisions

- **Value objects as records**: `CustomerId`, `AccountId`, `TransactionId`, `Money`, `Password` — immutable, self-validating.
- **Sealed account hierarchy**: `Account` is a `sealed abstract class` permitting `CheckingAccount`, `SavingsAccount`, and `TimeDepositAccount`. Each subtype overrides `deposit`/`withdraw`/`transferOut` with its own rules: `CheckingAccount` allows configurable overdraft; `SavingsAccount` rejects overdraft and supports monthly interest accrual via `accrueInterest(YearMonth)`; `TimeDepositAccount` locks principal at open and rejects withdrawals until `mature(LocalDate)` is called on or after the maturity date.
- **Three account types**: `CheckingAccount` carries an `overdraftLimit` — withdrawals may take the balance negative up to that limit. `SavingsAccount` carries an `annualInterestRate` and a `lastAccrualDate`; `accrueInterest` credits monthly interest and works on ACTIVE or FROZEN accounts (but not CLOSED). `TimeDepositAccount` locks the principal at open; `deposit` is rejected; `mature` must be called on or after the maturity date and credits the annual interest; withdrawals are then permitted.
- **Password history**: `Customer` holds `currentPassword` + up to 3 previous hashes. Reuse checking (BCrypt) lives in `CustomerApplicationService` since it requires the `PasswordHasherPort`.
- **Transfer fee**: free for same-customer transfers; `TransferDomainService.calculateFee()` applies the admin-configured percentage for cross-customer transfers. Fee stored in `settings` table.
- **Account status**: `AccountStatus` enum (`ACTIVE`, `FROZEN`, `CLOSED`) is a state machine inside `Account`. All mutating operations (`deposit`, `withdraw`, `transferOut`, `transferIn`) call `requireActive()` first. Transitions: `freeze()`, `unfreeze()`, `close()` — `CLOSED` is terminal. Invalid transitions throw `IllegalStateException`; the application service converts this to `AccountNotOperableException` → HTTP 422.
- **Authentication**: HTTP Basic Auth via Spring Security. Credentials loaded from the `customers` table by `BankUserDetailsService`. Roles: `ADMIN`, `CUSTOMER`.
- **JPA DTOs**: `CustomerJpaEntity`, `AccountJpaEntity`, `TransactionJpaEntity`, `SettingsJpaEntity`, `PasswordHistoryJpaEntity` — none of these cross the persistence adapter boundary.

## REST API Summary

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST | `/api/admin/customers` | ADMIN | Create customer |
| DELETE | `/api/admin/customers/{id}` | ADMIN | Delete customer |
| GET | `/api/admin/customers` | ADMIN | List all customers |
| PUT | `/api/admin/settings/transfer-fee` | ADMIN | Set transfer fee % |
| PUT | `/api/admin/accounts/{id}/freeze` | ADMIN | Freeze account |
| PUT | `/api/admin/accounts/{id}/unfreeze` | ADMIN | Unfreeze account |
| PUT | `/api/admin/accounts/{id}/close` | ADMIN | Close account (terminal) |
| PUT | `/api/admin/accounts/{id}/accrue-interest` | ADMIN | Credit monthly interest to a savings account |
| PUT | `/api/admin/accounts/{id}/mature` | ADMIN | Mature a time deposit and credit accrued interest |
| PUT | `/api/customers/{id}/password` | CUSTOMER | Change password |
| POST | `/api/accounts/checking?ownerId=` | CUSTOMER | Open checking account (with optional overdraft) |
| POST | `/api/accounts/savings?ownerId=` | CUSTOMER | Open savings account (with annual interest rate) |
| POST | `/api/accounts/time-deposit?ownerId=` | CUSTOMER | Open time deposit (principal locked until maturity) |
| GET | `/api/customers/{id}/accounts` | CUSTOMER | List accounts |
| GET | `/api/accounts/{id}/balance` | CUSTOMER | Get balance |
| POST | `/api/accounts/{id}/deposit` | CUSTOMER | Deposit |
| POST | `/api/accounts/{id}/withdraw` | CUSTOMER | Withdraw |
| POST | `/api/accounts/{id}/transfer` | CUSTOMER | Transfer to another account |
| GET | `/api/accounts/{id}/transactions` | CUSTOMER | Transaction history |

## Default Admin

Email: `admin@ayvalikbank.dev` / Password: `Admin@123!` (seeded by `data.sql`)
