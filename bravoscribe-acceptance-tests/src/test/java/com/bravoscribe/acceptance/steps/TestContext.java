package com.bravoscribe.acceptance.steps;

import io.restassured.response.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Scenario-scoped state shared across step-definition classes via PicoContainer DI
 * (one instance per scenario, injected wherever a step class asks for it in its
 * constructor). Replaces the Spring-context-backed state the per-service Level 2
 * suites get for free from cucumber-spring.
 */
public class TestContext {

    public String currentEmail;
    public String currentPassword;
    public String previousPassword;
    public String accessToken;
    public String refreshTokenCookie;
    public UUID userId;
    public Response lastResponse;
    public String resetToken;
    public UUID lastEntryId;
    public String lastMarkdown;

    private final Map<String, User> namedUsers = new HashMap<>();

    public User named(String name) {
        return namedUsers.computeIfAbsent(name, n -> new User());
    }

    public static class User {
        public String accessToken;
        public UUID entryId;
    }
}
