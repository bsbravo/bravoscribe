package com.bravoscribe.journalservice.api;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CalendarSteps {

    @Autowired
    StepContext ctx;

    private static final String BASE = "/api/journal/entries";

    @Given("I have entries on {string} and {string}")
    public void iHaveEntriesOnAnd(String date1, String date2) {
        RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("entryDate", date1, "content", "Entry on " + date1))
                .post(BASE);
        RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("entryDate", date2, "content", "Entry on " + date2))
                .post(BASE);
    }

    @When("I get entry dates for June 2026")
    public void iGetEntryDatesForJune2026() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .queryParam("from", "2026-06-01")
                .queryParam("to", "2026-06-30")
                .get(BASE + "/dates")
        );
    }

    @And("the response contains dates {string} and {string}")
    public void theResponseContainsDatesAnd(String date1, String date2) {
        List<String> dates = ctx.getLastResponse().jsonPath().getList("$");
        assertThat(dates).contains(date1, date2);
    }

    @When("I get the entry for today")
    public void iGetTheEntryForToday() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .get(BASE + "/date/" + LocalDate.now())
        );
    }

    @And("the response contains today's entry")
    public void theResponseContainsTodaysEntry() {
        String entryDate = ctx.getLastResponse().jsonPath().getString("entryDate");
        assertThat(entryDate).isEqualTo(LocalDate.now().toString());
    }

    @Given("I have no entry for yesterday")
    public void iHaveNoEntryForYesterday() {
        // no-op: DB is clean before each scenario
    }

    @When("I get the entry for yesterday")
    public void iGetTheEntryForYesterday() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .get(BASE + "/date/" + LocalDate.now().minusDays(1))
        );
    }
}
