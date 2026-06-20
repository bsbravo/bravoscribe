package com.bravoscribe.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        long accessExpirySeconds,
        long refreshExpiryDays,
        long passwordResetExpiryMinutes
) {}
