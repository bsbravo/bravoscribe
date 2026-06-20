package com.bravoscribe.userservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class UserEventProducer {

    private static final Logger log = LoggerFactory.getLogger(UserEventProducer.class);

    private static final String TOPIC_REGISTERED = "users.user.registered";
    private static final String TOPIC_DEACTIVATED = "users.user.deactivated";
    private static final String TOPIC_PASSWORD_RESET = "users.password.reset.requested";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishUserRegistered(UUID userId, String email) {
        var event = new UserRegisteredEvent(UUID.randomUUID().toString(), userId.toString(), email);
        kafkaTemplate.send(TOPIC_REGISTERED, userId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish user.registered event for userId={}", userId, ex);
                    } else {
                        log.info("Published user.registered event: userId={}", userId);
                    }
                });
    }

    public void publishUserDeactivated(UUID userId) {
        var event = new UserDeactivatedEvent(UUID.randomUUID().toString(), userId.toString());
        kafkaTemplate.send(TOPIC_DEACTIVATED, userId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish user.deactivated event for userId={}", userId, ex);
                    }
                });
    }

    public void publishPasswordResetRequested(UUID userId, String email, String rawResetToken, Instant expiresAt) {
        var event = new PasswordResetRequestedEvent(
                UUID.randomUUID().toString(), userId.toString(), email, rawResetToken, expiresAt.toString());
        kafkaTemplate.send(TOPIC_PASSWORD_RESET, userId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish password.reset.requested event for userId={}", userId, ex);
                    }
                });
    }

    public record UserRegisteredEvent(String eventId, String userId, String email) {}
    public record UserDeactivatedEvent(String eventId, String userId) {}
    public record PasswordResetRequestedEvent(String eventId, String userId, String email,
                                              String resetToken, String expiresAt) {}
}
