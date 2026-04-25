package dev.kaldiroglu.hexagonal.ayvalikbank.config;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.customer.PasswordValidationService;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.account.TransferDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final BankUserDetailsService userDetailsService;

    public SecurityConfig(BankUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/customers/**").hasRole("CUSTOMER")
                .requestMatchers("/api/accounts/**").hasRole("CUSTOMER")
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {});
        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(new BCryptPasswordEncoder(12));
        return provider;
    }

    @Bean
    public PasswordValidationService passwordValidationService() {
        return new PasswordValidationService();
    }

    @Bean
    public TransferDomainService transferDomainService() {
        return new TransferDomainService();
    }
}
