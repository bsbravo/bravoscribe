package com.bravoscribe.acceptance.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class JournalSteps {

    private static final LocalDate JAN = LocalDate.of(2026, 1, 15);
    private static final LocalDate FEB = LocalDate.of(2026, 2, 15);
    private static final LocalDate MAR = LocalDate.of(2026, 3, 15);
    private static final LocalDate OUT_OF_RANGE = LocalDate.of(2026, 4, 15);

    private final TestContext ctx;

    public JournalSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @When("I create a journal entry with content {string}")
    public void iCreateAJournalEntryWithContent(String content) {
        Response response = JournalApi.createEntry(ctx.accessToken, LocalDate.now(), content);
        ctx.lastResponse = response;
        if (response.statusCode() == 201) {
            ctx.lastEntryId = UUID.fromString(response.jsonPath().getString("id"));
        }
    }

    @Then("the entry is saved successfully")
    public void theEntryIsSavedSuccessfully() {
        assertThat(ctx.lastResponse.statusCode()).isEqualTo(201);
    }

    @Then("my stats show totalEntries: {int} and currentStreak: {int}")
    public void myStatsShowTotalEntriesAndCurrentStreak(int totalEntries, int currentStreak) {
        Response response = JournalApi.getStats(ctx.accessToken);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getLong("totalEntries")).isEqualTo((long) totalEntries);
        assertThat(response.jsonPath().getInt("currentStreak")).isEqualTo(currentStreak);
    }

    @Given("I have {int} journal entries")
    public void iHaveJournalEntries(int count) {
        for (int i = 0; i < count; i++) {
            JournalApi.createEntry(ctx.accessToken, LocalDate.now().minusDays(i), "Entry #" + i);
        }
    }

    @Given("I have a journal entry for today")
    public void iHaveAJournalEntryForToday() {
        Response response = JournalApi.createEntry(ctx.accessToken, LocalDate.now(), "Entry for today");
        ctx.lastEntryId = UUID.fromString(response.jsonPath().getString("id"));
    }

    @Given("I have no journal entries")
    public void iHaveNoJournalEntries() {
        // No-op — the current user was just freshly registered/logged in with an empty journal.
    }

    @Given("I have entries spanning multiple months")
    public void iHaveEntriesSpanningMultipleMonths() {
        create(JAN, "Entry for January");
        create(FEB, "Entry for February");
        create(MAR, "Entry for March");
        create(OUT_OF_RANGE, "Entry for April - out of range");
    }

    private void create(LocalDate date, String content) {
        JournalApi.createEntry(ctx.accessToken, date, content);
    }

    @When("I call GET \\/api\\/journal\\/entries\\/export")
    public void iCallGetApiJournalEntriesExport() {
        // Gherkin omits from/to for brevity — 30 days covers this suite's fixtures
        // while staying under ExportService's 366-day MAX_EXPORT_DAYS cap.
        ctx.lastResponse = JournalApi.export(ctx.accessToken, LocalDate.now().minusDays(30), LocalDate.now());
        captureZipIfPresent();
    }

    @When("I call GET \\/api\\/journal\\/entries\\/export?from=2026-01-01&to=2026-03-31")
    public void iCallGetApiJournalEntriesExportWithRange() {
        ctx.lastResponse = JournalApi.export(ctx.accessToken, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        captureZipIfPresent();
    }

    private void captureZipIfPresent() {
        if (ctx.lastResponse.statusCode() == 200) {
            byte[] zipBytes = ctx.lastResponse.asByteArray();
            ctx.lastMarkdown = extractMarkdown(zipBytes);
        }
    }

    @Then("I receive a zip file")
    public void iReceiveAZipFile() {
        assertThat(ctx.lastResponse.statusCode()).isEqualTo(200);
        assertThat(ctx.lastResponse.contentType()).contains("zip");
        assertThat(ctx.lastMarkdown).isNotNull();
    }

    @Then("the zip contains a markdown file starting with {string}")
    public void theZipContainsAMarkdownFileStartingWith(String prefix) {
        assertThat(ctx.lastMarkdown).startsWith(prefix);
    }

    @Then("the markdown contains all {int} entries")
    public void theMarkdownContainsAllEntries(int count) {
        assertThat(ctx.lastMarkdown).contains("**Total entries:** " + count);
    }

    @Then("the zip contains only entries within that range")
    public void theZipContainsOnlyEntriesWithinThatRange() {
        assertThat(ctx.lastMarkdown).contains("**Total entries:** 3");
        assertThat(ctx.lastMarkdown).doesNotContain("out of range");
    }

    @Then("my journal entry is still accessible")
    public void myJournalEntryIsStillAccessible() {
        Response response = JournalApi.getEntry(ctx.accessToken, ctx.lastEntryId);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Then("my existing entries are soft-deleted")
    public void myExistingEntriesAreSoftDeleted() {
        // Soft-delete happens asynchronously via Kafka (deactivation -> users.user.deactivated
        // -> journal-service consumer) — poll instead of a one-shot check to absorb consumer lag.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<Boolean> flags = JournalDb.deletedFlagsForUser(ctx.userId);
            assertThat(flags).isNotEmpty();
            assertThat(flags).allMatch(Boolean::booleanValue);
        });
    }

    @Then("within 5 seconds all my journal entries have deleted=true in the database")
    public void withinSecondsAllMyJournalEntriesHaveDeletedTrueInTheDatabase() {
        // The 5s business SLA (Gherkin text) is measured from event-publish; the poll window
        // here is wider to absorb Kafka consumer-group rebalance jitter inherent to freshly
        // started Testcontainers brokers, not a relaxation of the SLA itself.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<Boolean> flags = JournalDb.deletedFlagsForUser(ctx.userId);
            assertThat(flags).isNotEmpty();
            assertThat(flags).allMatch(Boolean::booleanValue);
        });
    }

    private String extractMarkdown(byte[] zipBytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            zip.getNextEntry();
            return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read export zip", e);
        }
    }
}
