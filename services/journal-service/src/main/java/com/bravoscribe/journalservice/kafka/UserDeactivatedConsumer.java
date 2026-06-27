package com.bravoscribe.journalservice.kafka;

import com.bravoscribe.journalservice.service.JournalService;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserDeactivatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserDeactivatedConsumer.class);

    private final JournalService journalService;
    private final ObjectMapper objectMapper;

    public UserDeactivatedConsumer(JournalService journalService, ObjectMapper objectMapper) {
        this.journalService = journalService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "users.user.deactivated", groupId = "journal-service")
    public void consume(String message) {
        try {
            var event = objectMapper.readValue(message, UserDeactivatedEvent.class);
            UUID userId = UUID.fromString(event.userId());
            journalService.softDeleteAllByUserId(userId);
        } catch (Exception e) {
            log.error("Failed to process users.user.deactivated event: {}", e.getMessage(), e);
        }
    }

    public record UserDeactivatedEvent(String eventId, String userId) {}
}
