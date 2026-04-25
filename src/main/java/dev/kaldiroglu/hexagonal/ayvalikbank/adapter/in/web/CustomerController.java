package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.ChangePasswordRequest;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.customer.ChangePasswordUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final ChangePasswordUseCase changePassword;

    public CustomerController(ChangePasswordUseCase changePassword) {
        this.changePassword = changePassword;
    }

    @PutMapping("/{customerId}/password")
    public ResponseEntity<Void> changePassword(@PathVariable String customerId,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        changePassword.changePassword(
                new ChangePasswordUseCase.Command(CustomerId.of(customerId), request.newPassword()));
        return ResponseEntity.ok().build();
    }
}
