package com.bravoscribe.userservice.api.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class UserSteps {

    @Autowired StepContext ctx;

    @When("I get my profile")
    public void getMyProfile() {
        Response r = RestAssured.given()
                .header("Authorization", "Bearer " + ctx.getAccessToken())
                .get("/me");
        ctx.setLastResponse(r);
    }

    @When("I update my name to {string}")
    public void updateName(String newName) {
        String body = """
                {"name":"%s"}
                """.formatted(newName);
        Response r = RestAssured.given()
                .header("Authorization", "Bearer " + ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(body)
                .put("/me");
        ctx.setLastResponse(r);
    }

    @Then("the response name is {string}")
    public void responseNameIs(String expectedName) {
        assertThat(ctx.getLastResponse().jsonPath().getString("name")).isEqualTo(expectedName);
    }

    @Then("the response email is {string}")
    public void responseEmailIs(String expectedEmail) {
        assertThat(ctx.getLastResponse().jsonPath().getString("email")).isEqualTo(expectedEmail);
    }
}
