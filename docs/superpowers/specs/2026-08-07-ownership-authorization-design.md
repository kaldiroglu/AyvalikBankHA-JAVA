# Ownership Authorization — Design

Claude Opus 5 (1M context) — created 2026-08-07

## Problem

Any authenticated customer can operate on any other customer's data. Three concrete holes:

**1. Account takeover.** `CustomerController.java:20-21` reads the target customer from the path:

```java
@PutMapping("/{customerId}/password")
public ResponseEntity<Void> changePassword(@PathVariable String customerId, ...)
```

`SecurityConfig.java:32` gates `/api/customers/**` on `hasRole("CUSTOMER")` and nothing more. Any
customer can set any other customer's password, then log in as them.

**2. No ownership check on account operations.** `/api/accounts/**` (`SecurityConfig.java:33`)
requires only `hasRole("CUSTOMER")`. A customer can deposit to, withdraw from, transfer out of, read the balance
of and read the transaction history of any account, given its id.

**3. Accounts can be opened for other people.** `AccountController.java:30`, `:41` and `:49` take
`ownerId` as a request parameter with no check.

`UnauthorizedAccessException` exists and is mapped to HTTP 403 at `GlobalExceptionHandler.java:54`.
**No production code throws it.** The existing 403 tests (`AccountControllerTest.java:91`,
`AdminControllerTest.java:109`) assert *role* separation — a CUSTOMER hitting an ADMIN route — never
ownership.

### Root cause

Not one of the customer-facing `Command` records carries the caller's identity:

```java
record DepositCommand(AccountId accountId, TransactionAmount amount) {}
record ChangePasswordCommand(CustomerId customerId, String rawNewPassword) {}
```

The rule "the caller must own this account" is therefore **inexpressible** — there is no caller to
compare against. This is the mirror image of the `TransactionAmount` lesson: there, the type made a
bad state impossible; here, the type made the correct rule impossible to state, and so it went
missing.

## Approach

**The fact lives in the domain; the policy lives in the application.**

- `Account.isOwnedBy(CustomerId)` — the domain owns the fact. It already holds `ownerId`.
- `AccountApplicationService` / `CustomerApplicationService` decide and throw
  `UnauthorizedAccessException`. A *caller* is a session concept; the domain never learns it exists.

This needs no new exception class and no new package, and it matches how the codebase already
translates domain refusals into application exceptions.

### Caller identity

`BankUserDetailsService.java:24-28` already loads the full `Customer` at login and discards
everything but email and role. It will instead build a principal carrying the `CustomerId`:

```java
public class BankUserPrincipal extends User {
    private final CustomerId customerId;
    public BankUserPrincipal(CustomerId customerId, String email, String passwordHash,
                             Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, authorities);
        this.customerId = customerId;
    }
    public CustomerId customerId() { return customerId; }
}
```

Controllers read it with `@AuthenticationPrincipal BankUserPrincipal caller`. **Zero extra queries
per request** — the lookup already happened during authentication.

### The three shapes of the rule

Deliberately different, because the situations differ:

| Situation | Technique | Endpoints |
|---|---|---|
| The resource *is* the caller's | **Eliminate the parameter** — the caller is the owner, so there is nothing to check | `POST /api/accounts/{checking,savings,time-deposit}` |
| The path names a customer | **Require self** — path id must equal caller | `GET /api/customers/{id}/accounts`, `PUT /api/customers/{id}/password` |
| The path names an account | **Require ownership** — load the account, compare `ownerId` | deposit, withdraw, transfer, balance, transactions |

The first row is the `TransactionAmount` move applied to authorization: rather than validating
`ownerId == caller`, remove `ownerId` so opening an account for someone else cannot be expressed.

**Transfer is asymmetric.** The caller must own the **source** account only. The target must not be
checked — sending money to other people is the entire point.

### API change (needs your sign-off)

Removing `ownerId` changes three endpoints:

```
POST /api/accounts/checking?ownerId=<id>     →  POST /api/accounts/checking
POST /api/accounts/savings?ownerId=<id>      →  POST /api/accounts/savings
POST /api/accounts/time-deposit?ownerId=<id> →  POST /api/accounts/time-deposit
```

The account is opened for the authenticated caller. This is a breaking REST change and the only
externally visible one in this work. The alternative — keep `ownerId` and reject when it differs
from the caller — preserves the URL shape but leaves a parameter whose only legal value is a value
the server already knows.

Note there is currently **no** admin route for opening an account on a customer's behalf. If that is
ever wanted, it belongs on `AccountAdministrationPort`, not here.

## Port changes

`CustomerAccountPort` — every method gains the caller, always first:

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

`CustomerSelfServicePort`:

```java
record ChangePasswordCommand(CustomerId callerId, CustomerId customerId, String rawNewPassword) {}
```

`listAccounts` and `ChangePasswordCommand` carry both ids on purpose: the path still names a
customer, so the equality check is the thing being enforced and should be visible.

The three admin ports are **unchanged** — role gating already covers them.

## Enforcement

New domain method:

```java
public final boolean isOwnedBy(CustomerId customerId) {
    return this.ownerId.equals(customerId);
}
```

Two private helpers in the application services:

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

Messages deliberately omit the account id and the owner id — an error message is not the place to
confirm which accounts exist.

## Error handling

`UnauthorizedAccessException` → HTTP 403, already wired at `GlobalExceptionHandler.java:54`. No new
exception type, no handler change.

**Known trade-off, deliberately accepted:** a request for a *non-existent* account returns 404 while
a request for an account owned by someone else returns 403, which lets an attacker distinguish the
two and enumerate valid account ids. Returning 404 for both is the hardened choice. This design
keeps 403 because the distinction is clearer to read in a teaching codebase, and records the
trade-off rather than hiding it. Account ids are random UUIDs, which makes enumeration impractical
in any case.

## Test fixture (required, not optional)

`@WithMockUser` produces a plain Spring `User`, so `@AuthenticationPrincipal BankUserPrincipal`
resolves to **null** and every existing controller test would NPE. A test-side fixture is therefore
part of this work, not an afterthought:

```java
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithBankUserSecurityContextFactory.class)
public @interface WithBankUser {
    String customerId();
    String role() default "CUSTOMER";
}
```

The factory builds a `BankUserPrincipal` and puts it in the `SecurityContext`. Every
`@WithMockUser(roles = "CUSTOMER")` in `AccountControllerTest` and `CustomerControllerTest` becomes
`@WithBankUser(customerId = "...")`. `AdminControllerTest` keeps `@WithMockUser(roles = "ADMIN")` —
admin routes take no principal.

## Testing

New tests, one per rule, each asserting 403:

| Test | Asserts |
|---|---|
| `deposit_returnsForbiddenWhenAccountNotOwned` | ownership on deposit |
| `withdraw_returnsForbiddenWhenAccountNotOwned` | ownership on withdraw |
| `transfer_returnsForbiddenWhenSourceNotOwned` | ownership on the **source** |
| `transfer_succeedsWhenTargetOwnedByAnother` | the target is deliberately **not** checked |
| `getBalance_returnsForbiddenWhenAccountNotOwned` | ownership on a query |
| `getTransactions_returnsForbiddenWhenAccountNotOwned` | ownership on a query |
| `listAccounts_returnsForbiddenForAnotherCustomer` | self check |
| `changePassword_returnsForbiddenForAnotherCustomer` | self check — the takeover hole |

Plus application-service tests for `requireOwner` / `requireSelf` denial paths, and a domain test for
`Account.isOwnedBy`.

`transfer_succeedsWhenTargetOwnedByAnother` is the most important test here: it pins the asymmetry,
so a later "tighten the transfer check" edit fails loudly instead of quietly breaking the product.

## Scope

| | |
|---|---|
| New | `BankUserPrincipal`, `@WithBankUser` + its factory, `Account.isOwnedBy` |
| Modified | `BankUserDetailsService`, `CustomerAccountPort`, `CustomerSelfServicePort`, both application services, `AccountController`, `CustomerController`, `AccountControllerTest`, `CustomerControllerTest`, both application-service tests |
| Untouched | persistence, `SecurityConfig` route rules, `GlobalExceptionHandler`, the three admin ports, `AdminController` |
| Behavior changes | 3 endpoints lose `?ownerId=`; 8 previously-allowed cross-customer operations now return 403 |

## Out of scope

`changePassword` does not verify the *current* password. Worth fixing, but it is a separate concern:
under HTTP Basic the caller has already proven the password on this very request, so re-verifying
adds little until session-based auth arrives. Recorded here so it is not mistaken for an oversight.

## Documentation deliverable

`Refactorings.md` entry 3: the three holes with evidence, the inexpressible-rule root cause and its
symmetry with entry 1, the three enforcement shapes and why they differ, the transfer asymmetry, the
403-versus-404 trade-off, and the test-fixture requirement as an example of a security change whose
cost lands mostly in test infrastructure.
