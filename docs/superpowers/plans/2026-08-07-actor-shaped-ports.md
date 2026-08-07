# Actor-Shaped Driving Ports Implementation Plan

Claude Opus 5 (1M context) — created 2026-08-07

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace 20 single-method driving ports with 5 actor-shaped ports, and move them from `domain/port/in/` to `application/port/in/`.

**Architecture:** A port is one conversation with one kind of outside actor (Cockburn), not one method. This system has two driving actors — Customer and Admin — talking about three subjects, which yields five ports. Driving ports become an application concern; driven ports stay in the domain, because the domain declares the interfaces it requires. That asymmetry is deliberate.

**Tech Stack:** Java 25, Spring Boot 3.4, JUnit 5, AssertJ, Mockito, Maven.

## Global Constraints

- Root package `dev.kaldiroglu`. New interfaces go in `dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.{account,customer}`.
- The domain layer must have **zero** Spring or JPA imports. `domain/port/out/` is not touched.
- All code and comments in English. American spelling.
- Every `.md` file created must carry the model name, version, and creation date at the top.
- **Zero behavior change.** No endpoint, status code, exception or message changes.
- Baseline and gate: **184 tests, 0 failures**, before and after. No tests are added or removed.
- Design reference: `docs/superpowers/specs/2026-08-07-actor-shaped-ports-design.md`.

---

## File Structure

**New:** five interfaces under `application/port/in/`.

| Path | Actor × subject |
|---|---|
| `application/port/in/account/CustomerAccountPort.java` | customer × accounts |
| `application/port/in/account/AccountAdministrationPort.java` | admin × accounts |
| `application/port/in/account/BankSettingsPort.java` | admin × bank config |
| `application/port/in/customer/CustomerAdministrationPort.java` | admin × customers |
| `application/port/in/customer/CustomerSelfServicePort.java` | customer × self |

**Deleted:** all 20 files under `domain/port/in/`, and the `domain/port/in/` directory itself.

**Modified:** `AccountApplicationService`, `CustomerApplicationService`, `AccountController`, `AdminController`, `CustomerController`, `AccountControllerTest`, `AdminControllerTest`, `CustomerControllerTest`, `AccountApplicationServiceTest`, `CustomerApplicationServiceTest`.

**Untouched:** the whole domain model, `domain/port/out/`, `domain/service/`, every persistence and security adapter, `SecurityConfig`, `GlobalExceptionHandler`, all web DTOs.

---

### Task 1: Create the five ports

Nothing consumes them yet, so the tree stays green throughout.

**Files:**
- Create: the five paths in the table above

**Interfaces:**
- Consumes: existing domain types only — `Account`, `CheckingAccount`, `SavingsAccount`, `TimeDepositAccount`, `Transaction`, `Money`, `TransactionAmount`, `AccountId`, `Currency`, `Customer`, `CustomerId`, `CustomerTier`
- Produces: the five port interfaces and their nested `Command` records, exactly as written below. Task 2 depends on these names being precise.

- [ ] **Step 1: Create `CustomerAccountPort`**

`src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/port/in/account/CustomerAccountPort.java`:

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything a <b>customer</b> can do with their own accounts — one conversation with one actor.
 *
 * <p>This is a port in Cockburn's sense: it groups the operations that belong to a single kind of
 * outside party, rather than devoting an interface to each individual method. The admin-facing
 * counterpart is {@link AccountAdministrationPort}; keeping the two apart is what lets the
 * ownership rule be stated once, for this port, instead of method by method.
 */
public interface CustomerAccountPort {

    record OpenCheckingCommand(CustomerId ownerId, Currency currency, Money overdraftLimit) {}

    record OpenSavingsCommand(CustomerId ownerId, Currency currency, BigDecimal annualInterestRate) {}

    record OpenTimeDepositCommand(CustomerId ownerId, Currency currency, Money principal,
                                  LocalDate maturityDate, BigDecimal annualInterestRate) {}

    record DepositCommand(AccountId accountId, TransactionAmount amount) {}

    record WithdrawCommand(AccountId accountId, TransactionAmount amount) {}

    record TransferCommand(AccountId sourceAccountId, AccountId targetAccountId, TransactionAmount amount) {}

    CheckingAccount openChecking(OpenCheckingCommand command);

    SavingsAccount openSavings(OpenSavingsCommand command);

    TimeDepositAccount openTimeDeposit(OpenTimeDepositCommand command);

    Transaction deposit(DepositCommand command);

    Transaction withdraw(WithdrawCommand command);

    void transfer(TransferCommand command);

    Money getBalance(AccountId accountId);

    List<Account> listAccounts(CustomerId ownerId);

    List<Transaction> getTransactions(AccountId accountId);
}
```

- [ ] **Step 2: Create `AccountAdministrationPort`**

`src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/port/in/account/AccountAdministrationPort.java`:

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Transaction;

import java.time.YearMonth;

/**
 * Everything an <b>administrator</b> can do to an account they do not own.
 *
 * <p>Separate from {@link CustomerAccountPort} because it is a different actor having a different
 * conversation: freezing, closing, accruing and maturing are bank actions, not customer actions.
 * The split is enforced at the route level by {@code SecurityConfig}'s {@code hasRole("ADMIN")}.
 */
public interface AccountAdministrationPort {

    record AccrueInterestCommand(AccountId accountId, YearMonth month) {}

    record MatureCommand(AccountId accountId) {}

    void freezeAccount(AccountId accountId);

    void unfreezeAccount(AccountId accountId);

    void closeAccount(AccountId accountId);

    Transaction accrueInterest(AccrueInterestCommand command);

    Transaction mature(MatureCommand command);
}
```

- [ ] **Step 3: Create `BankSettingsPort`**

`src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/port/in/account/BankSettingsPort.java`:

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account;

import java.math.BigDecimal;

/**
 * Bank-wide configuration an <b>administrator</b> can change.
 *
 * <p>Its own port because the transfer fee is a property of the bank, not of any customer or any
 * account. It previously lived on {@code CustomerApplicationService} purely because an admin
 * invoked it — which is what "grouped by whoever happens to call it" looks like in practice.
 */
public interface BankSettingsPort {

    record SetTransferFeeCommand(BigDecimal feePercent) {}

    void setTransferFee(SetTransferFeeCommand command);
}
```

- [ ] **Step 4: Create `CustomerAdministrationPort`**

`src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/port/in/customer/CustomerAdministrationPort.java`:

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerTier;

import java.util.List;

/**
 * Everything an <b>administrator</b> can do to the customer roster.
 */
public interface CustomerAdministrationPort {

    record CreateCustomerCommand(String name, String email, String rawPassword) {}

    record ChangeCustomerTierCommand(CustomerId customerId, CustomerTier tier) {}

    Customer createCustomer(CreateCustomerCommand command);

    void deleteCustomer(CustomerId customerId);

    List<Customer> listCustomers();

    void changeCustomerTier(ChangeCustomerTierCommand command);
}
```

- [ ] **Step 5: Create `CustomerSelfServicePort`**

`src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/port/in/customer/CustomerSelfServicePort.java`:

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

/**
 * What a <b>customer</b> can do to their own record.
 *
 * <p>One method today. It stays a separate port rather than merging into
 * {@link CustomerAdministrationPort} because the actor is different: a customer changing their own
 * password is not an administrator editing the roster.
 */
public interface CustomerSelfServicePort {

    record ChangePasswordCommand(CustomerId customerId, String rawNewPassword) {}

    void changePassword(ChangePasswordCommand command);
}
```

- [ ] **Step 6: Verify the tree still compiles and passes**

Run: `mvn -o clean test`
Expected: 184 tests, 0 failures. The new interfaces are unreferenced; nothing else changed.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/port/
git commit -m "Add five actor-shaped driving ports

Grouped by actor x subject rather than one interface per method. Not yet
wired up - the old ports are still in use."
```

---

### Task 2: Migrate to the new ports and delete the old ones

Necessarily atomic: the services, controllers and their tests all reference the port types, so there is no smaller change that compiles. Do not commit partway through.

**Files:**
- Modify: `application/service/AccountApplicationService.java`, `application/service/CustomerApplicationService.java`
- Modify: `adapter/in/web/AccountController.java`, `AdminController.java`, `CustomerController.java`
- Modify: `AccountControllerTest`, `AdminControllerTest`, `CustomerControllerTest`, `AccountApplicationServiceTest`, `CustomerApplicationServiceTest`
- Delete: all 20 files under `domain/port/in/`

**Interfaces:**
- Consumes: the five ports from Task 1
- Produces: `AccountApplicationService implements CustomerAccountPort, AccountAdministrationPort, BankSettingsPort`; `CustomerApplicationService implements CustomerAdministrationPort, CustomerSelfServicePort`

- [ ] **Step 1: Rewrite `AccountApplicationService`'s declaration and imports**

Replace the 14-interface `implements` list with three ports:

```java
@Service
@Transactional
public class AccountApplicationService implements
        CustomerAccountPort,
        AccountAdministrationPort,
        BankSettingsPort {
```

Replace the two port imports:

```java
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.AccountAdministrationPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.BankSettingsPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.CustomerAccountPort;
```

and delete these two lines:

```java
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer.*;
```

- [ ] **Step 2: Update every `Command` type in `AccountApplicationService`**

Method bodies do not change — only the parameter types in the signatures:

| Old signature | New signature |
|---|---|
| `openChecking(OpenCheckingAccountUseCase.Command)` | `openChecking(OpenCheckingCommand)` |
| `openSavings(OpenSavingsAccountUseCase.Command)` | `openSavings(OpenSavingsCommand)` |
| `openTimeDeposit(OpenTimeDepositAccountUseCase.Command)` | `openTimeDeposit(OpenTimeDepositCommand)` |
| `deposit(DepositMoneyUseCase.Command)` | `deposit(DepositCommand)` |
| `withdraw(WithdrawMoneyUseCase.Command)` | `withdraw(WithdrawCommand)` |
| `transfer(TransferMoneyUseCase.Command)` | `transfer(TransferCommand)` |
| `accrueInterest(AccrueInterestUseCase.Command)` | `accrueInterest(AccrueInterestCommand)` |
| `mature(MatureTimeDepositUseCase.Command)` | `mature(MatureCommand)` |

`getBalance`, `listAccounts`, `getTransactions`, `freezeAccount`, `unfreezeAccount` and `closeAccount` take bare identifiers and are unchanged.

- [ ] **Step 3: Move `setTransferFee` into `AccountApplicationService`**

Cut this method from `CustomerApplicationService` (line ~88) and paste it into `AccountApplicationService`, retyping the command:

```java
    @Override
    public void setTransferFee(SetTransferFeeCommand command) {
        if (command.feePercent().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Transfer fee percent cannot be negative");
        settingsRepository.setTransferFeePercent(command.feePercent());
    }
```

The negative-fee guard is part of the original and must come across — do not simplify it away.
`AccountApplicationService` already holds the `settingsRepository` field and already imports
`java.math.BigDecimal`, so neither the constructor nor the imports need changing. (The original
wrote the guard as `java.math.BigDecimal.ZERO` fully qualified; the plain `BigDecimal.ZERO` above
is equivalent given the existing import.)

- [ ] **Step 4: Strip `SettingsRepositoryPort` out of `CustomerApplicationService`**

Its declaration becomes:

```java
@Service
@Transactional
public class CustomerApplicationService implements
        CustomerAdministrationPort,
        CustomerSelfServicePort {

    private final CustomerRepositoryPort customerRepository;
    private final PasswordHasherPort passwordHasher;
    private final PasswordValidationService passwordValidationService;

    public CustomerApplicationService(CustomerRepositoryPort customerRepository,
                                      PasswordHasherPort passwordHasher,
                                      PasswordValidationService passwordValidationService) {
        this.customerRepository = customerRepository;
        this.passwordHasher = passwordHasher;
        this.passwordValidationService = passwordValidationService;
    }
```

Remove the `SettingsRepositoryPort` import and the `settingsRepository` field. Retype the remaining commands: `CreateCustomerUseCase.Command` → `CreateCustomerCommand`, `ChangePasswordUseCase.Command` → `ChangePasswordCommand`, `ChangeCustomerTierUseCase.Command` → `ChangeCustomerTierCommand`.

- [ ] **Step 5: Collapse `AccountController`'s nine dependencies to one**

```java
@RestController
@RequestMapping("/api")
public class AccountController {

    private final CustomerAccountPort customerAccount;

    public AccountController(CustomerAccountPort customerAccount) {
        this.customerAccount = customerAccount;
    }
```

Every call site becomes `customerAccount.<method>(new CustomerAccountPort.<X>Command(...))`. For example:

```java
        var tx = customerAccount.deposit(new CustomerAccountPort.DepositCommand(
                AccountId.of(accountId), TransactionAmount.of(request.amount(), request.currency())));
```

Keep the `Money.zero` / `Money.of` calls in `openCheckingAccount` and `openTimeDepositAccount` exactly as they are — an overdraft limit and a principal are not transaction amounts.

- [ ] **Step 6: Collapse `AdminController`'s ten dependencies to three**

```java
    private final AccountAdministrationPort accountAdministration;
    private final CustomerAdministrationPort customerAdministration;
    private final BankSettingsPort bankSettings;

    public AdminController(AccountAdministrationPort accountAdministration,
                           CustomerAdministrationPort customerAdministration,
                           BankSettingsPort bankSettings) {
        this.accountAdministration = accountAdministration;
        this.customerAdministration = customerAdministration;
        this.bankSettings = bankSettings;
    }
```

Route each existing call to the right field: freeze/unfreeze/close/accrue/mature → `accountAdministration`; create/delete/list/tier → `customerAdministration`; transfer fee → `bankSettings`.

- [ ] **Step 7: Update `CustomerController`**

```java
    private final CustomerSelfServicePort customerSelfService;

    public CustomerController(CustomerSelfServicePort customerSelfService) {
        this.customerSelfService = customerSelfService;
    }
```

with

```java
        customerSelfService.changePassword(
                new CustomerSelfServicePort.ChangePasswordCommand(CustomerId.of(customerId), request.newPassword()));
```

- [ ] **Step 8: Delete the old ports**

```bash
git rm -r src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/port/in/
```

- [ ] **Step 9: Collapse the controller-test mocks**

`AccountControllerTest` — replace the nine use-case `@MockitoBean` fields with one, keeping the `BankUserDetailsService` mock:

```java
    @MockitoBean BankUserDetailsService userDetailsService;
    @MockitoBean CustomerAccountPort customerAccount;
```

Every `when(depositMoney.deposit(any()))` becomes `when(customerAccount.deposit(any()))`; `verify(transferMoney).transfer(any())` becomes `verify(customerAccount).transfer(any())`; and `verifyNoInteractions(transferMoney)` becomes `verifyNoInteractions(customerAccount)`.

`AdminControllerTest` — replace its ten with three: `accountAdministration`, `customerAdministration`, `bankSettings`. Route each stub to the field that owns that method.

`CustomerControllerTest` — replace `ChangePasswordUseCase` with `CustomerSelfServicePort`.

**Watch for a behavior difference in `verifyNoInteractions`.** Where a test asserted `verifyNoInteractions(someNarrowUseCase)`, the merged mock now also receives calls from sibling methods in the same test class run. Check each such assertion still means what it meant; prefer `verify(port, never()).theSpecificMethod(any())` if it does not.

- [ ] **Step 10: Update the service tests**

In `AccountApplicationServiceTest` and `CustomerApplicationServiceTest`, change imports and `Command` type names per the Step 2 and Step 4 tables. Assertions do not change.

Move the `setTransferFee` test from `CustomerApplicationServiceTest` to `AccountApplicationServiceTest`, and delete the now-unused `SettingsRepositoryPort` mock from `CustomerApplicationServiceTest`.

- [ ] **Step 11: Compile and iterate**

Run: `mvn -o clean test-compile`

Fix errors file by file. Expect "cannot find symbol" for every stale import — that is the compiler enumerating the work. Repeat until clean.

- [ ] **Step 12: Run the suite**

Run: `mvn -o clean test`
Expected: **184 tests, 0 failures** — the same number as before, because nothing behavioral changed. A different count means a test was lost in the churn; find it before continuing.

- [ ] **Step 13: Verify the old package is gone and nothing references it**

```bash
grep -rn "domain.port.in" src/ ; echo "---"
ls src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/port/
```

Expected: no grep matches, and `domain/port/` containing only `out`.

- [ ] **Step 14: Verify domain purity is intact**

```bash
grep -rn "import org.springframework\|import jakarta" src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/
```

Expected: no matches.

- [ ] **Step 15: Commit**

```bash
git add -A src/
git commit -m "Replace 20 single-method driving ports with 5 actor-shaped ports

Grouped by actor x subject per Cockburn's definition of a port. Driving
ports move to application/port/in; driven ports stay in domain/port/out
because the domain declares the interfaces it requires.

AccountController drops from 9 constructor parameters to 1, AdminController
from 10 to 3. setTransferFee moves to AccountApplicationService, which
already holds SettingsRepositoryPort, so CustomerApplicationService loses
that dependency entirely.

No behavior change: 184 tests, unchanged."
```

---

### Task 3: Documentation

**Files:**
- Modify: `Refactorings.md` (new entry 2), `CLAUDE.md`, `Architecture.md`, `Enhancement.md`

**Interfaces:**
- Consumes: the final state from Task 2
- Produces: nothing consumed by other tasks

- [ ] **Step 1: Add `Refactorings.md` entry 2**

Append below entry 1, following its structure. Record the baseline commit for the "before" line numbers, as entry 1 does. Content:

1. **The symptom** — 20 ports of 7–15 lines; the consumer table (`AccountController` 9, `AdminController` 10, service `implements` lists of 14 and 5; controller-test mocks 10, 11, 2).
2. **The deeper problem** — the grouping had no principle. Cite `SetTransferFeeUseCase` implemented by `CustomerApplicationService`, and `CustomerApplicationService` injecting `SettingsRepositoryPort` solely to serve it.
3. **The organizing principle** — Cockburn: a port is one conversation with one kind of outside actor. Explain why "one interface per method" is not what Interface Segregation asks for: ISP says clients should not depend on methods they do not use, and a customer-facing controller uses all nine customer-facing methods.
4. **Before and after** — the port inventory table, and `AccountController`'s constructor shown both ways as adjacent code blocks. That contrast is the clearest single artifact.
5. **The placement asymmetry**, stated as a rule: driving ports are an application concern; driven ports belong to the domain because the domain declares what it needs. Note that Hombergs puts both under `application/` and say why this project chose otherwise.
6. **The honest cost** — multi-method interfaces are harder to fake by hand, and `verifyNoInteractions` on a merged mock is less precise than on a narrow one.
7. **Scope table** — 20 ports deleted, 5 created, 30 files re-imported, 0 behavior change, 184 tests unchanged.

Quote no number the suite does not assert or a command cannot prove.

- [ ] **Step 2: Update `CLAUDE.md`**

Rewrite the two port lines in the Architecture section:

```
application/port/in/account/   → CustomerAccountPort, AccountAdministrationPort, BankSettingsPort
application/port/in/customer/  → CustomerAdministrationPort, CustomerSelfServicePort
domain/port/out/account/       → AccountRepositoryPort, TransactionRepositoryPort, SettingsRepositoryPort
domain/port/out/customer/      → CustomerRepositoryPort, PasswordHasherPort
```

Delete the two `domain/port/in/...` lines. Add a Key Design Decisions bullet stating the actor-shaped principle and the driving/driven placement asymmetry, referencing `Refactorings.md` entry 2.

- [ ] **Step 3: Update `Architecture.md`**

Find every reference to `domain/port/in` or the individual use-case interfaces and bring them into line with the five ports. Read the surrounding prose rather than pattern-replacing — this file explains the structure, so stale prose is worse than a stale path.

- [ ] **Step 4: Update `Enhancement.md`**

Its worked example lists `domain/port/in/account/SetAccountDailyLimitUseCase.java` as a file to add. Update that row to add a method to `AccountAdministrationPort` at `application/port/in/account/` instead, since a daily limit is set by an admin. Adjust the surrounding narrative to match.

- [ ] **Step 5: Do not touch the historical plans**

`docs/superpowers/plans/2026-04-15-ayvalik-bank.md`, `docs/superpowers/plans/2026-04-25-account-types.md`, and the two `2026-08-07-transaction-amount*` files record decisions as they were made. Leave them alone; a plan that no longer matches the code is a historical record, not a defect.

- [ ] **Step 6: Verify every claim in the new documentation**

For each file-and-line citation and each count, run the command that proves it. At minimum:

```bash
grep -c "Port " src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AccountController.java
ls src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/port/in/*/ | grep -c java
grep -rn "domain.port.in" src/ || echo "clean"
```

- [ ] **Step 7: Final verification**

Run: `mvn clean verify`
Expected: BUILD SUCCESS, 184 tests, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add Refactorings.md CLAUDE.md Architecture.md Enhancement.md docs/superpowers/
git commit -m "Document the actor-shaped ports refactoring

Adds Refactorings.md entry 2 and syncs CLAUDE.md, Architecture.md and
Enhancement.md to the new application/port/in layout."
```

---

## Self-Review

**Spec coverage.** Every spec section maps to a task: the five ports → Task 1; placement move → Task 2 Steps 1, 8, 13; the `setTransferFee` relocation and the dropped `SettingsRepositoryPort` → Task 2 Steps 3–4; controller collapse → Steps 5–7; test churn → Steps 9–10; documentation → Task 3. The spec's "do not update historical plans" instruction is Task 3 Step 5.

**Placeholder scan.** No TBDs. All five interfaces are given in full. Task 2's per-method retyping is expressed as explicit old→new tables rather than "update the types".

**Type consistency.** The `Command` record names in Task 1 (`OpenCheckingCommand`, `DepositCommand`, `WithdrawCommand`, `TransferCommand`, `OpenSavingsCommand`, `OpenTimeDepositCommand`, `AccrueInterestCommand`, `MatureCommand`, `CreateCustomerCommand`, `ChangeCustomerTierCommand`, `ChangePasswordCommand`, `SetTransferFeeCommand`) are used with exactly those names in Task 2's tables and code. Port field names (`customerAccount`, `accountAdministration`, `customerAdministration`, `bankSettings`, `customerSelfService`) are consistent between controllers and their tests.

**Known risks.** Task 2 is large and cannot be subdivided without a non-compiling tree. Two specific traps are called out inline: `verifyNoInteractions` losing precision on merged mocks (Step 9), and the test count being the signal that nothing was lost (Step 12).
