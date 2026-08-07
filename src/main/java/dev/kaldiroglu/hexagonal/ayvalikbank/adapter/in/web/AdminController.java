package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.AccrueInterestRequest;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.ChangeCustomerTierRequest;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.CreateCustomerRequest;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.SetTransferFeeRequest;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.CustomerResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.TransactionResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.AccountAdministrationPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account.BankSettingsPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer.CustomerAdministrationPort;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

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

    @PostMapping("/customers")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        var customer = customerAdministration.createCustomer(
                new CustomerAdministrationPort.CreateCustomerCommand(request.name(), request.email(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.from(customer));
    }

    @DeleteMapping("/customers/{customerId}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable String customerId) {
        customerAdministration.deleteCustomer(CustomerId.of(customerId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/customers")
    public ResponseEntity<List<CustomerResponse>> listCustomers() {
        var customers = customerAdministration.listCustomers().stream()
                .map(CustomerResponse::from)
                .toList();
        return ResponseEntity.ok(customers);
    }

    @PutMapping("/customers/{customerId}/tier")
    public ResponseEntity<Void> changeCustomerTier(@PathVariable String customerId,
                                                    @Valid @RequestBody ChangeCustomerTierRequest request) {
        customerAdministration.changeCustomerTier(new CustomerAdministrationPort.ChangeCustomerTierCommand(
                CustomerId.of(customerId), request.tier()));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/settings/transfer-fee")
    public ResponseEntity<Void> setTransferFee(@Valid @RequestBody SetTransferFeeRequest request) {
        bankSettings.setTransferFee(new BankSettingsPort.SetTransferFeeCommand(request.feePercent()));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/accounts/{accountId}/freeze")
    public ResponseEntity<Void> freezeAccount(@PathVariable String accountId) {
        accountAdministration.freezeAccount(AccountId.of(accountId));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/accounts/{accountId}/unfreeze")
    public ResponseEntity<Void> unfreezeAccount(@PathVariable String accountId) {
        accountAdministration.unfreezeAccount(AccountId.of(accountId));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/accounts/{accountId}/close")
    public ResponseEntity<Void> closeAccount(@PathVariable String accountId) {
        accountAdministration.closeAccount(AccountId.of(accountId));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/accounts/{accountId}/accrue-interest")
    public ResponseEntity<TransactionResponse> accrueInterest(@PathVariable String accountId,
                                                               @Valid @RequestBody AccrueInterestRequest request) {
        var tx = accountAdministration.accrueInterest(new AccountAdministrationPort.AccrueInterestCommand(
                AccountId.of(accountId), request.month()));
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }

    @PutMapping("/accounts/{accountId}/mature")
    public ResponseEntity<TransactionResponse> matureTimeDeposit(@PathVariable String accountId) {
        var tx = accountAdministration.mature(new AccountAdministrationPort.MatureCommand(AccountId.of(accountId)));
        return ResponseEntity.ok(TransactionResponse.from(tx));
    }
}
