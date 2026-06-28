package com.bravoscribe.notificationservice.repository;

import com.bravoscribe.notificationservice.document.EmailLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EmailLogRepository extends MongoRepository<EmailLog, String> {
    List<EmailLog> findByRecipientEmail(String email);
    List<EmailLog> findByStatus(String status);
}
