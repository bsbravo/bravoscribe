package com.bravoscribe.journalservice.api;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class EntrySteps {

    @Autowired
    StepContext ctx;

    private static final String BASE = "/api/journal/entries";

    @When("I create an entry with content {string} for today")
    public void iCreateAnEntryWithContentForToday(String content) {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("entryDate", LocalDate.now().toString(), "content", content))
                .post(BASE)
        );
        if (ctx.getLastResponse().getStatusCode() == 201) {
            ctx.setLastEntryId(UUID.fromString(ctx.getLastResponse().jsonPath().getString("id")));
        }
    }

    @And("the entry is returned with the correct date")
    public void theEntryIsReturnedWithTheCorrectDate() {
        String entryDate = ctx.getLastResponse().jsonPath().getString("entryDate");
        assertThat(entryDate).isEqualTo(LocalDate.now().toString());
    }

    @Given("I have an entry for today")
    public void iHaveAnEntryForToday() {
        var resp = RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("entryDate", LocalDate.now().toString(), "content", "Today's entry"))
                .post(BASE);
        ctx.setLastEntryId(UUID.fromString(resp.jsonPath().getString("id")));
    }

    @When("I create another entry for today")
    public void iCreateAnotherEntryForToday() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("entryDate", LocalDate.now().toString(), "content", "Duplicate"))
                .post(BASE)
        );
    }

    @When("I create an entry for tomorrow")
    public void iCreateAnEntryForTomorrow() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("entryDate", LocalDate.now().plusDays(1).toString(), "content", "Future entry"))
                .post(BASE)
        );
    }

    @Given("another user has an entry")
    public void anotherUserHasAnEntry() {
        var resp = RestAssured.given()
                .auth().oauth2(ctx.getOtherUserToken())
                .contentType(ContentType.JSON)
                .body(Map.of("entryDate", LocalDate.now().toString(), "content", "Other user's entry"))
                .post(BASE);
        ctx.setOtherUserEntryId(UUID.fromString(resp.jsonPath().getString("id")));
    }

    @When("I request that entry")
    public void iRequestThatEntry() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .get(BASE + "/" + ctx.getOtherUserEntryId())
        );
    }

    @When("I update that entry")
    public void iUpdateThatEntry() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("content", "Hacked"))
                .put(BASE + "/" + ctx.getOtherUserEntryId())
        );
    }

    @When("I delete that entry")
    public void iDeleteThatEntry() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .delete(BASE + "/" + ctx.getLastEntryId())
        );
    }

    @And("the entry list is empty")
    public void theEntryListIsEmpty() {
        var resp = RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .get(BASE);
        assertThat(resp.jsonPath().getList("content")).isEmpty();
    }
}
