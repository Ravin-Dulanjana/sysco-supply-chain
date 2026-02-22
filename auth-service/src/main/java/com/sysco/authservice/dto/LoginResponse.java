package com.sysco.authservice.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds
) {
}
