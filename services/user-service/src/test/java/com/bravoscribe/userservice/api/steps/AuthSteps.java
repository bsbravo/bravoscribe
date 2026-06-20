package com.bravoscribe.userservice.api.steps;

import com.bravoscribe.userservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthSteps {

    @Autowired StepContext ctx;
    @Autowired UserRepository userRepo;

    @Before
    public void resetContext() {
        ctx.reset();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Given("I register a user with email {string} and password {string}")
    public void registerUser(String email, String password) {
        String body = """
                {"name":"Test User","email":"%s","password":"%s"}
                """.formatted(email, password);
        Response r = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/register");
        ctx.setLastResponse(r);
    }

    @Given("a registered user with email {string} and password {string}")
    public void aRegisteredUser(String email, String password) {
        userRepo.findByEmail(email).ifPresent(userRepo::delete);
        registerUser(email, password);
        assertThat(ctx.getLastResponse().statusCode()).isEqualTo(201);
        ctx.reset();
    }

    @When("I log in with email {string} and password {string}")
    public void login(String email, String password) {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        Response r = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/login");
        ctx.setLastResponse(r);
    }

    @When("I refresh the access token")
    public void refreshToken() {
        var reqSpec = RestAssured.given().contentType(ContentType.JSON);
        if (ctx.getRefreshTokenCookie() != null) {
            reqSpec = reqSpec.cookie("refreshToken", ctx.getRefreshTokenCookie());
        }
        Response r = reqSpec.post("/refresh");
        ctx.setLastResponse(r);
    }

    @When("I log out")
    public void logout() {
        var reqSpec = RestAssured.given();
        if (ctx.getRefreshTokenCookie() != null) {
            reqSpec = reqSpec.cookie("refreshToken", ctx.getRefreshTokenCookie());
        }
        Response r = reqSpec.delete("/logout");
        ctx.setLastResponse(r);
    }

    @Then("the response status is {int}")
    public void responseStatus(int expectedStatus) {
        assertThat(ctx.getLastResponse().statusCode()).isEqualTo(expectedStatus);
    }

    @And("the response contains an access token")
    public void responseContainsAccessToken() {
        assertThat(ctx.getLastResponse().jsonPath().getString("accessToken")).isNotBlank();
    }

    @And("the response has an httpOnly cookie named {string}")
    public void responseHasHttpOnlyCookie(String cookieName) {
        assertThat(ctx.getLastResponse().getCookie(cookieName)).isNotNull();
    }

    @And("I am authenticated")
    public void iAmAuthenticated() {
        assertThat(ctx.getAccessToken()).isNotBlank();
    }

    @And("I save my current refresh token")
    public void saveCurrentRefreshToken() {
        ctx.setSavedRefreshTokenCookie(ctx.getRefreshTokenCookie());
    }

    @When("I refresh with the saved refresh token")
    public void refreshWithSavedToken() {
        Response r = RestAssured.given()
                .contentType(ContentType.JSON)
                .cookie("refreshToken", ctx.getSavedRefreshTokenCookie())
                .post("/refresh");
        ctx.setLastResponse(r);
    }

    @And("I save the returned user id")
    public void saveReturnedUserId() {
        try {
            String jwt = ctx.getAccessToken();
            String[] parts = jwt.split("\\.");
            int pad = (4 - parts[1].length() % 4) % 4;
            String padded = parts[1] + "====".substring(0, pad);
            String json = new String(Base64.getUrlDecoder().decode(padded));
            ctx.setTargetUserId(new ObjectMapper().readTree(json).get("sub").asText());
        } catch (Exception e) {
            throw new RuntimeException("Could not extract user id from JWT", e);
        }
    }
}
