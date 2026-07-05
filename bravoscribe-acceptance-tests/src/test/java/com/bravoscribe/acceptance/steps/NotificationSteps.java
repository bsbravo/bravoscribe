package com.bravoscribe.acceptance.steps;

import com.bravoscribe.acceptance.config.ServiceConfig;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import io.cucumber.java.en.Then;
import org.bson.Document;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class NotificationSteps {

    private final TestContext ctx;

    public NotificationSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @Then("the notification service sends a welcome email to {string}")
    public void theNotificationServiceSendsAWelcomeEmailTo(String email) {
        Document log = awaitEmailLog(email);
        assertThat(log.getString("status")).isEqualTo("sent");
    }

    @Then("a reset email is sent containing a {string} hash fragment link")
    public void aResetEmailIsSentContainingAHashFragmentLink(String fragment) {
        String body = WireMockApi.awaitEmailBodyContaining(ctx.currentEmail);
        assertThat(body).contains(fragment);
        ctx.resetToken = WireMockApi.extractResetToken(body);
    }

    // The same literal email can be registered more than once across the suite,
    // logging more than one welcome-email document — sort explicitly instead of
    // relying on MongoDB's implicit natural/insertion order to pick the latest.
    // 20s (not 10s) absorbs notification-service's Kafka consumer-group cold-start
    // jitter on the very first message of a freshly started Testcontainers broker.
    private Document awaitEmailLog(String recipientEmail) {
        try (MongoClient client = MongoClients.create(ServiceConfig.mongoConnectionString())) {
            MongoCollection<Document> collection = client.getDatabase("bravoscribe").getCollection("email_logs");
            return await().atMost(Duration.ofSeconds(20)).until(
                    () -> {
                        FindIterable<Document> found = collection.find(Filters.eq("recipientEmail", recipientEmail))
                                .sort(Sorts.descending("sentAt"));
                        return found.first();
                    },
                    doc -> doc != null);
        }
    }
}
