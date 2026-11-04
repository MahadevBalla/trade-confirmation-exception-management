package com.tcem.ingestion.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lets the UI check credentials at sign-in and learn the user's role.
 */
@RestController
public class AuthController {

    @GetMapping("/auth/me")
    public Map<String, String> me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst()
                .orElse("");
        return Map.of("username", authentication.getName(), "role", role);
    }
}
