package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.ChangePasswordRequest;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer.CustomerSelfServicePort;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerSelfServicePort customerSelfService;

    public CustomerController(CustomerSelfServicePort customerSelfService) {
        this.customerSelfService = customerSelfService;
    }

    @PutMapping("/{customerId}/password")
    public ResponseEntity<Void> changePassword(@PathVariable String customerId,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        customerSelfService.changePassword(
                new CustomerSelfServicePort.ChangePasswordCommand(CustomerId.of(customerId), request.newPassword()));
        return ResponseEntity.ok().build();
    }
}
