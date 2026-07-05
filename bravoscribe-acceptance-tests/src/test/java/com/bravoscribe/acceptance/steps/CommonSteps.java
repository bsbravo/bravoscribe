package com.bravoscribe.acceptance.steps;

import com.bravoscribe.acceptance.config.ServiceConfig;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonSteps {

    private final TestContext ctx;

    public CommonSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @Given("all services are running")
    public void allServicesAreRunning() {
        assertHealthy(ServiceConfig.userServiceHealthUrl());
        assertHealthy(ServiceConfig.journalServiceHealthUrl());
        assertHealthy(ServiceConfig.notificationServiceHealthUrl());
    }

    private void assertHealthy(String healthUrl) {
        Response response = RestAssured.given().get(healthUrl);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
    }

    @When("I call GET {word} without a token")
    public void iCallGetWithoutAToken(String path) {
        ctx.lastResponse = RestAssured.given()
                .baseUri(ServiceConfig.originFor(path))
                .get(path);
    }

    @When("I call GET {word} with the expired token")
    public void iCallGetWithTheExpiredToken(String path) {
        String expiredToken = ServiceConfig.mintExpiredAccessToken(ctx.userId);
        ctx.lastResponse = RestAssured.given()
                .baseUri(ServiceConfig.originFor(path))
                .header("Authorization", "Bearer " + expiredToken)
                .get(path);
    }

    @Then("I receive status {int}")
    public void iReceiveStatus(int statusCode) {
        assertThat(ctx.lastResponse.statusCode()).isEqualTo(statusCode);
    }
}
