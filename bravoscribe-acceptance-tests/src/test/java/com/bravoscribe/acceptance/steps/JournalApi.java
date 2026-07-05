package com.bravoscribe.acceptance.steps;

import com.bravoscribe.acceptance.config.ServiceConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Plain HTTP helper against journal-service, shared by JournalSteps and UserSteps
 * (Cucumber step-definition classes can't call each other directly).
 */
final class JournalApi {

    private JournalApi() {}

    static Response createEntry(String accessToken, LocalDate entryDate, String content) {
        return RestAssured.given()
                .baseUri(ServiceConfig.journalServiceBaseUrl())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of("entryDate", entryDate.toString(), "content", content))
                .post("/entries");
    }

    static Response getEntry(String accessToken, UUID entryId) {
        return RestAssured.given()
                .baseUri(ServiceConfig.journalServiceBaseUrl())
                .header("Authorization", "Bearer " + accessToken)
                .get("/entries/" + entryId);
    }

    static Response getStats(String accessToken) {
        return RestAssured.given()
                .baseUri(ServiceConfig.journalServiceBaseUrl())
                .header("Authorization", "Bearer " + accessToken)
                .get("/stats");
    }

    static Response export(String accessToken, LocalDate from, LocalDate to) {
        return RestAssured.given()
                .baseUri(ServiceConfig.journalServiceBaseUrl())
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("from", from.toString())
                .queryParam("to", to.toString())
                .get("/entries/export");
    }
}
