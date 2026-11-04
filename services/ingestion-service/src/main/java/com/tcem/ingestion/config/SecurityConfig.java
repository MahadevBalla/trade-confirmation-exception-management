package com.tcem.ingestion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Basic authentication and role checks (spec section 13: "basic authentication/authorization is sufficient for MVP").
 * Reads and health checks stay open, because all the data is synthetic; changes require a signed-in user.
 * The accounts are demo accounts, with passwords taken from the environment.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/trades/**").authenticated()
                        .requestMatchers("/auth/**").authenticated()
                        .anyRequest().permitAll())
                // A WWW-Authenticate header would make browsers show their own password dialog over the UI's login.
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, ex) ->
                        writeError(response, 401, "Sign in to do this")))
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, ex) ->
                        writeError(response, 403, "Your role does not allow this")))
                .build();
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + message + "\"}");
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder,
                             @Value("${TCEM_OPS_PASSWORD:ops-demo}") String opsPassword,
                             @Value("${TCEM_SUPERVISOR_PASSWORD:supervisor-demo}") String supervisorPassword,
                             @Value("${TCEM_ADMIN_PASSWORD:admin-demo}") String adminPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("ops").password(encoder.encode(opsPassword)).roles("OPERATIONS_SPECIALIST").build(),
                User.withUsername("supervisor").password(encoder.encode(supervisorPassword)).roles("SUPERVISOR").build(),
                User.withUsername("admin").password(encoder.encode(adminPassword)).roles("SYSTEM_ADMIN").build());
    }
}
