package com.bravoscribe.userservice.api.steps;

import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

public class PasswordSteps {

    @Autowired StepContext ctx;

    @When("I change my password from {string} to {string}")
    public void changePassword(String current, String newPassword) {
        String body = """
                {"currentPassword":"%s","newPassword":"%s"}
                """.formatted(current, newPassword);
        Response r = RestAssured.given()
                .header("Authorization", "Bearer " + ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(body)
                .put("/me/password");
        ctx.setLastResponse(r);
    }

    @When("I request a password reset for {string}")
    public void requestPasswordReset(String email) {
        String body = """
                {"email":"%s"}
                """.formatted(email);
        Response r = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/password-reset/request");
        ctx.setLastResponse(r);
    }

    @When("I confirm a password reset with token {string} and new password {string}")
    public void confirmPasswordReset(String token, String newPassword) {
        String body = """
                {"token":"%s","newPassword":"%s"}
                """.formatted(token, newPassword);
        Response r = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
                .put("/password-reset/confirm");
        ctx.setLastResponse(r);
    }
}
