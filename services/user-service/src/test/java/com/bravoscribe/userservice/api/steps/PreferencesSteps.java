package com.bravoscribe.userservice.api.steps;

import io.cucumber.java.ParameterType;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class PreferencesSteps {

    @Autowired StepContext ctx;

    @ParameterType("true|false")
    public Boolean bool(String value) {
        return Boolean.parseBoolean(value);
    }

    @When("I update my preferences with reminderTime {string} and weeklySummaryEnabled {bool}")
    public void updatePreferences(String reminderTime, Boolean weeklySummaryEnabled) {
        String body = """
                {"reminderTime":"%s","weeklySummaryEnabled":%s}
                """.formatted(reminderTime, weeklySummaryEnabled);
        Response r = RestAssured.given()
                .header("Authorization", "Bearer " + ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(body)
                .put("/me/preferences");
        ctx.setLastResponse(r);
    }

    @And("the preference reminderTime is {string}")
    public void preferenceReminderTimeIs(String expected) {
        assertThat(ctx.getLastResponse().jsonPath().getString("reminderTime")).isEqualTo(expected);
    }

    @And("the preference weeklySummaryEnabled is {bool}")
    public void preferenceWeeklySummaryEnabledIs(Boolean expected) {
        assertThat(ctx.getLastResponse().jsonPath().getBoolean("weeklySummaryEnabled")).isEqualTo(expected);
    }
}
