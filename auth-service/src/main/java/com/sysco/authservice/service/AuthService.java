package com.sysco.authservice.service;

import com.sysco.authservice.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final Map<String, String> users = new HashMap<>();
    private final JwtService jwtService;

    public AuthService(
            JwtService jwtService,
            @Value("${app.auth.demo-user.username}") String demoUsername,
            @Value("${app.auth.demo-user.password}") String demoPassword
    ) {
        this.jwtService = jwtService;
        users.put(demoUsername, demoPassword);
    }

    public String login(String username, String password) {
        String expectedPassword = users.get(username);
        if (expectedPassword == null || !expectedPassword.equals(password)) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return jwtService.generateToken(username);
    }

    public long getTokenTtlSeconds() {
        return jwtService.getTtlSeconds();
    }
}
