# Ayvalık Bank CC-1

A banking application built as a learning project to practice **Hexagonal Architecture** (Ports & Adapters). The codebase is intentionally kept small and focused so every layer of the pattern is clearly visible.

For further enquiry please contact Akin Kaldiroglu at akin@kaldiroglu.dev

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
| Testing | JUnit 5 · AssertJ · Mockito · MockMvc · H2 (in-memory, `@DataJpaTest`) |
| Build | Maven |
| Infrastructure | Docker Compose (PostgreSQL) |

## Quick Start

```bash
# Build and run all tests - no database needed, tests use in-memory H2
mvn clean verify

# Start PostgreSQL (required only to run the application)
docker compose up -d                 # PostgreSQL on host port 5437, database ayvalikbank_ha_java

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

## Ports across the six repos

The six Ayvalık Bank implementations are meant to be compared side by side, so every one
takes its own application port and its own PostgreSQL port. All six can run at once.

| Repo | App | PostgreSQL | Database |
|---|---|---|---|
| `AyvalikBankHA-JAVA` | **8080** | **5437** | `ayvalikbank_ha_java` |
| `AyvalikBankLA-JAVA` | **8081** | **5438** | `ayvalikbank_la_java` |
| `AyvalikBankHA-NET` | **5080** | **5434** | `ayvalikbank_ha_net` |
| `AyvalikBankLA-NET` | **5050** | **5433** | `ayvalikbank_la_net` |
| `AyvalikBankHA-Python` | **8000** | **5436** | `ayvalikbank` |
| `AyvalikBankLA-Python` | **8001** | **5435** | `ayvalikbank` |

**5432 is deliberately left free** for a native PostgreSQL install (Postgres.app, Homebrew).
A container bound to it collides, and — worse — an application pointed at it connects to the
native server instead of its own container without any error to say so.

Each stack pins its port differently, because each offers a different mechanism:

| Repo | Where its port comes from |
|---|---|
| `AyvalikBankHA-JAVA` | Spring Boot's default 8080 — nothing to configure |
| `AyvalikBankLA-JAVA` | `server.port=8081` in `application.properties` |
| `AyvalikBankHA-NET` | no `launchSettings.json`, so `--urls http://localhost:5080` is **required** — without it Kestrel binds 5000 |
| `AyvalikBankLA-NET` | `AyvalikBankLA.Api/Properties/launchSettings.json` |
| `AyvalikBankHA-Python` | `--port 8000` on the uvicorn command line |
| `AyvalikBankLA-Python` | `--port 8001` on the uvicorn command line |

The two Python repos are the fragile pair: uvicorn takes its port as a launch argument and
has no configuration file to default it in, so **omitting `--port` gives both 8000** and the
second one to start fails to bind. The documented commands always pass it explicitly.
