package com.bravoscribe.notificationservice.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "email_logs")
public record EmailLog(
    @Id String id,
    String eventId,
    String topic,
    String recipientEmail,
    String subject,
    Instant sentAt,
    String status,
    String sendGridMessageId,
    int retryCount,
    String errorMessage
) {}
