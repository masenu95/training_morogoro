package com.training.addupdateproduct.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfig {

    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        if (!securityEnabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakRoles())));
        return http.build();
    }

    private JwtAuthenticationConverter keycloakRoles() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            Collection<String> roles = realmAccess == null ? List.of() : (Collection<String>) realmAccess.get("roles");
            return roles.stream().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        });
        return converter;
    }
}
