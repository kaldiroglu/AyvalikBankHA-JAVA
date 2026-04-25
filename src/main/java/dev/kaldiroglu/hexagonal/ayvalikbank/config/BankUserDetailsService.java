package dev.kaldiroglu.hexagonal.ayvalikbank.config;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer.CustomerRepositoryPort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BankUserDetailsService implements UserDetailsService {

    private final CustomerRepositoryPort customerRepository;

    public BankUserDetailsService(CustomerRepositoryPort customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return customerRepository.findByEmail(email)
                .map(customer -> User.builder()
                        .username(customer.getEmail())
                        .password(customer.getCurrentPassword().hashedValue())
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + customer.getRole())))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
