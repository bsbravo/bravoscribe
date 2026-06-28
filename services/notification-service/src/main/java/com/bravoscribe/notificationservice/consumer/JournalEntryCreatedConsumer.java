package com.bravoscribe.notificationservice.consumer;

import com.bravoscribe.notificationservice.dto.JournalEntryCreatedEvent;
import com.bravoscribe.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class JournalEntryCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(JournalEntryCreatedConsumer.class);
    private static final String TOPIC = "journal.entry.created";

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public JournalEntryCreatedConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "notification-service")
    public void consume(String message) {
        try {
            var event = objectMapper.readValue(message, JournalEntryCreatedEvent.class);
            notificationService.processJournalEntryCreated(event);
        } catch (Exception e) {
            log.error("Kafka consumer error — topic: {} — {}", TOPIC, e.getMessage());
        }
    }
}
