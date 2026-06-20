package com.bravoscribe.userservice.api;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.RestAssured;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CucumberContextConfiguration
public class CucumberSpringConfiguration {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("bravoscribe")
            .withUsername("user_svc")
            .withPassword("user_svc_password")
            .withInitScript("db/test-schema.sql");

    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    static RSAKey testRsaKey;

    static {
        try {
            testRsaKey = new RSAKeyGenerator(2048).generate();
        } catch (Exception e) {
            throw new RuntimeException("Cannot generate test RSA key", e);
        }
        Startables.deepStart(postgres, kafka).join();
    }

    @LocalServerPort
    int port;

    @Before
    public void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/users";
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("jwt.private-key", () -> toPemPrivate(testRsaKey));
        registry.add("jwt.public-key", () -> toPemPublic(testRsaKey));
        registry.add("app.cookie.secure", () -> "false");
    }

    static String toPemPrivate(RSAKey key) {
        try {
            RSAPrivateKey privateKey = key.toRSAPrivateKey();
            String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
            return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
