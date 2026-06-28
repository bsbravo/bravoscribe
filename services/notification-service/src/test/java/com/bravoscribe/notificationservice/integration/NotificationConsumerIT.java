package com.bravoscribe.notificationservice.integration;

import com.bravoscribe.notificationservice.repository.EmailLogRepository;
import com.bravoscribe.notificationservice.service.SendGridGateway;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class NotificationConsumerIT {

    @TestConfiguration
    static class TestKafkaProducerConfig {
        @Bean
        KafkaTemplate<String, String> kafkaTemplate(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
            )));
        }
    }

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379)
        .waitingFor(org.testcontainers.containers.wait.strategy.Wait
            .forLogMessage(".*Ready to accept connections.*\\n", 1));

    @Container
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("spring.data.redis.url", () -> "redis://localhost:" + redis.getMappedPort(6379));
        // Spring Boot 4.1: MongoClient comes from spring-boot-mongodb which reads spring.mongodb.uri.
        // DataMongoAutoConfiguration reuses that MongoClient, so spring.data.mongodb.uri is not needed.
        r.add("spring.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @MockitoBean
    SendGridGateway sendGridGateway;

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired EmailLogRepository emailLogRepository;
    @Autowired StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        emailLogRepository.deleteAll();

        Set<String> keys = stringRedisTemplate.keys("notify:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }

        doReturn("sendgrid-msg-id").when(sendGridGateway).send(anyString(), anyString(), anyString());
    }

    @Test
    void userRegisteredEvent_triggersWelcomeEmailAndSavesLog() {
        kafkaTemplate.send("users.user.registered",
            """
            {"eventId":"it-evt-1","userId":"it-user-1","email":"it@example.com"}""");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var logs = emailLogRepository.findByRecipientEmail("it@example.com");
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().status()).isEqualTo("sent");
            assertThat(logs.getFirst().topic()).isEqualTo("users.user.registered");
            assertThat(logs.getFirst().sendGridMessageId()).isEqualTo("sendgrid-msg-id");
        });
        assertThat(stringRedisTemplate.hasKey("notify:processed:users.user.registered:it-evt-1")).isTrue();
    }

    @Test
    void userRegisteredEvent_duplicateSkipped() throws Exception {
        String payload = """
            {"eventId":"it-evt-dup","userId":"it-user-dup","email":"dup@example.com"}""";
        kafkaTemplate.send("users.user.registered", payload);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
            assertThat(emailLogRepository.findByRecipientEmail("dup@example.com")).hasSize(1));

        kafkaTemplate.send("users.user.registered", payload);
        TimeUnit.SECONDS.sleep(3);

        assertThat(emailLogRepository.findByRecipientEmail("dup@example.com")).hasSize(1);
        verify(sendGridGateway, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void passwordResetEvent_triggersEmailWithHashFragment() {
        kafkaTemplate.send("users.password.reset.requested",
            """
            {"eventId":"it-evt-2","userId":"it-user-2","email":"reset@example.com","resetToken":"tok-xyz","expiresAt":"2026-07-01T00:00:00Z"}""");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var logs = emailLogRepository.findByRecipientEmail("reset@example.com");
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().status()).isEqualTo("sent");
        });

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(sendGridGateway).send(eq("reset@example.com"), eq("Reset your Bravoscribe password"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("#token=tok-xyz");
        assertThat(bodyCaptor.getValue()).doesNotContain("?token=");
    }

    @Test
    void journalEntryCreatedEvent_storesIdempotencyKeyWithoutEmail() {
        kafkaTemplate.send("journal.entry.created",
            """
            {"eventId":"it-evt-3","entryId":"entry-1","userId":"it-user-3","entryDate":"2026-06-28"}""");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
            assertThat(stringRedisTemplate.hasKey("notify:processed:journal.entry.created:it-evt-3")).isTrue()
        );
        assertThat(emailLogRepository.findAll()).isEmpty();
        verifyNoInteractions(sendGridGateway);
    }

    @Test
    void journalEntryUpdatedEvent_storesIdempotencyKeyWithoutEmail() {
        kafkaTemplate.send("journal.entry.updated",
            """
            {"eventId":"it-evt-4","entryId":"entry-2","userId":"it-user-4"}""");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
            assertThat(stringRedisTemplate.hasKey("notify:processed:journal.entry.updated:it-evt-4")).isTrue()
        );
        assertThat(emailLogRepository.findAll()).isEmpty();
        verifyNoInteractions(sendGridGateway);
    }
}
