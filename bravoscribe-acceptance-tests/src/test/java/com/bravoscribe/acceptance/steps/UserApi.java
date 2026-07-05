package com.bravoscribe.acceptance.steps;

import com.bravoscribe.acceptance.config.ServiceConfig;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.util.Map;
import java.util.UUID;

/**
 * Plain HTTP helper against user-service, shared by UserSteps and JournalSteps
 * (Cucumber step-definition classes can't call each other directly).
 */
final class UserApi {

    private UserApi() {}

    static Response register(String name, String email, String password) {
        // Feature files reuse literal emails (e.g. "bruno@email.com") across scenarios,
        // but containers/DB persist for the whole suite run — delete any leftover row
        // from a prior scenario first to avoid a spurious 409 (Lesson L5 pattern).
        UserDb.deleteUserByEmail(email);
        return RestAssured.given()
                .baseUri(ServiceConfig.userServiceBaseUrl())
                .contentType(ContentType.JSON)
                .body(Map.of("name", name, "email", email, "password", password))
                .post("/register");
    }

    static Response login(String email, String password) {
        return RestAssured.given()
                .baseUri(ServiceConfig.userServiceBaseUrl())
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password))
                .post("/login");
    }

    /** Registers and logs in a user with a generated unique email, populating the given handle. */
    static void registerAndLogin(TestContext.User user, String emailPrefix) {
        String email = emailPrefix + "-" + UUID.randomUUID() + "@email.com";
        String password = "P@ssword123";
        Response registerResponse = register("Test User", email, password);
        if (registerResponse.statusCode() != 201) {
            throw new IllegalStateException("Registration failed: " + registerResponse.asString());
        }
        user.accessToken = registerResponse.jsonPath().getString("accessToken");
    }

    static UUID extractUserId(String accessToken) {
        try {
            return UUID.fromString(SignedJWT.parse(accessToken).getJWTClaimsSet().getSubject());
        } catch (Exception e) {
            throw new RuntimeException("Cannot parse access token", e);
        }
    }
}
