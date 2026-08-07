# Ownership Authorization Implementation Plan

Claude Opus 5 (1M context) — created 2026-08-07

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop any authenticated customer from operating on another customer's accounts or changing another customer's password.

**Architecture:** The domain owns the *fact* (`Account.isOwnedBy`); the application services own the *policy* and throw `UnauthorizedAccessException`, already mapped to HTTP 403. The caller's `CustomerId` travels in the Spring Security principal, populated at login where the `Customer` is already loaded, and enters the domain through the customer-facing `Command` records.

**Tech Stack:** Java 25, Spring Boot 3.4, Spring Security, JUnit 5, AssertJ, Mockito, Maven.

## Global Constraints

- Root package `dev.kaldiroglu`.
- The domain layer must have **zero** Spring or JPA imports. `Account.isOwnedBy` takes a `CustomerId` and returns `boolean` — no security types.
- All code and comments in English. American spelling.
- Every `.md` file created must carry the model name, version, and creation date at the top.
- Baseline: **186 tests, 0 failures**.
- Design reference: `docs/superpowers/specs/2026-08-07-ownership-authorization-design.md`.
- `mvn clean verify` must pass before the work is reported complete.

## Correction to the spec's test plan — read before writing any test

The spec listed controller tests named `deposit_returnsForbiddenWhenAccountNotOwned`. **Those tests
cannot work as named, and writing them would repeat the exact defect documented in
`Refactorings.md` entry 1.**

`AccountControllerTest` is a `@WebMvcTest` with `CustomerAccountPort` mocked. The ownership rule
lives in `AccountApplicationService` — which is *the mocked object*. A controller test asserting 403
would only prove that a mock configured to throw does throw.

The responsibilities split cleanly, and each is tested where it lives:

| Layer | Its actual job | How it is tested |
|---|---|---|
| Controller | Put the authenticated caller's id into the command | `ArgumentCaptor` on the mocked port — assert `command.callerId()` |
| Controller | Map `UnauthorizedAccessException` to 403 | Stub the port to throw; assert the status |
| Application service | Decide whether the caller may proceed | Real service, mocked repositories, assert the exception |
| Domain | Answer "is this account owned by X?" | Plain JUnit on `Account` |

Task 5 follows this split. Task 6 amends the spec to match.

---

## File Structure

**New (main):**

| Path | Responsibility |
|---|---|
| `config/BankUserPrincipal.java` | `UserDetails` carrying the authenticated `CustomerId` |

**New (test):**

| Path | Responsibility |
|---|---|
| `adapter/in/web/WithBankUser.java` | Test annotation supplying a `BankUserPrincipal` |
| `adapter/in/web/WithBankUserSecurityContextFactory.java` | Builds the `SecurityContext` for it |

**Modified (main):** `config/BankUserDetailsService.java`, `domain/model/account/Account.java`, `application/port/in/account/CustomerAccountPort.java`, `application/port/in/customer/CustomerSelfServicePort.java`, `application/service/AccountApplicationService.java`, `application/service/CustomerApplicationService.java`, `adapter/in/web/AccountController.java`, `adapter/in/web/CustomerController.java`

**Modified (test):** `AccountControllerTest`, `CustomerControllerTest`, `AccountApplicationServiceTest`, `CustomerApplicationServiceTest`, `AccountTest`

**Untouched:** all persistence, `SecurityConfig` route rules, `GlobalExceptionHandler`, the three admin ports, `AdminController`, `AdminControllerTest`.

**`AdminControllerTest` keeps its `@WithMockUser`.** Its four `@WithMockUser(roles = "CUSTOMER")` tests — `createCustomer_returnsForbiddenForCustomerRole`, `changeCustomerTier_returnsForbiddenForCustomerRole`, `closeAccount_returnsForbiddenForCustomerRole`, `accrueInterest_returnsForbiddenForCustomerRole` — assert that Spring Security rejects a CUSTOMER before the controller runs. They need no principal and must not be converted.

---

### Task 1: `Account.isOwnedBy`

The domain fact, in isolation. Nothing consumes it yet.

**Files:**
- Modify: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/Account.java`
- Test: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/AccountTest.java`

**Interfaces:**
- Consumes: `CustomerId`, the existing `Account.ownerId` field (`Account.java:39`)
- Produces: `public final boolean isOwnedBy(CustomerId customerId)` on `Account`

- [ ] **Step 1: Write the failing tests**

Append to `AccountTest`:

```java
    @Test
    void shouldReportOwnershipForTheOwningCustomer() {
        CustomerId owner = CustomerId.generate();
        Account account = CheckingAccount.open(owner, Currency.USD);

        assertThat(account.isOwnedBy(owner)).isTrue();
    }

    @Test
    void shouldDenyOwnershipForAnotherCustomer() {
        Account account = CheckingAccount.open(CustomerId.generate(), Currency.USD);

        assertThat(account.isOwnedBy(CustomerId.generate())).isFalse();
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -o test -Dtest=AccountTest`
Expected: FAIL — `cannot find symbol: method isOwnedBy`

- [ ] **Step 3: Add the method**

In `Account.java`, beside the other accessors near line 136:

```java
    /**
     * Whether this account belongs to the given customer.
     *
     * <p>The domain owns this <i>fact</i>; it does not own the <i>policy</i> built on top of it.
     * Deciding that a caller may therefore not proceed is an application concern — a "caller" is a
     * session notion the domain has no business knowing about. See {@code Refactorings.md} entry 3.
     */
    public final boolean isOwnedBy(CustomerId customerId) {
        return this.ownerId.equals(customerId);
    }
```

- [ ] **Step 4: Run to verify they pass**

Run: `mvn -o clean test`
Expected: 188 tests, 0 failures

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/Account.java \
        src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/AccountTest.java
git commit -m "Add Account.isOwnedBy

The domain owns the ownership fact. The policy built on it belongs to the
application layer, which is where a 'caller' exists."
```

---

### Task 2: `BankUserPrincipal`

Carries the `CustomerId` through authentication. Nothing reads it yet.

**Files:**
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/config/BankUserPrincipal.java`
- Modify: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/config/BankUserDetailsService.java`

**Interfaces:**
- Consumes: `CustomerId`, `CustomerRepositoryPort.findByEmail`
- Produces: `BankUserPrincipal extends org.springframework.security.core.userdetails.User` with `public CustomerId customerId()`

- [ ] **Step 1: Create the principal**

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.config;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * The authenticated principal, carrying the caller's {@link CustomerId}.
 *
 * <p>Spring Security identifies users by username — here, the email address. Authorization rules
 * need the {@code CustomerId}, and resolving email to id on every request would mean a database
 * query per call to recover something login already knew: {@link BankUserDetailsService} loads the
 * whole {@code Customer} to check the password hash. Carrying the id on the principal costs nothing
 * and saves that query.
 */
public class BankUserPrincipal extends User {

    private final transient CustomerId customerId;

    public BankUserPrincipal(CustomerId customerId, String email, String passwordHash,
                             Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, authorities);
        this.customerId = customerId;
    }

    public CustomerId customerId() {
        return customerId;
    }
}
```

- [ ] **Step 2: Build it in `BankUserDetailsService`**

Replace the `User.builder()` chain (lines 24-30) with:

```java
        return customerRepository.findByEmail(email)
                .map(customer -> (UserDetails) new BankUserPrincipal(
                        customer.getId(),
                        customer.getEmail(),
                        customer.getCurrentPassword().hashedValue(),
                        List.of(new SimpleGrantedAuthority("ROLE_" + customer.getRole()))))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
```

Remove the now-unused `import org.springframework.security.core.userdetails.User;` if the compiler reports it unused; keep the `UserDetails` import.

- [ ] **Step 3: Verify nothing broke**

Run: `mvn -o clean test`
Expected: 188 tests, 0 failures. Authentication behavior is unchanged — the principal is a `User` subclass with the same username, password and authorities.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/config/
git commit -m "Carry the CustomerId on the authenticated principal

BankUserDetailsService already loads the whole Customer to check the
password hash, then discarded everything but email and role. Keeping the
id avoids an email-to-id lookup on every authorized request."
```

---

### Task 3: The `@WithBankUser` test fixture

Test-only. Converts the 22 customer-role annotations so Task 5 has a principal to read.

**Files:**
- Create: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/WithBankUser.java`
- Create: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/WithBankUserSecurityContextFactory.java`
- Modify: `AccountControllerTest` (17 annotations), `CustomerControllerTest` (5 annotations)

**Interfaces:**
- Consumes: `BankUserPrincipal` from Task 2
- Produces: `@WithBankUser(customerId = "<uuid>")`, defaulting `role` to `"CUSTOMER"`

- [ ] **Step 1: Create the annotation**

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Authenticates the test as a real {@code BankUserPrincipal} carrying a {@code CustomerId}.
 *
 * <p>{@code @WithMockUser} builds a plain Spring {@code User}, so a controller parameter declared
 * {@code @AuthenticationPrincipal BankUserPrincipal} would resolve to {@code null}. Any endpoint
 * that reads the caller's identity therefore needs this annotation instead.
 *
 * <p>{@code customerId} must be a compile-time constant, so tests declare a {@code static final
 * String} and reuse it — see {@code AccountControllerTest.CALLER_ID}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithBankUserSecurityContextFactory.class)
public @interface WithBankUser {

    String customerId();

    String email() default "customer@ayvalikbank.dev";

    String role() default "CUSTOMER";
}
```

- [ ] **Step 2: Create the factory**

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.config.BankUserPrincipal;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;

public class WithBankUserSecurityContextFactory implements WithSecurityContextFactory<WithBankUser> {

    @Override
    public SecurityContext createSecurityContext(WithBankUser annotation) {
        BankUserPrincipal principal = new BankUserPrincipal(
                CustomerId.of(annotation.customerId()),
                annotation.email(),
                "test-password-hash",
                List.of(new SimpleGrantedAuthority("ROLE_" + annotation.role())));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, principal.getPassword(), principal.getAuthorities()));
        return context;
    }
}
```

- [ ] **Step 3: Add caller constants to the two test classes**

At the top of `AccountControllerTest`'s class body:

```java
    static final String CALLER_ID = "11111111-1111-1111-1111-111111111111";
    static final CustomerId CALLER = CustomerId.of(CALLER_ID);
    static final CustomerId OTHER_CUSTOMER = CustomerId.of("22222222-2222-2222-2222-222222222222");
```

And in `CustomerControllerTest`:

```java
    static final String CALLER_ID = "11111111-1111-1111-1111-111111111111";
    static final CustomerId CALLER = CustomerId.of(CALLER_ID);
    static final String OTHER_CUSTOMER_ID = "22222222-2222-2222-2222-222222222222";
```

Add `import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;` where missing.

- [ ] **Step 4: Convert the 22 annotations**

In `AccountControllerTest` and `CustomerControllerTest` only, replace every

```java
@WithMockUser(roles = "CUSTOMER")
```

with

```java
@WithBankUser(customerId = CALLER_ID)
```

Leave `@WithMockUser(roles = "ADMIN")` untouched everywhere, and leave **all four** of
`AdminControllerTest`'s `@WithMockUser(roles = "CUSTOMER")` annotations untouched — those assert
role separation on admin routes and need no principal.

Remove the `WithMockUser` import from the two converted classes only if no usage remains.

- [ ] **Step 5: Verify the suite is still green**

Run: `mvn -o clean test`
Expected: 188 tests, 0 failures. Controllers do not read the principal yet, so this must be a no-op.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/
git commit -m "Add @WithBankUser test fixture

@WithMockUser yields a plain Spring User, so @AuthenticationPrincipal
BankUserPrincipal would resolve to null. Converts the 22 customer-role
annotations; AdminControllerTest's four role-separation tests keep
@WithMockUser because admin routes take no principal."
```

---

### Task 4: Thread the caller through the ports, services and controllers

Atomic — changing the `Command` records breaks every caller in the same compile. Do not commit partway through. This task adds no rule yet; it only carries the identity.

**Files:**
- Modify: `application/port/in/account/CustomerAccountPort.java`, `application/port/in/customer/CustomerSelfServicePort.java`
- Modify: `application/service/AccountApplicationService.java`, `application/service/CustomerApplicationService.java`
- Modify: `adapter/in/web/AccountController.java`, `adapter/in/web/CustomerController.java`
- Modify: `AccountControllerTest`, `CustomerControllerTest`, `AccountApplicationServiceTest`, `CustomerApplicationServiceTest`

**Interfaces:**
- Consumes: `BankUserPrincipal.customerId()` from Task 2
- Produces: the command shapes below; Task 5 adds the enforcement inside the services

- [ ] **Step 1: Add `callerId` to `CustomerAccountPort`**

```java
    record OpenCheckingCommand(CustomerId callerId, Currency currency, Money overdraftLimit) {}

    record OpenSavingsCommand(CustomerId callerId, Currency currency, BigDecimal annualInterestRate) {}

    record OpenTimeDepositCommand(CustomerId callerId, Currency currency, Money principal,
                                  LocalDate maturityDate, BigDecimal annualInterestRate) {}

    record DepositCommand(CustomerId callerId, AccountId accountId, TransactionAmount amount) {}

    record WithdrawCommand(CustomerId callerId, AccountId accountId, TransactionAmount amount) {}

    record TransferCommand(CustomerId callerId, AccountId sourceAccountId,
                           AccountId targetAccountId, TransactionAmount amount) {}

    Money getBalance(CustomerId callerId, AccountId accountId);

    List<Account> listAccounts(CustomerId callerId, CustomerId ownerId);

    List<Transaction> getTransactions(CustomerId callerId, AccountId accountId);
```

Note the three `Open*Command` records **lose `ownerId`** — the caller is the owner. That is the API
change signed off in the spec.

- [ ] **Step 2: Add `callerId` to `CustomerSelfServicePort`**

```java
    record ChangePasswordCommand(CustomerId callerId, CustomerId customerId, String rawNewPassword) {}
```

- [ ] **Step 3: Update `AccountApplicationService` signatures**

The three `open*` methods now use `command.callerId()` where they used `command.ownerId()`:

```java
    @Override
    public CheckingAccount openChecking(OpenCheckingCommand command) {
        requireCustomerExists(command.callerId());
        CheckingAccount account = CheckingAccount.open(command.callerId(), command.currency(), command.overdraftLimit());
        return (CheckingAccount) accountRepository.save(account);
    }
```

Apply the same substitution in `openSavings` and `openTimeDeposit`. Change the three query methods to
accept the extra first parameter:

```java
    @Override
    @Transactional(readOnly = true)
    public Money getBalance(CustomerId callerId, AccountId accountId) {
        return findAccountOrThrow(accountId).getBalance();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getTransactions(CustomerId callerId, AccountId accountId) {
        findAccountOrThrow(accountId);
        return transactionRepository.findByAccountId(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> listAccounts(CustomerId callerId, CustomerId ownerId) {
        requireCustomerExists(ownerId);
        return accountRepository.findByOwnerId(ownerId);
    }
```

`callerId` is deliberately unused in this task. Task 5 uses it.

- [ ] **Step 4: Update `CustomerApplicationService.changePassword`**

No body change beyond keeping `command.customerId()` as the subject — `callerId` goes unused until
Task 5.

- [ ] **Step 5: Update `AccountController`**

Delete `@RequestParam String ownerId` from the three open endpoints and take the principal instead:

```java
    @PostMapping("/accounts/checking")
    public ResponseEntity<AccountResponse> openCheckingAccount(
            @AuthenticationPrincipal BankUserPrincipal caller,
            @Valid @RequestBody OpenCheckingAccountRequest request) {
        Money overdraft = request.overdraftLimit() == null
                ? Money.zero(request.currency())
                : Money.of(request.overdraftLimit(), request.currency());
        var account = customerAccount.openChecking(new CustomerAccountPort.OpenCheckingCommand(
                caller.customerId(), request.currency(), overdraft));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }
```

Every other customer endpoint gains `@AuthenticationPrincipal BankUserPrincipal caller` as its first
parameter and passes `caller.customerId()` first into the command. For example:

```java
    @PostMapping("/accounts/{accountId}/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal BankUserPrincipal caller,
            @PathVariable String accountId,
            @Valid @RequestBody MoneyOperationRequest request) {
        var tx = customerAccount.deposit(new CustomerAccountPort.DepositCommand(
                caller.customerId(), AccountId.of(accountId),
                TransactionAmount.of(request.amount(), request.currency())));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(tx));
    }
```

and for the queries:

```java
    @GetMapping("/customers/{customerId}/accounts")
    public ResponseEntity<List<AccountResponse>> listAccounts(
            @AuthenticationPrincipal BankUserPrincipal caller,
            @PathVariable String customerId) {
        var accounts = customerAccount.listAccounts(caller.customerId(), CustomerId.of(customerId)).stream()
                .map(AccountResponse::from).toList();
        return ResponseEntity.ok(accounts);
    }
```

Add imports for `BankUserPrincipal` and `org.springframework.security.core.annotation.AuthenticationPrincipal`.

- [ ] **Step 6: Update `CustomerController`**

```java
    @PutMapping("/{customerId}/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal BankUserPrincipal caller,
            @PathVariable String customerId,
            @Valid @RequestBody ChangePasswordRequest request) {
        customerSelfService.changePassword(new CustomerSelfServicePort.ChangePasswordCommand(
                caller.customerId(), CustomerId.of(customerId), request.newPassword()));
        return ResponseEntity.ok().build();
    }
```

- [ ] **Step 7: Fix the tests the compiler flags**

Run `mvn -o clean test-compile` and work through the errors:

- `AccountControllerTest`: the three open-account requests drop `?ownerId=`; every command
  constructor gains `CALLER` first.
- `AccountApplicationServiceTest`: `openChecking`/`openSavings`/`openTimeDeposit` commands take
  `callerId` in place of `ownerId`; `getBalance`, `getTransactions` and `listAccounts` take an extra
  first argument.
- `CustomerApplicationServiceTest`: `ChangePasswordCommand` gains a first argument. Where the test
  previously used one id, pass the **same** id twice so the caller is the subject.

Where a test used `CustomerId.generate()` for the owner and then asserted on it, pass that same
generated id as the caller — these tests are not yet exercising denial.

- [ ] **Step 8: Run the suite**

Run: `mvn -o clean test`
Expected: 188 tests, 0 failures. No rule is enforced yet, so nothing may change behaviorally.

- [ ] **Step 9: Commit**

```bash
git add -A src/
git commit -m "Carry the caller's CustomerId into the customer-facing commands

No rule enforced yet - this only makes the caller expressible. The three
open-account endpoints lose their ?ownerId= parameter: the caller is the
owner, so opening an account for someone else can no longer be stated."
```

---

### Task 5: Enforce the rules

**Files:**
- Modify: `application/service/AccountApplicationService.java`, `application/service/CustomerApplicationService.java`
- Test: `AccountApplicationServiceTest`, `CustomerApplicationServiceTest`, `AccountControllerTest`, `CustomerControllerTest`

**Interfaces:**
- Consumes: `Account.isOwnedBy` (Task 1), the `callerId` fields (Task 4)
- Produces: `UnauthorizedAccessException` thrown from both application services

- [ ] **Step 1: Write the failing application-service tests**

Append to `AccountApplicationServiceTest`. These use the **real** service with mocked repositories,
so the rule is genuinely exercised:

```java
    // ── ownership ─────────────────────────────────────────────────────────

    @Test
    void shouldRejectDepositIntoAnotherCustomersAccount() {
        CustomerId owner = CustomerId.generate();
        CustomerId intruder = CustomerId.generate();
        Account account = CheckingAccount.open(owner, Currency.USD);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.deposit(new CustomerAccountPort.DepositCommand(
                intruder, account.getId(), TransactionAmount.of(100.0, Currency.USD))))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldRejectWithdrawalFromAnotherCustomersAccount() {
        CustomerId owner = CustomerId.generate();
        CustomerId intruder = CustomerId.generate();
        Account account = CheckingAccount.open(owner, Currency.USD);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.withdraw(new CustomerAccountPort.WithdrawCommand(
                intruder, account.getId(), TransactionAmount.of(10.0, Currency.USD))))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldRejectTransferFromAnotherCustomersAccount() {
        CustomerId owner = CustomerId.generate();
        CustomerId intruder = CustomerId.generate();
        Account source = CheckingAccount.open(owner, Currency.USD);
        Account target = CheckingAccount.open(intruder, Currency.USD);
        when(accountRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.transfer(new CustomerAccountPort.TransferCommand(
                intruder, source.getId(), target.getId(), TransactionAmount.of(10.0, Currency.USD))))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("the transfer target is deliberately not ownership-checked — you may send money to other people")
    void shouldAllowTransferToAnotherCustomersAccount() {
        CustomerId sender = CustomerId.generate();
        CustomerId recipient = CustomerId.generate();
        Account source = CheckingAccount.open(sender, Currency.USD);
        source.deposit(TransactionAmount.of(500.0, Currency.USD));
        Account target = CheckingAccount.open(recipient, Currency.USD);
        when(accountRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(customerRepository.findById(sender)).thenReturn(Optional.of(stubCustomer(sender, CustomerTier.STANDARD)));
        when(settingsRepository.getTransferFeePercent()).thenReturn(new BigDecimal("1.0"));

        service.transfer(new CustomerAccountPort.TransferCommand(
                sender, source.getId(), target.getId(), TransactionAmount.of(100.0, Currency.USD)));

        assertThat(target.getBalance().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRejectBalanceReadOnAnotherCustomersAccount() {
        Account account = CheckingAccount.open(CustomerId.generate(), Currency.USD);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.getBalance(CustomerId.generate(), account.getId()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void shouldRejectTransactionHistoryOnAnotherCustomersAccount() {
        Account account = CheckingAccount.open(CustomerId.generate(), Currency.USD);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.getTransactions(CustomerId.generate(), account.getId()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void shouldRejectListingAnotherCustomersAccounts() {
        assertThatThrownBy(() -> service.listAccounts(CustomerId.generate(), CustomerId.generate()))
                .isInstanceOf(UnauthorizedAccessException.class);

        verifyNoInteractions(accountRepository);
    }
```

Add the import `dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.UnauthorizedAccessException;`
and `org.junit.jupiter.api.DisplayName`.

Append to `CustomerApplicationServiceTest`:

```java
    @Test
    void shouldRejectChangingAnotherCustomersPassword() {
        CustomerId caller = CustomerId.generate();
        CustomerId victim = CustomerId.generate();

        assertThatThrownBy(() -> service.changePassword(
                new CustomerSelfServicePort.ChangePasswordCommand(caller, victim, "NewPass@123!")))
                .isInstanceOf(UnauthorizedAccessException.class);

        verifyNoInteractions(customerRepository);
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -o test -Dtest=AccountApplicationServiceTest+CustomerApplicationServiceTest`
Expected: 8 failures — no `UnauthorizedAccessException` is thrown by anything yet.

- [ ] **Step 3: Add the guard to `AccountApplicationService`**

Add the helper beside the other private helpers:

```java
    private void requireOwner(Account account, CustomerId callerId) {
        if (!account.isOwnedBy(callerId))
            throw new UnauthorizedAccessException("Account does not belong to the caller");
    }

    private void requireSelf(CustomerId subject, CustomerId callerId) {
        if (!subject.equals(callerId))
            throw new UnauthorizedAccessException("Callers may only act on their own customer record");
    }
```

Then call it. In `deposit`, `withdraw`, `getBalance` and `getTransactions`, immediately after the
account is loaded:

```java
        Account account = findAccountOrThrow(command.accountId());
        requireOwner(account, command.callerId());
```

In `transfer`, **source only**:

```java
        Account source = findAccountOrThrow(command.sourceAccountId());
        Account target = findAccountOrThrow(command.targetAccountId());
        requireOwner(source, command.callerId());
        // The target is deliberately NOT checked: sending money to another customer is the point.
```

In `listAccounts`, before touching the repository:

```java
    public List<Account> listAccounts(CustomerId callerId, CustomerId ownerId) {
        requireSelf(ownerId, callerId);
        requireCustomerExists(ownerId);
        return accountRepository.findByOwnerId(ownerId);
    }
```

Add the `UnauthorizedAccessException` import.

- [ ] **Step 4: Add the guard to `CustomerApplicationService`**

```java
    @Override
    public void changePassword(ChangePasswordCommand command) {
        if (!command.customerId().equals(command.callerId()))
            throw new UnauthorizedAccessException("Callers may only change their own password");

        Customer customer = customerRepository.findById(command.customerId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + command.customerId()));
        ...
```

The check must come **before** the repository lookup, so a caller cannot probe which customer ids
exist by observing 404 versus 403.

- [ ] **Step 5: Run to verify they pass**

Run: `mvn -o clean test`
Expected: 196 tests, 0 failures

- [ ] **Step 6: Add the controller-level tests**

These test the controller's own two jobs, not the rule. Append to `AccountControllerTest`:

```java
    @Test
    @WithBankUser(customerId = CALLER_ID)
    void deposit_passesTheAuthenticatedCallerIntoTheCommand() throws Exception {
        Transaction tx = Transaction.create(AccountId.generate(), TransactionType.DEPOSIT,
                Money.of(50.0, Currency.USD), "Deposit");
        when(customerAccount.deposit(any())).thenReturn(tx);

        mockMvc.perform(post("/api/accounts/{id}/deposit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50.00,"currency":"USD"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CustomerAccountPort.DepositCommand> captor =
                ArgumentCaptor.forClass(CustomerAccountPort.DepositCommand.class);
        verify(customerAccount).deposit(captor.capture());
        assertThat(captor.getValue().callerId()).isEqualTo(CALLER);
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void deposit_returnsForbiddenWhenTheServiceDeniesAccess() throws Exception {
        doThrow(new UnauthorizedAccessException("Account does not belong to the caller"))
                .when(customerAccount).deposit(any());

        mockMvc.perform(post("/api/accounts/{id}/deposit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50.00,"currency":"USD"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithBankUser(customerId = CALLER_ID)
    void openChecking_opensTheAccountForTheAuthenticatedCaller() throws Exception {
        when(customerAccount.openChecking(any()))
                .thenReturn(CheckingAccount.open(CALLER, Currency.USD));

        mockMvc.perform(post("/api/accounts/checking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"USD"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CustomerAccountPort.OpenCheckingCommand> captor =
                ArgumentCaptor.forClass(CustomerAccountPort.OpenCheckingCommand.class);
        verify(customerAccount).openChecking(captor.capture());
        assertThat(captor.getValue().callerId()).isEqualTo(CALLER);
    }
```

Append to `CustomerControllerTest`:

```java
    @Test
    @WithBankUser(customerId = CALLER_ID)
    void changePassword_passesBothTheCallerAndTheSubjectIntoTheCommand() throws Exception {
        mockMvc.perform(put("/api/customers/{id}/password", OTHER_CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"NewPass@123!"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<CustomerSelfServicePort.ChangePasswordCommand> captor =
                ArgumentCaptor.forClass(CustomerSelfServicePort.ChangePasswordCommand.class);
        verify(customerSelfService).changePassword(captor.capture());
        assertThat(captor.getValue().callerId()).isEqualTo(CALLER);
        assertThat(captor.getValue().customerId()).isEqualTo(CustomerId.of(OTHER_CUSTOMER_ID));
    }
```

That last test asserts the controller *forwards* both ids faithfully — the refusal itself is the
service's job and is tested in `CustomerApplicationServiceTest`. Add imports for
`org.mockito.ArgumentCaptor` and `UnauthorizedAccessException` where needed.

- [ ] **Step 7: Run the full suite**

Run: `mvn -o clean test`
Expected: 200 tests, 0 failures

- [ ] **Step 8: Prove `UnauthorizedAccessException` is now live**

```bash
grep -rn "throw new UnauthorizedAccessException" src/main/java
```

Expected: matches in both application services. Before this task there were none.

- [ ] **Step 9: Commit**

```bash
git add -A src/
git commit -m "Enforce account ownership and self-service on customer operations

Any authenticated customer could previously operate on any account and set
any other customer's password. UnauthorizedAccessException was mapped to 403
but never thrown by production code; it now is.

The transfer target is deliberately not checked - sending money to another
customer is the point - and a test pins that asymmetry."
```

---

### Task 6: Documentation

**Files:**
- Modify: `Refactorings.md`, `CLAUDE.md`, `API.txt`, `Tests.md` (and `README.md` only if it turns out to reference the changed endpoints)
- Modify: `docs/superpowers/specs/2026-08-07-ownership-authorization-design.md`

- [ ] **Step 1: Add `Refactorings.md` entry 3**

Following the structure of entries 1 and 2, recording the baseline commit. Cover:

1. **The three holes**, with evidence: the password path parameter, the missing account ownership
   check, and the `?ownerId=` parameter.
2. **The root cause** — no `Command` carried a caller, so the rule was *inexpressible*. State the
   symmetry with entry 1 explicitly: there, types made a bad state impossible; here, types made the
   correct rule impossible to say, so it went unsaid.
3. **`UnauthorizedAccessException` was mapped to 403 and never thrown** — the third dead-code
   instance in this codebase, after the dead handler and the untested guard. Draw the conclusion:
   an exception no code throws is a rule no code enforces, and it is worth grepping for.
4. **Fact versus policy** — why `isOwnedBy` is domain and the refusal is application.
5. **The three enforcement shapes** and why they differ, with the elimination of `?ownerId=` framed
   as the entry-1 move applied to security.
6. **The transfer asymmetry**, and the test that pins it.
7. **403 versus 404**, stated as an accepted trade-off with its reasoning.
8. **The test-fixture cost** — `@WithMockUser` yields a null principal, so 22 annotations had to
   change. A security fix whose weight lands in test infrastructure.
9. **Where each rule is tested and why** — the controller/service split from this plan's opening
   correction, as a worked example of testing each layer against its neighbor's real output.

- [ ] **Step 2: Update `CLAUDE.md`**

Add to Key Design Decisions:

```markdown
- **Ownership authorization**: every customer-facing `Command` carries `CustomerId callerId`, supplied by `BankUserPrincipal` via `@AuthenticationPrincipal`. `Account.isOwnedBy` is the domain fact; the application services enforce the policy and throw `UnauthorizedAccessException` → HTTP 403. Transfers check the **source only** — the target is deliberately unchecked. Opening an account takes no `ownerId`: the caller is the owner. See `Refactorings.md` entry 3.
```

Update the REST API table: remove `?ownerId=` from the three open-account rows.

- [ ] **Step 3: Update `API.txt`**

`API.txt:9` and `:12` document the open-account call with `?ownerId={customerId}`, including a
runnable `curl`. Correct both to the parameterless form and note that the account is opened for the
authenticated caller. Verify `README.md` separately — it does **not** currently mention `ownerId`,
so it may need no change; read it rather than assuming.

- [ ] **Step 4: Update `Tests.md`**

Change the header count to the actual figure from Task 5 Step 7, add the new tests to the relevant
class sections, and add a short subsection explaining the `@WithBankUser` fixture and why
`@WithMockUser` is insufficient.

- [ ] **Step 5: Amend the design spec**

Replace the spec's controller-test table with this plan's corrected split, so the spec does not
outlive the plan describing tests that cannot work.

- [ ] **Step 6: Verify every documented claim**

```bash
grep -rn "ownerId" src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AccountController.java
grep -rn "throw new UnauthorizedAccessException" src/main/java
grep -rn "@WithMockUser(roles = \"CUSTOMER\")" src/test/java
```

Expected in order: no `@RequestParam` ownerId; matches in both services; only the four
`AdminControllerTest` role-separation tests.

- [ ] **Step 7: Final verification**

Run: `mvn clean verify`
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add Refactorings.md CLAUDE.md API.txt Tests.md docs/superpowers/
git commit -m "Document the ownership authorization work

Adds Refactorings.md entry 3 and syncs CLAUDE.md, README.md, API.txt and
Tests.md, including the removal of ?ownerId= from the open-account endpoints."
```

---

## Self-Review

**Spec coverage.** Every spec section maps to a task: caller identity → Task 2; the test fixture →
Task 3; port changes → Task 4; the three enforcement shapes → Task 4 Step 1 (elimination) and Task 5
Steps 3–4 (self and ownership); transfer asymmetry → Task 5 Steps 3 and 1; the 403/404 trade-off →
Task 6 Step 1; documentation → Task 6. The spec's out-of-scope note on current-password verification
is carried forward untouched.

**Deviation from the spec, deliberate and flagged.** The spec's controller-test table named tests
that a `@WebMvcTest` cannot honestly implement, because the object holding the rule is the mocked
one. This plan replaces them with `ArgumentCaptor` tests at the controller and real-service tests at
the application layer, and Task 6 Step 5 amends the spec.

**Placeholder scan.** No TBDs. Every test and guard is given in full. The only judgment-based steps
are Task 4 Step 7 (compiler-guided test fixes) and Task 6 Steps 1–4, each of which names the exact
files and the rule to apply.

**Type consistency.** `BankUserPrincipal.customerId()` is defined in Task 2 and used in Tasks 3, 4
and 5. `Account.isOwnedBy` is defined in Task 1 and used in Task 5 Step 3. `CALLER_ID` / `CALLER` /
`OTHER_CUSTOMER_ID` are introduced in Task 3 Step 3 and used in Tasks 4 and 5. The command shapes in
Task 4 Step 1 match every constructor call in Task 5.

**Test-count checkpoints.** 186 → 188 (Task 1) → 188 (Tasks 2, 3, 4, all no-ops behaviorally) → 196
(Task 5 Step 5) → 200 (Task 5 Step 7). A count that does not match at a checkpoint means something
was lost; stop and find it.
