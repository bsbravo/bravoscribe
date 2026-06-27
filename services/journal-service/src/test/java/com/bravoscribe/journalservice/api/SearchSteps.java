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

public class SearchSteps {

    @Autowired
    StepContext ctx;

    private static final String BASE = "/api/journal/entries";

    @Given("I have entries with content {string} and {string}")
    public void iHaveEntriesWithContentAnd(String content1, String content2) {
        RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("entryDate", LocalDate.now().toString(), "content", content1))
                .post(BASE);
        RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("entryDate", LocalDate.now().minusDays(1).toString(), "content", content2))
                .post(BASE);
    }

    @When("I search entries with q {string}")
    public void iSearchEntriesWithQ(String q) {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .queryParam("q", q)
                .get(BASE)
        );
    }

    @And("I receive only the entry containing {string}")
    public void iReceiveOnlyTheEntryContaining(String expected) {
        List<String> contents = ctx.getLastResponse().jsonPath().getList("content.content");
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0)).containsIgnoringCase(expected);
    }

    @When("I search entries with a 201-character query")
    public void iSearchEntriesWithA201CharacterQuery() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .queryParam("q", "a".repeat(201))
                .get(BASE)
        );
    }

    @Given("I have {int} entries")
    public void iHaveEntries(int count) {
        for (int i = 0; i < count; i++) {
            RestAssured.given()
                    .auth().oauth2(ctx.getAccessToken())
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                        "entryDate", LocalDate.now().minusDays(i).toString(),
                        "content", "Entry " + i))
                    .post(BASE);
        }
    }

    @When("I list entries with page size {int}")
    public void iListEntriesWithPageSize(int size) {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .queryParam("size", size)
                .get(BASE)
        );
    }

    @And("I receive at most {int} entries")
    public void iReceiveAtMostEntries(int max) {
        int count = ctx.getLastResponse().jsonPath().getList("content").size();
        assertThat(count).isLessThanOrEqualTo(max);
    }
}
