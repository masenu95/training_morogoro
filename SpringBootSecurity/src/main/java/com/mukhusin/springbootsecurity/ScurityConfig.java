package com.mukhusin.springbootsecurity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class ScurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder) {

        UserDetails learner = User.withUsername("learner")
                .password(passwordEncoder.encode("Learner@123"))
                .roles("USER")
                .build();


        UserDetails trainer = User.withUsername("trainer")
                .password(passwordEncoder.encode("Trainer@123"))
                .roles("USER", "ADMIN")
                .build();

        return new InMemoryUserDetailsManager(
                learner,
                trainer
        );
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        return http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/hello").permitAll()
                        .requestMatchers("/secure").hasRole("ADMIN")
                        .requestMatchers("/bye").hasRole("USER")
                        .anyRequest().authenticated()
                )

                .httpBasic(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())

                .build();
    }
}
