package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;

public record CustomerResponse(String id, String name, String email, String role) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId().toString(),
                customer.getName(),
                customer.getEmail(),
                customer.getRole()
        );
    }
}
