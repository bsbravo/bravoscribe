package com.bravoscribe.notificationservice.service;

@FunctionalInterface
public interface SendGridGateway {
    // Returns the SendGrid X-Message-Id header value, or null if not provided
    String send(String recipientEmail, String subject, String body);
}
