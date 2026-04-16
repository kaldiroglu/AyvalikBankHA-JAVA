# Use Case Flow Diagrams — Ayvalık Bank CC-1

Each diagram shows the full call chain from the HTTP client through every architectural layer.

**Layer key:**

| Lane | Layer |
|------|-------|
| Actor | Human user (Admin / Customer) |
| Security | Spring Security filter chain |
| Controller | Inbound adapter (REST) |
| AppService | Application service (orchestration) |
| Domain | Entities and domain services (pure Java) |
| Persistence | Outbound adapter (JPA) |
| DB | PostgreSQL |

---

## 1. CreateCustomerUseCase

Admin creates a new customer with a validated, hashed password.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Security   as Security Filter
    participant Ctrl       as AdminController
    participant AppSvc     as CustomerApplicationService
    participant PwdVal     as PasswordValidationService
    participant PwdHasher  as BCryptPasswordHasherAdapter
    participant Customer   as Customer (entity)
    participant Persist    as CustomerPersistenceAdapter
    participant DB        as PostgreSQL

    Admin->>Security: POST /api/admin/customers<br/>{name, email, password}<br/>Basic Auth header
    Security->>Security: loadUserByUsername(admin@ayvalikbank.dev)
    Security->>Persist: findByEmail("admin@ayvalikbank.dev")
    Persist->>DB: SELECT * FROM customers WHERE email=?
    DB-->>Persist: CustomerJpaEntity (role=ADMIN)
    Persist-->>Security: Customer domain object
    Security->>Security: BCrypt.matches(rawPass, storedHash) ✓
    Security->>Security: hasRole(ROLE_ADMIN) ✓
    Security->>Ctrl: forward request

    Ctrl->>AppSvc: createCustomer(Command{name, email, rawPassword})

    AppSvc->>PwdVal: validate(rawPassword)
    Note over PwdVal: checks length 8–16,<br/>upper, lower, digit,<br/>special character
    PwdVal-->>AppSvc: OK (or throws InvalidPasswordException)

    AppSvc->>PwdHasher: hash(rawPassword)
    PwdHasher-->>AppSvc: "$2a$12$..."

    AppSvc->>Customer: Customer.create(name, email, Password.ofHashed(hash))
    Customer-->>AppSvc: new Customer{id=UUID, role=CUSTOMER}

    AppSvc->>Persist: save(customer)
    Persist->>DB: INSERT INTO customers (...)
    DB-->>Persist: saved row
    Persist-->>AppSvc: Customer domain object

    AppSvc-->>Ctrl: Customer
    Ctrl-->>Admin: 201 Created {id, name, email, role}
```

---

## 2. ChangePasswordUseCase

Customer changes their own password. New password must pass format rules and must not match the last 3 used passwords.

```mermaid
sequenceDiagram
    autonumber
    actor Cust
    participant Security  as Security Filter
    participant Ctrl      as CustomerController
    participant AppSvc    as CustomerApplicationService
    participant PwdVal    as PasswordValidationService
    participant PwdHasher as BCryptPasswordHasherAdapter
    participant Entity    as Customer (entity)
    participant Persist   as CustomerPersistenceAdapter
    participant DB        as PostgreSQL

    Cust->>Security: PUT /api/customers/{id}/password<br/>{newPassword}<br/>Basic Auth header
    Security->>Security: authenticate + hasRole(ROLE_CUSTOMER) ✓
    Security->>Ctrl: forward request

    Ctrl->>AppSvc: changePassword(Command{customerId, rawNewPassword})

    AppSvc->>PwdVal: validate(rawNewPassword)
    PwdVal-->>AppSvc: OK (or throws InvalidPasswordException)

    AppSvc->>Persist: findById(customerId)
    Persist->>DB: SELECT * FROM customers + password_history WHERE id=?
    DB-->>Persist: CustomerJpaEntity + PasswordHistoryJpaEntity[]
    Persist-->>AppSvc: Customer{currentPassword, passwordHistory[0..2]}

    AppSvc->>Entity: getAllPasswordsForReuseCheck()
    Entity-->>AppSvc: [currentPassword, hist[0], hist[1], hist[2]]

    loop for each previous password hash
        AppSvc->>PwdHasher: matches(rawNewPassword, previousHash)
        PwdHasher-->>AppSvc: false (or throws PasswordReusedException)
    end

    AppSvc->>PwdHasher: hash(rawNewPassword)
    PwdHasher-->>AppSvc: "$2a$12$newHash..."

    AppSvc->>Entity: changePassword(Password.ofHashed(newHash))
    Note over Entity: shifts currentPassword → history[0]<br/>trims history to max 3<br/>sets new currentPassword

    AppSvc->>Persist: save(customer)
    Persist->>DB: UPDATE customers SET current_password=?<br/>DELETE + INSERT password_history
    DB-->>Persist: OK
    Persist-->>AppSvc: updated Customer

    AppSvc-->>Ctrl: void
    Ctrl-->>Cust: 200 OK
```

---

## 3. DepositMoneyUseCase

Customer deposits money into one of their accounts.

```mermaid
sequenceDiagram
    autonumber
    actor Cust
    participant Security as Security Filter
    participant Ctrl     as AccountController
    participant AppSvc  as AccountApplicationService
    participant Account as Account (entity)
    participant TxRepo  as TransactionPersistenceAdapter
    participant AccRepo as AccountPersistenceAdapter
    participant DB      as PostgreSQL

    Cust->>Security: POST /api/accounts/{id}/deposit<br/>{amount, currency}<br/>Basic Auth header
    Security->>Security: authenticate + hasRole(ROLE_CUSTOMER) ✓
    Security->>Ctrl: forward request

    Ctrl->>AppSvc: deposit(Command{accountId, Money{amount, currency}})

    AppSvc->>AccRepo: findById(accountId)
    AccRepo->>DB: SELECT * FROM accounts WHERE id=?
    DB-->>AccRepo: AccountJpaEntity
    AccRepo-->>AppSvc: Account{balance, currency}

    AppSvc->>Account: deposit(Money{amount, currency})
    Note over Account: validates deposit currency<br/>= account currency<br/>balance = balance + amount
    Account-->>AppSvc: Transaction{DEPOSIT, amount, timestamp}

    AppSvc->>AccRepo: save(account)
    AccRepo->>DB: UPDATE accounts SET balance=?
    DB-->>AccRepo: OK

    AppSvc->>TxRepo: save(transaction)
    TxRepo->>DB: INSERT INTO transactions (...)
    DB-->>TxRepo: saved row

    AppSvc-->>Ctrl: Transaction
    Ctrl-->>Cust: 201 Created {id, type=DEPOSIT, amount, currency, timestamp}
```

---

## 4. TransferMoneyUseCase

The most complex flow. Customer transfers money between accounts. Fee is 0% for same-customer transfers; admin-configured % for cross-customer transfers.

```mermaid
sequenceDiagram
    autonumber
    actor Cust
    participant Security    as Security Filter
    participant Ctrl        as AccountController
    participant AppSvc     as AccountApplicationService
    participant TransferSvc as TransferDomainService
    participant SrcAccount  as Source Account (entity)
    participant TgtAccount  as Target Account (entity)
    participant Settings    as SettingsPersistenceAdapter
    participant AccRepo     as AccountPersistenceAdapter
    participant TxRepo      as TransactionPersistenceAdapter
    participant DB          as PostgreSQL

    Cust->>Security: POST /api/accounts/{sourceId}/transfer<br/>{targetAccountId, amount, currency}<br/>Basic Auth header
    Security->>Security: authenticate + hasRole(ROLE_CUSTOMER) ✓
    Security->>Ctrl: forward request

    Ctrl->>AppSvc: transfer(Command{sourceId, targetId, Money{amount, currency}})

    AppSvc->>AccRepo: findById(sourceAccountId)
    AccRepo->>DB: SELECT * FROM accounts WHERE id=?
    DB-->>AccRepo: AccountJpaEntity (ownerId = A)
    AccRepo-->>AppSvc: Source Account

    AppSvc->>AccRepo: findById(targetAccountId)
    AccRepo->>DB: SELECT * FROM accounts WHERE id=?
    DB-->>AccRepo: AccountJpaEntity (ownerId = B)
    AccRepo-->>AppSvc: Target Account

    AppSvc->>AppSvc: sameCustomer = (source.ownerId == target.ownerId)?

    AppSvc->>Settings: getTransferFeePercent()
    Settings->>DB: SELECT value FROM settings WHERE key='TRANSFER_FEE_PERCENT'
    DB-->>Settings: "1.0"
    Settings-->>AppSvc: BigDecimal(1.0)

    AppSvc->>TransferSvc: calculateFee(amount, sameCustomer, feePercent)
    Note over TransferSvc: if sameCustomer → fee = 0<br/>else fee = amount × feePercent / 100
    TransferSvc-->>AppSvc: Money fee

    AppSvc->>SrcAccount: transferOut(amount, fee, targetId)
    Note over SrcAccount: validates currency match<br/>totalDebit = amount + fee<br/>checks balance ≥ totalDebit<br/>balance = balance − totalDebit
    SrcAccount-->>AppSvc: Transaction{TRANSFER_OUT, amount}

    AppSvc->>TgtAccount: transferIn(amount, sourceId)
    Note over TgtAccount: validates currency match<br/>balance = balance + amount
    TgtAccount-->>AppSvc: Transaction{TRANSFER_IN, amount}

    AppSvc->>AccRepo: save(sourceAccount)
    AccRepo->>DB: UPDATE accounts SET balance=? WHERE id=sourceId
    AppSvc->>AccRepo: save(targetAccount)
    AccRepo->>DB: UPDATE accounts SET balance=? WHERE id=targetId

    AppSvc->>TxRepo: save(outboundTransaction)
    TxRepo->>DB: INSERT INTO transactions (TRANSFER_OUT ...)
    AppSvc->>TxRepo: save(inboundTransaction)
    TxRepo->>DB: INSERT INTO transactions (TRANSFER_IN ...)

    DB-->>AppSvc: OK

    AppSvc-->>Ctrl: void
    Ctrl-->>Cust: 200 OK
```

---

## 5. DeleteCustomerUseCase

Admin deletes an existing customer by ID.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Security as Security Filter
    participant Ctrl     as AdminController
    participant AppSvc  as CustomerApplicationService
    participant Persist  as CustomerPersistenceAdapter
    participant DB       as PostgreSQL

    Admin->>Security: DELETE /api/admin/customers/{id}<br/>Basic Auth header
    Security->>Security: authenticate + hasRole(ROLE_ADMIN) ✓
    Security->>Ctrl: forward request

    Ctrl->>AppSvc: deleteCustomer(CustomerId)

    AppSvc->>Persist: existsById(customerId)
    Persist->>DB: SELECT COUNT(*) FROM customers WHERE id=?
    DB-->>Persist: 1
    Persist-->>AppSvc: true

    AppSvc->>Persist: deleteById(customerId)
    Persist->>DB: DELETE FROM customers WHERE id=?
    Note over DB: CASCADE deletes<br/>password_history rows
    DB-->>Persist: OK

    AppSvc-->>Ctrl: void
    Ctrl-->>Admin: 204 No Content
```

---

## 6. GetBalanceUseCase

Customer queries the current balance of an account.

```mermaid
sequenceDiagram
    autonumber
    actor Cust
    participant Security as Security Filter
    participant Ctrl     as AccountController
    participant AppSvc  as AccountApplicationService
    participant AccRepo as AccountPersistenceAdapter
    participant DB      as PostgreSQL

    Cust->>Security: GET /api/accounts/{id}/balance<br/>Basic Auth header
    Security->>Security: authenticate + hasRole(ROLE_CUSTOMER) ✓
    Security->>Ctrl: forward request

    Ctrl->>AppSvc: getBalance(AccountId)

    AppSvc->>AccRepo: findById(accountId)
    AccRepo->>DB: SELECT * FROM accounts WHERE id=?
    DB-->>AccRepo: AccountJpaEntity{balance, currency}
    AccRepo-->>AppSvc: Account domain object

    AppSvc->>AppSvc: account.getBalance()
    AppSvc-->>Ctrl: Money{amount, currency}

    Ctrl-->>Cust: 200 OK {amount, currency}
```

---

## 7. FreezeAccountUseCase

Admin freezes an account. The state transition lives entirely in the `Account` domain entity.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Security as Security Filter
    participant Ctrl     as AdminController
    participant AppSvc  as AccountApplicationService
    participant Account as Account (entity)
    participant AccRepo as AccountPersistenceAdapter
    participant DB      as PostgreSQL

    Admin->>Security: PUT /api/admin/accounts/{id}/freeze<br/>Basic Auth header
    Security->>Security: authenticate + hasRole(ROLE_ADMIN) ✓
    Security->>Ctrl: forward request

    Ctrl->>AppSvc: freezeAccount(AccountId)

    AppSvc->>AccRepo: findById(accountId)
    AccRepo->>DB: SELECT * FROM accounts WHERE id=?
    DB-->>AccRepo: AccountJpaEntity{status=ACTIVE, ...}
    AccRepo-->>AppSvc: Account domain object

    AppSvc->>Account: freeze()
    Note over Account: guards: status must be ACTIVE<br/>throws IllegalStateException<br/>if FROZEN or CLOSED<br/>sets status = FROZEN
    Account-->>AppSvc: (status updated in memory)

    AppSvc->>AccRepo: save(account)
    AccRepo->>DB: UPDATE accounts SET status='FROZEN' WHERE id=?
    DB-->>AccRepo: OK

    AppSvc-->>Ctrl: void
    Ctrl-->>Admin: 200 OK
```

> **Unfreeze** (`PUT /api/admin/accounts/{id}/unfreeze`) follows the same flow with `account.unfreeze()`: requires `FROZEN`, sets `ACTIVE`.
>
> **Close** (`PUT /api/admin/accounts/{id}/close`) follows the same flow with `account.close()`: accepts `ACTIVE` or `FROZEN`, sets `CLOSED` (terminal — no further transitions possible).
