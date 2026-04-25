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
adapter/in/web/                → REST controllers + request/response DTOs
adapter/out/persistence/       → JPA entities (DTOs), Spring Data repos, mappers, adapters
adapter/out/security/          → BCryptPasswordHasherAdapter
application/service/           → CustomerApplicationService, AccountApplicationService
config/                        → SecurityConfig (Spring beans), BankUserDetailsService
domain/model/account/          → Account hierarchy (sealed) + AccountState pattern + Money, Currency, Transaction, AccountId/Type/Status
domain/model/customer/         → Customer, CustomerId, Password
domain/service/account/        → TransferDomainService
domain/service/customer/       → PasswordValidationService
domain/port/in/account/        → 15 account use-case interfaces (open*, deposit, withdraw, transfer, freeze/unfreeze/close, accrue, mature, set transfer fee, ...)
domain/port/in/customer/       → CreateCustomer, DeleteCustomer, ListCustomers, ChangePassword
domain/port/out/account/       → AccountRepositoryPort, TransactionRepositoryPort, SettingsRepositoryPort
domain/port/out/customer/      → CustomerRepositoryPort, PasswordHasherPort
```

The domain is split per aggregate (`account` vs `customer`) at every layer — model, service, ports — so each aggregate's vocabulary is locally complete.

**Domain layer has zero Spring/JPA imports.** Domain services are instantiated as `@Bean` in `SecurityConfig` so they can be injected without becoming Spring components.

## Key Design Decisions

- **Value objects as records**: `CustomerId`, `AccountId`, `TransactionId`, `Money`, `Password` — immutable, self-validating.
- **Sealed account hierarchy**: `Account` is a `sealed abstract class` permitting `CheckingAccount`, `SavingsAccount`, and `TimeDepositAccount`. Each subtype overrides `deposit`/`withdraw`/`transferOut` with its own rules: `CheckingAccount` allows configurable overdraft; `SavingsAccount` rejects overdraft and supports monthly interest accrual via `accrueInterest(YearMonth)`; `TimeDepositAccount` locks principal at open and rejects withdrawals until `mature(LocalDate)` is called on or after the maturity date.
- **Three account types**: `CheckingAccount` carries an `overdraftLimit` — withdrawals may take the balance negative up to that limit. `SavingsAccount` carries an `annualInterestRate` and a `lastAccrualDate`; `accrueInterest` credits monthly interest and works on ACTIVE or FROZEN accounts (but not CLOSED). `TimeDepositAccount` locks the principal at open; `deposit` is rejected; `mature` must be called on or after the maturity date and credits the annual interest; withdrawals are then permitted.
- **Password history**: `Customer` holds `currentPassword` + up to 3 previous hashes. Reuse checking (BCrypt) lives in `CustomerApplicationService` since it requires the `PasswordHasherPort`.
- **Transfer fee**: free for same-customer transfers; `TransferDomainService.calculateFee()` applies the admin-configured percentage for cross-customer transfers, scaled by the **source customer's tier multiplier** (STANDARD=1.0×, PREMIUM=0.5×, PRIVATE=0.0×). Fee stored in `settings` table.
- **Customer tiers**: `CustomerTier` enum (`STANDARD`, `PREMIUM`, `PRIVATE`) on `Customer`. Each tier carries a fee multiplier and per-transaction caps for transfers/withdrawals (PRIVATE = unlimited). `TransferDomainService.requireTransferWithinLimit` / `requireWithdrawalWithinLimit` enforce caps; the application service wraps any violation as `LimitExceededException` → HTTP 422. Admin promotes/demotes via `PUT /api/admin/customers/{id}/tier`.
- **Account status (State pattern)**: `Account` holds an `AccountState` — a sealed interface with three stateless singleton implementations (`ActiveState`, `FrozenState`, `ClosedState`). Each state owns its valid transitions and its `requireOperable()` check; `Account.freeze()`/`unfreeze()`/`close()` are one-line delegations. The `AccountStatus` enum (`ACTIVE`, `FROZEN`, `CLOSED`) is preserved as the boundary type for persistence and REST, with `AccountState.of(status)` converting at construction time. Invalid transitions throw `IllegalStateException`; the application service converts this to `AccountNotOperableException` → HTTP 422.
- **Authentication**: HTTP Basic Auth via Spring Security. Credentials loaded from the `customers` table by `BankUserDetailsService`. Roles: `ADMIN`, `CUSTOMER`.
- **JPA DTOs**: `CustomerJpaEntity`, `AccountJpaEntity`, `TransactionJpaEntity`, `SettingsJpaEntity`, `PasswordHistoryJpaEntity` — none of these cross the persistence adapter boundary.

## REST API Summary

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST | `/api/admin/customers` | ADMIN | Create customer |
| DELETE | `/api/admin/customers/{id}` | ADMIN | Delete customer |
| GET | `/api/admin/customers` | ADMIN | List all customers |
| PUT | `/api/admin/customers/{id}/tier` | ADMIN | Change customer tier (STANDARD / PREMIUM / PRIVATE) |
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
