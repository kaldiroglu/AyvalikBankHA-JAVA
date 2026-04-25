# Use Case Flow Diagrams — Ayvalık Bank HA-1

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

Customer deposits money into one of their accounts. The persistence adapter reconstructs the correct `Account` subtype (`CheckingAccount`, `SavingsAccount`, or `TimeDepositAccount`) based on the `type` discriminator column; each subtype overrides `deposit` with its own rules. `TimeDepositAccount.deposit` always throws — the application service maps that to HTTP 422.

```mermaid
sequenceDiagram
    autonumber
    actor Cust
    participant Security as Security Filter
    participant Ctrl     as AccountController
    participant AppSvc  as AccountApplicationService
    participant Account as Account (subtype)
    participant State   as AccountState
    participant TxRepo  as TransactionPersistenceAdapter
    participant AccRepo as AccountPersistenceAdapter
    participant DB      as PostgreSQL

    Cust->>Security: POST /api/accounts/{id}/deposit<br/>{amount, currency}<br/>Basic Auth header
    Security->>Security: authenticate + hasRole(ROLE_CUSTOMER) ✓
    Security->>Ctrl: forward request

    Ctrl->>AppSvc: deposit(Command{accountId, Money{amount, currency}})

    AppSvc->>AccRepo: findById(accountId)
    AccRepo->>DB: SELECT * FROM accounts WHERE id=?
    DB-->>AccRepo: AccountJpaEntity{type, balance, ...}
    Note over AccRepo: mapper switches on type<br/>and constructs the<br/>correct Account subtype
    AccRepo-->>AppSvc: CheckingAccount / SavingsAccount / TimeDepositAccount

    AppSvc->>Account: deposit(Money{amount, currency})
    Account->>State: requireOperable()
    Note over State: ActiveState: no-op<br/>FrozenState/ClosedState: throws<br/>IllegalStateException
    State-->>Account: OK
    Note over Account: validates currency match,<br/>rejects negative amount,<br/>balance = balance + amount<br/>(TimeDepositAccount.deposit<br/>always throws "locked")
    Account-->>AppSvc: Transaction{DEPOSIT, amount, timestamp}

    Note over AppSvc: try { account.deposit(...) }<br/>catch IllegalStateException<br/>→ InvalidAccountOperationException (HTTP 422)

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

The most complex flow. Customer transfers money between accounts. Before any debit, the application service fetches the **source customer** to read their `CustomerTier`, then asks `TransferDomainService` to (a) reject the transfer if the amount exceeds the tier's per-transaction cap (→ `LimitExceededException`, HTTP 422) and (b) calculate the fee. Same-customer transfers are free; cross-customer transfers charge `amount × admin_fee_percent × tier.feeMultiplier()` — STANDARD pays full, PREMIUM pays half, PRIVATE pays nothing.

The source account's `transferOut` follows subtype-specific rules: `CheckingAccount` allows the balance to go negative down to `-overdraftLimit`; `SavingsAccount` rejects any overdraw; `TimeDepositAccount` always throws ("transfers not supported"). Any `IllegalStateException` is wrapped by the service as `InvalidAccountOperationException` (HTTP 422).

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
    participant CustRepo    as CustomerPersistenceAdapter
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

    AppSvc->>CustRepo: findById(source.ownerId)
    CustRepo->>DB: SELECT * FROM customers WHERE id=?
    DB-->>CustRepo: CustomerJpaEntity{tier=...}
    CustRepo-->>AppSvc: Source Customer{tier}

    AppSvc->>TransferSvc: requireTransferWithinLimit(amount, tier)
    Note over TransferSvc: throws IllegalStateException<br/>→ wrapped as<br/>LimitExceededException (HTTP 422)<br/>if amount > tier.maxPerTransfer

    AppSvc->>AppSvc: sameCustomer = (source.ownerId == target.ownerId)?

    AppSvc->>Settings: getTransferFeePercent()
    Settings->>DB: SELECT value FROM settings WHERE key='TRANSFER_FEE_PERCENT'
    DB-->>Settings: "1.0"
    Settings-->>AppSvc: BigDecimal(1.0)

    AppSvc->>TransferSvc: calculateFee(amount, sameCustomer, feePercent, sourceTier)
    Note over TransferSvc: if sameCustomer → fee = 0<br/>else fee = amount × feePercent × tier.feeMultiplier / 100<br/>(STANDARD 1.0×, PREMIUM 0.5×, PRIVATE 0.0×)
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

Admin freezes an account. The state transition is implemented with the **State pattern** — `Account.freeze()` is a one-line delegation to `state.freeze()`, which returns the new state instance (or throws if the transition is invalid).

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Security as Security Filter
    participant Ctrl     as AdminController
    participant AppSvc  as AccountApplicationService
    participant Account as Account (entity)
    participant State   as AccountState (current)
    participant AccRepo as AccountPersistenceAdapter
    participant DB      as PostgreSQL

    Admin->>Security: PUT /api/admin/accounts/{id}/freeze<br/>Basic Auth header
    Security->>Security: authenticate + hasRole(ROLE_ADMIN) ✓
    Security->>Ctrl: forward request

    Ctrl->>AppSvc: freezeAccount(AccountId)

    AppSvc->>AccRepo: findById(accountId)
    AccRepo->>DB: SELECT * FROM accounts WHERE id=?
    DB-->>AccRepo: AccountJpaEntity{status='ACTIVE', ...}
    Note over AccRepo: mapper passes status to<br/>Account constructor, which<br/>calls AccountState.of(ACTIVE)<br/>→ ActiveState.INSTANCE
    AccRepo-->>AppSvc: Account{state = ActiveState.INSTANCE}

    AppSvc->>Account: freeze()
    Account->>State: freeze()
    Note over State: ActiveState.freeze()<br/>returns FrozenState.INSTANCE<br/><br/>FrozenState.freeze() throws<br/>"already frozen"<br/><br/>ClosedState.freeze() throws<br/>"cannot freeze closed"
    State-->>Account: FrozenState.INSTANCE
    Note over Account: this.state = new state

    Note over AppSvc: try { account.freeze() }<br/>catch IllegalStateException<br/>→ AccountNotOperableException (HTTP 422)

    AppSvc->>AccRepo: save(account)
    Note over AccRepo: getStatus() reads<br/>state.status() = FROZEN
    AccRepo->>DB: UPDATE accounts SET status='FROZEN' WHERE id=?
    DB-->>AccRepo: OK

    AppSvc-->>Ctrl: void
    Ctrl-->>Admin: 200 OK
```

> **Unfreeze** (`PUT /api/admin/accounts/{id}/unfreeze`) follows the same flow with `account.unfreeze()` → `state.unfreeze()`: `FrozenState` returns `ActiveState.INSTANCE`; `ActiveState`/`ClosedState` throw.
>
> **Close** (`PUT /api/admin/accounts/{id}/close`) follows the same flow with `account.close()` → `state.close()`: `ActiveState` and `FrozenState` return `ClosedState.INSTANCE`; `ClosedState` throws ("already closed"). `CLOSED` is terminal.

---

## 8. OpenCheckingAccountUseCase

Customer opens a new checking account, optionally with an overdraft limit. Two parallel flows exist for `POST /api/accounts/savings` (rate field, `SavingsAccount`) and `POST /api/accounts/time-deposit` (principal/maturity/rate fields, `TimeDepositAccount`) — same shape, different command and factory.

```mermaid
sequenceDiagram
    autonumber
    actor Cust
    participant Security as Security Filter
    participant Ctrl     as AccountController
    participant AppSvc   as AccountApplicationService
    participant CustRepo as CustomerPersistenceAdapter
    participant Account  as CheckingAccount (entity)
    participant AccRepo  as AccountPersistenceAdapter
    participant DB       as PostgreSQL

    Cust->>Security: POST /api/accounts/checking?ownerId={id}<br/>{currency, overdraftLimit?}<br/>Basic Auth header
    Security->>Security: authenticate + hasRole(ROLE_CUSTOMER) ✓
    Security->>Ctrl: forward request

    Note over Ctrl: defaults overdraftLimit to<br/>Money.zero(currency) if absent
    Ctrl->>AppSvc: openChecking(Command{ownerId, currency, overdraftLimit})

    AppSvc->>CustRepo: existsById(ownerId)
    CustRepo->>DB: SELECT COUNT(*) FROM customers WHERE id=?
    DB-->>CustRepo: 1
    CustRepo-->>AppSvc: true (else throws CustomerNotFoundException → 404)

    AppSvc->>Account: CheckingAccount.open(ownerId, currency, overdraftLimit)
    Note over Account: state = ActiveState.INSTANCE<br/>balance = Money.zero(currency)<br/>type = CHECKING
    Account-->>AppSvc: new CheckingAccount

    AppSvc->>AccRepo: save(account)
    Note over AccRepo: mapper sets type='CHECKING',<br/>writes overdraft_limit column,<br/>leaves savings/time-deposit<br/>columns null
    AccRepo->>DB: INSERT INTO accounts (..., type, overdraft_limit) VALUES (..., 'CHECKING', ?)
    DB-->>AccRepo: saved row
    AccRepo-->>AppSvc: CheckingAccount

    AppSvc-->>Ctrl: CheckingAccount
    Ctrl-->>Cust: 201 Created {id, type='CHECKING', currency, balance=0, overdraftLimit, ...}
```

---

## 9. AccrueInterestUseCase

Admin triggers a monthly interest accrual on a savings account. The application service rejects the request if the account is not a `SavingsAccount`. Frozen savings accounts still accrue (interest is a system action, not a customer action); closed accounts do not.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Security  as Security Filter
    participant Ctrl      as AdminController
    participant AppSvc    as AccountApplicationService
    participant Account   as SavingsAccount (entity)
    participant State     as AccountState
    participant AccRepo   as AccountPersistenceAdapter
    participant TxRepo    as TransactionPersistenceAdapter
    participant DB        as PostgreSQL

    Admin->>Security: PUT /api/admin/accounts/{id}/accrue-interest<br/>{"month":"2026-04"}<br/>Basic Auth header
    Security->>Security: authenticate + hasRole(ROLE_ADMIN) ✓
    Security->>Ctrl: forward request

    Ctrl->>AppSvc: accrueInterest(Command{accountId, YearMonth})

    AppSvc->>AccRepo: findById(accountId)
    AccRepo->>DB: SELECT * FROM accounts WHERE id=?
    DB-->>AccRepo: AccountJpaEntity{type='SAVINGS', ...}
    AccRepo-->>AppSvc: SavingsAccount

    Note over AppSvc: if (!(account instanceof SavingsAccount))<br/>→ InvalidAccountOperationException<br/>("Account is not a savings account")<br/>→ HTTP 422

    AppSvc->>Account: accrueInterest(YearMonth)
    Account->>State: isTerminal()
    State-->>Account: false (ACTIVE or FROZEN) / true (CLOSED → throw)
    Note over Account: rejects double-accrual:<br/>if firstOfNextMonth ≤ lastAccrualDate<br/>throws "already accrued"<br/><br/>monthlyRate = annualRate / 12<br/>interest = balance × monthlyRate<br/>balance += interest<br/>lastAccrualDate = first of next month
    Account-->>AppSvc: Transaction{INTEREST, amount, timestamp}

    AppSvc->>AccRepo: save(account)
    AccRepo->>DB: UPDATE accounts SET balance=?, last_accrual_date=? WHERE id=?
    AppSvc->>TxRepo: save(transaction)
    TxRepo->>DB: INSERT INTO transactions (type='INTEREST', ...)

    AppSvc-->>Ctrl: Transaction
    Ctrl-->>Admin: 200 OK {id, type='INTEREST', amount, currency, timestamp}
```

---

## 10. MatureTimeDepositUseCase

Admin matures a time deposit. Maturation credits the full annualised interest (`principal × rate × yearsHeld`) as a single `INTEREST` transaction, sets the `matured` flag (which unlocks future withdrawals), and is rejected if the account is closed, already matured, or before the maturity date. Frozen time deposits can still be matured.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Security as Security Filter
    participant Ctrl     as AdminController
    participant AppSvc   as AccountApplicationService
    participant Account  as TimeDepositAccount (entity)
    participant State    as AccountState
    participant AccRepo  as AccountPersistenceAdapter
    participant TxRepo   as TransactionPersistenceAdapter
    participant DB       as PostgreSQL

    Admin->>Security: PUT /api/admin/accounts/{id}/mature<br/>Basic Auth header
    Security->>Security: authenticate + hasRole(ROLE_ADMIN) ✓
    Security->>Ctrl: forward request

    Ctrl->>AppSvc: mature(Command{accountId})

    AppSvc->>AccRepo: findById(accountId)
    AccRepo->>DB: SELECT * FROM accounts WHERE id=?
    DB-->>AccRepo: AccountJpaEntity{type='TIME_DEPOSIT', ...}
    AccRepo-->>AppSvc: TimeDepositAccount{matured=false, maturityDate, principal, ...}

    Note over AppSvc: if (!(account instanceof TimeDepositAccount))<br/>→ InvalidAccountOperationException<br/>("Account is not a time deposit")<br/>→ HTTP 422

    AppSvc->>Account: mature(LocalDate.now())
    Account->>State: isTerminal()
    State-->>Account: false (ACTIVE/FROZEN) / true (CLOSED → throw)
    Note over Account: rejects double-mature: matured=true → throw<br/>rejects pre-maturity: today < maturityDate → throw<br/><br/>years = months(openedOn → maturityDate) / 12<br/>interest = principal × annualRate × years<br/>balance += interest<br/>matured = true
    Account-->>AppSvc: Transaction{INTEREST, amount, "Maturity interest credit"}

    AppSvc->>AccRepo: save(account)
    AccRepo->>DB: UPDATE accounts SET balance=?, matured=true WHERE id=?
    AppSvc->>TxRepo: save(transaction)
    TxRepo->>DB: INSERT INTO transactions (type='INTEREST', ...)

    AppSvc-->>Ctrl: Transaction
    Ctrl-->>Admin: 200 OK {id, type='INTEREST', amount, currency, timestamp}
```

> After maturation, `withdraw` becomes available on the time deposit (subject to the standard `requireOperable()` state check). `deposit` and `transferOut` remain forbidden by design — the principal stays "fixed".
