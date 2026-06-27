package com.bravoscribe.journalservice.api;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ExportSteps {

    @Autowired
    StepContext ctx;

    private static final String BASE = "/api/journal/entries";

    @Given("I have {int} entries this month")
    public void iHaveEntriesThisMonth(int count) {
        LocalDate today = LocalDate.now();
        for (int i = 0; i < count; i++) {
            RestAssured.given()
                    .auth().oauth2(ctx.getAccessToken())
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                        "entryDate", today.minusDays(i).toString(),
                        "content", "Entry " + i + " content for export test"))
                    .post(BASE);
        }
    }

    @When("I export entries for this month")
    public void iExportEntriesForThisMonth() {
        LocalDate today = LocalDate.now();
        String from = today.withDayOfMonth(1).toString();
        String to = today.toString();
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .queryParam("from", from)
                .queryParam("to", to)
                .get(BASE + "/export")
        );
    }

    @And("I receive a zip file")
    public void iReceiveAZipFile() {
        String contentType = ctx.getLastResponse().getContentType();
        assertThat(contentType).contains("application/zip");
    }

    @And("the zip contains a valid markdown file")
    public void theZipContainsAValidMarkdownFile() throws Exception {
        byte[] body = ctx.getLastResponse().asByteArray();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(body))) {
            var entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).endsWith(".md");
            byte[] content = zis.readAllBytes();
            assertThat(content.length).isGreaterThan(0);
        }
    }

    @And("the markdown contains no email or name")
    public void theMarkdownContainsNoEmailOrName() throws Exception {
        byte[] body = ctx.getLastResponse().asByteArray();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(body))) {
            zis.getNextEntry();
            String content = new String(zis.readAllBytes());
            assertThat(content).doesNotContain("@");
        }
    }

    @When("I export entries for a range with no entries")
    public void iExportEntriesForARangeWithNoEntries() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .queryParam("from", "2020-01-01")
                .queryParam("to", "2020-01-31")
                .get(BASE + "/export")
        );
    }

    @When("I export entries for a range exceeding 366 days")
    public void iExportEntriesForARangeExceeding366Days() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .queryParam("from", "2020-01-01")
                .queryParam("to", "2021-12-31")
                .get(BASE + "/export")
        );
    }
}
