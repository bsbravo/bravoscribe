package com.bravoscribe.notificationservice.service;

import com.bravoscribe.notificationservice.document.EmailLog;
import com.bravoscribe.notificationservice.dto.JournalEntryCreatedEvent;
import com.bravoscribe.notificationservice.dto.JournalEntryUpdatedEvent;
import com.bravoscribe.notificationservice.dto.PasswordResetRequestedEvent;
import com.bravoscribe.notificationservice.dto.UserRegisteredEvent;
import com.bravoscribe.notificationservice.repository.EmailLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock SendGridGateway sendGridGateway;
    @Mock EmailLogRepository emailLogRepository;
    @Mock MeterRegistry meterRegistry;
    @Mock Counter counter;

    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
            redis, sendGridGateway, emailLogRepository, meterRegistry, "http://localhost:5173"
        );
        lenient().doReturn(false).when(redis).hasKey(anyString());
        lenient().doReturn(valueOps).when(redis).opsForValue();
        lenient().doReturn("msg-id-123").when(sendGridGateway).send(anyString(), anyString(), anyString());
        lenient().doReturn(counter).when(meterRegistry).counter(anyString());
    }

    @Test
    void processUserRegistered_sendsWelcomeEmail() {
        var event = new UserRegisteredEvent("evt-1", "user-1", "user@example.com");
        service.processUserRegistered(event);
        verify(sendGridGateway).send(eq("user@example.com"), anyString(), anyString());
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("sent");
        assertThat(captor.getValue().recipientEmail()).isEqualTo("user@example.com");
        assertThat(captor.getValue().topic()).isEqualTo("users.user.registered");
        assertThat(captor.getValue().sendGridMessageId()).isEqualTo("msg-id-123");
    }

    @Test
    void processUserRegistered_skipsDuplicateEvent() {
        doReturn(true).when(redis).hasKey(anyString());
        var event = new UserRegisteredEvent("evt-1", "user-1", "user@example.com");
        service.processUserRegistered(event);
        verifyNoInteractions(sendGridGateway, emailLogRepository);
    }

    @Test
    void processPasswordResetRequested_sendsEmailWithHashFragment() {
        var event = new PasswordResetRequestedEvent("evt-2", "user-2", "user@example.com", "raw-token-abc", "2026-07-01T00:00:00Z");
        service.processPasswordResetRequested(event);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(sendGridGateway).send(eq("user@example.com"), eq("Reset your Bravoscribe password"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("#token=raw-token-abc");
        assertThat(bodyCaptor.getValue()).doesNotContain("?token=");
        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().status()).isEqualTo("sent");
    }

    @Test
    void processPasswordResetRequested_skipsDuplicateEvent() {
        doReturn(true).when(redis).hasKey(anyString());
        var event = new PasswordResetRequestedEvent("evt-2", "user-2", "user@example.com", "raw-token-abc", "2026-07-01T00:00:00Z");
        service.processPasswordResetRequested(event);
        verifyNoInteractions(sendGridGateway, emailLogRepository);
    }

    @Test
    void processJournalEntryCreated_storesIdempotencyKeyWithoutSendingEmail() {
        var event = new JournalEntryCreatedEvent("evt-3", "entry-1", "user-3", "2026-06-28");
        service.processJournalEntryCreated(event);
        verify(valueOps).set(argThat(k -> k.contains("journal.entry.created:evt-3")), eq("1"), any(Duration.class));
        verifyNoInteractions(sendGridGateway, emailLogRepository);
    }

    @Test
    void processJournalEntryCreated_skipsDuplicateEvent() {
        doReturn(true).when(redis).hasKey(anyString());
        var event = new JournalEntryCreatedEvent("evt-3", "entry-1", "user-3", "2026-06-28");
        service.processJournalEntryCreated(event);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(sendGridGateway, emailLogRepository);
    }

    @Test
    void processJournalEntryUpdated_storesIdempotencyKeyWithoutSendingEmail() {
        var event = new JournalEntryUpdatedEvent("evt-4", "entry-1", "user-4");
        service.processJournalEntryUpdated(event);
        verify(valueOps).set(argThat(k -> k.contains("journal.entry.updated:evt-4")), eq("1"), any(Duration.class));
        verifyNoInteractions(sendGridGateway, emailLogRepository);
    }

    @Test
    void sendEmail_onSendGridFailure_logsFailedEmailLog() {
        doThrow(new RuntimeException("SendGrid timeout")).when(sendGridGateway).send(anyString(), anyString(), anyString());
        var event = new UserRegisteredEvent("evt-5", "user-5", "user@example.com");
        service.processUserRegistered(event);
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("failed");
        assertThat(captor.getValue().errorMessage()).isEqualTo("SendGrid timeout");
        assertThat(captor.getValue().retryCount()).isEqualTo(0);
    }

    @Test
    void isAlreadyProcessed_returnsTrueWhenRedisKeyExists() {
        doReturn(true).when(redis).hasKey("notify:processed:users.user.registered:evt-1");
        assertThat(service.isAlreadyProcessed("users.user.registered", "evt-1")).isTrue();
    }

    @Test
    void isAlreadyProcessed_returnsFalseWhenRedisKeyAbsent() {
        assertThat(service.isAlreadyProcessed("users.user.registered", "evt-1")).isFalse();
    }
}
