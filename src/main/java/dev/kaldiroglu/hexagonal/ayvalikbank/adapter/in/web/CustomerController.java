package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.ChangePasswordRequest;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer.CustomerSelfServicePort;
import dev.kaldiroglu.hexagonal.ayvalikbank.config.BankUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerSelfServicePort customerSelfService;

    public CustomerController(CustomerSelfServicePort customerSelfService) {
        this.customerSelfService = customerSelfService;
    }

    @PutMapping("/{customerId}/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal BankUserPrincipal caller,
                                               @PathVariable String customerId,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        customerSelfService.changePassword(new CustomerSelfServicePort.ChangePasswordCommand(
                caller.customerId(), CustomerId.of(customerId), request.newPassword()));
        return ResponseEntity.ok().build();
    }
}
