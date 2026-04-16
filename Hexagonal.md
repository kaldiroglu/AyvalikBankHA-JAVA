# Hexagonal Architecture Diagram — Ayvalık Bank CC-1

---

## ASCII Representation

```
                         ┌─────────────────────────┐
                         │       HTTP Clients      │
                         │    Admin     Customer   │
                         └────────────┬────────────┘
                                      │  HTTPS / Basic Auth
                    ╔═════════════════╪══════════════════════╗
                    ║  INBOUND ADAPTERS                      ║
                    ║  ┌─────────────────────────────────┐   ║
                    ║  │  Security Filter (Basic Auth)   │   ║
                    ║  │  BankUserDetailsService         │   ║
                    ║  └─────────────────────────────────┘   ║
                    ║  ┌───────────────┐ ┌───────────────┐   ║
                    ║  │AdminController│ │AccountCtrl    │   ║
                    ║  │CustomerCtrl   │ │GlobalException│   ║
                    ║  └───────┬───────┘ └───────┬───────┘   ║
                    ╚══════════╪═════════════════╪══════════╝
                               │  calls use-case interfaces
                          ╱────┴─────────────────┴────╲
                        ╱                               ╲
                       ╱   ┌─────────────────────────┐   ╲
                      ╱    │        PORTS IN          │    ╲
                     ╱     │  CreateCustomerUseCase   │     ╲
                    ╱      │  DeleteCustomerUseCase   │      ╲
                   │       │  ListCustomersUseCase    │       │
                   │       │  ChangePasswordUseCase   │       │
                   │       │  SetTransferFeeUseCase   │       │
                   │       │  CreateAccountUseCase    │       │
                   │       │  DepositMoneyUseCase     │       │
                   │       │  WithdrawMoneyUseCase    │       │
                   │       │  TransferMoneyUseCase    │       │
                   │       │  GetBalanceUseCase       │       │
                   │       │  GetTransactionsUseCase  │       │
                   │       │  ListAccountsUseCase     │       │
                   │       │  FreezeAccountUseCase    │       │
                   │       │  UnfreezeAccountUseCase  │       │
                   │       │  CloseAccountUseCase     │       │
                   │       └────────────┬────────────┘        │
                   │                    │  implemented by     │
                   │       ┌────────────▼────────────┐        │
                   │       │    APPLICATION LAYER    │        │
                   │       │                         │        │
     T H E         │       │ CustomerApplicationSvc  │        │  H E X A G O N
                   │       │ AccountApplicationSvc   │        │
                   │       └────────────┬────────────┘        │
                   │                    │  uses               │
                   │       ┌────────────▼────────────┐        │
                   │       │       DOMAIN LAYER       │       │
                   │       │  ── Entities ──          │       │
                   │       │  Customer                │       │
                   │       │  Account                 │       │
                   │       │  Transaction             │       │
                   │       │  ── Value Objects ──     │       │
                   │       │  Money  Password         │       │
                   │       │  CustomerId  AccountId   │       │
                   │       │  TransactionId           │       │
                   │       │  Currency TransactionType│       │
                   │       │  AccountStatus           │       │
                   │       │  ── Domain Services ──   │       │
                   │       │  PasswordValidationSvc   │       │
                   │       │  TransferDomainService   │       │
                   │       └────────────┬────────────┘        │
                   │                    │  declares           │
                   │       ┌────────────▼────────────┐        │
                   │       │        PORTS OUT         │       │
                   │       │  CustomerRepositoryPort  │       │
                   │       │  AccountRepositoryPort   │       │
                   │       │  TransactionRepository   │       │
                   │       │  PasswordHasherPort      │       │
                   │       │  SettingsRepositoryPort  │       │
                   │       └─────────────────────────┘        │
                    \                                        ╱
                     \                                      ╱
                      ╲────────────────────────────────────╱
                               │  implemented by
                    ╔══════════╪═══════════════════════════════╗
                    ║  OUTBOUND ADAPTERS                       ║
                    ║  ┌────────────────────┐  ┌────────────┐  ║
                    ║  │ Persistence        │  │ Security   │  ║
                    ║  │ CustomerPersistence│  │ BCrypt     │  ║
                    ║  │ AccountPersistence │  │ Password   │  ║
                    ║  │ TransactionPersist │  │ Hasher     │  ║
                    ║  │ SettingsPersistence│  │ Adapter    │  ║
                    ║  └─────────┬──────────┘  └─────┬──────┘  ║
                    ╚════════════╪═══════════════════╪════════╝
                                 │  JDBC / JPA        │ BCrypt
                    ┌────────────▼───────────────┐    │
                    │        PostgreSQL          │◄───┘
                    │  ┌──────────────────────┐  │
                    │  │ customers            │  │
                    │  │ password_history     │  │
                    │  │ accounts             │  │
                    │  │ transactions         │  │
                    │  │ settings             │  │
                    │  └──────────────────────┘  │
                    └────────────────────────────┘
```

---

## Mermaid Flowchart

```mermaid
flowchart TB
    HTTP(["👤 Admin / Customer\nHTTPS · Basic Auth"])

    subgraph INBOUND["⬡  Inbound Adapters"]
        direction LR
        SEC["Security Filter\nBankUserDetailsService"]
        CTRL["AdminController\nCustomerController\nAccountController\nGlobalExceptionHandler"]
    end

    subgraph HEXAGON["⬡  THE HEXAGON"]
        direction TB

        subgraph PORTS_IN["Ports In — Use Cases"]
            direction LR
            PIC["CreateCustomerUseCase · DeleteCustomerUseCase · ListCustomersUseCase\nChangePasswordUseCase · SetTransferFeeUseCase · CreateAccountUseCase\nDepositMoneyUseCase · WithdrawMoneyUseCase · TransferMoneyUseCase\nGetBalanceUseCase · GetTransactionsUseCase · ListAccountsUseCase\nFreezeAccountUseCase · UnfreezeAccountUseCase · CloseAccountUseCase"]
        end

        subgraph APP["Application Layer"]
            direction LR
            CSVC["CustomerApplicationService"]
            ASVC["AccountApplicationService"]
        end

        subgraph DOMAIN["Domain Layer — Pure Java"]
            direction TB
            ENT["Entities\nCustomer · Account · Transaction"]
            VO["Value Objects\nMoney · Password · CustomerId · AccountId · TransactionId\nCurrency · TransactionType · AccountStatus"]
            DS["Domain Services\nPasswordValidationService · TransferDomainService"]
        end

        subgraph PORTS_OUT["Ports Out — Infrastructure Interfaces"]
            direction LR
            POC["CustomerRepositoryPort · AccountRepositoryPort\nTransactionRepositoryPort · PasswordHasherPort · SettingsRepositoryPort"]
        end
    end

    subgraph OUTBOUND["⬡  Outbound Adapters"]
        direction LR
        JPA["JPA Persistence Adapters\nCustomer · Account · Transaction · Settings"]
        BCRYPT["BCryptPasswordHasherAdapter"]
    end

    DB[("PostgreSQL\ncustomers · password_history\naccounts · transactions · settings")]

    HTTP         -->|"authenticates"| SEC
    SEC          -->|"forwards to"| CTRL
    CTRL         -->|"calls"| PIC
    PIC          -->|"implemented by"| APP
    APP          -->|"uses"| DOMAIN
    APP          -->|"calls via"| PORTS_OUT
    PORTS_OUT    -->|"implemented by"| JPA
    PORTS_OUT    -->|"implemented by"| BCRYPT
    JPA          --> DB

    style HEXAGON fill:#fff8f0,stroke:#cc6600,stroke-width:3px,stroke-dasharray:8 4
    style DOMAIN  fill:#ffe0e0,stroke:#cc2222,stroke-width:2px
    style APP     fill:#fff3cc,stroke:#aa8800,stroke-width:2px
    style PORTS_IN  fill:#d0e8ff,stroke:#2255aa,stroke-width:2px
    style PORTS_OUT fill:#d0f0d0,stroke:#226622,stroke-width:2px
    style INBOUND   fill:#e8f0ff,stroke:#3355cc,stroke-width:2px
    style OUTBOUND  fill:#e8ffe8,stroke:#226622,stroke-width:2px
```

---

## Key Rule

> **All arrows cross the hexagon boundary from outside in — never from inside out.**
> The domain layer has zero imports from Spring, JPA, BCrypt, or any adapter.

| Zone | Contents | Depends on |
|------|----------|-----------|
| Domain Layer | Entities (`Customer`, `Account`, `Transaction`), Value Objects, `AccountStatus` state machine, Domain Services | Nothing |
| Ports In | 15 use-case interfaces + Command records | Domain model types only |
| Ports Out | Repository + infrastructure interfaces | Domain model types only |
| Application Layer | Application services | Domain layer + Ports In + Ports Out |
| Inbound Adapters | REST controllers, Security | Application layer (via Ports In) |
| Outbound Adapters | JPA adapters, BCrypt adapter | Ports Out (implements them) |
| Infrastructure | PostgreSQL | — |
