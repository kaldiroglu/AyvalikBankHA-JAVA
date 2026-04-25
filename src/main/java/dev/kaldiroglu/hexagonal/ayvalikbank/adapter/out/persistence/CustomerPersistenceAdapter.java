package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.mapper.CustomerPersistenceMapper;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.repository.CustomerJpaRepository;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer.CustomerRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CustomerPersistenceAdapter implements CustomerRepositoryPort {

    private final CustomerJpaRepository repository;
    private final CustomerPersistenceMapper mapper;

    public CustomerPersistenceAdapter(CustomerJpaRepository repository, CustomerPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Customer save(Customer customer) {
        var entity = mapper.toJpaEntity(customer);
        var saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public List<Customer> findAll() {
        return repository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(CustomerId id) {
        repository.deleteById(id.value());
    }

    @Override
    public boolean existsById(CustomerId id) {
        return repository.existsById(id.value());
    }
}
