package com.bravoscribe.journalservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class JournalEventProducer {

    private static final Logger log = LoggerFactory.getLogger(JournalEventProducer.class);

    private static final String TOPIC_ENTRY_CREATED = "journal.entry.created";
    private static final String TOPIC_ENTRY_UPDATED = "journal.entry.updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public JournalEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishEntryCreated(UUID entryId, UUID userId, LocalDate entryDate) {
        var event = new EntryCreatedEvent(UUID.randomUUID().toString(),
                entryId.toString(), userId.toString(), entryDate.toString());
        kafkaTemplate.send(TOPIC_ENTRY_CREATED, entryId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish entry.created event for entryId={}", entryId, ex);
                    }
                });
    }

    public void publishEntryUpdated(UUID entryId, UUID userId) {
        var event = new EntryUpdatedEvent(UUID.randomUUID().toString(),
                entryId.toString(), userId.toString());
        kafkaTemplate.send(TOPIC_ENTRY_UPDATED, entryId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish entry.updated event for entryId={}", entryId, ex);
                    }
                });
    }

    public record EntryCreatedEvent(String eventId, String entryId, String userId, String entryDate) {}
    public record EntryUpdatedEvent(String eventId, String entryId, String userId) {}
}
