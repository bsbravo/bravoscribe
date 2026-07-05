package com.bravoscribe.acceptance.steps;

import com.bravoscribe.acceptance.config.ServiceConfig;
import io.restassured.RestAssured;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.awaitility.Awaitility.await;

/**
 * Reads WireMock's request journal to recover what notification-service actually
 * sent to the stubbed SendGrid endpoint. EmailLog (MongoDB) only stores status/
 * subject/recipient — the raw password-reset token only ever appears in the
 * outbound email body, so this is the only way to observe it from outside.
 */
final class WireMockApi {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("#token=([A-Za-z0-9._\\-]+)");

    private WireMockApi() {}

    // 20s absorbs notification-service's Kafka consumer-group cold-start jitter on
    // the very first message of a freshly started Testcontainers broker.
    static String awaitEmailBodyContaining(String recipientEmail) {
        return await().atMost(Duration.ofSeconds(20)).until(
                () -> findRequestBody(recipientEmail),
                body -> body != null);
    }

    static String extractResetToken(String emailBody) {
        Matcher matcher = TOKEN_PATTERN.matcher(emailBody);
        if (!matcher.find()) {
            throw new IllegalStateException("No #token= fragment found in email body: " + emailBody);
        }
        return matcher.group(1);
    }

    // Two scenarios reuse the same literal email for password resets across feature
    // files, so the journal can contain more than one matching request — pick the
    // most recent by "loggedDate" explicitly rather than relying on journal order.
    @SuppressWarnings("unchecked")
    private static String findRequestBody(String recipientEmail) {
        List<Map<String, Object>> requests = RestAssured.given()
                .baseUri(ServiceConfig.wiremockAdminUrl())
                .get("/requests")
                .jsonPath()
                .getList("requests");

        String latestBody = null;
        long latestLoggedDate = Long.MIN_VALUE;
        for (Map<String, Object> entry : requests) {
            Map<String, Object> request = (Map<String, Object>) entry.get("request");
            Object body = request.get("body");
            if (body instanceof String text && text.contains(recipientEmail)) {
                long loggedDate = ((Number) entry.get("loggedDate")).longValue();
                if (loggedDate > latestLoggedDate) {
                    latestLoggedDate = loggedDate;
                    latestBody = text;
                }
            }
        }
        return latestBody;
    }
}
