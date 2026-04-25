package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.AccrueInterestRequest;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.CreateCustomerRequest;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.SetTransferFeeRequest;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.CustomerResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.TransactionResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.account.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer.*;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CreateCustomerUseCase createCustomer;
    private final DeleteCustomerUseCase deleteCustomer;
    private final ListCustomersUseCase listCustomers;
    private final SetTransferFeeUseCase setTransferFee;
    private final FreezeAccountUseCase freezeAccount;
    private final UnfreezeAccountUseCase unfreezeAccount;
    private final CloseAccountUseCase closeAccount;
    private final AccrueInterestUseCase accrueInterest;
    private final MatureTimeDepositUseCase matureTimeDeposit;

    public AdminController(CreateCustomerUseCase createCustomer,
                           DeleteCustomerUseCase deleteCustomer,
                           ListCustomersUseCase listCustomers,
                           SetTransferFeeUseCase setTransferFee,
                           FreezeAccountUseCase freezeAccount,
                           UnfreezeAccountUseCase unfreezeAccount,
                           CloseAccountUseCase closeAccount,
                           AccrueInterestUseCase accrueInterest,
                           MatureTimeDepositUseCase matureTimeDeposit) {
        this.createCustomer = createCustomer;
        this.deleteCustomer = deleteCustomer;
        this.listCustomers = listCustomers;
        this.setTransferFee = setTransferFee;
        this.freezeAccount = freezeAccount;
        this.unfreezeAccount = unfreezeAccount;
        this.closeAccount = closeAccount;
        this.accrueInterest = accrueInterest;
        this.matureTimeDeposit = matureTimeDeposit;
    }

    @PostMapping("/customers")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        var customer = createCustomer.createCustomer(
                new CreateCustomerUseCase.Command(request.name(), request.email(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.from(customer));
    }

    @DeleteMapping("/customers/{customerId}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable String customerId) {
        deleteCustomer.deleteCustomer(CustomerId.of(customerId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/customers")
    public ResponseEntity<List<CustomerResponse>> listCustomers() {
        var customers = listCustomers.listCustomers().stream()
                .map(CustomerResponse::from)
                .toList();
        return ResponseEntity.ok(customers);
    }

    @PutMapping("/settings/transfer-fee")
    public ResponseEntity<Void> setTransferFee(@Valid @RequestBody SetTransferFeeRequest request) {
        setTransferFee.setTransferFee(new SetTransferFeeUseCase.Command(request.feePercent()));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/accounts/{accountId}/freeze")
    public ResponseEntity<Void> freezeAccount(@PathVariable String accountId) {
        freezeAccount.freezeAccount(AccountId.of(accountId));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/accounts/{accountId}/unfreeze")
    public ResponseEntity<Void> unfreezeAccount(@PathVariable String accountId) {
        unfreezeAccount.unfreezeAccount(AccountId.of(accountId));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/accounts/{accountId}/close")
    public ResponseEntity<Void> closeAccount(@PathVariable String accountId) {
        closeAccount.closeAccount(AccountId.of(accountId));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/accounts/{accountId}/accrue-interest")
    public ResponseEntity<TransactionResponse> accrueInterest(@PathVariable String accountId,
                                                               @Valid @RequestBody AccrueInterestRequest request) {
        var tx = accrueInterest.accrueInterest(new AccrueInterestUseCase.Command(
                AccountId.of(accountId), request.month()));
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }

    @PutMapping("/accounts/{accountId}/mature")
    public ResponseEntity<TransactionResponse> matureTimeDeposit(@PathVariable String accountId) {
        var tx = matureTimeDeposit.mature(new MatureTimeDepositUseCase.Command(AccountId.of(accountId)));
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }
}
