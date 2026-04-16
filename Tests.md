# Test Suite — Ayvalık Bank CC-1

74 tests across 8 test classes. Every test runs with JUnit 5 and AssertJ assertions. No test touches a real database or starts a Spring container unless noted.

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
                       │ (MockMvc, mocked use cases)│  36 tests
                       └────────────────────────────┘
               ┌──────────────────────────────────────────────┐
               │          Application Service Tests           │  Mockito only
               │    (mocked ports, real domain objects)       │  13 tests
               └──────────────────────────────────────────────┘
      ┌──────────────────────────────────────────────────────────────┐
      │                     Domain Unit Tests                        │  Pure Java
      │                (no mocks, no Spring, no I/O)                 │  25 tests
      └──────────────────────────────────────────────────────────────┘
```

---

## Domain Unit Tests

These tests cover the core business logic. They are pure Java — no Spring context, no mocks, no I/O. They run in milliseconds.

---

### `MoneyTest` — 7 tests

**Class under test:** `domain/model/Money.java` (value object / record)

`Money` holds an amount and a currency. It enforces non-negative amounts and disallows arithmetic across different currencies.

| Test | What it verifies |
|------|-----------------|
| `shouldCreateMoneyWithValidAmountAndCurrency` | `Money.of(100.0, USD)` stores the amount as a `BigDecimal` and the correct currency. |
| `shouldRejectNegativeAmount` | Constructing money with a negative amount throws `IllegalArgumentException` with "negative" in the message. |
| `shouldAddMoneyOfSameCurrency` | Adding two USD amounts returns a new `Money` with the correct sum. |
| `shouldRejectAddingDifferentCurrencies` | Adding USD to EUR throws `IllegalArgumentException` with "Currency mismatch". |
| `shouldSubtractMoneyOfSameCurrency` | Subtracting a smaller TL amount from a larger one returns the correct difference. |
| `shouldRejectSubtractingMoreThanAvailable` | Subtracting an amount larger than the minuend throws `IllegalArgumentException` with "Insufficient". |
| `shouldReturnZeroMoneyForCurrency` | `Money.zero(EUR)` returns a `Money` with amount `0` and currency EUR. |

---

### `AccountTest` — 7 tests

**Class under test:** `domain/model/Account.java` (entity)

`Account` is a rich entity. Its methods — `deposit`, `withdraw`, `transferOut`, `transferIn` — enforce currency matching and balance invariants, and each returns a `Transaction` object.

| Test | What it verifies |
|------|-----------------|
| `shouldOpenAccountWithZeroBalance` | `Account.open(...)` starts with a zero balance in the correct currency. |
| `shouldDepositAndIncreaseBalance` | `deposit(500 USD)` raises the balance to 500 and returns a `DEPOSIT` transaction with the correct amount. |
| `shouldWithdrawAndDecreaseBalance` | After depositing 500 then withdrawing 200, balance is 300 and a `WITHDRAWAL` transaction is returned. |
| `shouldRejectWithdrawalExceedingBalance` | Withdrawing more than the current balance throws `IllegalArgumentException` with "Insufficient". |
| `shouldRejectDepositWithWrongCurrency` | Depositing EUR into a USD account throws `IllegalArgumentException` mentioning "currency". |
| `shouldTransferOutWithFeeDeducted` | `transferOut(200 USD, fee=2 USD, ...)` deducts 202 from a 1000 USD balance, leaving 798. |
| `shouldTransferInAndIncreaseBalance` | `transferIn(300 USD, ...)` credits the account, raising balance from 0 to 300. |

---

### `CustomerTest` — 3 tests

**Class under test:** `domain/model/Customer.java` (entity)

`Customer` tracks the current password and up to 3 previous password hashes. The password history is managed entirely inside the entity's `changePassword` method.

| Test | What it verifies |
|------|-----------------|
| `shouldChangePasswordAndMoveCurrentToHistory` | After one `changePassword`, the new hash is current and the old hash is in `passwordHistory[0]`. |
| `shouldKeepAtMostThreePasswordsInHistory` | After 5 password changes starting from "hash-0", only hashes 1–3 remain in history (hash-0 is evicted). Current is hash-4. |
| `getAllPasswordsForReuseCheckShouldIncludeCurrentAndHistory` | After two changes, `getAllPasswordsForReuseCheck()` returns 3 entries (current + 2 history) with the most recent first. |

---

### `PasswordValidationServiceTest` — 8 tests

**Class under test:** `domain/service/PasswordValidationService.java` (domain service)

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

### `CustomerApplicationServiceTest` — 6 tests

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

---

### `AccountApplicationServiceTest` — 7 tests

**Class under test:** `application/service/AccountApplicationService.java`

**Mocked ports:** `AccountRepositoryPort`, `CustomerRepositoryPort`, `TransactionRepositoryPort`, `SettingsRepositoryPort`
**Real domain objects:** `Account`, `Transaction`, `TransferDomainService`

| Test | What it verifies |
|------|-----------------|
| `shouldCreateAccountForExistingCustomer` | `createAccount` checks that the owner exists, opens an account with zero balance, saves it, and returns it with the correct currency and owner. |
| `shouldThrowCustomerNotFoundWhenOwnerMissing` | `createAccount` throws `CustomerNotFoundException` when `existsById` returns false. |
| `shouldDepositMoneyToAccount` | `deposit` loads the account, calls `account.deposit(...)`, saves the updated account, saves the transaction, and returns the `DEPOSIT` transaction. Balance on the in-memory account is 200 after the call. |
| `shouldThrowAccountNotFoundOnDepositToMissingAccount` | `deposit` throws `AccountNotFoundException` when `findById` returns empty. |
| `shouldTransferBetweenAccountsOfSameCustomerFreeOfCharge` | Transfer between two accounts owned by the same customer applies 0% fee regardless of the configured rate. Source ends at 300, target at 200. |
| `shouldDeductFeeForTransferBetweenDifferentCustomers` | Transfer between accounts of different customers with 1% fee deducts 202 from source (200 + 2 fee) and credits 200 to target. |
| `shouldThrowOnWithdrawExceedingBalance` | `withdraw` propagates the `IllegalArgumentException("Insufficient funds")` thrown by the domain entity when the amount exceeds the balance. |

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

### `AdminControllerTest` — 12 tests

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

#### `PUT /api/admin/settings/transfer-fee`

| Test | What it verifies |
|------|-----------------|
| `setTransferFee_returnsOk` | Valid `feePercent` → 200; verifies use case received the exact `Command(1.5)`. |
| `setTransferFee_returnsBadRequestForNegativeValue` | `feePercent: -1.0` → 400; use case never called. |
| `setTransferFee_returnsBadRequestForValueAbove100` | `feePercent: 101.0` → 400 (`@DecimalMax("100.0")` constraint). |

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

### `AccountControllerTest` — 17 tests

**Controller:** `adapter/in/web/AccountController.java` (`/api/accounts/**`, `/api/customers/**`)
**Required role:** `ROLE_CUSTOMER`

#### `POST /api/accounts`

| Test | What it verifies |
|------|-----------------|
| `createAccount_returnsCreated` | Valid body with `ownerId` param → 201, JSON has `currency: "USD"` and `balance: 0`. |
| `createAccount_returnsBadRequestOnMissingCurrency` | Empty body `{}` → 400 (`@NotNull` on currency); use case never called. |
| `createAccount_returnsForbiddenForAdminRole` | `ROLE_ADMIN` → 403. |

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
| `InsufficientFundsException` | 422 Unprocessable Entity | `AccountControllerTest` |
| `InvalidPasswordException` | 400 Bad Request | `CustomerControllerTest` |
| `PasswordReusedException` | 409 Conflict | `CustomerControllerTest` |
| `UnauthorizedAccessException` | 403 Forbidden | (role check via Spring Security) |
| `IllegalArgumentException` | 400 Bad Request | domain entity invariant violations |
| `MethodArgumentNotValidException` | 400 Bad Request | Jakarta Bean Validation failures |
