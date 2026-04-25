# Test Suite — Ayvalık Bank CC-1

176 tests across 15 test classes. Every test runs with JUnit 5 and AssertJ assertions. No test touches a real database or starts a Spring container unless noted.

Run all tests:
```bash
mvn test
```

Run a single class:
```bash
mvn test -Dtest=AccountControllerTest
```

---

## Test Pyramid

```
                       ┌────────────────────────────┐
                       │     Controller Tests       │  @WebMvcTest
                       │ (MockMvc, mocked use cases)│  51 tests
                       └────────────────────────────┘
               ┌──────────────────────────────────────────────┐
               │          Application Service Tests           │  Mockito only
               │    (mocked ports, real domain objects)       │  29 tests
               └──────────────────────────────────────────────┘
      ┌──────────────────────────────────────────────────────────────┐
      │                     Domain Unit Tests                        │  Pure Java
      │                (no mocks, no Spring, no I/O)                 │  96 tests
      └──────────────────────────────────────────────────────────────┘
```

---

## Domain Unit Tests

These tests cover the core business logic. They are pure Java — no Spring context, no mocks, no I/O. They run in milliseconds.

---

### `MoneyTest` — 9 tests

**Class under test:** `domain/model/account/Money.java` (value object / record)

`Money` holds an amount and a currency. It permits negative amounts (required to represent overdraft balances) and disallows arithmetic across different currencies.

| Test | What it verifies |
|------|-----------------|
| `shouldCreateMoneyWithValidAmountAndCurrency` | `Money.of(100.0, USD)` stores the amount as a `BigDecimal` and the correct currency. |
| `shouldAllowNegativeAmount` | `Money.of(-50.0, EUR)` succeeds and `isNegative()` returns true. |
| `shouldAddMoneyOfSameCurrency` | Adding two USD amounts returns a new `Money` with the correct sum. |
| `shouldRejectAddingDifferentCurrencies` | Adding USD to EUR throws `IllegalArgumentException` with "Currency mismatch". |
| `shouldSubtractMoneyOfSameCurrency` | Subtracting a smaller TL amount from a larger one returns the correct positive difference. |
| `shouldSubtractLargerFromSmallerReturningNegative` | Subtracting a larger amount from a smaller one returns a negative `Money` (no exception). |
| `shouldReturnZeroMoneyForCurrency` | `Money.zero(EUR)` returns a `Money` with amount `0` and currency EUR. |
| `shouldNegatePositiveMoney` | `positive.negate()` returns a `Money` with the negated amount and `isNegative()` true. |
| `shouldRejectNullAmount` | `Money.of(null, USD)` throws `IllegalArgumentException` with "null". |

---

### `AccountTest` — 21 tests

**Class under test:** `domain/model/account/Account.java` (entity)

`Account` is a rich entity. Its methods — `deposit`, `withdraw`, `transferOut`, `transferIn` — enforce currency matching and balance invariants, and each returns a `Transaction` object. It also owns an `AccountStatus` state machine with `freeze()`, `unfreeze()`, and `close()` transitions.

#### Balance and currency invariants

| Test | What it verifies |
|------|-----------------|
| `shouldOpenAccountWithZeroBalance` | `Account.open(...)` starts with a zero balance in the correct currency. |
| `shouldDepositAndIncreaseBalance` | `deposit(500 USD)` raises the balance to 500 and returns a `DEPOSIT` transaction with the correct amount. |
| `shouldWithdrawAndDecreaseBalance` | After depositing 500 then withdrawing 200, balance is 300 and a `WITHDRAWAL` transaction is returned. |
| `shouldRejectWithdrawalExceedingBalance` | Withdrawing more than the current balance throws `IllegalArgumentException` with "Insufficient". |
| `shouldRejectDepositWithWrongCurrency` | Depositing EUR into a USD account throws `IllegalArgumentException` mentioning "currency". |
| `shouldTransferOutWithFeeDeducted` | `transferOut(200 USD, fee=2 USD, ...)` deducts 202 from a 1000 USD balance, leaving 798. |
| `shouldTransferInAndIncreaseBalance` | `transferIn(300 USD, ...)` credits the account, raising balance from 0 to 300. |

#### Status: initial state

| Test | What it verifies |
|------|-----------------|
| `shouldOpenAccountWithActiveStatus` | `Account.open(...)` starts with `AccountStatus.ACTIVE`. |

#### Status: freeze / unfreeze transitions

| Test | What it verifies |
|------|-----------------|
| `shouldFreezeActiveAccount` | `freeze()` on an ACTIVE account sets status to `FROZEN`. |
| `shouldUnfreezeAccount` | `freeze()` followed by `unfreeze()` returns status to `ACTIVE`. |
| `shouldRejectFreezingAlreadyFrozenAccount` | `freeze()` on a FROZEN account throws `IllegalStateException` with "already frozen". |
| `shouldRejectUnfreezingActiveAccount` | `unfreeze()` on an ACTIVE account throws `IllegalStateException` with "not frozen". |

#### Status: close transitions

| Test | What it verifies |
|------|-----------------|
| `shouldCloseActiveAccount` | `close()` on an ACTIVE account sets status to `CLOSED`. |
| `shouldCloseFrozenAccount` | `close()` on a FROZEN account also sets status to `CLOSED`. |
| `shouldRejectClosingAlreadyClosedAccount` | `close()` on a CLOSED account throws `IllegalStateException` with "already closed". |
| `shouldRejectFreezingClosedAccount` | `freeze()` on a CLOSED account throws `IllegalStateException` with "closed". |
| `shouldRejectUnfreezingClosedAccount` | `unfreeze()` on a CLOSED account throws `IllegalStateException` with "closed". |

#### Status: operation guards

| Test | What it verifies |
|------|-----------------|
| `shouldRejectDepositOnFrozenAccount` | `deposit(...)` on a FROZEN account throws `IllegalStateException` with "frozen". |
| `shouldRejectWithdrawOnClosedAccount` | `withdraw(...)` on a CLOSED account throws `IllegalStateException` with "closed". |
| `shouldRejectTransferOutOnFrozenAccount` | `transferOut(...)` on a FROZEN account throws `IllegalStateException` with "frozen". |
| `shouldRejectTransferInOnClosedAccount` | `transferIn(...)` on a CLOSED account throws `IllegalStateException` with "closed". |

---

### `CustomerTest` — 6 tests

**Class under test:** `domain/model/customer/Customer.java` (entity)

`Customer` tracks the current password and up to 3 previous password hashes, plus a `CustomerTier`. The password history is managed entirely inside the entity's `changePassword` method.

| Test | What it verifies |
|------|-----------------|
| `shouldChangePasswordAndMoveCurrentToHistory` | After one `changePassword`, the new hash is current and the old hash is in `passwordHistory[0]`. |
| `shouldKeepAtMostThreePasswordsInHistory` | After 5 password changes starting from "hash-0", only hashes 1–3 remain in history (hash-0 is evicted). Current is hash-4. |
| `getAllPasswordsForReuseCheckShouldIncludeCurrentAndHistory` | After two changes, `getAllPasswordsForReuseCheck()` returns 3 entries (current + 2 history) with the most recent first. |
| `shouldDefaultToStandardTier` | `Customer.create(...)` produces a customer with `tier == STANDARD`. |
| `shouldChangeTier` | `changeTier(PREMIUM)` then `changeTier(PRIVATE)` advances through both transitions. |
| `shouldRejectNullTier` | `changeTier(null)` throws `IllegalArgumentException`. |

---

### `CheckingAccountTest` — 4 tests

**Class under test:** `domain/model/account/CheckingAccount.java` (sealed subtype of `Account`)

`CheckingAccount` extends the base account with an optional overdraft limit. Withdrawals may take the balance negative down to `-overdraftLimit`.

| Test | What it verifies |
|------|-----------------|
| `shouldOpenWithZeroBalanceAndNoOverdraftByDefault` | `CheckingAccount.open(owner, USD)` starts with zero balance, `CHECKING` type, and zero overdraft limit. |
| `shouldWithdrawIntoOverdraftWhenLimitAllows` | With a $100 overdraft limit and $50 balance, withdrawing $120 succeeds and leaves the balance at -$70. |
| `shouldRejectWithdrawalBeyondOverdraftLimit` | Withdrawing $60 with no balance and only a $50 overdraft limit throws `IllegalArgumentException` with "overdraft". |
| `shouldRejectWithdrawalWhenNoOverdraftAndInsufficientFunds` | Withdrawing more than the balance from an account with no overdraft throws `IllegalArgumentException` with "Insufficient". |

---

### `SavingsAccountTest` — 7 tests

**Class under test:** `domain/model/account/SavingsAccount.java` (sealed subtype of `Account`)

`SavingsAccount` carries an annual interest rate and tracks the last accrual date. Interest is credited month by month via `accrueInterest(YearMonth)`.

| Test | What it verifies |
|------|-----------------|
| `shouldOpenWithGivenInterestRateAndZeroBalance` | `SavingsAccount.open(owner, USD, 0.03)` starts with `SAVINGS` type, the given rate, and zero balance. |
| `shouldRejectNegativeInterestRate` | Constructing with a negative rate throws `IllegalArgumentException`. |
| `shouldRejectWithdrawalThatWouldOverdraw` | Withdrawing more than the balance throws `IllegalArgumentException` with "Insufficient". |
| `shouldAccrueInterestForAMonth` | On a $1,000 balance at 12% annual, `accrueInterest(2026-04)` returns an `INTEREST` transaction of $10, raises balance to $1,010, and sets `lastAccrualDate` to 2026-05-01. |
| `shouldAccrueInterestEvenWhenFrozen` | `accrueInterest` succeeds on a FROZEN account (admin-triggered bookkeeping is not blocked by frozen status). |
| `shouldRejectAccrualOnClosedAccount` | `accrueInterest` on a CLOSED account throws `IllegalStateException` with "closed". |
| `shouldRejectDoubleAccrualForSameMonth` | Calling `accrueInterest` twice for the same `YearMonth` throws `IllegalStateException` with "already accrued". |

---

### `TimeDepositAccountTest` — 9 tests

**Class under test:** `domain/model/account/TimeDepositAccount.java` (sealed subtype of `Account`)

`TimeDepositAccount` locks the principal at open. Deposits are rejected, withdrawals are blocked until `mature(LocalDate)` is called on or after the maturity date.

| Test | What it verifies |
|------|-----------------|
| `shouldOpenWithPrincipalAsBalance` | Opening sets the balance to the principal amount, type to `TIME_DEPOSIT`, and `isMatured()` to false. |
| `shouldRejectFurtherDeposits` | Calling `deposit` on a time deposit throws `IllegalStateException` with "locked". |
| `shouldRejectWithdrawalBeforeMaturity` | Calling `withdraw` before maturity throws `IllegalStateException` with "matured". |
| `shouldRejectMaturationBeforeMaturityDate` | `mature(date)` with a date before the maturity date throws `IllegalStateException` with "not yet". |
| `shouldMatureOnOrAfterMaturityDateAndCreditInterest` | `mature(maturityDate)` returns an `INTEREST` transaction equal to `principal × rate`, adds it to the balance, and sets `isMatured()` to true. |
| `shouldAllowWithdrawalAfterMaturity` | After `mature(...)`, `withdraw` succeeds and reduces the balance correctly. |
| `shouldRejectDoubleMaturation` | Calling `mature` a second time throws `IllegalStateException` with "already matured". |
| `shouldMatureWhenFrozen` | `mature` succeeds on a FROZEN account and credits interest; account status remains `FROZEN`. |
| `shouldRejectMaturationOnClosedAccount` | `mature` on a CLOSED account throws `IllegalStateException`. |

---

### `AccountStateTest` — 20 tests

**Class under test:** `domain/model/account/AccountState.java` and the three implementations (`ActiveState`, `FrozenState`, `ClosedState`).

`AccountState` is the State pattern's polymorphic core: a sealed interface with three stateless singleton implementations, each owning the valid transitions and operability check for its state. Tests are grouped with `@Nested` per state, plus a factory and singleton-invariant group.

| Group / Test | What it verifies |
|------|-----------------|
| `shouldMapEachAccountStatusToItsState` | `AccountState.of(ACTIVE/FROZEN/CLOSED)` returns the expected singleton. |
| **Active** — `shouldExposeActiveStatus` | `ActiveState.status()` returns `AccountStatus.ACTIVE`. |
| **Active** — `shouldNotBeTerminal` | `isTerminal()` returns false. |
| **Active** — `shouldAllowOperations` | `requireOperable()` does not throw. |
| **Active** — `shouldTransitionToFrozenOnFreeze` | `freeze()` returns `FrozenState.INSTANCE`. |
| **Active** — `shouldRejectUnfreezeOnActive` | `unfreeze()` throws "not frozen". |
| **Active** — `shouldTransitionToClosedOnClose` | `close()` returns `ClosedState.INSTANCE`. |
| **Frozen** — 6 tests | Mirror of Active: status=FROZEN, not terminal, `requireOperable` throws "frozen", `freeze` throws "already frozen", `unfreeze`→Active, `close`→Closed. |
| **Closed** — 6 tests | status=CLOSED, terminal, `requireOperable` throws "closed", all three transitions throw with "closed" / "already closed". |
| **StatesAreSingletons** — `factoryReturnsSameInstanceForRepeatedCalls` | Repeated `AccountState.of(...)` calls return the same instance per status — confirms no allocation per lookup. |

---

### `CustomerTierTest` — 3 tests

**Class under test:** `domain/model/customer/CustomerTier.java` (enum with policy data)

`CustomerTier` carries the per-tier policy bundle: a fee multiplier (`BigDecimal`) that scales the admin's transfer-fee percent, and two `Optional<BigDecimal>` per-transaction caps (one for transfers, one for withdrawals; `Optional.empty()` for unlimited).

| Test | What it verifies |
|------|-----------------|
| `standardHasFullFeeAndModestCaps` | `STANDARD.feeMultiplier()` is 1.00; both caps are 5,000. |
| `premiumHalvesFeeAndRaisesCaps` | `PREMIUM.feeMultiplier()` is 0.50; transfer cap 50,000, withdrawal cap 25,000. |
| `privateIsFreeAndUnlimited` | `PRIVATE.feeMultiplier()` is 0.00; both caps are `Optional.empty()`. |

---

### `TransferDomainServiceTest` — 9 tests

**Class under test:** `domain/service/account/TransferDomainService.java` (domain service)

`TransferDomainService` calculates fees and validates per-transaction limits. Tests are pure Java — no Spring, no mocks. Same-customer transfers are always free regardless of tier.

| Test | What it verifies |
|------|-----------------|
| `sameCustomerTransferIsFreeRegardlessOfTier` | Same-customer flag short-circuits to zero fee. |
| `standardTierPaysFullFee` | At 1% admin setting, STANDARD pays the full 1% (10.00 on 1000.00). |
| `premiumTierPaysHalfFee` | PREMIUM pays half (5.00 on 1000.00). |
| `privateTierPaysNoFee` | PRIVATE pays nothing. |
| `rejectsTransferAboveStandardCap` | 5001 USD on STANDARD throws `IllegalStateException` mentioning the tier and limit. |
| `allowsTransferAtExactlyTheCap` | Exactly 5000 USD passes (boundary inclusive). |
| `privateTierTransferIsUnlimited` | 1,000,000 USD on PRIVATE passes. |
| `rejectsWithdrawalAbovePremiumCap` | 25,001 USD on PREMIUM throws. |
| `privateTierWithdrawalIsUnlimited` | Same as transfer for withdrawals. |

---

### `PasswordValidationServiceTest` — 8 tests

**Class under test:** `domain/service/customer/PasswordValidationService.java` (domain service)

`PasswordValidationService` enforces the password policy: 8–16 characters, at least one uppercase letter, one lowercase letter, one digit, and one special character.

| Test | What it verifies |
|------|-----------------|
| `shouldAcceptValidPassword` | `"Valid@123"` passes all rules without throwing. |
| `shouldRejectPasswordOutOfLengthRange` (parameterized × 2) | `"Short1!"` (7 chars) and `"ThisIsWayTooLong1!"` (18 chars) both throw with "between 8 and 16". |
| `shouldRejectPasswordWithoutUppercase` | `"nouppercase1!"` throws with "uppercase". |
| `shouldRejectPasswordWithoutLowercase` | `"NOLOWERCASE1!"` throws with "lowercase". |
| `shouldRejectPasswordWithoutDigit` | `"NoDigitHere!"` throws with "digit". |
| `shouldRejectPasswordWithoutSpecialCharacter` | `"NoSpecial123"` throws with "special". |
| `shouldRejectNullPassword` | `null` throws `IllegalArgumentException`. |

---

## Application Service Tests

These tests cover the orchestration layer. All repository and infrastructure ports are replaced with Mockito mocks. Domain objects (`Account`, `Customer`, `TransferDomainService`) are used as real instances — the application service's job is to wire them together correctly.

---

### `CustomerApplicationServiceTest` — 8 tests

**Class under test:** `application/service/CustomerApplicationService.java`

**Mocked ports:** `CustomerRepositoryPort`, `PasswordHasherPort`, `SettingsRepositoryPort`
**Real domain objects:** `Customer`, `PasswordValidationService`

| Test | What it verifies |
|------|-----------------|
| `shouldCreateCustomerWithHashedPassword` | `createCustomer` validates the password, hashes it via `PasswordHasherPort`, constructs a `Customer`, saves it, and returns it with the correct email and hashed password. |
| `shouldThrowInvalidPasswordExceptionForWeakPassword` | `createCustomer` with `"weak"` throws `InvalidPasswordException` before calling the repository. |
| `shouldDeleteExistingCustomer` | `deleteCustomer` calls `existsById` first; if the customer exists it calls `deleteById`. |
| `shouldThrowCustomerNotFoundOnDeleteOfMissingCustomer` | `deleteCustomer` throws `CustomerNotFoundException` when `existsById` returns false. |
| `shouldChangePasswordSuccessfully` | `changePassword` validates format, loads the customer, checks that the new hash does not match the old one, hashes it, calls `customer.changePassword(...)`, and saves. |
| `shouldThrowPasswordReusedExceptionWhenNewPasswordMatchesCurrent` | `changePassword` throws `PasswordReusedException` when BCrypt confirms the new password matches the current hash. |
| `shouldChangeCustomerTier` | `changeCustomerTier(PREMIUM)` loads the customer, calls `customer.changeTier(...)`, saves; the in-memory tier is now `PREMIUM`. |
| `shouldThrowCustomerNotFoundOnChangeTierForMissingCustomer` | `changeCustomerTier` throws `CustomerNotFoundException` when `findById` returns empty. |

---

### `AccountApplicationServiceTest` — 21 tests

**Class under test:** `application/service/AccountApplicationService.java`

**Mocked ports:** `AccountRepositoryPort`, `CustomerRepositoryPort`, `TransactionRepositoryPort`, `SettingsRepositoryPort`
**Real domain objects:** `Account` subtypes, `Transaction`, `TransferDomainService`

#### Account opening

| Test | What it verifies |
|------|-----------------|
| `shouldOpenCheckingAccountForExistingCustomer` | `openChecking` verifies the owner exists, opens a `CheckingAccount`, saves it, and returns it with the correct currency. |
| `shouldOpenSavingsAccountForExistingCustomer` | `openSavings` opens a `SavingsAccount` with the given interest rate and saves it. |
| `shouldOpenTimeDepositAccountForExistingCustomer` | `openTimeDeposit` opens a `TimeDepositAccount` with the given principal and maturity date and saves it. |
| `shouldThrowCustomerNotFoundWhenOpeningCheckingForMissingOwner` | `openChecking` throws `CustomerNotFoundException` when the owner does not exist. |

#### Account operations

| Test | What it verifies |
|------|-----------------|
| `shouldDepositMoneyToAccount` | `deposit` loads the account, calls `account.deposit(...)`, saves the updated account, saves the transaction, and returns the `DEPOSIT` transaction. Balance on the in-memory account is 200 after the call. |
| `shouldThrowAccountNotFoundOnDepositToMissingAccount` | `deposit` throws `AccountNotFoundException` when `findById` returns empty. |
| `shouldTransferBetweenAccountsOfSameCustomerFreeOfCharge` | Transfer between two accounts owned by the same customer applies 0% fee regardless of the configured rate. Source ends at 300, target at 200. |
| `shouldDeductFeeForTransferBetweenDifferentCustomers` | Transfer between accounts of different customers with 1% fee deducts 202 from source (200 + 2 fee) and credits 200 to target. |
| `shouldThrowOnWithdrawExceedingBalance` | `withdraw` propagates the `IllegalArgumentException("Insufficient funds")` thrown by the domain entity when the amount exceeds the balance. |

#### Account status management

| Test | What it verifies |
|------|-----------------|
| `shouldFreezeAccount` | `freezeAccount` loads the account, calls `account.freeze()`, saves it; status on the in-memory account becomes `FROZEN`. |
| `shouldUnfreezeAccount` | `unfreezeAccount` loads a FROZEN account, calls `account.unfreeze()`, saves it; status returns to `ACTIVE`. |
| `shouldCloseAccount` | `closeAccount` loads the account, calls `account.close()`, saves it; status becomes `CLOSED`. |
| `shouldThrowAccountNotOperableWhenFreezingClosedAccount` | When the domain entity throws `IllegalStateException` (e.g. freezing a closed account), the service wraps it in `AccountNotOperableException`. |
| `shouldThrowAccountNotFoundWhenFreezingMissingAccount` | `freezeAccount` throws `AccountNotFoundException` when `findById` returns empty. |

#### Savings and time-deposit operations

| Test | What it verifies |
|------|-----------------|
| `shouldAccrueInterestOnSavingsAccount` | `accrueInterest` loads a `SavingsAccount`, delegates to `account.accrueInterest(yearMonth)`, saves the account, saves the transaction, and returns the `INTEREST` transaction. |
| `shouldRejectAccrueInterestOnNonSavingsAccount` | `accrueInterest` called with a non-`SavingsAccount` throws `AccountNotOperableException`. |
| `shouldMatureTimeDepositOnOrAfterMaturityDate` | `matureTimeDeposit` loads a `TimeDepositAccount`, delegates to `account.mature(date)`, saves the account, saves the transaction, and returns the `INTEREST` transaction. |
| `shouldRejectMatureOnNonTimeDepositAccount` | `matureTimeDeposit` called with a non-`TimeDepositAccount` throws `AccountNotOperableException`. |

#### Customer-tier-aware fee and limits

| Test | What it verifies |
|------|-----------------|
| `shouldHalveFeeForPremiumSourceCustomer` | When the source owner is `PREMIUM`, the cross-customer transfer fee is halved (1% × 0.5 multiplier × 200 = 1.00 fee, source debited 201.00). |
| `shouldRejectTransferAboveStandardCap` | A 5001 USD transfer for a `STANDARD` source customer throws `LimitExceededException` (the cap is 5000). |
| `shouldRejectWithdrawAboveStandardCap` | A 5001 USD withdrawal on an account whose owner is `STANDARD` throws `LimitExceededException`. |

---

## Controller Tests (Web Layer)

These tests use Spring's `@WebMvcTest` to load only the web layer — the controller, `GlobalExceptionHandler`, and the `SecurityConfig`. All use-case interfaces are replaced with `@MockitoBean`. Authentication is provided by `@WithMockUser`.

No real application service or database is involved. The goal is to verify:
- Correct HTTP status codes
- Correct JSON structure in responses
- Bean validation on request bodies
- Role-based access control (ADMIN vs CUSTOMER)
- That `GlobalExceptionHandler` maps domain exceptions to the right HTTP statuses

---

### `AdminControllerTest` — 25 tests

**Controller:** `adapter/in/web/AdminController.java` (`/api/admin/**`)
**Required role:** `ROLE_ADMIN`

#### `POST /api/admin/customers`

| Test | What it verifies |
|------|-----------------|
| `createCustomer_returnsCreated` | Valid body → 201, response JSON contains `name`, `email`, `role`. |
| `createCustomer_returnsBadRequestOnMissingName` | Body without `name` → 400; use case is never called. |
| `createCustomer_returnsBadRequestOnInvalidEmail` | Body with `"not-an-email"` → 400 (Jakarta `@Email` constraint). |
| `createCustomer_returnsForbiddenForCustomerRole` | `ROLE_CUSTOMER` → 403. |
| `createCustomer_returnsUnauthorizedWithoutCredentials` | No auth header → 401. |

#### `DELETE /api/admin/customers/{id}`

| Test | What it verifies |
|------|-----------------|
| `deleteCustomer_returnsNoContent` | Use case succeeds → 204. |
| `deleteCustomer_returnsNotFoundWhenMissing` | Use case throws `CustomerNotFoundException` → 404. |

#### `GET /api/admin/customers`

| Test | What it verifies |
|------|-----------------|
| `listCustomers_returnsOkWithList` | Use case returns two customers → 200, array of length 2, correct emails. |
| `listCustomers_returnsEmptyList` | Use case returns empty list → 200, empty array. |

#### `PUT /api/admin/customers/{id}/tier`

| Test | What it verifies |
|------|-----------------|
| `changeCustomerTier_returnsOk` | Valid body `{"tier":"PREMIUM"}` → 200; verifies the use case was called once. |
| `changeCustomerTier_returnsBadRequestOnMissingTier` | Empty body `{}` → 400 (Jakarta `@NotNull` constraint); use case is never called. |
| `changeCustomerTier_returnsForbiddenForCustomerRole` | `ROLE_CUSTOMER` → 403. |

#### `PUT /api/admin/settings/transfer-fee`

| Test | What it verifies |
|------|-----------------|
| `setTransferFee_returnsOk` | Valid `feePercent` → 200; verifies use case received the exact `Command(1.5)`. |
| `setTransferFee_returnsBadRequestForNegativeValue` | `feePercent: -1.0` → 400; use case never called. |
| `setTransferFee_returnsBadRequestForValueAbove100` | `feePercent: 101.0` → 400 (`@DecimalMax("100.0")` constraint). |

#### `PUT /api/admin/accounts/{id}/freeze`

| Test | What it verifies |
|------|-----------------|
| `freezeAccount_returnsOk` | Use case succeeds → 200; verifies `freezeAccount` use case was called. |
| `freezeAccount_returnsUnprocessableEntityForInvalidTransition` | Use case throws `AccountNotOperableException` (e.g. already frozen) → 422. |

#### `PUT /api/admin/accounts/{id}/unfreeze`

| Test | What it verifies |
|------|-----------------|
| `unfreezeAccount_returnsOk` | Use case succeeds → 200. |
| `unfreezeAccount_returnsUnprocessableEntityForInvalidTransition` | Use case throws `AccountNotOperableException` (e.g. not frozen) → 422. |

#### `PUT /api/admin/accounts/{id}/close`

| Test | What it verifies |
|------|-----------------|
| `closeAccount_returnsOk` | Use case succeeds → 200. |
| `closeAccount_returnsUnprocessableEntityForAlreadyClosed` | Use case throws `AccountNotOperableException` (already closed) → 422. |
| `closeAccount_returnsForbiddenForCustomerRole` | `ROLE_CUSTOMER` → 403. |

#### `PUT /api/admin/accounts/{id}/accrue-interest`

| Test | What it verifies |
|------|-----------------|
| `accrueInterest_returnsOk` | Valid `yearMonth` → 200; use case called once with correct command. |
| `accrueInterest_returnsForbiddenForCustomerRole` | `ROLE_CUSTOMER` → 403. |

#### `PUT /api/admin/accounts/{id}/mature`

| Test | What it verifies |
|------|-----------------|
| `matureTimeDeposit_returnsOk` | Valid `date` → 200; use case called once with correct command. |

---

### `CustomerControllerTest` — 7 tests

**Controller:** `adapter/in/web/CustomerController.java` (`/api/customers/**`)
**Required role:** `ROLE_CUSTOMER`

#### `PUT /api/customers/{id}/password`

| Test | What it verifies |
|------|-----------------|
| `changePassword_returnsOk` | Valid body → 200; use case is called once. |
| `changePassword_returnsBadRequestOnBlankPassword` | Empty string `""` → 400 (`@NotBlank`); use case never called. |
| `changePassword_returnsBadRequestOnWeakPassword` | Use case throws `InvalidPasswordException` → 400. |
| `changePassword_returnsConflictOnPasswordReuse` | Use case throws `PasswordReusedException` → 409. |
| `changePassword_returnsNotFoundForUnknownCustomer` | Use case throws `CustomerNotFoundException` → 404. |
| `changePassword_returnsForbiddenForAdminRole` | `ROLE_ADMIN` → 403. |
| `changePassword_returnsUnauthorizedWithoutCredentials` | No auth → 401. |

---

### `AccountControllerTest` — 19 tests

**Controller:** `adapter/in/web/AccountController.java` (`/api/accounts/**`, `/api/customers/**`)
**Required role:** `ROLE_CUSTOMER`

#### `POST /api/accounts/checking`

| Test | What it verifies |
|------|-----------------|
| `openChecking_returnsCreated` | Valid body with `ownerId` param → 201, JSON has `type: "CHECKING"`, `currency: "USD"`, and `overdraftLimit: 0`. |
| `openChecking_returnsBadRequestOnMissingCurrency` | Empty body `{}` → 400 (`@NotNull` on currency); use case never called. |
| `openChecking_returnsForbiddenForAdminRole` | `ROLE_ADMIN` → 403. |

#### `POST /api/accounts/savings`

| Test | What it verifies |
|------|-----------------|
| `openSavings_returnsCreated` | Valid body → 201, JSON has `type: "SAVINGS"` and `interestRate: 0.03`. |

#### `POST /api/accounts/time-deposit`

| Test | What it verifies |
|------|-----------------|
| `openTimeDeposit_returnsCreated` | Valid body with principal and maturity date → 201, JSON has `type: "TIME_DEPOSIT"` and `principal: 1000`. |

#### `GET /api/customers/{id}/accounts`

| Test | What it verifies |
|------|-----------------|
| `listAccounts_returnsOkWithList` | Two accounts returned → 200, array length 2, correct currencies. |

#### `GET /api/accounts/{id}/balance`

| Test | What it verifies |
|------|-----------------|
| `getBalance_returnsOk` | Use case returns `Money(250, USD)` → 200, `{"amount":250.0,"currency":"USD"}`. |
| `getBalance_returnsNotFoundForUnknownAccount` | Use case throws `AccountNotFoundException` → 404. |

#### `POST /api/accounts/{id}/deposit`

| Test | What it verifies |
|------|-----------------|
| `deposit_returnsCreated` | Valid body → 201, transaction JSON has `type: "DEPOSIT"`, correct amount and currency. |
| `deposit_returnsBadRequestOnNegativeAmount` | `amount: -50` → 400 (`@Positive`); use case never called. |
| `deposit_returnsNotFoundForUnknownAccount` | Use case throws `AccountNotFoundException` → 404. |

#### `POST /api/accounts/{id}/withdraw`

| Test | What it verifies |
|------|-----------------|
| `withdraw_returnsCreated` | Valid body → 201, transaction JSON has `type: "WITHDRAWAL"`. |
| `withdraw_returnsUnprocessableEntityOnInsufficientFunds` | Use case throws `InsufficientFundsException` → 422. |

#### `POST /api/accounts/{id}/transfer`

| Test | What it verifies |
|------|-----------------|
| `transfer_returnsOk` | Valid body with target account ID → 200; use case called once. |
| `transfer_returnsBadRequestOnMissingTarget` | Body without `targetAccountId` → 400; use case never called. |
| `transfer_returnsUnprocessableEntityOnInsufficientFunds` | Use case throws `InsufficientFundsException` → 422. |

#### `GET /api/accounts/{id}/transactions`

| Test | What it verifies |
|------|-----------------|
| `getTransactions_returnsOkWithList` | Two transactions returned → 200, array length 2, correct types. |
| `getTransactions_returnsNotFoundForUnknownAccount` | Use case throws `AccountNotFoundException` → 404. |
| `getTransactions_returnsUnauthorizedWithoutCredentials` | No auth → 401. |

---

## Exception-to-HTTP Mapping

`GlobalExceptionHandler` (`@RestControllerAdvice`) converts domain exceptions to RFC 9457 Problem Detail responses. The controller tests exercise every mapping:

| Exception | HTTP Status | Tested in |
|-----------|-------------|-----------|
| `CustomerNotFoundException` | 404 Not Found | `AdminControllerTest`, `CustomerControllerTest` |
| `AccountNotFoundException` | 404 Not Found | `AccountControllerTest` |
| `AccountNotOperableException` | 422 Unprocessable Entity | `AdminControllerTest` |
| `InsufficientFundsException` | 422 Unprocessable Entity | `AccountControllerTest` |
| `InvalidPasswordException` | 400 Bad Request | `CustomerControllerTest` |
| `PasswordReusedException` | 409 Conflict | `CustomerControllerTest` |
| `UnauthorizedAccessException` | 403 Forbidden | (role check via Spring Security) |
| `IllegalArgumentException` | 400 Bad Request | domain entity invariant violations |
| `MethodArgumentNotValidException` | 400 Bad Request | Jakarta Bean Validation failures |

---

## Testing Style Analysis

The three styles are:
- **Output testing** — assert what a method *returns* (including thrown exceptions)
- **State testing** — assert how an object's *internal state* changed after an operation
- **Communication testing** — assert that the SUT *called* a collaborator in the right way (`verify`, `verifyNoInteractions`)

---

### `MoneyTest` — Pure Output Testing

`Money` is an immutable value object. Every operation returns a new `Money` instance, so there is no state to observe and no collaborators to verify. Every test here asserts either a return value or a thrown exception.

```java
// output: verifies the value returned by add()
assertThat(a.add(b).amount()).isEqualByComparingTo("150.00");

// output: verifies the exception thrown by subtract()
assertThatThrownBy(() -> a.subtract(b))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Insufficient");
```

All 7 tests are output tests. This is the correct style for a value object.

---

### `PasswordValidationServiceTest` — Pure Output Testing

`validate()` returns nothing; its only observable output is whether it throws. All 7 tests verify that contract.

```java
assertThatNoException().isThrownBy(() -> service.validate("Valid@123"));
assertThatThrownBy(() -> service.validate("nouppercase1!"))
    .hasMessageContaining("uppercase");
```

Pure output style. Correct for a stateless, side-effect-free domain service.

---

### `AccountTest` — State + Output (mixed)

`Account` is a rich entity: it has mutable state and its methods return `Transaction` objects. The tests naturally split across both styles.

**State tests** — call a mutating method, then inspect the entity:
```java
// state: status machine transitions
account.freeze();
assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);

// state: balance after deposit
account.deposit(Money.of(500.0, Currency.USD));
assertThat(account.getBalance().amount()).isEqualByComparingTo("500.00");
```

**Output tests** — assert the return value or exception:
```java
// output: returned Transaction from deposit()
Transaction tx = account.deposit(Money.of(500.0, Currency.USD));
assertThat(tx.getType()).isEqualTo(TransactionType.DEPOSIT);

// output: exception from invalid transition
assertThatThrownBy(account::freeze)
    .isInstanceOf(IllegalStateException.class)
    .hasMessageContaining("already frozen");
``` 

**Mixed tests** — `shouldDepositAndIncreaseBalance` and `shouldWithdrawAndDecreaseBalance` test both the return value *and* the resulting balance in a single test. That is legitimate since both are part of the same operation contract.

---

### `CustomerTest` — State + Output (mostly state)

Two tests assert the internal state of the password history list after calling `changePassword()` — classic state testing. One test asserts the return value of `getAllPasswordsForReuseCheck()` — output testing.

```java
// state: history list grows and oldest entry is evicted
customer.changePassword(Password.ofHashed("hash-4"));
assertThat(customer.getPasswordHistory()).hasSize(3);
assertThat(customer.getPasswordHistory().get(0).hashedValue()).isEqualTo("hash-3");

// output: verifies the list returned by getAllPasswordsForReuseCheck()
var all = customer.getAllPasswordsForReuseCheck();
assertThat(all).hasSize(3);
```

---

### `AccountApplicationServiceTest` — All Three Styles

This is where all three styles appear, because the application service has collaborators (ports) that can be observed with `verify`.

**Output tests** (exceptions, returned values):
```java
assertThatThrownBy(() -> service.createAccount(...))
    .isInstanceOf(CustomerNotFoundException.class);

assertThat(tx.getType()).isEqualTo(TransactionType.DEPOSIT);
```

**State tests** (balance and status on domain objects passed through mocks):
```java
// the Account object is real; its state is inspectable after the service acts on it
assertThat(source.getBalance().amount()).isEqualByComparingTo("798.00");
assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);
```

**Communication tests** — but only in one test:
```java
// shouldFreezeAccount is the only test that verifies a collaborator call
verify(accountRepository).save(account);
```

**Inconsistency**: `shouldUnfreezeAccount` and `shouldCloseAccount` skip the `verify(accountRepository).save(...)` that `shouldFreezeAccount` includes. The service must call `save` in all three cases — that contract is untested for unfreeze and close.

---

### `CustomerApplicationServiceTest` — Output + Communication (mostly)

**Communication tests** — the dominant style here:
```java
// pure communication: only asserts the collaborator was called
service.deleteCustomer(id);
verify(customerRepository).deleteById(id);

// negative communication: asserts the collaborator was NOT called
assertThatThrownBy(() -> service.createCustomer(...));
verifyNoInteractions(customerRepository);
```

**Mixed output + communication** in `shouldCreateCustomerWithHashedPassword`:
```java
assertThat(result.getEmail()).isEqualTo("ali@test.com");        // output
assertThat(result.getCurrentPassword().hashedValue()).isEqualTo("hashed"); // output
verify(customerRepository).save(any());                         // communication
```

**Mixed state + communication** in `shouldChangePasswordSuccessfully`:
```java
assertThat(customer.getCurrentPassword().hashedValue()).isEqualTo("new-hash"); // state
verify(customerRepository).save(customer);                                     // communication
```

---

### Controller Tests — Output + Negative Communication

The controller tests are overwhelmingly **output tests** at the HTTP protocol layer. The "output" is the HTTP response — status code and JSON body.

```java
// output: HTTP status and JSON fields
mockMvc.perform(post("/api/accounts")....)
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.currency").value("USD"))
    .andExpect(jsonPath("$.balance").value(0));
```

**Communication tests** appear as positive and negative verification:
```java
// positive: use case was invoked
verify(transferMoney).transfer(any());

// negative: use case was NOT reached because validation failed first
verifyNoInteractions(createAccount);
```

The negative communication tests (`verifyNoInteractions`) are important here — they prove that bean-validation failures short-circuit before the use case is called.

---

### Summary Table

| Test class | Output | State | Communication |
|---|---|---|---|
| `MoneyTest` | all 9 | — | — |
| `PasswordValidationServiceTest` | all 8 | — | — |
| `AccountTest` | ~11 (exceptions + returned Tx) | ~9 (balance, status) | — |
| `CheckingAccountTest` | ~3 (exceptions + returned state) | ~1 (balance) | — |
| `SavingsAccountTest` | ~5 (exceptions + returned Tx) | ~2 (balance, lastAccrualDate) | — |
| `TimeDepositAccountTest` | ~6 (exceptions + returned Tx) | ~3 (balance, matured, status) | — |
| `AccountStateTest` | ~6 (exceptions on invalid transitions) | ~14 (returned-state identity, status, terminal) | — |
| `CustomerTierTest` | — | 3 (multiplier + caps per tier) | — |
| `TransferDomainServiceTest` | ~3 (limit-exceeded exceptions) | ~6 (returned fees, allowed limits) | — |
| `CustomerTest` | 2 (null tier, default tier) | 4 (password history + tier change) | — |
| `AccountApplicationServiceTest` | ~8 (exceptions + returned objects) | ~6 (balance, status) | 1 (only `shouldFreezeAccount`) |
| `CustomerApplicationServiceTest` | ~3 (exceptions) | 1 | ~4 |
| `AccountControllerTest` | ~17 (HTTP status + JSON) | — | ~5 (verify / verifyNoInteractions) |
| `AdminControllerTest` | ~18 (HTTP status + JSON) | — | ~4 |
| `CustomerControllerTest` | ~7 (HTTP status) | — | ~3 |

---

### Notable Issues

**Missing communication tests in `AccountApplicationServiceTest`**: `shouldUnfreezeAccount` and `shouldCloseAccount` verify state but do not verify that `accountRepository.save()` was called. The service contract requires that save — those tests should have a `verify(accountRepository).save(account)` to match `shouldFreezeAccount`.

**`shouldDepositAndIncreaseBalance` mixes state and output correctly**: asserting both the returned `Transaction` and the updated balance is appropriate since both are part of the operation's contract. Not a problem — just worth noting it spans two styles intentionally.

**Domain model tests have no communication tests**: correct, because `Account`, `Customer`, and `Money` have no collaborators. Introducing mocks there would be a design smell.

---

## Coverage Analysis

Coverage was measured with **JaCoCo 0.8.13** on Java 25, produced by `mvn verify`. The raw data comes from `target/site/jacoco/jacoco.csv`. Test classes themselves are excluded from all figures. The figures below pre-date the account-types feature (sealed `Account` hierarchy + 3 subtypes) and the State-pattern refactor (`AccountState` + 3 singletons); rerun `mvn verify` to refresh.

---

### Coverage Techniques

JaCoCo measures five dimensions simultaneously. Each answers a different question about how thoroughly the tests exercise the production code.

| Technique | Granularity | Question answered |
|-----------|-------------|------------------|
| **Instruction** | JVM bytecode instruction | Has this specific bytecode instruction been executed? |
| **Branch** | Boolean decision outcome | Has each true/false arm of every `if`, `switch`, and ternary been taken? |
| **Line** | Source line | Has at least one instruction on this line been reached? |
| **Cyclomatic Complexity** | Execution path | How many of the linearly independent paths through each method have been traversed? |
| **Method** | Method body | Has this method been entered at all? |

**Why they differ:**

- A single test can cover a *line* while missing one *branch* of the condition on that line — this is why branch coverage is always ≤ line coverage.
- A method with 5 branches has cyclomatic complexity 6; full complexity coverage requires at least 6 distinct tests exercising every path combination, so it is the hardest metric to saturate.
- *Instruction coverage* is the most precise: it operates below the source level and catches dead code inside a single statement that line coverage would mark green.
- *Method coverage* is the most forgiving: a method with 50 lines counts the same as one with 2. Low method coverage signals that whole entry points are never reached.

---

### Overall Summary

| Technique | Covered | Total | Coverage |
|-----------|---------|-------|----------|
| Instruction | 1,810 | 2,799 | **64.7%** |
| Branch | 78 | 102 | **76.5%** |
| Line | 392 | 619 | **63.3%** |
| Complexity | 175 | 303 | **57.8%** |
| Method | 143 | 252 | **56.7%** |

These headline numbers are pulled down substantially by the outbound persistence layer (persistence adapters, mappers, JPA entities) which sits at **0%** across all metrics by design — no integration test exercises a real database. Filtering that layer out gives a much more representative picture of intentionally tested code.

---

### By Architectural Layer

| Layer | Instruction | Branch | Line | Method |
|-------|-------------|--------|------|--------|
| **Domain** (model + services) | 809 / 898 → **90.1%** | 69 / 82 → **84.1%** | 175 / 185 → **94.6%** | 62 / 66 → **93.9%** |
| **Application services** | 324 / 412 → **78.6%** | 8 / 12 → **66.7%** | 79 / 97 → **81.4%** | 16 / 22 → **72.7%** |
| **Web adapters** (controllers + DTOs + GEH) | 494 / 507 → **97.4%** | 1 / 2 → **50.0%** | 104 / 106 → **98.1%** | 42 / 44 → **95.5%** |
| **Ports In** (Command records) | 66 / 66 → **100%** | — | 7 / 7 → **100%** | 7 / 7 → **100%** |
| **Config** (SecurityConfig + initializers) | 90 / 213 → **42.3%** | 0 / 4 → **0.0%** | 19 / 49 → **38.8%** | 9 / 17 → **52.9%** |
| **Outbound adapters + security** | 0 / 667 → **0.0%** | 0 / 2 → **0.0%** | 0 / 164 → **0.0%** | 0 / 87 → **0.0%** |

---

### Class-Level Detail

| Class | Instruction | Branch | Line | Method |
|-------|-------------|--------|------|--------|
| `Account` | 259 / 290 → **89.3%** | 27 / 32 → **84.4%** | 58 / 63 → **92.1%** | 15 / 15 → **100%** |
| `Customer` | 96 / 96 → **100%** | 2 / 2 → **100%** | 24 / 24 → **100%** | 10 / 10 → **100%** |
| `Money` | 130 / 153 → **85.0%** | 10 / 12 → **83.3%** | 22 / 23 → **95.7%** | 9 / 10 → **90.0%** |
| `PasswordValidationService` | 103 / 103 → **100%** | 23 / 24 → **95.8%** | 17 / 17 → **100%** | 2 / 2 → **100%** |
| `TransferDomainService` | 24 / 24 → **100%** | 2 / 2 → **100%** | 7 / 7 → **100%** | 2 / 2 → **100%** |
| `CustomerApplicationService` | 128 / 156 → **82.1%** | 6 / 8 → **75.0%** | 32 / 37 → **86.5%** | 6 / 9 → **66.7%** |
| `AccountApplicationService` | 196 / 256 → **76.6%** | 2 / 4 → **50.0%** | 47 / 60 → **78.3%** | 10 / 13 → **76.9%** |
| `GlobalExceptionHandler` | 60 / 73 → **82.2%** | 1 / 2 → **50.0%** | 11 / 13 → **84.6%** | 10 / 12 → **83.3%** |
| `AccountController` | 136 / 136 → **100%** | — | 31 / 31 → **100%** | 8 / 8 → **100%** |
| `AdminController` | 97 / 97 → **100%** | — | 26 / 26 → **100%** | 8 / 8 → **100%** |
| `CustomerPersistenceAdapter` | 0 / 75 → **0%** | — | 0 / 13 → **0%** | 0 / 7 → **0%** |
| `BCryptPasswordHasherAdapter` | 0 / 20 → **0%** | — | 0 / 4 → **0%** | 0 / 3 → **0%** |

Controllers show no branch data because their routing logic carries no explicit conditionals — all branching happens at the Spring dispatcher level or inside the use cases.

---

### Key Observations

**The domain layer is the best-covered layer (90.1% instruction).** This is the right outcome for hexagonal architecture: the core business logic lives in pure Java with no infrastructure dependencies, so unit tests can reach it directly without any mocking of the environment. `Customer`, `TransferDomainService`, and `PasswordValidationService` all reach 100% instruction and line coverage.

**Branch coverage consistently lags instruction and line coverage by 10–15 percentage points.** This is the normal pattern: a single test reaching a line marks it covered even if only one arm of its condition is exercised. `AccountApplicationService` shows this most starkly — 78% line but only 50% branch — because the same-customer/different-customer conditional inside the transfer flow is exercised in both directions by only two tests, but the error-path branches (e.g. source account missing a balance check) are not fully driven from both sides.

**Web adapters achieve 97–98% line coverage** because every controller method has at least one `@WebMvcTest` case and every request/response DTO is instantiated in the process. The 50% branch figure for the layer is entirely due to `GlobalExceptionHandler`, which has one handler method (`handleUnauthorizedAccess`) that Spring Security intercepts before the exception can reach the handler in the `@WebMvcTest` slice.

**Cyclomatic complexity coverage (57.8%) is the lowest metric overall** and the most meaningful signal of path-testing gaps. A method with complexity N needs N tests to cover every independent path. The application service methods each have complexity 4–11 but only 3–6 tests, meaning some path combinations remain untested.

**The outbound persistence layer is intentionally at 0%.** `CustomerPersistenceAdapter`, `AccountPersistenceAdapter`, `TransactionPersistenceAdapter`, `SettingsPersistenceAdapter`, their three mappers, five JPA entities, and `BCryptPasswordHasherAdapter` have no test coverage. Closing this gap requires integration tests that boot a real database — for example with Testcontainers using the PostgreSQL image from `docker-compose.yml`.

**Three application service methods are uncovered (method coverage 72.7%).** `CustomerApplicationService` has three methods, one of which (`listCustomers`) is never invoked in the service tests (it is tested only through the controller slice). `AccountApplicationService` is missing three methods — `listAccounts`, `getBalance`, and `getTransactions` — from its own unit test class, each exercised only at the controller layer.
