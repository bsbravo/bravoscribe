package com.bravoscribe.journalservice.api;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.RestAssured;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CucumberContextConfiguration
public class CucumberSpringConfiguration {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("bravoscribe")
            .withUsername("journal_svc")
            .withPassword("journal_svc_password")
            .withInitScript("db/test-schema.sql");

    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static RSAKey RSA_KEY;

    static {
        try {
            RSA_KEY = new RSAKeyGenerator(2048).generate();
        } catch (Exception e) {
            throw new RuntimeException("Cannot generate test RSA key", e);
        }
        Startables.deepStart(postgres, kafka, redis).join();
    }

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.url",
                () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        registry.add("jwt.public-key", () -> toPemPublic(RSA_KEY));
    }

    static String toPemPublic(RSAKey key) {
        try {
            RSAPublicKey publicKey = key.toRSAPublicKey();
            String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
