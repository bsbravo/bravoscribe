package com.bravoscribe.acceptance.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Starts the full Bravoscribe stack for Level 3 acceptance tests. Unlike the
 * per-service Level 2 Cucumber suites, no Spring context runs in this module —
 * User Service, Journal Service, and Notification Service run as the same
 * black-box Docker images CI builds, and step definitions talk to them over
 * real HTTP, matching production topology as closely as Testcontainers allows.
 */
public final class ServiceConfig {

    private static final RSAKey RSA_KEY = generateRsaKey();

    public static final Network NETWORK = Network.newNetwork();

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withDatabaseName("journal")
            .withUsername("postgres")
            .withPassword("postgres")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/init-schemas.sql"),
                    "/docker-entrypoint-initdb.d/init.sql");

    public static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.9.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka");

    @SuppressWarnings("resource")
    public static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withNetwork(NETWORK)
            .withNetworkAliases("redis")
            .withExposedPorts(6379)
            // Phase 4 lesson L3 — default wait strategy races on Redis startup
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    public static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"))
            .withNetwork(NETWORK)
            .withNetworkAliases("mongo");

    @SuppressWarnings("resource")
    public static final GenericContainer<?> WIREMOCK = new GenericContainer<>("wiremock/wiremock:3.9.1")
            .withNetwork(NETWORK)
            .withNetworkAliases("wiremock")
            .withExposedPorts(8080)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("wiremock/mappings/sendgrid-send.json"),
                    "/home/wiremock/mappings/sendgrid-send.json")
            // Spring's RestClient (JDK HttpClient) negotiating HTTP/2 cleartext against WireMock's
            // Jetty server causes RST_STREAM/EOF errors under concurrent requests — force HTTP/1.1.
            .withCommand("--disable-http2-plain", "--disable-http2-tls")
            .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    public static final GenericContainer<?> USER_SERVICE = new GenericContainer<>("bravoscribe/user-service:test")
            .withNetwork(NETWORK)
            .withNetworkAliases("user-service")
            .withExposedPorts(8081)
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/journal?currentSchema=users")
            .withEnv("SPRING_DATASOURCE_USERNAME", "user_svc")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "user_svc_password")
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
            .withEnv("JWT_PRIVATE_KEY", toPemPrivate(RSA_KEY))
            .withEnv("JWT_PUBLIC_KEY", toPemPublic(RSA_KEY))
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    public static final GenericContainer<?> JOURNAL_SERVICE = new GenericContainer<>("bravoscribe/journal-service:test")
            .withNetwork(NETWORK)
            .withNetworkAliases("journal-service")
            .withExposedPorts(8082)
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/journal?currentSchema=journal")
            .withEnv("SPRING_DATASOURCE_USERNAME", "journal_svc")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "journal_svc_password")
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
            .withEnv("REDIS_URL", "redis://redis:6379")
            .withEnv("JWT_PUBLIC_KEY", toPemPublic(RSA_KEY))
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    public static final GenericContainer<?> NOTIFICATION_SERVICE = new GenericContainer<>("bravoscribe/notification-service:test")
            .withNetwork(NETWORK)
            .withNetworkAliases("notification-service")
            .withExposedPorts(8083)
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
            .withEnv("REDIS_URL", "redis://redis:6379")
            .withEnv("MONGODB_URI", "mongodb://mongo:27017/bravoscribe")
            // D2 — redirect SendGrid calls to the WireMock stub instead of the real API
            .withEnv("NOTIFICATION_SENDGRID_BASE_URL", "http://wiremock:8080")
            .withEnv("SENDGRID_API_KEY", "test-key")
            .withEnv("FROM_EMAIL", "noreply@bravoscribe.com")
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    static {
        Startables.deepStart(POSTGRES, KAFKA, REDIS, MONGO, WIREMOCK).join();
        Startables.deepStart(USER_SERVICE, JOURNAL_SERVICE, NOTIFICATION_SERVICE).join();
    }

    private ServiceConfig() {}

    public static String userServiceOrigin() {
        return "http://" + USER_SERVICE.getHost() + ":" + USER_SERVICE.getMappedPort(8081);
    }

    public static String journalServiceOrigin() {
        return "http://" + JOURNAL_SERVICE.getHost() + ":" + JOURNAL_SERVICE.getMappedPort(8082);
    }

    public static String userServiceBaseUrl() {
        return userServiceOrigin() + "/api/users";
    }

    public static String journalServiceBaseUrl() {
        return journalServiceOrigin() + "/api/journal";
    }

    /** Routes a raw path like "/api/journal/entries" or "/api/users/me" to the owning service's origin. */
    public static String originFor(String path) {
        if (path.startsWith("/api/journal")) {
            return journalServiceOrigin();
        }
        if (path.startsWith("/api/users")) {
            return userServiceOrigin();
        }
        throw new IllegalArgumentException("Unknown route: " + path);
    }

    public static String userServiceHealthUrl() {
        return "http://" + USER_SERVICE.getHost() + ":" + USER_SERVICE.getMappedPort(8081) + "/actuator/health";
    }

    public static String journalServiceHealthUrl() {
        return "http://" + JOURNAL_SERVICE.getHost() + ":" + JOURNAL_SERVICE.getMappedPort(8082) + "/actuator/health";
    }

    public static String notificationServiceHealthUrl() {
        return "http://" + NOTIFICATION_SERVICE.getHost() + ":" + NOTIFICATION_SERVICE.getMappedPort(8083) + "/actuator/health";
    }

    public static String postgresJdbcUrl() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/journal?currentSchema=journal";
    }

    public static String mongoConnectionString() {
        return "mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017) + "/bravoscribe";
    }

    public static String wiremockAdminUrl() {
        return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080) + "/__admin";
    }

    /**
     * Mints a signed ADMIN-role access token directly, bypassing user-service.
     * There is no self-service admin-creation endpoint — AdminController only
     * checks the JWT's "role" claim (no DB lookup of the caller), so a token
     * signed with the same shared key is indistinguishable from a real one.
     */
    public static String mintAdminAccessToken() {
        Instant now = Instant.now();
        return mintToken(UUID.randomUUID().toString(), "ADMIN", now, now.plusSeconds(900));
    }

    /** Signs an arbitrary claim set with the shared test key — used to mint an already-expired token. */
    public static String mintExpiredAccessToken(UUID userId) {
        Instant past = Instant.now().minusSeconds(3600);
        return mintToken(userId.toString(), "USER", past.minusSeconds(900), past);
    }

    private static String mintToken(String subject, String role, Instant issueTime, Instant expirationTime) {
        try {
            RSAPrivateKey privateKey = RSA_KEY.toRSAPrivateKey();
            JWSSigner signer = new RSASSASigner(privateKey);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .claim("role", role)
                    .issueTime(Date.from(issueTime))
                    .expirationTime(Date.from(expirationTime))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Cannot mint JWT", e);
        }
    }

    private static RSAKey generateRsaKey() {
        try {
            return new RSAKeyGenerator(2048).generate();
        } catch (Exception e) {
            throw new RuntimeException("Cannot generate shared test RSA key", e);
        }
    }

    private static String toPemPrivate(RSAKey key) {
        try {
            RSAPrivateKey privateKey = key.toRSAPrivateKey();
            String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
            return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toPemPublic(RSAKey key) {
        try {
            RSAPublicKey publicKey = key.toRSAPublicKey();
            String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
