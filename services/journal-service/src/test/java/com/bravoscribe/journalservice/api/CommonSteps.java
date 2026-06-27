package com.bravoscribe.journalservice.api;

import com.bravoscribe.journalservice.repository.JournalEntryRepository;
import com.bravoscribe.journalservice.repository.TagRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.restassured.RestAssured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonSteps {

    @LocalServerPort
    int port;

    @Autowired
    JournalEntryRepository entryRepo;

    @Autowired
    TagRepository tagRepo;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    StepContext ctx;

    static final UUID MAIN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Before
    public void setUp() {
        RestAssured.port = port;
        entryRepo.deleteAll();
        tagRepo.deleteAll();
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        ctx.reset();
        ctx.setAccessToken(generateToken(MAIN_USER_ID));
        ctx.setOtherUserToken(generateToken(OTHER_USER_ID));
    }

    @Given("I am logged in as {string}")
    public void iAmLoggedInAs(String email) {
        // no-op: token already set in @Before
    }

    @Then("I receive status {int}")
    public void iReceiveStatus(int expectedStatus) {
        assertThat(ctx.getLastResponse().getStatusCode()).isEqualTo(expectedStatus);
    }

    static String generateToken(UUID userId) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.RS256),
                    claims);
            jwt.sign(new RSASSASigner(CucumberSpringConfiguration.RSA_KEY));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
