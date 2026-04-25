# Ayvalık Bank CC-1

A banking application built as a learning project to practice **Hexagonal Architecture** (Ports & Adapters). The codebase is intentionally kept small and focused so every layer of the pattern is clearly visible.

## Objective

Demonstrate how to apply hexagonal architecture to a real domain — a simplified bank — using Java and Spring Boot. The domain layer contains zero framework imports; all Spring and JPA concerns are confined to adapters at the edges.

## Tech Stack

| Concern | Technology |
|---------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 3.4 |
| Persistence | Spring Data JPA + PostgreSQL |
| Security | Spring Security (HTTP Basic Auth) |
| Validation | Jakarta Bean Validation |
| Testing | JUnit 5 · AssertJ · Mockito · MockMvc |
| Build | Maven |
| Infrastructure | Docker Compose (PostgreSQL) |

## Quick Start

```bash
# Start PostgreSQL
docker compose up -d

# Build and run all tests
mvn clean verify

# Run the application
mvn spring-boot:run
```

Default admin credentials: `admin@ayvalikbank.dev` / `Admin@123!`

## Domain

The domain models a bank with two roles:

- **Admin** — creates and deletes customers, sets the transfer fee, changes customer tiers, freezes/unfreezes/closes accounts
- **Customer** — opens accounts, deposits, withdraws, transfers money, changes password

Key domain rules:
- Accounts have a currency; all operations are currency-matched
- Accounts follow a state machine: `ACTIVE → FROZEN → ACTIVE`, `ACTIVE|FROZEN → CLOSED` (terminal)
- Transfers between accounts of the same customer are free; cross-customer transfers carry an admin-configured fee scaled by the source customer's tier
- Each customer has a tier — `STANDARD`, `PREMIUM`, or `PRIVATE` — that scales their cross-customer fee (1.0× / 0.5× / 0.0×) and caps per-transaction transfers and withdrawals (PRIVATE = unlimited)
- Passwords must meet a strength policy and cannot reuse the last 3 passwords

Three account types are supported, each with its own behavior:
- **CheckingAccount** — general-purpose account with a configurable overdraft limit; withdrawals may take the balance negative up to that limit
- **SavingsAccount** — no overdraft; supports monthly interest accrual (`accrueInterest`) at a configurable annual rate; accrual works on ACTIVE and FROZEN accounts
- **TimeDepositAccount** — principal is locked at opening; deposits are rejected; the account must be matured (`mature`) on or after the maturity date, which credits the full annual interest; withdrawals are only permitted after maturity

## Documentation

| Document | Contents |
|----------|---------|
| [Architecture.md](Architecture.md) | Layer-by-layer breakdown of the hexagonal architecture, key design decisions, and the rationale behind them |
| [Hexagonal.md](Hexagonal.md) | Full diagram of the hexagon — inbound adapters, ports in, application layer, domain layer, ports out, outbound adapters — in both ASCII and Mermaid format |
| [Flows.md](Flows.md) | Sequence diagrams for each use case tracing the call chain from HTTP client through every architectural layer to PostgreSQL and back |
| [Tests.md](Tests.md) | Test suite reference: test pyramid, per-class test tables, exception-to-HTTP mapping, and a testing style analysis (output / state / communication) |

## Project Structure

```
src/main/java/
└── dev/kaldiroglu/hexagonal/ayvalikbank/
    ├── adapter/
    │   ├── in/web/          # REST controllers, request/response DTOs, GlobalExceptionHandler
    │   └── out/
    │       ├── persistence/ # JPA entities, Spring Data repos, mappers, adapters
    │       └── security/    # BCryptPasswordHasherAdapter
    ├── application/
    │   ├── exception/       # Typed application exceptions
    │   └── service/         # CustomerApplicationService, AccountApplicationService
    ├── config/              # SecurityConfig, BankUserDetailsService, AdminDataInitializer
    └── domain/              # Split per aggregate: account vs customer
        ├── model/
        │   ├── account/     # Account hierarchy + State pattern + Money/Currency/Transaction
        │   └── customer/    # Customer, CustomerId, Password
        ├── port/
        │   ├── in/
        │   │   ├── account/   # 15 account use cases (open*, deposit, withdraw, freeze, accrue, mature, ...)
        │   │   └── customer/  # 4 customer use cases (create, delete, list, change password)
        │   └── out/
        │       ├── account/   # AccountRepositoryPort, TransactionRepositoryPort, SettingsRepositoryPort
        │       └── customer/  # CustomerRepositoryPort, PasswordHasherPort
        └── service/
            ├── account/     # TransferDomainService
            └── customer/    # PasswordValidationService
```
