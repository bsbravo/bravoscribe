package com.bravoscribe.journalservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String publicKey) {}
