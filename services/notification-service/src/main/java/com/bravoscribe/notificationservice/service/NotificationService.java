package com.bravoscribe.notificationservice.service;

import com.bravoscribe.notificationservice.document.EmailLog;
import com.bravoscribe.notificationservice.dto.JournalEntryCreatedEvent;
import com.bravoscribe.notificationservice.dto.JournalEntryUpdatedEvent;
import com.bravoscribe.notificationservice.dto.PasswordResetRequestedEvent;
import com.bravoscribe.notificationservice.dto.UserRegisteredEvent;
import com.bravoscribe.notificationservice.repository.EmailLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String TOPIC_REGISTERED = "users.user.registered";
    private static final String TOPIC_PASSWORD_RESET = "users.password.reset.requested";
    private static final String TOPIC_ENTRY_CREATED = "journal.entry.created";
    private static final String TOPIC_ENTRY_UPDATED = "journal.entry.updated";

    private final StringRedisTemplate redis;
    private final SendGridGateway sendGridGateway;
    private final EmailLogRepository emailLogRepository;
    private final MeterRegistry meterRegistry;
    private final String frontendUrl;

    public NotificationService(
            StringRedisTemplate redis,
            SendGridGateway sendGridGateway,
            EmailLogRepository emailLogRepository,
            MeterRegistry meterRegistry,
            @Value("${notification.frontend-url}") String frontendUrl) {
        this.redis = redis;
        this.sendGridGateway = sendGridGateway;
        this.emailLogRepository = emailLogRepository;
        this.meterRegistry = meterRegistry;
        this.frontendUrl = frontendUrl;
    }

    public void processUserRegistered(UserRegisteredEvent event) {
        if (isAlreadyProcessed(TOPIC_REGISTERED, event.eventId())) {
            log.warn("Duplicate event skipped — topic: {} eventId: {}", TOPIC_REGISTERED, event.eventId());
            return;
        }
        String subject = "Welcome to Bravoscribe";
        String body = "Welcome! Your account is ready. Start writing your first journal entry today.";
        sendEmail(TOPIC_REGISTERED, event.eventId(), event.userId(), event.email(), subject, body);
    }

    public void processPasswordResetRequested(PasswordResetRequestedEvent event) {
        if (isAlreadyProcessed(TOPIC_PASSWORD_RESET, event.eventId())) {
            log.warn("Duplicate event skipped — topic: {} eventId: {}", TOPIC_PASSWORD_RESET, event.eventId());
            return;
        }
        // Hash fragment (#token=) — never sent to server, never appears in APIM or Application Insights logs
        String resetLink = frontendUrl + "/reset-password#token=" + event.resetToken();
        String subject = "Reset your Bravoscribe password";
        String body = """
                Someone requested a password reset for your account.
                Click the link below to set a new password.
                This link expires in 15 minutes.

                %s

                If you didn't request this, ignore this email.
                Your password will not change.""".formatted(resetLink);
        sendEmail(TOPIC_PASSWORD_RESET, event.eventId(), event.userId(), event.email(), subject, body);
    }

    // journal.entry.created payload has no email (journal-service stores no user emails).
    // To send a confirmation email we need either: (a) add email to the event payload in
    // journal-service, or (b) call an internal GET /api/users/{id}/email endpoint on user-service.
    // Decision deferred — see PLAN.md §"Open decisions".
    public void processJournalEntryCreated(JournalEntryCreatedEvent event) {
        if (isAlreadyProcessed(TOPIC_ENTRY_CREATED, event.eventId())) {
            log.warn("Duplicate event skipped — topic: {} eventId: {}", TOPIC_ENTRY_CREATED, event.eventId());
            return;
        }
        markProcessed(TOPIC_ENTRY_CREATED, event.eventId());
        log.info("Entry created event received — userId: {} entryId: {} (email pending open decision)",
            event.userId(), event.entryId());
    }

    public void processJournalEntryUpdated(JournalEntryUpdatedEvent event) {
        if (isAlreadyProcessed(TOPIC_ENTRY_UPDATED, event.eventId())) {
            log.warn("Duplicate event skipped — topic: {} eventId: {}", TOPIC_ENTRY_UPDATED, event.eventId());
            return;
        }
        markProcessed(TOPIC_ENTRY_UPDATED, event.eventId());
    }

    private void sendEmail(String topic, String eventId, String userId, String to,
                           String subject, String body) {
        String sendGridMessageId = null;
        String errorMessage = null;
        String status;

        try {
            sendGridMessageId = sendGridGateway.send(to, subject, body);
            status = "sent";
            markProcessed(topic, eventId);
            log.info("Email sent — event: {} userId: {}", topic, userId);
            meterRegistry.counter("notifications.sent").increment();
        } catch (Exception e) {
            status = "failed";
            errorMessage = e.getMessage();
            log.error("Email delivery failed — userId: {} provider: sendgrid — {}", userId, e.getMessage());
            meterRegistry.counter("notifications.failed").increment();
        }

        emailLogRepository.save(new EmailLog(
            null, eventId, topic, to, subject,
            Instant.now(), status, sendGridMessageId, 0, errorMessage
        ));
    }

    boolean isAlreadyProcessed(String topic, String eventId) {
        return Boolean.TRUE.equals(redis.hasKey(idempotencyKey(topic, eventId)));
    }

    void markProcessed(String topic, String eventId) {
        redis.opsForValue().set(idempotencyKey(topic, eventId), "1", Duration.ofDays(7));
    }

    private String idempotencyKey(String topic, String eventId) {
        return "notify:processed:" + topic + ":" + eventId;
    }
}
