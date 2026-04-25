package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.mapper;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity.CustomerJpaEntity;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity.PasswordHistoryJpaEntity;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Password;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CustomerPersistenceMapper {

    public Customer toDomain(CustomerJpaEntity entity) {
        List<Password> history = entity.getPasswordHistory().stream()
                .map(h -> Password.ofHashed(h.getHashedPassword()))
                .toList();
        return new Customer(
                CustomerId.of(entity.getId()),
                entity.getName(),
                entity.getEmail(),
                entity.getRole(),
                Password.ofHashed(entity.getCurrentPassword()),
                history
        );
    }

    public CustomerJpaEntity toJpaEntity(Customer customer) {
        CustomerJpaEntity entity = new CustomerJpaEntity();
        entity.setId(customer.getId().value());
        entity.setName(customer.getName());
        entity.setEmail(customer.getEmail());
        entity.setRole(customer.getRole());
        entity.setCurrentPassword(customer.getCurrentPassword().hashedValue());

        List<PasswordHistoryJpaEntity> historyEntities = new ArrayList<>();
        List<Password> history = customer.getPasswordHistory();
        for (int i = 0; i < history.size(); i++) {
            historyEntities.add(new PasswordHistoryJpaEntity(entity, history.get(i).hashedValue(), i));
        }
        entity.setPasswordHistory(historyEntities);
        return entity;
    }
}
