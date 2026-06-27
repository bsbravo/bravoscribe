package com.bravoscribe.journalservice.api;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TagSteps {

    @Autowired
    StepContext ctx;

    private static final String ENTRY_BASE = "/api/journal/entries";
    private static final String TAG_BASE = "/api/journal/tags";

    @When("I create a tag named {string}")
    public void iCreateATagNamed(String name) {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("name", name))
                .post(TAG_BASE)
        );
        if (ctx.getLastResponse().getStatusCode() == 201) {
            ctx.setLastTagId(UUID.fromString(ctx.getLastResponse().jsonPath().getString("id")));
        }
    }

    @When("I create a tag with a name of 51 characters")
    public void iCreateATagWithANameOf51Characters() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("name", "a".repeat(51)))
                .post(TAG_BASE)
        );
    }

    @Given("I have created tags {string} and {string}")
    public void iHaveCreatedTagsAnd(String name1, String name2) {
        RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("name", name1))
                .post(TAG_BASE);
        RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("name", name2))
                .post(TAG_BASE);
    }

    @When("I list tags")
    public void iListTags() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .get(TAG_BASE)
        );
    }

    @And("the tags are {string} and {string} in alphabetical order")
    public void theTagsAreAndInAlphabeticalOrder(String first, String second) {
        List<String> names = ctx.getLastResponse().jsonPath().getList("name");
        assertThat(names).containsExactly(first, second);
    }

    @Given("I have a tag {string} assigned to an entry")
    public void iHaveATagAssignedToAnEntry(String tagName) {
        var tagResp = RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("name", tagName))
                .post(TAG_BASE);
        UUID tagId = UUID.fromString(tagResp.jsonPath().getString("id"));
        ctx.setLastTagId(tagId);

        var entryResp = RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of(
                    "entryDate", LocalDate.now().toString(),
                    "content", "Entry with tag",
                    "tagIds", List.of(tagId.toString())))
                .post(ENTRY_BASE);
        ctx.setLastEntryId(UUID.fromString(entryResp.jsonPath().getString("id")));
    }

    @When("I delete that tag")
    public void iDeleteThatTag() {
        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .delete(TAG_BASE + "/" + ctx.getLastTagId())
        );
    }

    @And("the entry no longer shows the {string} tag")
    public void theEntryNoLongerShowsTheTag(String tagName) {
        var resp = RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .get(ENTRY_BASE + "/" + ctx.getLastEntryId());
        List<String> tagNames = resp.jsonPath().getList("tags.name");
        assertThat(tagNames).doesNotContain(tagName);
    }

    @Given("I have {int} tags")
    public void iHaveTags(int count) {
        for (int i = 0; i < count; i++) {
            RestAssured.given()
                    .auth().oauth2(ctx.getAccessToken())
                    .contentType(ContentType.JSON)
                    .body(Map.of("name", "tag" + i))
                    .post(TAG_BASE);
        }
    }

    @When("I create an entry with all {int} tags plus one more")
    public void iCreateAnEntryWithAllTagsPlusOneMore(int count) {
        List<String> existing = RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .get(TAG_BASE)
                .jsonPath()
                .getList("id");

        var extra = RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("name", "extra"))
                .post(TAG_BASE);

        List<String> allIds = new ArrayList<>(existing);
        allIds.add(extra.jsonPath().getString("id"));

        ctx.setLastResponse(
            RestAssured.given()
                .auth().oauth2(ctx.getAccessToken())
                .contentType(ContentType.JSON)
                .body(Map.of(
                    "entryDate", LocalDate.now().toString(),
                    "content", "Too many tags",
                    "tagIds", allIds))
                .post(ENTRY_BASE)
        );
    }
}
