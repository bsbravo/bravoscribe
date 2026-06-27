package com.bravoscribe.journalservice.api;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class StatsSteps {

    @Autowired
    StepContext ctx;

    private static final String ENTRY_BASE = "/api/journal/entries";

    @Given("I have {int} entries on consecutive days")
    public void iHaveEntriesOnConsecutiveDays(int count) {
        for (int i = 0; i < count; i++) {
            RestAssured.given()
                    .auth().oauth2(ctx.getAccessToken())
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                        "entryDate", LocalDate.now().minusDays(i).toString(),
                        "content", "Consecutive entry " + i))
                    .post(ENTRY_BASE);
        }
    }

    @When("I request journal stats")
    public void iRequestJournalStats() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .get("/api/journal/stats")
        );
    }

    @And("totalEntries is {int}")
    public void totalEntriesIs(int expected) {
        int actual = ctx.getLastResponse().jsonPath().getInt("totalEntries");
        assertThat(actual).isEqualTo(expected);
    }

    @And("currentStreak is {int}")
    public void currentStreakIs(int expected) {
        int actual = ctx.getLastResponse().jsonPath().getInt("currentStreak");
        assertThat(actual).isEqualTo(expected);
    }
}
